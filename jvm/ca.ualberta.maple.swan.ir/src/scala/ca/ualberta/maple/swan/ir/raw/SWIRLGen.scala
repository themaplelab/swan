/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.ir.raw

import ca.ualberta.maple.swan.ir.Exceptions.{ExperimentalException, IncorrectSWIRLStructureException, UnexpectedSILFormatException, UnexpectedSILTypeBehaviourException}
import ca.ualberta.maple.swan.ir.{Argument, BinaryOperation, Block, BlockRef, Constants, DynamicDispatchGraph, EnumAssignCase, Function, FunctionAttribute, Literal, Module, ModuleMetadata, Operator, Position, RawInstructionDef, RawOperator, RawOperatorDef, RawTerminator, RawTerminatorDef, RefTable, SILMap, SwitchCase, SwitchEnumCase, Symbol, SymbolRef, Terminator, Type, UnaryOperation}
import ca.ualberta.maple.swan.parser._
import ca.ualberta.maple.swan.utils.Logging

import scala.collection.mutable.ArrayBuffer
import scala.collection.{immutable, mutable}
import scala.util.control.Breaks.{break, breakable}

/*
 * For now, boxes are treated as pointers.
 *
 * Type information either at apply or reference time may be useful for
 * generating stubs later. However, it gets complicated for dynamic dispatch
 * so leave it for now.
 *
 * See SWIRL documentation in Wiki for more info.
 *
 * TODO: Recovery options.
 * TODO: Manually "checked" exceptions are gross.
 */
class SWIRLGen {

  // This isn't true dynamic context. It's just a container to hold
  // the module/function/block when translating instructions.
  class Context(val silModule: SILModule, val silFunction: SILFunction,
                val silBlock: SILBlock, val pos: Option[Position],
                val refTable: RefTable, val instantiatedTypes: mutable.HashSet[String],
                val arguments: ArrayBuffer[Argument], val silMap: SILMap) {
    def globalsSingletonName: String = Constants.globalsSingleton + silModule.toString
  }
  object Context {
    def dummy(silModule: SILModule, refTable: RefTable): Context = {
      new Context(silModule, null, null, null, refTable, null, null, null)
    }
  }

  val NonePosition: Option[Position] = None // For Java

  val NOP: Null = null // explicit NOP marker
  val COPY: Null = null // explicit COPY marker
  
  protected val missingStructs: mutable.Set[String] = mutable.Set[String]()

  @throws[IncorrectSWIRLStructureException]
  @throws[UnexpectedSILFormatException]
  @throws[UnexpectedSILTypeBehaviourException]
  def translateSILModule(silModule: SILModule): Module = {
    Logging.printInfo("Translating " + silModule.meta.file.getName + " to SWIRL")
    val functions = new ArrayBuffer[Function](0)
    val silMap = new SILMap
    silModule.functions.zipWithIndex.foreach(zippedFunction => {
      val silFunction = zippedFunction._1
      val instantiatedTypes = new mutable.HashSet[String]()
      functions.append({
        intermediateSymbols.clear()
        val refTable = new RefTable()
        val blocks = new ArrayBuffer[Block](0)
        var attribute = getFunctionAttribute(silFunction)
        silFunction.blocks.foreach((silBlock: SILBlock) => {
          blocks.append({
            val arguments = new ArrayBuffer[Argument]()
            silBlock.arguments.foreach((a: SILArgument) => {
              val bbArgRef = new SymbolRef(a.valueName)
              refTable.temporaryBBArgs.put(bbArgRef.name, bbArgRef)
              arguments.append(new Argument(bbArgRef,
                Utils.SILTypeToType(a.tpe)))
            })
            val operators = new ArrayBuffer[RawOperatorDef](0)
            silBlock.operatorDefs.foreach((silOperatorDef: SILOperatorDef) => {
              breakable {
                val position: Option[Position] = Utils.SILSourceInfoToPosition(silOperatorDef.sourceInfo)
                val ctx = new Context(silModule, silFunction, silBlock, position, refTable, instantiatedTypes, arguments, silMap)
                val instructions: ArrayBuffer[RawInstructionDef] = translateSILInstruction(SILInstructionDef.operator(silOperatorDef), ctx)
                if (instructions == NOP) {
                  break()
                }
                instructions.foreach((inst: RawInstructionDef) => {
                  inst match {
                    case RawInstructionDef.operator(operatorDef) =>
                      operators.append(operatorDef)
                      silMap.map(silOperatorDef, operatorDef)
                    case _ =>
                      throw new IncorrectSWIRLStructureException("Raw operator expected")
                  }
                })
              }
            })
            val terminator: RawTerminatorDef = {
              val position: Option[Position] = Utils.SILSourceInfoToPosition(silBlock.terminatorDef.sourceInfo)
              val ctx = new Context(silModule, silFunction, silBlock, position, refTable, instantiatedTypes, arguments, silMap)
              val instructions = translateSILInstruction(
                SILInstructionDef.terminator(silBlock.terminatorDef), ctx)
              if (instructions == null) {
                null // Only for while terminators are WIP
              } else {
                var terminator: RawTerminatorDef = null
                instructions.zipWithIndex.foreach(term => {
                  val instruction = term._1
                  if (term._2 != instructions.length - 1) {
                    instruction match {
                      case RawInstructionDef.operator(operatorDef) =>
                        operators.append(operatorDef)
                        silMap.map(silBlock.terminatorDef, operatorDef)
                      case _ =>
                        throw new IncorrectSWIRLStructureException("All instructions before the last instruction must be gen operators")
                    }
                  } else {
                    instruction match {
                      case RawInstructionDef.terminator(terminatorDef) =>
                        terminator = terminatorDef
                      case _ =>
                        throw new IncorrectSWIRLStructureException("Last instruction must be a gen terminator.")
                    }
                  }
                })
                if (terminator == null) {
                  throw new IncorrectSWIRLStructureException("Terminator expected for block " +
                    silBlock.identifier + " in function " + silFunction.name.demangled)
                }
                silMap.map(silBlock.terminatorDef, terminator)
                terminator
              }
            }
            val blockRef = new BlockRef(silBlock.identifier)
            refTable.blocks.put(blockRef.label, blockRef)
            val b = new Block(blockRef, arguments, operators, terminator)
            silMap.map(silBlock, b)
            b
          })
        })
        val returnType = Utils.SILFunctionTypeToReturnType(silFunction.tpe)
        // If function is empty, generate a stub based on return type.
        // However, param types isn't reliable - actual args may be different.
        if (silFunction.blocks.isEmpty) {
          intermediateSymbols.clear()
          val dummyCtx = Context.dummy(silModule, refTable)
          val blockRef = makeBlockRef("bb0", dummyCtx)
          val retRef = makeSymbolRef("%ret", dummyCtx)
          refTable.blocks.put(blockRef.label, blockRef)
          blocks.append(new Block(blockRef, {
            val args = new ArrayBuffer[Argument]()
            Utils.SILFunctionTypeToParamTypes(silFunction.tpe).zipWithIndex.foreach(t => {
              args.append(new Argument(makeSymbolRef("%" + t._2.toString, dummyCtx), t._1))
            })
            args
          }, ArrayBuffer(new RawOperatorDef(
            Operator.neww(new Symbol(retRef, returnType)), None)),
            new RawTerminatorDef(Terminator.ret(retRef), None)))
          attribute = Some(FunctionAttribute.stub)
        }
        val f = new Function(attribute, silFunction.name.demangled, returnType,
          blocks, refTable, immutable.HashSet[String]() ++ instantiatedTypes)
        silMap.map(silFunction, f)
        f
      })
    })

    // Create fake main function. The fake main function initializes globals
    // and then calls the actual main function. We currently can't properly
    // handle the SIL `object` instruction, and therefore we don't consider
    // when a global is initialized with instructions/object. This global
    // initialization is here mostly for completeness and explicitness.
    val mainFunction = functions.find(f => f.name == "main")
    var fmFunction: Option[Function] = None
    if (mainFunction.nonEmpty) {
      val newMainFunctionName = "main_" + silModule.toString
      val fakeMainFunctionName = Constants.fakeMain + silModule.toString
      mainFunction.get.name = newMainFunctionName
      intermediateSymbols.clear()
      val fmRefTable = new RefTable()
      val fmInstantiatedTypes = new immutable.HashSet[String]
      val dummyCtx = Context.dummy(silModule, fmRefTable)
      fmFunction = Some(new Function(Some(FunctionAttribute.entry), fakeMainFunctionName,
        new Type("Int32"), new ArrayBuffer[Block](), fmRefTable, fmInstantiatedTypes))
      val blockRef = makeBlockRef("bb0", dummyCtx)
      val retRef = makeSymbolRef("ret", dummyCtx)
      val block = new Block(blockRef, ArrayBuffer.empty, {
          val ops = new ArrayBuffer[RawOperatorDef]()
          ops
        }, new RawTerminatorDef(Terminator.ret(retRef), None))
      fmFunction.get.blocks.append(block)
      silModule.globalVariables.zipWithIndex.foreach(g => {
        val global = g._1
        val idx = g._2
        val ref = makeSymbolRef("g_" + idx.toString, dummyCtx)
        block.operators.append(new RawOperatorDef(
          Operator.neww(new Symbol(ref, Utils.SILTypeToType(global.tpe))), None))
        block.operators.append(new RawOperatorDef(
          Operator.singletonWrite(ref, dummyCtx.globalsSingletonName, global.globalName.demangled), None))
      })
      val functionRef = makeSymbolRef("main_function_ref", dummyCtx)
      val arg0 = makeSymbolRef("arg0", dummyCtx)
      val arg1 = makeSymbolRef("arg1", dummyCtx)
      block.operators.append(new RawOperatorDef(
        Operator.neww(new Symbol(arg0, new Type("Int32"))), None))
      block.operators.append(new RawOperatorDef(
        Operator.neww(new Symbol(arg1,
          new Type("UnsafeMutablePointer<Optional<UnsafeMutablePointer<Int8>>>"))), None))
      block.operators.append(new RawOperatorDef(
        Operator.functionRef(new Symbol(functionRef, new Type("Any")), newMainFunctionName), None))
      block.operators.append(new RawOperatorDef(
        Operator.apply(new Symbol(retRef, new Type("Int32")), functionRef, ArrayBuffer(arg0, arg1)), None))
      functions.insert(0, fmFunction.get)
    }
    new Module(functions, fmFunction, Some(new DynamicDispatchGraph(silModule)), silMap, new ModuleMetadata(None, Some(silModule.meta.file)))
  }

  private def getFunctionAttribute(silFunction: SILFunction): Option[FunctionAttribute] = {
    silFunction.tpe match {
      case attributedType: SILType.attributedType => {
        attributedType.attributes.foreach(attr => {
          if (attr == SILTypeAttribute.yieldOnce) {
            return Some(FunctionAttribute.coroutine)
          }
        })
      }
      case _ =>
    }
    None
  }

  // Needs to be cleared for every function.
  private val intermediateSymbols: mutable.HashMap[String, Integer] = new mutable.HashMap()

  // We make it explicit that the value generated is an intermediate value
  // for the given value.
  // We keep track of these values so we do not generate duplicates.
  // Can be used for any string, like block names.
  protected def generateSymbolName(value: String, ctx: Context): SymbolRef = {
    if (!intermediateSymbols.contains(value)) {
      intermediateSymbols.put(value, 0)
    }
    val ret = value + "i" + intermediateSymbols(value).toString
    intermediateSymbols(value) = intermediateSymbols(value) + 1
    makeSymbolRef(ret, ctx)
  }

  protected def makeSymbolRef(ref: String, ctx: Context): SymbolRef = {
    val symbols = ctx.refTable.symbols
    if (symbols.contains(ref)) {
      symbols.put(ref, symbols(ref))
      symbols(ref)
    } else {
      if (ctx.refTable.temporaryBBArgs.contains(ref)) {
        symbols.put(ref, ctx.refTable.temporaryBBArgs(ref))
        ctx.refTable.temporaryBBArgs.remove(ref)
        symbols(ref)
      } else {
        val symbolRef = new SymbolRef(ref)
        symbols.put(ref, symbolRef)
        symbolRef
      }
    }
  }

  protected def makeBlockRef(ref: String, ctx: Context): BlockRef = {
    val blocks = ctx.refTable.blocks
    if (blocks.contains(ref)) {
      blocks.put(ref, blocks(ref))
      blocks(ref)
    } else {
      val blockRef = new BlockRef(ref)
      blocks.put(ref, blockRef)
      blockRef
    }
  }

  @throws[IncorrectSWIRLStructureException]
  @throws[UnexpectedSILFormatException]
  @throws[UnexpectedSILTypeBehaviourException]
  def translateSILInstruction(silInstructionDef: SILInstructionDef, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    silInstructionDef match {
      case SILInstructionDef.operator(operator) => {
        val result = operator.result
        val instruction = operator.operator
        instruction match {
          case inst: SILOperator.allocStack => visitAllocStack(result, inst, ctx)
          case inst: SILOperator.allocRef => visitAllocRef(result, inst, ctx)
          case inst: SILOperator.allocRefDynamic => visitAllocRefDynamic(result, inst, ctx)
          case inst: SILOperator.allocBox => visitAllocBox(result, inst, ctx)
          case inst: SILOperator.allocValueBuffer => visitAllocValueBuffer(result, inst, ctx)
          case inst: SILOperator.allocGlobal => visitAllocGlobal(result, inst, ctx)
          case inst: SILOperator.deallocStack => visitDeallocStack(result, inst, ctx)
          case inst: SILOperator.deallocBox => visitDeallocBox(result, inst, ctx)
          case inst: SILOperator.projectBox => visitProjectBox(result, inst, ctx)
          case inst: SILOperator.deallocRef => visitDeallocRef(result, inst, ctx)
          case inst: SILOperator.deallocPartialRef => visitDeallocPartialRef(result, inst, ctx)
          case inst: SILOperator.debugValue => visitDebugValue(result, inst, ctx)
          case inst: SILOperator.debugValueAddr => visitDebugValueAddr(result, inst, ctx)
          case inst: SILOperator.load => visitLoad(result, inst, ctx)
          case inst: SILOperator.store => visitStore(result, inst, ctx)
          case inst: SILOperator.loadBorrow => visitLoadBorrow(result, inst, ctx)
          case inst: SILOperator.storeBorrow => visitStoreBorrow(result, inst, ctx)
          case inst: SILOperator.beginBorrow => visitBeginBorrow(result, inst, ctx)
          case inst: SILOperator.endBorrow => visitEndBorrow(result, inst, ctx)
          case inst: SILOperator.endLifetime => visitEndLifetime(result, inst, ctx)
          case inst: SILOperator.copyAddr => visitCopyAddr(result, inst, ctx)
          case inst: SILOperator.destroyAddr => visitDestroyAddr(result, inst, ctx)
          case inst: SILOperator.indexAddr => visitIndexAddr(result, inst, ctx)
          case inst: SILOperator.bindMemory => visitBindMemory(result, inst, ctx)
          case inst: SILOperator.beginAccess => visitBeginAccess(result, inst, ctx)
          case inst: SILOperator.endAccess => visitEndAccess(result, inst, ctx)
          case inst: SILOperator.strongRetain => visitStrongRetain(result, inst, ctx)
          case inst: SILOperator.strongRelease => visitStrongRelease(result, inst, ctx)
          case inst: SILOperator.copyUnownedValue => visitCopyUnownedValue(result, inst, ctx)
          case inst: SILOperator.strongCopyUnownedValue => visitStrongCopyUnownedValue(result, inst, ctx)
          case inst: SILOperator.unownedRetain => visitUnownedRetain(result, inst, ctx)
          case inst: SILOperator.loadWeak => visitLoadWeak(result, inst, ctx)
          case inst: SILOperator.storeWeak => visitStoreWeak(result, inst, ctx)
          case inst: SILOperator.loadUnowned => visitLoadUnowned(result, inst, ctx)
          case inst: SILOperator.storeUnowned => visitStoreUnowned(result, inst, ctx)
          case inst: SILOperator.markDependence => visitMarkDependence(result, inst, ctx)
          case inst: SILOperator.isEscapingClosure => visitIsEscapingClosure(result, inst, ctx)
          case inst: SILOperator.copyBlock => visitCopyBlock(result, inst, ctx)
          case inst: SILOperator.copyBlockWithoutEscaping => visitCopyBlockWithoutEscaping(result, inst, ctx)
          case inst: SILOperator.functionRef => visitFunctionRef(result, inst, ctx)
          case inst: SILOperator.dynamicFunctionRef => visitDynamicFunctionRef(result, inst, ctx)
          case inst: SILOperator.prevDynamicFunctionRef => visitPrevDynamicFunctionRef(result, inst, ctx)
          case inst: SILOperator.globalAddr => visitGlobalAddr(result, inst, ctx)
          case inst: SILOperator.integerLiteral => visitIntegerLiteral(result, inst, ctx)
          case inst: SILOperator.floatLiteral => visitFloatLiteral(result, inst, ctx)
          case inst: SILOperator.stringLiteral => visitStringLiteral(result, inst, ctx)
          case inst: SILOperator.classMethod => visitClassMethod(result, inst, ctx)
          case inst: SILOperator.objcMethod => visitObjCMethod(result, inst, ctx)
          case inst: SILOperator.objcSuperMethod => visitObjCSuperMethod(result, inst, ctx)
          case inst: SILOperator.witnessMethod => visitWitnessMethod(result, inst, ctx)
          case inst: SILOperator.apply => visitApply(result, inst, ctx)
          case inst: SILOperator.beginApply => visitBeginApply(result, inst, ctx)
          case inst: SILOperator.abortApply => visitAbortApply(result, inst, ctx)
          case inst: SILOperator.endApply => visitEndApply(result, inst, ctx)
          case inst: SILOperator.partialApply => visitPartialApply(result, inst, ctx)
          case inst: SILOperator.builtin => visitBuiltin(result, inst, ctx)
          case inst: SILOperator.metatype => visitMetatype(result, inst, ctx)
          case inst: SILOperator.valueMetatype => visitValueMetatype(result, inst, ctx)
          case inst: SILOperator.existentialMetatype => visitExistentialMetatype(result, inst, ctx)
          case inst: SILOperator.objcProtocol => visitObjCProtocol(result, inst, ctx)
          case inst: SILOperator.retainValue => visitRetainValue(result, inst, ctx)
          case inst: SILOperator.copyValue => visitCopyValue(result, inst, ctx)
          case inst: SILOperator.strongCopyUnmanagedValue => visitStrongCopyUnmanagedValue(result, inst, ctx)
          case inst: SILOperator.releaseValue => visitReleaseValue(result, inst, ctx)
          case inst: SILOperator.unmanagedReleaseValue => visitUnmanagedReleaseValue(result, inst, ctx)
          case inst: SILOperator.destroyValue => visitDestroyValue(result, inst, ctx)
          case inst: SILOperator.autoreleaseValue => visitAutoreleaseValue(result, inst, ctx)
          case inst: SILOperator.tuple => visitTuple(result, inst, ctx)
          case inst: SILOperator.tupleExtract => visitTupleExtract(result, inst, ctx)
          case inst: SILOperator.tupleElementAddr => visitTupleElementAddr(result, inst, ctx)
          case inst: SILOperator.destructureTuple => visitDestructureTuple(result, inst, ctx)
          case inst: SILOperator.struct => visitStruct(result, inst, ctx)
          case inst: SILOperator.structExtract => visitStructExtract(result, inst, ctx)
          case inst: SILOperator.structElementAddr => visitStructElementAddr(result, inst, ctx)
          case inst: SILOperator.objct => visitObject(result, inst, ctx)
          case inst: SILOperator.refElementAddr => visitRefElementAddr(result, inst, ctx)
          case inst: SILOperator.refTailAddr => visitRefTailAddr(result, inst, ctx)
          case inst: SILOperator.enm => visitEnum(result, inst, ctx)
          case inst: SILOperator.uncheckedEnumData => visitUncheckedEnumData(result, inst, ctx)
          case inst: SILOperator.initEnumDataAddr => visitInitEnumDataAddr(result, inst, ctx)
          case inst: SILOperator.injectEnumAddr => visitInjectEnumAddr(result, inst, ctx)
          case inst: SILOperator.uncheckedTakeEnumDataAddr => visitUncheckedTakeEnumDataAddr(result, inst, ctx)
          case inst: SILOperator.selectEnum => visitSelectEnum(result, inst, ctx)
          case inst: SILOperator.selectEnumAddr => visitSelectEnumAddr(result, inst, ctx)
          case inst: SILOperator.initExistentialAddr => visitInitExistentialAddr(result, inst, ctx)
          case inst: SILOperator.deinitExistentialAddr => visitDeinitExistentialAddr(result, inst, ctx)
          case inst: SILOperator.openExistentialAddr => visitOpenExistentialAddr(result, inst, ctx)
          case inst: SILOperator.initExistentialRef => visitInitExistentialRef(result, inst, ctx)
          case inst: SILOperator.openExistentialRef => visitOpenExistentialRef(result, inst, ctx)
          case inst: SILOperator.initExistentialMetatype => visitInitExistentialMetatype(result, inst, ctx)
          case inst: SILOperator.openExistentialMetatype => visitOpenExistentialMetatype(result, inst, ctx)
          case inst: SILOperator.allocExistentialBox => visitAllocExistentialBox(result, inst, ctx)
          case inst: SILOperator.projectExistentialBox => visitProjectExistentialBox(result, inst, ctx)
          case inst: SILOperator.openExistentialBox => visitOpenExistentialBox(result, inst, ctx)
          case inst: SILOperator.deallocExistentialBox => visitDeallocExistentialBox(result, inst, ctx)
          case inst: SILOperator.projectBlockStorage => visitProjectBlockStorage(result, inst, ctx)
          case inst: SILOperator.initBlockStorageHeader => visitInitBlockStorageHeader(result, inst, ctx)
          case inst: SILOperator.upcast => visitUpcast(result, inst, ctx)
          case inst: SILOperator.addressToPointer => visitAddressToPointer(result, inst, ctx)
          case inst: SILOperator.pointerToAddress => visitPointerToAddress(result, inst, ctx)
          case inst: SILOperator.uncheckedRefCast => visitUncheckedRefCast(result, inst, ctx)
          case inst: SILOperator.uncheckedAddrCast => visitUncheckedAddrCast(result, inst, ctx)
          case inst: SILOperator.uncheckedTrivialBitCast => visitUncheckedTrivialBitCast(result, inst, ctx)
          case inst: SILOperator.uncheckedOwnershipConversion => visitUncheckedOwnershipConverstion(result, inst, ctx)
          case inst: SILOperator.rawPointerToRef => visitRawPointerToRef(result, inst, ctx)
          case inst: SILOperator.refToUnowned => visitRefToUnowned(result, inst, ctx)
          case inst: SILOperator.refToUnmanaged => visitRefToUnmanaged(result, inst, ctx)
          case inst: SILOperator.unmanagedToRef => visitUnmanagedToRef(result, inst, ctx)
          case inst: SILOperator.convertFunction => visitConvertFunction(result, inst, ctx)
          case inst: SILOperator.convertEscapeToNoescape => visitConvertEscapeToNoEscape(result, inst, ctx)
          case inst: SILOperator.valueToBridgeObject => visitValueToBridgeObject(result, inst, ctx)
          case inst: SILOperator.bridgeObjectToRef => visitBridgeObjectToRef(result, inst, ctx)
          case inst: SILOperator.thinToThickFunction => visitThinToThickFunction(result, inst, ctx)
          case inst: SILOperator.thickToObjcMetatype => visitThickToObjCMetatype(result, inst, ctx)
          case inst: SILOperator.objcToThickMetatype => visitObjCToThickMetatype(result, inst, ctx)
          case inst: SILOperator.objcMetatypeToObject => visitObjCMetatypeToObject(result, inst, ctx)
          case inst: SILOperator.objcExistentialMetatypeToObject => visitObjCExistentialMetatypeToObject(result, inst, ctx)
          case inst: SILOperator.unconditionalCheckedCast => visitUnconditionalCheckedCast(result, inst, ctx)
          case inst: SILOperator.unconditionalCheckedCastAddr => visitUnconditionalCheckedCastAddr(result, inst, ctx)
          case inst: SILOperator.condFail => visitCondFail(result, inst, ctx)
        }
      }
      // These methods should return an array with exactly one terminator.
      // The terminator can be preceded by operators.
      case SILInstructionDef.terminator(terminatorDef) => {
        val instruction = terminatorDef.terminator
        instruction match {
          case SILTerminator.unreachable => visitUnreachable(ctx)
          case inst: SILTerminator.ret => visitReturn(inst, ctx)
          case inst: SILTerminator.thro => visitThrow(inst, ctx)
          case inst: SILTerminator.yld => visitYield(inst, ctx)
          case SILTerminator.unwind => visitUnwind(ctx)
          case inst: SILTerminator.br => visitBr(inst, ctx)
          case inst: SILTerminator.condBr => visitCondBr(inst, ctx)
          case inst: SILTerminator.switchValue => visitSwitchValue(inst, ctx)
          case inst: SILTerminator.switchEnum => visitSwitchEnum(inst, ctx)
          case inst: SILTerminator.switchEnumAddr => visitSwitchEnumAddr(inst, ctx)
          case inst: SILTerminator.dynamicMethodBr => visitDynamicMethodBr(inst, ctx)
          case inst: SILTerminator.checkedCastBr => visitCheckedCastBr(inst, ctx)
          case inst: SILTerminator.checkedCastAddrBr => visitCheckedCastAddrBr(inst, ctx)
          case inst: SILTerminator.tryApply => visitTryApply(inst, ctx)
        }
      }
    }
  }

  private def makeOperator(ctx: Context, operator: RawOperator*): ArrayBuffer[RawInstructionDef] = {
    val arr: ArrayBuffer[RawInstructionDef] = ArrayBuffer.empty[RawInstructionDef]
    operator.zipWithIndex.foreach( (op: (RawOperator, Int))  => {
      arr.append(RawInstructionDef.operator(new RawOperatorDef(op._1, ctx.pos)))
    })
    arr
  }

  private def stringArrayToSymbolRefArrayBuffer(arr: ArrayBuffer[String], ctx: Context): ArrayBuffer[SymbolRef] = {
    val ret = new ArrayBuffer[SymbolRef]()
    arr.foreach(e => {
      ret.append(makeSymbolRef(e, ctx))
    })
    ret
  }

  private def makeTerminator(ctx: Context, terminator: RawTerminator): ArrayBuffer[RawInstructionDef] = {
    ArrayBuffer[RawInstructionDef](RawInstructionDef.terminator(new RawTerminatorDef(terminator, ctx.pos)))
  }

  private def copySymbol(from: String, to: String, ctx: Context): Null = {
    ctx.refTable.symbols.put(to, makeSymbolRef(from, ctx))
    COPY
  }

  @throws[UnexpectedSILFormatException]
  private def verifySILResult(r: Option[SILResult], i: Int): Unit = {
    if (!(r.nonEmpty && r.get.valueNames.length == i)) {
      throw new UnexpectedSILFormatException("Expected " + i.toString + " result")
    }
  }

  /* ALLOCATION AND DEALLOCATION */

  @throws[UnexpectedSILFormatException]
  def visitAllocStack(r: Option[SILResult], I: SILOperator.allocStack, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToPointerType(I.tpe), ctx) // $T to $*T
    ctx.instantiatedTypes.add(Utils.print(I.tpe))
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  def visitAllocRef(r: Option[SILResult], I: SILOperator.allocRef, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    ctx.instantiatedTypes.add(Utils.print(I.tpe))
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  def visitAllocRefDynamic(r: Option[SILResult], I: SILOperator.allocRefDynamic, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    ctx.instantiatedTypes.add(Utils.print(I.tpe))
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  def visitAllocBox(r: Option[SILResult], I: SILOperator.allocBox, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    // Boxes are treated like pointers.
    val result = getSingleResult(r, Utils.SILTypeToPointerType(I.tpe), ctx) // $T to $*T
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  def visitAllocValueBuffer(r: Option[SILResult], I: SILOperator.allocValueBuffer, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToPointerType(I.tpe), ctx) // $T to $*T
    makeOperator(ctx, Operator.neww(result))
  }

  def visitAllocGlobal(r: Option[SILResult], I: SILOperator.allocGlobal, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  def visitDeallocStack(r: Option[SILResult], I: SILOperator.deallocStack, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  def visitDeallocBox(r: Option[SILResult], I: SILOperator.deallocBox, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  @throws[UnexpectedSILFormatException]
  def visitProjectBox(r: Option[SILResult], I: SILOperator.projectBox, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  def visitDeallocRef(r: Option[SILResult], I: SILOperator.deallocRef, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  def visitDeallocPartialRef(r: Option[SILResult], I: SILOperator.deallocPartialRef, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  // def visitDeallocValueBuffer(r: Option[SILResult], I: SILOperator.deallocValueBuffer, ctx: Context): ArrayBuffer[RawInstructionDef]
  // visitProjectValueBuffer(r: Option[SILResult], I: SILOperator.projectValueBuffer, ctx: Context): ArrayBuffer[RawInstructionDef]

  /* DEBUG INFORMATION */

  def visitDebugValue(r: Option[SILResult], I: SILOperator.debugValue, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    if (I.operand.value != "undef") {
      I.attributes.foreach {
        case SILDebugAttribute.argno(index) => {
          // For some reason "argno" is not necessarily within bounds.
          if (ctx.arguments.length >= index) {
            ctx.arguments(index - 1).pos = ctx.pos
          }
        }
        case SILDebugAttribute.name(name) => {
          makeSymbolRef(I.operand.value, ctx).name = name
        }
        case SILDebugAttribute.let =>
        case SILDebugAttribute.variable =>
      }
    }
    NOP
  }

  def visitDebugValueAddr(r: Option[SILResult], I: SILOperator.debugValueAddr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    I.attributes.foreach {
      case SILDebugAttribute.argno(index) => {
        if (ctx.arguments.length >= index) {
          ctx.arguments(index - 1).pos = ctx.pos
        }
      }
      case SILDebugAttribute.name(name) => {
        makeSymbolRef(I.operand.value, ctx).name = name
      }
      case SILDebugAttribute.let =>
      case SILDebugAttribute.variable =>
    }
    NOP
  }

  /* ACCESSING MEMORY */

  @throws[UnexpectedSILFormatException]
  @throws[UnexpectedSILTypeBehaviourException]
  def visitLoad(r: Option[SILResult], I: SILOperator.load, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILPointerTypeToType(I.operand.tpe), ctx) // $*T to $T
    makeOperator(ctx, Operator.pointerRead(result, makeSymbolRef(I.operand.value, ctx)))
  }

  def visitStore(r: Option[SILResult], I: SILOperator.store, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    makeOperator(ctx, Operator.pointerWrite(makeSymbolRef(I.from, ctx), makeSymbolRef(I.to.value, ctx)))
  }

  // def visitStoreBorrow(r: Option[SILResult], I: SILOperator.storeBorrow, ctx: Context): ArrayBuffer[RawInstructionDef]

  @throws[UnexpectedSILFormatException]
  @throws[UnexpectedSILTypeBehaviourException]
  @throws[UnexpectedSILTypeBehaviourException]
  def visitLoadBorrow(r: Option[SILResult], I: SILOperator.loadBorrow, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILPointerTypeToType(I.operand.tpe), ctx) // $*T to $T
    makeOperator(ctx, Operator.pointerRead(result, makeSymbolRef(I.operand.value, ctx)))
  }

  @throws[UnexpectedSILFormatException]
  @throws[UnexpectedSILTypeBehaviourException]
  @throws[UnexpectedSILTypeBehaviourException]
  def visitStoreBorrow(r: Option[SILResult], I: SILOperator.storeBorrow, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    makeOperator(ctx, Operator.pointerWrite(makeSymbolRef(I.from, ctx), makeSymbolRef(I.to.value, ctx)))
  }

  @throws[UnexpectedSILFormatException]
  def visitBeginBorrow(r: Option[SILResult], I: SILOperator.beginBorrow, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  def visitEndBorrow(r: Option[SILResult], I: SILOperator.endBorrow, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  def visitEndLifetime(r: Option[SILResult], I: SILOperator.endLifetime, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  @throws[UnexpectedSILTypeBehaviourException]
  def visitCopyAddr(r: Option[SILResult], I: SILOperator.copyAddr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val pointerReadResult = new Symbol(
      generateSymbolName(I.value, ctx), Utils.SILPointerTypeToType(I.operand.tpe)) // $*T to $T
    makeOperator(
      ctx,
      Operator.pointerRead(pointerReadResult, makeSymbolRef(I.value, ctx)),
      Operator.pointerWrite(pointerReadResult.ref, makeSymbolRef(I.operand.value, ctx)))
  }

  def visitDestroyAddr(r: Option[SILResult], I: SILOperator.destroyAddr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  @throws[UnexpectedSILFormatException]
  def visitIndexAddr(r: Option[SILResult], I: SILOperator.indexAddr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.addr.tpe), ctx)
    makeOperator(ctx, Operator.arrayRead(result, makeSymbolRef(I.addr.value, ctx)))
  }

  def visitBindMemory(r: Option[SILResult], I: SILOperator.bindMemory, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  // def visitTailAddr(r: Option[SILResult], I: SILOperator.tailAddr, ctx: Context): ArrayBuffer[RawInstructionDef]
  // def visitIndexRawPointer(r: Option[SILResult], I: SILOperator.indexRawPointer, ctx: Context): ArrayBuffer[RawInstructionDef]
  // def visitBindMemory(r: Option[SILResult], I: SILOperator.bindMemory, ctx: Context): ArrayBuffer[RawInstructionDef]

  @throws[UnexpectedSILFormatException]
  def visitBeginAccess(r: Option[SILResult], I: SILOperator.beginAccess, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  def visitEndAccess(r: Option[SILResult], I: SILOperator.endAccess, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  // def visitBeginUnpairedAccess(r: Option[SILResult], I: SILOperator.beginUnpairedAccess, ctx: Context): ArrayBuffer[RawInstructionDef]
  // def visitEndUnpairedAccess(r: Option[SILResult], I: SILOperator.endUnpairedAccess, ctx: Context): ArrayBuffer[RawInstructionDef]

  /* REFERENCE COUNTING */

  def visitStrongRetain(r: Option[SILResult], I: SILOperator.strongRetain, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  def visitStrongRelease(r: Option[SILResult], I: SILOperator.strongRelease, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  def visitCopyUnownedValue(r: Option[SILResult], I: SILOperator.copyUnownedValue, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    // TODO: Result should have the @sil_unowned removed from operand type.
    val result = getSingleResult(r, Utils.SILTypeToType(I.operand.tpe), ctx)
    // Assign for now, maybe COPY would work.
    makeOperator(ctx, Operator.assign(result, makeSymbolRef(I.operand.value, ctx)))
  }

  def visitStrongCopyUnownedValue(r: Option[SILResult], I: SILOperator.strongCopyUnownedValue, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    // TODO: Result should have the @owned removed from operand type.
    val result = getSingleResult(r, Utils.SILTypeToType(I.operand.tpe), ctx)
    // Assign for now, maybe COPY would work.
    makeOperator(ctx, Operator.assign(result, makeSymbolRef(I.operand.value, ctx)))
  }

  // def visitSetDeallocating(r: Option[SILResult], I: SILOperator.setDeallocating, ctx: Context): ArrayBuffer[RawInstructionDef]
  // def visitStrongRetainUnowned(r: Option[SILResult], I: SILOperator.strongRetainUnowned, ctx: Context): ArrayBuffer[RawInstructionDef]

  @throws[UnexpectedSILFormatException]
  @throws[UnexpectedSILTypeBehaviourException]
  def visitUnownedRetain(r: Option[SILResult], I: SILOperator.unownedRetain, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  // def visitUnownedRelease(r: Option[SILResult], I: SILOperator.unownedRelease, ctx: Context): ArrayBuffer[RawInstructionDef]

  @throws[UnexpectedSILFormatException]
  @throws[UnexpectedSILTypeBehaviourException]
  def visitLoadWeak(r: Option[SILResult], I: SILOperator.loadWeak, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILPointerTypeToType(I.operand.tpe), ctx) // $*T to $T
    makeOperator(ctx, Operator.pointerRead(result, makeSymbolRef(I.operand.value, ctx)))
  }

  def visitStoreWeak(r: Option[SILResult], I: SILOperator.storeWeak, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    makeOperator(ctx, Operator.pointerWrite(makeSymbolRef(I.from, ctx), makeSymbolRef(I.to.value, ctx)))
  }

  @throws[UnexpectedSILFormatException]
  @throws[UnexpectedSILTypeBehaviourException]
  def visitLoadUnowned(r: Option[SILResult], I: SILOperator.loadUnowned, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILPointerTypeToType(I.operand.tpe), ctx) // $*T to $T
    makeOperator(ctx, Operator.pointerRead(result, makeSymbolRef(I.operand.value, ctx)))
  }

  def visitStoreUnowned(r: Option[SILResult], I: SILOperator.storeUnowned, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    makeOperator(ctx, Operator.pointerWrite(makeSymbolRef(I.from, ctx), makeSymbolRef(I.to.value, ctx)))
  }

  // def visitFixLifetime(r: Option[SILResult], I: SILOperator.fixLifetime, ctx: Context): ArrayBuffer[RawInstructionDef]

  @throws[UnexpectedSILFormatException]
  def visitMarkDependence(r: Option[SILResult], I: SILOperator.markDependence, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  // def visitIsUnique(r: Option[SILResult], I: SILOperator.isUnique, ctx: Context): ArrayBuffer[RawInstructionDef]

  @throws[UnexpectedSILFormatException]
  def visitIsEscapingClosure(r: Option[SILResult], I: SILOperator.isEscapingClosure, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(SILType.namedType("Builtin.Int1")), ctx)
    makeOperator(ctx, Operator.unaryOp(result, UnaryOperation.arbitrary, makeSymbolRef(I.operand.value, ctx)))
  }

  @throws[UnexpectedSILFormatException]
  def visitCopyBlock(r: Option[SILResult], I: SILOperator.copyBlock, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.operand.tpe), ctx)
    makeOperator(ctx, Operator.assign(result, makeSymbolRef(I.operand.value, ctx)))
  }

  @throws[UnexpectedSILFormatException]
  def visitCopyBlockWithoutEscaping(r: Option[SILResult], I: SILOperator.copyBlockWithoutEscaping, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.operand1.tpe), ctx)
    makeOperator(ctx, Operator.assign(result, makeSymbolRef(I.operand1.value, ctx)))
  }

  /* LITERALS */

  @throws[UnexpectedSILFormatException]
  def visitFunctionRef(r: Option[SILResult], I: SILOperator.functionRef, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.functionRef(result, I.name.demangled))
  }

  @throws[UnexpectedSILFormatException]
  def visitDynamicFunctionRef(r: Option[SILResult], I: SILOperator.dynamicFunctionRef, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.functionRef(result, I.name.demangled))
  }

  @throws[UnexpectedSILFormatException]
  def visitPrevDynamicFunctionRef(r: Option[SILResult], I: SILOperator.prevDynamicFunctionRef, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.functionRef(result, I.name.demangled))
  }

  @throws[UnexpectedSILFormatException]
  def visitGlobalAddr(r: Option[SILResult], I: SILOperator.globalAddr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.singletonRead(result, ctx.globalsSingletonName, I.name.demangled))
  }

  // def visitGlobalValue(r: Option[SILResult], I: SILOperator.globalValue, ctx: Context): ArrayBuffer[RawInstructionDef]

  @throws[UnexpectedSILFormatException]
  def visitIntegerLiteral(r: Option[SILResult], I: SILOperator.integerLiteral, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.literal(result, Literal.int(I.value)))
  }

  @throws[UnexpectedSILFormatException]
  def visitFloatLiteral(r: Option[SILResult], I: SILOperator.floatLiteral, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.literal(result, Literal.float(Utils.SILFloatStringToDouble(I.value))))
  }

  @throws[UnexpectedSILFormatException]
  def visitStringLiteral(r: Option[SILResult], I: SILOperator.stringLiteral, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(SILType.namedType("$Builtin.RawPointer")), ctx)
    makeOperator(ctx, Operator.literal(result, Literal.string(I.value)))
  }

  /* DYNAMIC DISPATCH */

  @throws[UnexpectedSILFormatException]
  def visitClassMethod(r: Option[SILResult], I: SILOperator.classMethod, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.dynamicRef(result, Utils.print(I.declRef)))
  }

  @throws[UnexpectedSILFormatException]
  def visitObjCMethod(r: Option[SILResult], I: SILOperator.objcMethod, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.builtinRef(result, Utils.print(I.declRef)))
  }

  // def visitSuperMethod(r: Option[SILResult], I: SILOperator.superMethod, ctx: Context): ArrayBuffer[RawInstructionDef]

  @throws[UnexpectedSILFormatException]
  def visitObjCSuperMethod(r: Option[SILResult], I: SILOperator.objcSuperMethod, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    if (I.declRef.name.length < 2) { // #T.method[...]
      throw new UnexpectedSILFormatException("Expected decl ref of objc_super_method to have at least two components: " + Utils.print(I.declRef))
    }
    makeOperator(ctx, Operator.builtinRef(result, Utils.print(I.declRef)))
  }

  @throws[UnexpectedSILFormatException]
  def visitWitnessMethod(r: Option[SILResult], I: SILOperator.witnessMethod, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    if (I.declRef.name.length < 2) { // #T.method[...]
      throw new UnexpectedSILFormatException("Expected decl ref of witness_method to have at least two components: " + Utils.print(I.declRef))
    }
    makeOperator(ctx, Operator.dynamicRef(result, Utils.print(I.declRef)))
  }

  /* FUNCTION APPLICATION */

  @throws[UnexpectedSILFormatException]
  def visitApply(r: Option[SILResult], I: SILOperator.apply, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILFunctionTypeToReturnType(I.tpe), ctx)
    makeOperator(ctx, Operator.apply(result, makeSymbolRef(I.value, ctx), stringArrayToSymbolRefArrayBuffer(I.arguments, ctx)))
  }

  @throws[UnexpectedSILFormatException]
  def visitBeginApply(r: Option[SILResult], I: SILOperator.beginApply, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    if (r.isEmpty) {
      throw new UnexpectedSILFormatException("begin_apply instruction expected to have at least one result")
    }
    val yieldVal: Option[SymbolRef] = {
      if (r.get.valueNames.length > 1) {
        Some(makeSymbolRef(r.get.valueNames(0), ctx)) // yield value is always the first return value (when rets > 1)
      } else {
        None
      }
    }
    // Coroutines don't need a yield result. However, we create a dummy one in
    // that case to stay consistent with the architecture.
    val result = {
      if (yieldVal.nonEmpty) {
        new Symbol(yieldVal.get, Utils.SILFunctionTypeToReturnType(I.tpe))
      } else {
        // Note: Do NOT create a symbol ref entry for the token because the symbol table will break.
        // All symbol ref values have to appear as return values. This is coupled to end_apply and
        // abort_apply being NOPs.
        val token = r.get.valueNames(r.get.valueNames.length - 1) // token is always last return value
        new Symbol(generateSymbolName(token, ctx), Utils.SILTypeToType(SILType.namedType("*Any")))
      }
    }
    val operators = new ArrayBuffer[RawInstructionDef]()
    // TODO: This is a janky/temporary way to handle more than one yielded
    //  value and is required to not blow up the symbol table.
    if (r.get.valueNames.length > 2) {
      val resultTypes = Utils.SILFunctionTupleTypeToReturnType(I.tpe, removeAttributes = true)
      r.get.valueNames.zipWithIndex.foreach(vn => {
        val idx = vn._2
        if (idx > 0 && idx < r.get.valueNames.length - 1) {
          val r = new Symbol(makeSymbolRef(vn._1, ctx), resultTypes(idx))
          operators.append(makeOperator(ctx, Operator.neww(r))(0))
        }
      })
    }
    operators.append(makeOperator(ctx,
      Operator.apply/*Coroutine*/(result, makeSymbolRef(I.value, ctx),
        stringArrayToSymbolRefArrayBuffer(I.arguments, ctx),
      /* new Symbol(token, Utils.SILTypeToType(SILType.namedType("*Any")))*/ ))(0))
    operators
  }

  def visitAbortApply(r: Option[SILResult], I: SILOperator.abortApply, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    // makeOperator(ctx, Operator.abortCoroutine(makeSymbolRef(I.value, ctx)))
    NOP
  }

  def visitEndApply(r: Option[SILResult], I: SILOperator.endApply, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    // makeOperator(ctx, Operator.endCoroutine(makeSymbolRef(I.value, ctx)))
    NOP
  }

  def visitPartialApply(r: Option[SILResult], I: SILOperator.partialApply, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    // TODO: Need to first understand partial apply dataflow semantics.
    //  The return type is the partially-applied function type.
    // For now, just create a new value
    val result = getSingleResult(r, Utils.SILFunctionTypeToReturnType(I.tpe), ctx)
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  def visitBuiltin(r: Option[SILResult], I: SILOperator.builtin, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    // Use Any type because we don't know the type of the function ref value.
    val functionRef = new Symbol(generateSymbolName(r.get.valueNames(0), ctx), new Type())
    val arguments = ArrayBuffer[SymbolRef]()
    I.operands.foreach(op => {
      arguments.append(makeSymbolRef(op.value, ctx))
    })
    makeOperator(
      ctx,
      Operator.builtinRef(functionRef, I.name),
      Operator.apply(result, functionRef.ref, arguments)
    )
  }

  /* METATYPES */

  @throws[UnexpectedSILFormatException]
  def visitMetatype(r: Option[SILResult], I: SILOperator.metatype, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  def visitValueMetatype(r: Option[SILResult], I: SILOperator.valueMetatype, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    // I'm not sure what the operand is used for. The return type seems to already be provided
    // in the instruction. However, the documentation refers to a "dynamic metatype" of the operand.
    // For now, use the provided metatype.
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  def visitExistentialMetatype(r: Option[SILResult], I: SILOperator.existentialMetatype, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    // Same as value_metatype for now.
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  def visitObjCProtocol(r: Option[SILResult], I: SILOperator.objcProtocol, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.neww(result))
  }

  /* AGGREGATE TYPES */

  def visitRetainValue(r: Option[SILResult], I: SILOperator.retainValue, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  // def visitRetainValueAddr(r: Option[SILResult], I: SILOperator.retainValueAddr, ctx: Context): ArrayBuffer[RawInstructionDef]
  // def visitUnmanagedRetainValue(r: Option[SILResult], I: SILOperator.unmanagedRetainValue, ctx: Context): ArrayBuffer[RawInstructionDef]

  @throws[UnexpectedSILFormatException]
  def visitCopyValue(r: Option[SILResult], I: SILOperator.copyValue, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitStrongCopyUnmanagedValue(r: Option[SILResult], I: SILOperator.strongCopyUnmanagedValue, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  def visitReleaseValue(r: Option[SILResult], I: SILOperator.releaseValue, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  // def visitReleaseValueAddr(r: Option[SILResult], I: SILOperator.releaseValueAddr, ctx: Context): ArrayBuffer[RawInstructionDef]

  def visitUnmanagedReleaseValue(r: Option[SILResult], I: SILOperator.unmanagedReleaseValue, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  def visitDestroyValue(r: Option[SILResult], I: SILOperator.destroyValue, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  def visitAutoreleaseValue(r: Option[SILResult], I: SILOperator.autoreleaseValue, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  @throws[UnexpectedSILFormatException]
  def visitTuple(r: Option[SILResult], I: SILOperator.tuple, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTupleElementsToType(I.elements), ctx)
    val operators = ArrayBuffer[RawInstructionDef]()
    operators.append(makeOperator(ctx, Operator.neww(result))(0))
    I.elements match {
      case SILTupleElements.labeled(_, values) => {
        values.zipWithIndex.foreach(value => {
          operators.append(makeOperator(ctx,
            Operator.fieldWrite(makeSymbolRef(value._1, ctx), result.ref, value._2.toString))(0))
        })
      }
      case SILTupleElements.unlabeled(operands) => {
        operands.zipWithIndex.foreach(operand => {
          operators.append(makeOperator(ctx,
            Operator.fieldWrite(makeSymbolRef(operand._1.value, ctx), result.ref, operand._2.toString))(0))
        })
      }
    }
    operators
  }

  @throws[UnexpectedSILFormatException]
  @throws[UnexpectedSILTypeBehaviourException]
  def visitTupleExtract(r: Option[SILResult], I: SILOperator.tupleExtract, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTupleTypeToType(I.operand.tpe, I.declRef, pointer = false), ctx)
    makeOperator(ctx, Operator.fieldRead(result, None, makeSymbolRef(I.operand.value, ctx), I.declRef.toString))
  }

  @throws[UnexpectedSILFormatException]
  @throws[UnexpectedSILTypeBehaviourException]
  def visitTupleElementAddr(r: Option[SILResult], I: SILOperator.tupleElementAddr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTupleTypeToType(I.operand.tpe, I.declRef, pointer = true), ctx)
    val aliasResult = new Symbol(generateSymbolName(result.ref.name, ctx),
      Utils.SILTupleTypeToType(I.operand.tpe, I.declRef, pointer = false))
    makeOperator(ctx,
      Operator.neww(result),
      Operator.fieldRead(aliasResult, Some(result.ref), makeSymbolRef(I.operand.value, ctx), I.declRef.toString),
      Operator.pointerWrite(aliasResult.ref, result.ref))
  }

  @throws[UnexpectedSILFormatException]
  def visitDestructureTuple(r: Option[SILResult], I: SILOperator.destructureTuple, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    if (!I.operand.tpe.isInstanceOf[SILType.tupleType]) {
      throw new UnexpectedSILFormatException("Expected destructure_tuple operand type to be of tuple type")
    }
    val tupleType = I.operand.tpe.asInstanceOf[SILType.tupleType]
    verifySILResult(r, tupleType.parameters.length)
    val operators = ArrayBuffer[RawInstructionDef]()
    tupleType.parameters.zipWithIndex.foreach(param => {
      operators.append(makeOperator(ctx,
        Operator.fieldRead(
          new Symbol(makeSymbolRef(r.get.valueNames(param._2), ctx), Utils.SILTypeToType(param._1)),
          None, makeSymbolRef(I.operand.value, ctx), param._2.toString))(0))
    })
    operators
  }

  @throws[UnexpectedSILFormatException]
  def visitStruct(r: Option[SILResult], I: SILOperator.struct, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val init: Option[StructInit] = {
      var ret: Option[StructInit] = None
      ctx.silModule.inits.foreach(init => {
        if (init.name == Utils.print(I.tpe)) {
          ret = Some(init)
        }
      })
      ret
    }
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    val operators = ArrayBuffer[RawInstructionDef]()
    operators.append(makeOperator(ctx, Operator.neww(result))(0))
    if (init.nonEmpty) {
      if (init.get.args.length < I.operands.length) {
        throw new ExperimentalException("IMPORTANT: Init comment must have not included all arguments. Odd.")
      }
      I.operands.zipWithIndex.foreach(op => {
        operators.append(makeOperator(ctx,
          Operator.fieldWrite(makeSymbolRef(op._1.value, ctx), result.ref,init.get.args(op._2)))(0))
      })
    } else {
      val structName = Utils.print(I.tpe)
      if (!this.missingStructs.contains(structName)) {
        // Logger.getGlobal.warning("Missing struct init definition for " + Utils.print(I.tpe))
        this.missingStructs.add(structName)
      }
    }
    operators
  }

  @throws[UnexpectedSILFormatException]
  def visitStructExtract(r: Option[SILResult], I: SILOperator.structExtract, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    // Type is statically unknown, at least for now
    val result = getSingleResult(r, new Type("Any"), ctx)
    makeOperator(ctx, Operator.fieldRead(result, None, makeSymbolRef(I.operand.value, ctx), Utils.SILStructFieldDeclRefToString(I.declRef)))
  }

  @throws[UnexpectedSILFormatException]
  def visitStructElementAddr(r: Option[SILResult], I: SILOperator.structElementAddr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    // Type is statically unknown, at least for now
    val result = getSingleResult(r, new Type("*Any"), ctx)
    val aliasResult = new Symbol(generateSymbolName(result.ref.name, ctx), new Type("Any"))
    makeOperator(ctx,
      Operator.neww(result),
      Operator.fieldRead(aliasResult, Some(result.ref),
        makeSymbolRef(I.operand.value, ctx), Utils.SILStructFieldDeclRefToString(I.declRef)),
      Operator.pointerWrite(aliasResult.ref, result.ref))
  }

  // def visitDestructureStruct(r: Option[SILResult], I: SILOperator.destructureStruct, ctx: Context): ArrayBuffer[RawInstructionDef]

  @throws[UnexpectedSILFormatException]
  def visitObject(r: Option[SILResult], I: SILOperator.objct, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    // Only new instruction because field names are not known.
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  def visitRefElementAddr(r: Option[SILResult], I: SILOperator.refElementAddr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    // Type is statically unknown, at least for now
    val result = getSingleResult(r, new Type("*Any"), ctx)
    val aliasResult = new Symbol(generateSymbolName(result.ref.name, ctx), new Type("Any"))
    makeOperator(ctx,
      Operator.neww(result),
      Operator.fieldRead(aliasResult, Some(result.ref),
        makeSymbolRef(I.operand.value, ctx), Utils.SILStructFieldDeclRefToString(I.declRef)),
      Operator.pointerWrite(aliasResult.ref, result.ref))
  }

  @throws[UnexpectedSILFormatException]
  def visitRefTailAddr(r: Option[SILResult], I: SILOperator.refTailAddr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToPointerType(I.tpe), ctx) // $*T to $T
    makeOperator(ctx, Operator.arrayRead(result, makeSymbolRef(I.operand.value, ctx)))
  }

  /* ENUMS */

  @throws[UnexpectedSILFormatException]
  def visitEnum(r: Option[SILResult], I: SILOperator.enm, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    val typeString = new Symbol(generateSymbolName(result.ref.name, ctx), new Type("Builtin.RawPointer"))
    var instructions = makeOperator(ctx,
      Operator.neww(result),
      Operator.literal(typeString, Literal.string(Utils.print(I.declRef))),
      Operator.fieldWrite(typeString.ref, result.ref, "type"))
    if (I.operand.nonEmpty) {
      instructions :+= makeOperator(ctx, Operator.fieldWrite(makeSymbolRef(I.operand.get.value, ctx), result.ref, "data"))(0)
    }
    instructions
  }

  @throws[UnexpectedSILFormatException]
  def visitUncheckedEnumData(r: Option[SILResult], I: SILOperator.uncheckedEnumData, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, new Type("Any"), ctx)
    // No alias for now, but this might change.
    makeOperator(ctx, Operator.fieldRead(result, None, makeSymbolRef(I.operand.value, ctx), "data"))
  }

  @throws[UnexpectedSILFormatException]
  def visitInitEnumDataAddr(r: Option[SILResult], I: SILOperator.initEnumDataAddr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    // Type is statically unknown, at least for now.
    val result = getSingleResult(r, new Type("*Any"), ctx)
    val aliasResult = new Symbol(generateSymbolName(result.ref.name, ctx), new Type("Any"))
    makeOperator(ctx,
      Operator.neww(result),
      Operator.fieldRead(aliasResult, Some(result.ref), makeSymbolRef(I.operand.value, ctx), "data"),
      Operator.pointerWrite(aliasResult.ref, result.ref))
  }

  def visitInjectEnumAddr(r: Option[SILResult], I: SILOperator.injectEnumAddr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val typeString = new Symbol(generateSymbolName(I.operand.value, ctx), new Type("Builtin.RawPointer"))
    makeOperator(ctx,
      Operator.literal(typeString, Literal.string(Utils.print(I.declRef))),
      Operator.fieldWrite(typeString.ref, makeSymbolRef(I.operand.value, ctx), "type"))
  }

  @throws[UnexpectedSILFormatException]
  def visitUncheckedTakeEnumDataAddr(r: Option[SILResult], I: SILOperator.uncheckedTakeEnumDataAddr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    // Type is statically unknown, at least for now.
    val result = getSingleResult(r, new Type("*Any"), ctx)
    val aliasResult = new Symbol(generateSymbolName(result.ref.name, ctx), new Type("Any"))
    makeOperator(ctx,
      Operator.neww(result),
      Operator.fieldRead(aliasResult, Some(result.ref), makeSymbolRef(I.operand.value, ctx), "data"),
      Operator.pointerWrite(aliasResult.ref, result.ref))
  }

  @throws[UnexpectedSILFormatException]
  def visitSelectEnum(r: Option[SILResult], I: SILOperator.selectEnum, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    var default: Option[SymbolRef] = None
    val cases = {
      val arr = new ArrayBuffer[EnumAssignCase]()
      I.cases.foreach {
        case SILSwitchEnumCase.cs(declRef, result) => {
          arr.append(new EnumAssignCase(Utils.print(declRef), makeSymbolRef(result, ctx)))
        }
        case SILSwitchEnumCase.default(result) => {
          default = Some(makeSymbolRef(result, ctx))
        }
      }
      arr
    }
    makeOperator(ctx, Operator.switchEnumAssign(result, makeSymbolRef(I.operand.value, ctx), cases, default))
  }

  @throws[UnexpectedSILFormatException]
  @throws[UnexpectedSILTypeBehaviourException]
  def visitSelectEnumAddr(r: Option[SILResult], I: SILOperator.selectEnumAddr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    var default: Option[SymbolRef] = None
    val cases = {
      val arr = new ArrayBuffer[EnumAssignCase]()
      I.cases.foreach {
        case SILSwitchEnumCase.cs(declRef, result) => {
          arr.append(new EnumAssignCase(Utils.print(declRef), makeSymbolRef(result, ctx)))
        }
        case SILSwitchEnumCase.default(result) => {
          default = Some(makeSymbolRef(result, ctx))
        }
      }
      arr
    }
    val intermediateResult = new Symbol(generateSymbolName(I.operand.value, ctx), Utils.SILPointerTypeToType(I.operand.tpe))
    makeOperator(ctx,
      Operator.pointerRead(intermediateResult, makeSymbolRef(I.operand.value, ctx)),
      Operator.switchEnumAssign(result, intermediateResult.ref, cases, default))
  }

  /* PROTOCOL AND PROTOCOL COMPOSITION TYPES */

  @throws[UnexpectedSILFormatException]
  def visitInitExistentialAddr(r: Option[SILResult], I: SILOperator.initExistentialAddr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  // def visitInitExistentialValue(r: Option[SILResult], I: SILOperator.initExistentialValue, ctx: Context): ArrayBuffer[RawInstructionDef]

  def visitDeinitExistentialAddr(r: Option[SILResult], I: SILOperator.deinitExistentialAddr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  // def visitDeinitExistentialValue(r: Option[SILResult], I: SILOperator.deinitExistentialValue, ctx: Context): ArrayBuffer[RawInstructionDef]

  @throws[UnexpectedSILFormatException]
  def visitOpenExistentialAddr(r: Option[SILResult], I: SILOperator.openExistentialAddr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  // def visitOpenExistentialValue(r: Option[SILResult], I: SILOperator.openExistentialValue, ctx: Context): ArrayBuffer[RawInstructionDef]

  @throws[UnexpectedSILFormatException]
  def visitInitExistentialRef(r: Option[SILResult], I: SILOperator.initExistentialRef, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitOpenExistentialRef(r: Option[SILResult], I: SILOperator.openExistentialRef, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitInitExistentialMetatype(r: Option[SILResult], I: SILOperator.initExistentialMetatype, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitOpenExistentialMetatype(r: Option[SILResult], I: SILOperator.openExistentialMetatype, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitAllocExistentialBox(r: Option[SILResult], I: SILOperator.allocExistentialBox, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToPointerType(I.tpeP), ctx) // $T to $*T
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  def visitProjectExistentialBox(r: Option[SILResult], I: SILOperator.projectExistentialBox, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitOpenExistentialBox(r: Option[SILResult], I: SILOperator.openExistentialBox, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  // def visitOpenExistentialBoxValue(r: Option[SILResult], I: SILOperator.openExistentialBoxValue, ctx: Context): ArrayBuffer[RawInstructionDef]

  def visitDeallocExistentialBox(r: Option[SILResult], I: SILOperator.deallocExistentialBox, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  /* BLOCKS */

  @throws[UnexpectedSILFormatException]
  def visitProjectBlockStorage(r: Option[SILResult], I: SILOperator.projectBlockStorage, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitInitBlockStorageHeader(r: Option[SILResult], I: SILOperator.initBlockStorageHeader, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    // TODO: This is a complicated instruction. There isn't any SIL.rst
    //  documentation on it. It seems to call a thunk (I.invoke) with the
    //  I.operand as the first argument. In the called thunk, a function ref
    //  is read from I.operand (function ref to a closure) and called.
    // For now, just create a new value
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.neww(result))
  }

  /* UNCHECKED CONVERSIONS */

  @throws[UnexpectedSILFormatException]
  def visitUpcast(r: Option[SILResult], I: SILOperator.upcast, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitAddressToPointer(r: Option[SILResult], I: SILOperator.addressToPointer, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitPointerToAddress(r: Option[SILResult], I: SILOperator.pointerToAddress, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitUncheckedRefCast(r: Option[SILResult], I: SILOperator.uncheckedRefCast, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  // def visitUncheckedRefCastAddr(r: Option[SILResult], I: SILOperator.uncheckedRefCastAddr, ctx: Context): ArrayBuffer[RawInstructionDef]

  @throws[UnexpectedSILFormatException]
  def visitUncheckedAddrCast(r: Option[SILResult], I: SILOperator.uncheckedAddrCast, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitUncheckedTrivialBitCast(r: Option[SILResult], I: SILOperator.uncheckedTrivialBitCast, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  // def visitUncheckedBitwiseCast(r: Option[SILResult], I: SILOperator.uncheckedBitwiseCast, ctx: Context): ArrayBuffer[RawInstructionDef]
  // def visitRefToRawPointer(r: Option[SILResult], I: SILOperator.refToRawPointer, ctx: Context): ArrayBuffer[RawInstructionDef]

  @throws[UnexpectedSILFormatException]
  def visitUncheckedOwnershipConverstion(r: Option[SILResult], I: SILOperator.uncheckedOwnershipConversion, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitRawPointerToRef(r: Option[SILResult], I: SILOperator.rawPointerToRef, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitRefToUnowned(r: Option[SILResult], I: SILOperator.refToUnowned, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  // def visitUnownedToRef(r: Option[SILResult], I: SILOperator.unownedToRef, ctx: Context): ArrayBuffer[RawInstructionDef]

  @throws[UnexpectedSILFormatException]
  def visitRefToUnmanaged(r: Option[SILResult], I: SILOperator.refToUnmanaged, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitUnmanagedToRef(r: Option[SILResult], I: SILOperator.unmanagedToRef, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitConvertFunction(r: Option[SILResult], I: SILOperator.convertFunction, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitConvertEscapeToNoEscape(r: Option[SILResult], I: SILOperator.convertEscapeToNoescape, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  // def visitThinFunctionToPointer(r: Option[SILResult], I: SILOperator.thinFunctionToPointer, ctx: Context): ArrayBuffer[RawInstructionDef]
  // def visitPointerToThinFunction(r: Option[SILResult], I: SILOperator.pointerToThinFunction, ctx: Context): ArrayBuffer[RawInstructionDef]
  // def visitClassifyBridgeObject(r: Option[SILResult], I: SILOperator.classifyBridgeObject, ctx: Context): ArrayBuffer[RawInstructionDef]

  @throws[UnexpectedSILFormatException]
  def visitValueToBridgeObject(r: Option[SILResult], I: SILOperator.valueToBridgeObject, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  // def visitRefToBridgeObject(r: Option[SILResult], I: SILOperator.refToBridgeObject, ctx: Context): ArrayBuffer[RawInstructionDef]

  @throws[UnexpectedSILFormatException]
  def visitBridgeObjectToRef(r: Option[SILResult], I: SILOperator.bridgeObjectToRef, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  // def visitBridgeObjectToWord(r: Option[SILResult], I: SILOperator.bridgeObjectToWord, ctx: Context): ArrayBuffer[RawInstructionDef]

  @throws[UnexpectedSILFormatException]
  def visitThinToThickFunction(r: Option[SILResult], I: SILOperator.thinToThickFunction, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitThickToObjCMetatype(r: Option[SILResult], I: SILOperator.thickToObjcMetatype, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitObjCToThickMetatype(r: Option[SILResult], I: SILOperator.objcToThickMetatype, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitObjCMetatypeToObject(r: Option[SILResult], I: SILOperator.objcMetatypeToObject, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  @throws[UnexpectedSILFormatException]
  def visitObjCExistentialMetatypeToObject(r: Option[SILResult], I: SILOperator.objcExistentialMetatypeToObject, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
  }

  /* CHECKED CONVERSIONS */

  @throws[UnexpectedSILFormatException]
  def visitUnconditionalCheckedCast(r: Option[SILResult], I: SILOperator.unconditionalCheckedCast, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
    null
  }

  def visitUnconditionalCheckedCastAddr(r: Option[SILResult], I: SILOperator.unconditionalCheckedCastAddr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    NOP
  }

  // def visitUnconditionalCheckedCastValue(r: Option[SILResult], I: SILOperator.unconditionalCheckedCastValue, ctx: Context): ArrayBuffer[RawInstructionDef]

  /* RUNTIME FAILURES */

  def visitCondFail(r: Option[SILResult], I: SILOperator.condFail, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    makeOperator(ctx, Operator.condFail(makeSymbolRef(I.operand.value, ctx)))
  }

  /* TERMINATORS */

  def visitUnreachable(ctx: Context): ArrayBuffer[RawInstructionDef] = {
    makeTerminator(ctx, Terminator.unreachable)
  }

  def visitReturn(I: SILTerminator.ret, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    makeTerminator(ctx, Terminator.ret(makeSymbolRef(I.operand.value, ctx)))
  }

  def visitThrow(I: SILTerminator.thro, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    makeTerminator(ctx, Terminator.thro(makeSymbolRef(I.operand.value, ctx)))
  }

  def visitYield(I: SILTerminator.yld, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val yields: ArrayBuffer[SymbolRef] = {
      val arr = new ArrayBuffer[SymbolRef]()
      I.operands.foreach(o => {
        arr.append(makeSymbolRef(o.value, ctx))
      })
      arr
    }
    makeTerminator(ctx, Terminator.yld(yields, makeBlockRef(I.resumeLabel, ctx), makeBlockRef(I.unwindLabel, ctx)))
  }

  def visitUnwind(ctx: Context): ArrayBuffer[RawInstructionDef] = {
    makeTerminator(ctx, Terminator.unwind)
  }

  def visitBr(I: SILTerminator.br, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val args: ArrayBuffer[SymbolRef] = {
      val arr = new ArrayBuffer[SymbolRef]()
      I.operands.foreach(o => {
        arr.append(makeSymbolRef(o.value, ctx))
      })
      arr
    }
    makeTerminator(ctx, Terminator.br(makeBlockRef(I.label, ctx), args))
  }

  def visitCondBr(I: SILTerminator.condBr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val trueArgs: ArrayBuffer[SymbolRef] = {
      val arr = new ArrayBuffer[SymbolRef]()
      I.trueOperands.foreach(o => {
        arr.append(makeSymbolRef(o.value, ctx))
      })
      arr
    }
    val falseArgs: ArrayBuffer[SymbolRef] = {
      val arr = new ArrayBuffer[SymbolRef]()
      I.falseOperands.foreach(o => {
        arr.append(makeSymbolRef(o.value, ctx))
      })
      arr
    }
    makeTerminator(ctx, Terminator.condBr(makeSymbolRef(I.cond, ctx),
      makeBlockRef(I.trueLabel, ctx), trueArgs, makeBlockRef(I.falseLabel, ctx), falseArgs))
  }

  def visitSwitchValue(I: SILTerminator.switchValue, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    var default: Option[BlockRef] = None
    val cases = {
      val arr = new ArrayBuffer[SwitchCase]()
      I.cases.foreach {
        case SILSwitchValueCase.cs(value, label) => {
          arr.append(new SwitchCase(makeSymbolRef(value, ctx), makeBlockRef(label, ctx)))
        }
        case SILSwitchValueCase.default(label) => {
          default = Some(makeBlockRef(label, ctx))
        }
      }
      arr
    }
    makeTerminator(ctx, Terminator.switch(makeSymbolRef(I.operand.value, ctx), cases, default))
  }

  def visitSwitchEnum(I: SILTerminator.switchEnum, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    var default: Option[BlockRef] = None
    val cases = {
      val arr = new ArrayBuffer[SwitchEnumCase]()
      I.cases.foreach {
        case SILSwitchEnumCase.cs(declRef, label) => {
          arr.append(new SwitchEnumCase(Utils.print(declRef), makeBlockRef(label, ctx)))
        }
        case SILSwitchEnumCase.default(result) => {
          default = Some(makeBlockRef(result, ctx))
        }
      }
      arr
    }
    makeTerminator(ctx, Terminator.switchEnum(makeSymbolRef(I.operand.value, ctx), cases, default))
  }

  @throws[UnexpectedSILTypeBehaviourException]
  def visitSwitchEnumAddr(I: SILTerminator.switchEnumAddr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val readResult = new Symbol(generateSymbolName(I.operand.value, ctx), Utils.SILPointerTypeToType(I.operand.tpe))
    var default: Option[BlockRef] = None
    val cases = {
      val arr = new ArrayBuffer[SwitchEnumCase]()
      I.cases.foreach {
        case SILSwitchEnumCase.cs(declRef, label) => {
          arr.append(new SwitchEnumCase(Utils.print(declRef), makeBlockRef(label, ctx)))
        }
        case SILSwitchEnumCase.default(result) => {
          default = Some(makeBlockRef(result, ctx))
        }
      }
      arr
    }
    val instructions = new ArrayBuffer[RawInstructionDef]()
    instructions.appendAll(makeOperator(ctx, Operator.pointerRead(readResult, makeSymbolRef(I.operand.value, ctx))))
    instructions.appendAll(makeTerminator(ctx, Terminator.switchEnum(readResult.ref, cases, default)))
    instructions
  }

  def visitDynamicMethodBr(I: SILTerminator.dynamicMethodBr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = new Symbol(generateSymbolName(I.operand.value, ctx), new Type("Builtin.Int1"))
    val instructions = new ArrayBuffer[RawInstructionDef]()
    instructions.appendAll(makeOperator(ctx, Operator.unaryOp(result, UnaryOperation.arbitrary, makeSymbolRef(I.operand.value, ctx))))
    instructions.appendAll(makeTerminator(ctx,
      Terminator.condBr(result.ref,
        makeBlockRef(I.namedLabel, ctx), ArrayBuffer.empty, makeBlockRef(I.notNamedLabel, ctx), ArrayBuffer.empty)))
    instructions
  }

  def visitCheckedCastBr(I: SILTerminator.checkedCastBr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = new Symbol(generateSymbolName(I.operand.value, ctx), new Type("Builtin.Int1"))
    val instructions = new ArrayBuffer[RawInstructionDef]()
    instructions.appendAll(makeOperator(ctx, Operator.unaryOp(result, UnaryOperation.arbitrary, makeSymbolRef(I.operand.value, ctx))))
    instructions.appendAll(makeTerminator(ctx,
      Terminator.condBr(result.ref,
        makeBlockRef(I.succeedLabel, ctx), ArrayBuffer({makeSymbolRef(I.operand.value, ctx)}),
        makeBlockRef(I.failureLabel, ctx), ArrayBuffer.empty)))
    instructions
  }

  def visitCheckedCastAddrBr(I: SILTerminator.checkedCastAddrBr, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    val result = new Symbol(generateSymbolName(I.fromOperand.value, ctx), new Type("Builtin.Int1"))
    val instructions = new ArrayBuffer[RawInstructionDef]()
    instructions.appendAll(makeOperator(ctx,
      Operator.binaryOp(result, BinaryOperation.arbitrary,
        makeSymbolRef(I.fromOperand.value, ctx), makeSymbolRef(I.toOperand.value, ctx))))
    instructions.appendAll(makeTerminator(ctx,
      Terminator.condBr(result.ref,
        makeBlockRef(I.succeedLabel, ctx), ArrayBuffer.empty, makeBlockRef(I.failureLabel, ctx), ArrayBuffer.empty)))
    instructions
  }

  // def visitCheckedCastValueBr(I: SILTerminator.checkedCastValueBr, ctx: Context): ArrayBuffer[RawInstructionDef]

  @throws[UnexpectedSILFormatException]
  def visitTryApply(I: SILTerminator.tryApply, ctx: Context): ArrayBuffer[RawInstructionDef] = {
    // Receiving error block argument type does not have attributes (e.g., @error).
    val funcType = Utils.getFunctionTypeFromType(I.tpe)
    val retTypes: ArrayBuffer[Type] = {
      funcType.result match {
          // [...] -> @error $Error, in this case normal block takes $()
        case SILType.attributedType(_, tpe) => ArrayBuffer(new Type("()"), Utils.SILTypeToType(tpe))
        case _ =>
          Utils.SILFunctionTupleTypeToReturnType(I.tpe, removeAttributes = true)
      }
    }
    // retTypes can apparently be more than two
    // e.g., (@out O1.Element, @out O2.Element, @error Error)
    // therefore, use the last type for the error value
    if (retTypes.length < 2) {
      throw new UnexpectedSILFormatException("Expected try_apply function return type to have at least two elements: \n" + Utils.print(I.tpe))
    }
    val terminator = Terminator.tryApply(makeSymbolRef(I.value, ctx), stringArrayToSymbolRefArrayBuffer(I.arguments, ctx),
      makeBlockRef(I.normalLabel, ctx), retTypes(0), makeBlockRef(I.errorLabel, ctx), retTypes(retTypes.length-1))
    makeTerminator(ctx, terminator)
  }

  @throws[UnexpectedSILFormatException]
  def getSingleResult(r: Option[SILResult], tpe: Type, ctx: Context): Symbol = {
    verifySILResult(r, 1)
    new Symbol(makeSymbolRef(r.get.valueNames(0), ctx), tpe)
  }
}
