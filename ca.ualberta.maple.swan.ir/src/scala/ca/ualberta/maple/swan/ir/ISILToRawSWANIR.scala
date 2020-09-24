/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.ir

import ca.ualberta.maple.swan.parser.{SILArgument, SILBlock, SILFunction, SILInstructionDef, SILModule, SILOperator, SILOperatorDef, SILResult, SILTerminator, SILTerminatorDef}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.{break, breakable}

/*
 * This is does not include the compilation of each instruction
 * simply to keep it cleaner.
 */
trait ISILToRawSWANIR {

  val NOP: Null = null // explicit NOP marker

  def translateSILModule(silModule: SILModule): Module = {
    intermediateSymbols.clear()
    val functions = new ArrayBuffer[Function](0)
    silModule.functions.foreach( (silFunction: SILFunction) => {
      functions.append(translateSILFunction(silModule, silFunction))
    })
    new Module(functions.toArray, silModule.imports)
  }

  private def translateSILFunction(silModule: SILModule, silFunction: SILFunction): Function = {
    val blocks = new ArrayBuffer[Block](0)
    silFunction.blocks.foreach( (silBlock: SILBlock) => {
      blocks.append(translateSILBlock(silModule, silFunction, silBlock))
    })
    val coroutine = if(isCoroutine(silFunction)) Some(FunctionAttribute.coroutine) else None
    new Function(coroutine, silFunction.name.demangled, Utils.SILTypeToType(silFunction.tpe), blocks.toArray)
  }

  private def translateSILBlock(silModule: SILModule, silFunction: SILFunction, silBlock: SILBlock): Block = {
    val arguments = new ArrayBuffer[Argument]()
    silBlock.arguments.foreach( (a: SILArgument) => {
      arguments.append(Utils.SILArgumentToArgument(a))
    })
    val operators = new ArrayBuffer[OperatorDef](0)
    silBlock.operatorDefs.foreach( (silOperatorDef: SILOperatorDef) => {
      breakable {
        val position: Option[Position] = Utils.SILSourceInfoToPosition(silOperatorDef.sourceInfo)
        val ctx = new Context(silModule, silFunction, silBlock, position)
        val instructions: Array[InstructionDef] = translateSILInstruction(SILInstructionDef.operator(silOperatorDef), ctx)
        if (instructions == NOP) {
          break()
        }
        instructions.foreach( (inst: InstructionDef) => {
          assert(inst.isInstanceOf[InstructionDef.operator])
          val operator: OperatorDef = inst.asInstanceOf[InstructionDef.operator].operatorDef
          operators.append(operator)
        })
      }
    })
    val terminator: TerminatorDef = null
    /*
    val terminator: TerminatorDef = {
      val position: Option[Position] = Utils.SILSourceInfoToPosition(silBlock.terminatorDef.sourceInfo)
      val ctx = new Context(silModule, silFunction, silBlock, position)
      val instructions = translateSILInstruction(
        SILInstructionDef.terminator(silBlock.terminatorDef), ctx)
      assert(instructions.length == 1)
      val instruction: InstructionDef = instructions(0)
      assert(instruction.isInstanceOf[InstructionDef.terminator])
      Instruction.terminator.asInstanceOf[InstructionDef.terminator].terminatorDef
    }
     */
    new Block(silBlock.identifier, arguments.toArray, operators.toArray, terminator)
  }

  private def isCoroutine(silFunction: SILFunction): Boolean = {
    // TODO: Two options
    //  1. Check if function contains `yield` instruction
    //  2. See if the function type reliably has an indicator/attribute
    //     whether it is a coroutine
    false
  }

  // Needs to be cleared for every function.
  private val intermediateSymbols: mutable.HashMap[String, Integer] = new mutable.HashMap()

  // We make it explicit that the value generated is an intermediate value
  // for the given value.
  // We keep track of these values so we do not generate duplicates.
  protected def generateSymbol(value: String): String = {
    if (!intermediateSymbols.contains(value)) {
      intermediateSymbols.put(value, 0)
    }
    val ret = value + "_i_" + intermediateSymbols(value).toString
    intermediateSymbols(value) = intermediateSymbols(value) + 1
    ret
  }

  def translateSILInstruction(silInstructionDef: SILInstructionDef, ctx: Context): Array[InstructionDef] = {
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
          case inst: SILOperator.debugValue => visitDebugValue(result, inst, ctx)
          case inst: SILOperator.debugValueAddr => visitDebugValueAddr(result, inst, ctx)
          case inst: SILOperator.load => visitLoad(result, inst, ctx)
          case inst: SILOperator.store => visitStore(result, inst, ctx)
          case inst: SILOperator.loadBorrow => visitLoadBorrow(result, inst, ctx)
          case inst: SILOperator.beginBorrow => visitBeginBorrow(result, inst, ctx)
          case inst: SILOperator.endBorrow => visitEndBorrow(result, inst, ctx)
          case inst: SILOperator.copyAddr => visitCopyAddr(result, inst, ctx)
          case inst: SILOperator.destroyAddr => visitDestroyAddr(result, inst, ctx)
          case inst: SILOperator.indexAddr => visitIndexAddr(result, inst, ctx)
          case inst: SILOperator.beginAccess => visitBeginAccess(result, inst, ctx)
          case inst: SILOperator.endAccess => visitEndAccess(result, inst, ctx)
          case inst: SILOperator.strongRetain => visitStrongRetain(result, inst, ctx)
          case inst: SILOperator.strongRelease => visitStrongRelease(result, inst, ctx)
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
          case inst: SILOperator.releaseValue => visitReleaseValue(result, inst, ctx)
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
          case inst: SILOperator.refToUnowned => visitRefToUnowned(result, inst, ctx)
          case inst: SILOperator.refToUnmanaged => visitRefToUnmanaged(result, inst, ctx)
          case inst: SILOperator.unmanagedToRef => visitUnmanagedToRef(result, inst, ctx)
          case inst: SILOperator.convertFunction => visitConvertFunction(result, inst, ctx)
          case inst: SILOperator.convertEscapeToNoescape => visitConvertEscapeToNoEscape(result, inst, ctx)
          case inst: SILOperator.valueToBridgeObject => visitValueToBridgeObject(result, inst, ctx)
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
      case SILInstructionDef.terminator(terminatorDef) => {
        val instruction = terminatorDef.terminator
        instruction match {
          case SILTerminator.unreachable => visitUnreachable()
          case inst: SILTerminator.ret => visitReturn(inst, ctx)
          case inst: SILTerminator.thro => visitThrow(inst, ctx)
          case inst: SILTerminator.yld => visitYield(inst, ctx)
          case SILTerminator.unwind => visitUnwind()
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

  /* ALLOCATION AND DEALLOCATION */

  protected def visitAllocStack(r: Option[SILResult], I: SILOperator.allocStack, ctx: Context): Array[InstructionDef]

  protected def visitAllocRef(r: Option[SILResult], I: SILOperator.allocRef, ctx: Context): Array[InstructionDef]
  protected def visitAllocRefDynamic(r: Option[SILResult], I: SILOperator.allocRefDynamic, ctx: Context): Array[InstructionDef]
  protected def visitAllocBox(r: Option[SILResult], I: SILOperator.allocBox, ctx: Context): Array[InstructionDef]

  protected def visitAllocValueBuffer(r: Option[SILResult], I: SILOperator.allocValueBuffer, ctx: Context): Array[InstructionDef]
  protected def visitAllocGlobal(r: Option[SILResult], I: SILOperator.allocGlobal, ctx: Context): Array[InstructionDef]

  protected def visitDeallocStack(r: Option[SILResult], I: SILOperator.deallocStack, ctx: Context): Array[InstructionDef]

  protected def visitDeallocBox(r: Option[SILResult], I: SILOperator.deallocBox, ctx: Context): Array[InstructionDef]

  protected def visitProjectBox(r: Option[SILResult], I: SILOperator.projectBox, ctx: Context): Array[InstructionDef]

  protected def visitDeallocRef(r: Option[SILResult], I: SILOperator.deallocRef, ctx: Context): Array[InstructionDef]
  // protected def visitDeallocPartialRef(r: Option[SILResult], I: SILOperator.deallocPartialRef, ctx: Context): Array[InstructionDef]
  // protected def visitDeallocValueBuffer(r: Option[SILResult], I: SILOperator.deallocValueBuffer, ctx: Context): Array[InstructionDef]
  // protected def visitProjectValueBuffer(r: Option[SILResult], I: SILOperator.projectValueBuffer, ctx: Context): Array[InstructionDef]

  /* DEBUG INFORMATION */

  protected def visitDebugValue(r: Option[SILResult], I: SILOperator.debugValue, ctx: Context): Array[InstructionDef]

  protected def visitDebugValueAddr(r: Option[SILResult], I: SILOperator.debugValueAddr, ctx: Context): Array[InstructionDef]

  /* ACCESSING MEMORY */

  protected def visitLoad(r: Option[SILResult], I: SILOperator.load, ctx: Context): Array[InstructionDef]
  protected def visitStore(r: Option[SILResult], I: SILOperator.store, ctx: Context): Array[InstructionDef]
  // protected def visitStoreBorrow(r: Option[SILResult], I: SILOperator.storeBorrow, ctx: Context): Array[InstructionDef]
  protected def visitLoadBorrow(r: Option[SILResult], I: SILOperator.loadBorrow, ctx: Context): Array[InstructionDef]
  protected def visitBeginBorrow(r: Option[SILResult], I: SILOperator.beginBorrow, ctx: Context): Array[InstructionDef]
  protected def visitEndBorrow(r: Option[SILResult], I: SILOperator.endBorrow, ctx: Context): Array[InstructionDef]
  protected def visitCopyAddr(r: Option[SILResult], I: SILOperator.copyAddr, ctx: Context): Array[InstructionDef]
  protected def visitDestroyAddr(r: Option[SILResult], I: SILOperator.destroyAddr, ctx: Context): Array[InstructionDef]
  protected def visitIndexAddr(r: Option[SILResult], I: SILOperator.indexAddr, ctx: Context): Array[InstructionDef]
  // protected def visitTailAddr(r: Option[SILResult], I: SILOperator.tailAddr, ctx: Context): Array[InstructionDef]
  // protected def visitIndexRawPointer(r: Option[SILResult], I: SILOperator.indexRawPointer, ctx: Context): Array[InstructionDef]
  // protected def visitBindMemory(r: Option[SILResult], I: SILOperator.bindMemory, ctx: Context): Array[InstructionDef]
  protected def visitBeginAccess(r: Option[SILResult], I: SILOperator.beginAccess, ctx: Context): Array[InstructionDef]
  protected def visitEndAccess(r: Option[SILResult], I: SILOperator.endAccess, ctx: Context): Array[InstructionDef]
  // protected def visitBeginUnpairedAccess(r: Option[SILResult], I: SILOperator.beginUnpairedAccess, ctx: Context): Array[InstructionDef]
  // protected def visitEndUnpairedAccess(r: Option[SILResult], I: SILOperator.endUnpairedAccess, ctx: Context): Array[InstructionDef]

  /* REFERENCE COUNTING */

  protected def visitStrongRetain(r: Option[SILResult], I: SILOperator.strongRetain, ctx: Context): Array[InstructionDef]
  protected def visitStrongRelease(r: Option[SILResult], I: SILOperator.strongRelease, ctx: Context): Array[InstructionDef]
  // protected def visitSetDeallocating(r: Option[SILResult], I: SILOperator.setDeallocating, ctx: Context): Array[InstructionDef]
  // protected def visitStrongRetainUnowned(r: Option[SILResult], I: SILOperator.strongRetainUnowned, ctx: Context): Array[InstructionDef]
  // protected def visitUnownedRetain(r: Option[SILResult], I: SILOperator.unownedRetain, ctx: Context): Array[InstructionDef]
  // protected def visitUnownedRelease(r: Option[SILResult], I: SILOperator.unownedRelease, ctx: Context): Array[InstructionDef]
  protected def visitLoadWeak(r: Option[SILResult], I: SILOperator.loadWeak, ctx: Context): Array[InstructionDef]
  protected def visitStoreWeak(r: Option[SILResult], I: SILOperator.storeWeak, ctx: Context): Array[InstructionDef]
  protected def visitLoadUnowned(r: Option[SILResult], I: SILOperator.loadUnowned, ctx: Context): Array[InstructionDef]
  protected def visitStoreUnowned(r: Option[SILResult], I: SILOperator.storeUnowned, ctx: Context): Array[InstructionDef]
  // protected def visitFixLifetime(r: Option[SILResult], I: SILOperator.fixLifetime, ctx: Context): Array[InstructionDef]
  protected def visitMarkDependence(r: Option[SILResult], I: SILOperator.markDependence, ctx: Context): Array[InstructionDef]
  // protected def visitIsUnique(r: Option[SILResult], I: SILOperator.isUnique, ctx: Context): Array[InstructionDef]
  protected def visitIsEscapingClosure(r: Option[SILResult], I: SILOperator.isEscapingClosure, ctx: Context): Array[InstructionDef]
  protected def visitCopyBlock(r: Option[SILResult], I: SILOperator.copyBlock, ctx: Context): Array[InstructionDef]
  protected def visitCopyBlockWithoutEscaping(r: Option[SILResult], I: SILOperator.copyBlockWithoutEscaping, ctx: Context): Array[InstructionDef]

  /* LITERALS */

  protected def visitFunctionRef(r: Option[SILResult], I: SILOperator.functionRef, ctx: Context): Array[InstructionDef]
  protected def visitDynamicFunctionRef(r: Option[SILResult], I: SILOperator.dynamicFunctionRef, ctx: Context): Array[InstructionDef]
  protected def visitPrevDynamicFunctionRef(r: Option[SILResult], I: SILOperator.prevDynamicFunctionRef, ctx: Context): Array[InstructionDef]
  protected def visitGlobalAddr(r: Option[SILResult], I: SILOperator.globalAddr, ctx: Context): Array[InstructionDef]
  // protected def visitGlobalValue(r: Option[SILResult], I: SILOperator.globalValue, ctx: Context): Array[InstructionDef]
  protected def visitIntegerLiteral(r: Option[SILResult], I: SILOperator.integerLiteral, ctx: Context): Array[InstructionDef]
  protected def visitFloatLiteral(r: Option[SILResult], I: SILOperator.floatLiteral, ctx: Context): Array[InstructionDef]
  protected def visitStringLiteral(r: Option[SILResult], I: SILOperator.stringLiteral, ctx: Context): Array[InstructionDef]

  /* DYNAMIC DISPATCH */
  protected def visitClassMethod(r: Option[SILResult], I: SILOperator.classMethod, ctx: Context): Array[InstructionDef]
  protected def visitObjCMethod(r: Option[SILResult], I: SILOperator.objcMethod, ctx: Context): Array[InstructionDef]
  // protected def visitSuperMethod(r: Option[SILResult], I: SILOperator.superMethod, ctx: Context): Array[InstructionDef]
  protected def visitObjCSuperMethod(r: Option[SILResult], I: SILOperator.objcSuperMethod, ctx: Context): Array[InstructionDef]
  protected def visitWitnessMethod(r: Option[SILResult], I: SILOperator.witnessMethod, ctx: Context): Array[InstructionDef]

  /* FUNCTION APPLICATION */

  protected def visitApply(r: Option[SILResult], I: SILOperator.apply, ctx: Context): Array[InstructionDef]
  protected def visitBeginApply(r: Option[SILResult], I: SILOperator.beginApply, ctx: Context): Array[InstructionDef]
  protected def visitAbortApply(r: Option[SILResult], I: SILOperator.abortApply, ctx: Context): Array[InstructionDef]
  protected def visitEndApply(r: Option[SILResult], I: SILOperator.endApply, ctx: Context): Array[InstructionDef]
  protected def visitPartialApply(r: Option[SILResult], I: SILOperator.partialApply, ctx: Context): Array[InstructionDef]
  protected def visitBuiltin(r: Option[SILResult], I: SILOperator.builtin, ctx: Context): Array[InstructionDef]

  /* METATYPES */

  protected def visitMetatype(r: Option[SILResult], I: SILOperator.metatype, ctx: Context): Array[InstructionDef]
  protected def visitValueMetatype(r: Option[SILResult], I: SILOperator.valueMetatype, ctx: Context): Array[InstructionDef]
  protected def visitExistentialMetatype(r: Option[SILResult], I: SILOperator.existentialMetatype, ctx: Context): Array[InstructionDef]
  protected def visitObjCProtocol(r: Option[SILResult], I: SILOperator.objcProtocol, ctx: Context): Array[InstructionDef]

  /* AGGREGATE TYPES */
  protected def visitRetainValue(r: Option[SILResult], I: SILOperator.retainValue, ctx: Context): Array[InstructionDef]
  // protected def visitRetainValueAddr(r: Option[SILResult], I: SILOperator.retainValueAddr, ctx: Context): Array[InstructionDef]
  // protected def visitUnmanagedRetainValue(r: Option[SILResult], I: SILOperator.unmanagedRetainValue, ctx: Context): Array[InstructionDef]
  protected def visitCopyValue(r: Option[SILResult], I: SILOperator.copyValue, ctx: Context): Array[InstructionDef]
  protected def visitReleaseValue(r: Option[SILResult], I: SILOperator.releaseValue, ctx: Context): Array[InstructionDef]
  // protected def visitReleaseValueAddr(r: Option[SILResult], I: SILOperator.releaseValueAddr, ctx: Context): Array[InstructionDef]
  // protected def visitUnmanagedReleaseValue(r: Option[SILResult], I: SILOperator.unmanagedReleaseValue, ctx: Context): Array[InstructionDef]
  protected def visitDestroyValue(r: Option[SILResult], I: SILOperator.destroyValue, ctx: Context): Array[InstructionDef]
  protected def visitAutoreleaseValue(r: Option[SILResult], I: SILOperator.autoreleaseValue, ctx: Context): Array[InstructionDef]
  protected def visitTuple(r: Option[SILResult], I: SILOperator.tuple, ctx: Context): Array[InstructionDef]
  protected def visitTupleExtract(r: Option[SILResult], I: SILOperator.tupleExtract, ctx: Context): Array[InstructionDef]
  protected def visitTupleElementAddr(r: Option[SILResult], I: SILOperator.tupleElementAddr, ctx: Context): Array[InstructionDef]
  protected def visitDestructureTuple(r: Option[SILResult], I: SILOperator.destructureTuple, ctx: Context): Array[InstructionDef]
  protected def visitStruct(r: Option[SILResult], I: SILOperator.struct, ctx: Context): Array[InstructionDef]
  protected def visitStructExtract(r: Option[SILResult], I: SILOperator.structExtract, ctx: Context): Array[InstructionDef]
  protected def visitStructElementAddr(r: Option[SILResult], I: SILOperator.structElementAddr, ctx: Context): Array[InstructionDef]
  // protected def visitDestructureStruct(r: Option[SILResult], I: SILOperator.destructureStruct, ctx: Context): Array[InstructionDef]
  protected def visitObject(r: Option[SILResult], I: SILOperator.objct, ctx: Context): Array[InstructionDef]
  protected def visitRefElementAddr(r: Option[SILResult], I: SILOperator.refElementAddr, ctx: Context): Array[InstructionDef]
  // protected def visitRefTailAddr(r: Option[SILResult], I: SILOperator.refTailAddr, ctx: Context): Array[InstructionDef]

  /* ENUMS */

  protected def visitEnum(r: Option[SILResult], I: SILOperator.enm, ctx: Context): Array[InstructionDef]
  protected def visitUncheckedEnumData(r: Option[SILResult], I: SILOperator.uncheckedEnumData, ctx: Context): Array[InstructionDef]
  protected def visitInitEnumDataAddr(r: Option[SILResult], I: SILOperator.initEnumDataAddr, ctx: Context): Array[InstructionDef]
  protected def visitInjectEnumAddr(r: Option[SILResult], I: SILOperator.injectEnumAddr, ctx: Context): Array[InstructionDef]
  protected def visitUncheckedTakeEnumDataAddr(r: Option[SILResult], I: SILOperator.uncheckedTakeEnumDataAddr, ctx: Context): Array[InstructionDef]
  protected def visitSelectEnum(r: Option[SILResult], I: SILOperator.selectEnum, ctx: Context): Array[InstructionDef]
  protected def visitSelectEnumAddr(r: Option[SILResult], I: SILOperator.selectEnumAddr, ctx: Context): Array[InstructionDef]

  /* PROTOCOL AND PROTOCOL COMPOSITION TYPES */
  protected def visitInitExistentialAddr(r: Option[SILResult], I: SILOperator.initExistentialAddr, ctx: Context): Array[InstructionDef]
  // protected def visitInitExistentialValue(r: Option[SILResult], I: SILOperator.initExistentialValue, ctx: Context): Array[InstructionDef]
  protected def visitDeinitExistentialAddr(r: Option[SILResult], I: SILOperator.deinitExistentialAddr, ctx: Context): Array[InstructionDef]
  // protected def visitDeinitExistentialValue(r: Option[SILResult], I: SILOperator.deinitExistentialValue, ctx: Context): Array[InstructionDef]
  protected def visitOpenExistentialAddr(r: Option[SILResult], I: SILOperator.openExistentialAddr, ctx: Context): Array[InstructionDef]
  // protected def visitOpenExistentialValue(r: Option[SILResult], I: SILOperator.openExistentialValue, ctx: Context): Array[InstructionDef]
  protected def visitInitExistentialRef(r: Option[SILResult], I: SILOperator.initExistentialRef, ctx: Context): Array[InstructionDef]
  protected def visitOpenExistentialRef(r: Option[SILResult], I: SILOperator.openExistentialRef, ctx: Context): Array[InstructionDef]
  protected def visitInitExistentialMetatype(r: Option[SILResult], I: SILOperator.initExistentialMetatype, ctx: Context): Array[InstructionDef]
  protected def visitOpenExistentialMetatype(r: Option[SILResult], I: SILOperator.openExistentialMetatype, ctx: Context): Array[InstructionDef]
  protected def visitAllocExistentialBox(r: Option[SILResult], I: SILOperator.allocExistentialBox, ctx: Context): Array[InstructionDef]
  protected def visitProjectExistentialBox(r: Option[SILResult], I: SILOperator.projectExistentialBox, ctx: Context): Array[InstructionDef]
  protected def visitOpenExistentialBox(r: Option[SILResult], I: SILOperator.openExistentialBox, ctx: Context): Array[InstructionDef]
  // protected def visitOpenExistentialBoxValue(r: Option[SILResult], I: SILOperator.openExistentialBoxValue, ctx: Context): Array[InstructionDef]
  protected def visitDeallocExistentialBox(r: Option[SILResult], I: SILOperator.deallocExistentialBox, ctx: Context): Array[InstructionDef]

  /* BLOCKS */
  protected def visitProjectBlockStorage(r: Option[SILResult], I: SILOperator.projectBlockStorage, ctx: Context): Array[InstructionDef]
  protected def visitInitBlockStorageHeader(r: Option[SILResult], I: SILOperator.initBlockStorageHeader, ctx: Context): Array[InstructionDef]

  /* UNCHECKED CONVERSIONS */
  protected def visitUpcast(r: Option[SILResult], I: SILOperator.upcast, ctx: Context): Array[InstructionDef]
  protected def visitAddressToPointer(r: Option[SILResult], I: SILOperator.addressToPointer, ctx: Context): Array[InstructionDef]
  protected def visitPointerToAddress(r: Option[SILResult], I: SILOperator.pointerToAddress, ctx: Context): Array[InstructionDef]
  protected def visitUncheckedRefCast(r: Option[SILResult], I: SILOperator.uncheckedRefCast, ctx: Context): Array[InstructionDef]
  // protected def visitUncheckedRefCastAddr(r: Option[SILResult], I: SILOperator.uncheckedRefCastAddr, ctx: Context): Array[InstructionDef]
  protected def visitUncheckedAddrCast(r: Option[SILResult], I: SILOperator.uncheckedAddrCast, ctx: Context): Array[InstructionDef]
  protected def visitUncheckedTrivialBitCast(r: Option[SILResult], I: SILOperator.uncheckedTrivialBitCast, ctx: Context): Array[InstructionDef]
  // protected def visitUncheckedBitwiseCast(r: Option[SILResult], I: SILOperator.uncheckedBitwiseCast, ctx: Context): Array[InstructionDef]
  // protected def visitRefToRawPointer(r: Option[SILResult], I: SILOperator.refToRawPointer, ctx: Context): Array[InstructionDef]
  // protected def visitRawPointerToRef(r: Option[SILResult], I: SILOperator.rawPointerToRef, ctx: Context): Array[InstructionDef]
  protected def visitRefToUnowned(r: Option[SILResult], I: SILOperator.refToUnowned, ctx: Context): Array[InstructionDef]
  // protected def visitUnownedToRef(r: Option[SILResult], I: SILOperator.unownedToRef, ctx: Context): Array[InstructionDef]
  protected def visitRefToUnmanaged(r: Option[SILResult], I: SILOperator.refToUnmanaged, ctx: Context): Array[InstructionDef]
  protected def visitUnmanagedToRef(r: Option[SILResult], I: SILOperator.unmanagedToRef, ctx: Context): Array[InstructionDef]
  protected def visitConvertFunction(r: Option[SILResult], I: SILOperator.convertFunction, ctx: Context): Array[InstructionDef]
  protected def visitConvertEscapeToNoEscape(r: Option[SILResult], I: SILOperator.convertEscapeToNoescape, ctx: Context): Array[InstructionDef]
  // protected def visitThinFunctionToPointer(r: Option[SILResult], I: SILOperator.thinFunctionToPointer, ctx: Context): Array[InstructionDef]
  // protected def visitPointerToThinFunction(r: Option[SILResult], I: SILOperator.pointerToThinFunction, ctx: Context): Array[InstructionDef]
  // protected def visitClassifyBridgeObject(r: Option[SILResult], I: SILOperator.classifyBridgeObject, ctx: Context): Array[InstructionDef]
  protected def visitValueToBridgeObject(r: Option[SILResult], I: SILOperator.valueToBridgeObject, ctx: Context): Array[InstructionDef]
  // protected def visitRefToBridgeObject(r: Option[SILResult], I: SILOperator.refToBridgeObject, ctx: Context): Array[InstructionDef]
  // protected def visitBridgeObjectToRef(r: Option[SILResult], I: SILOperator.bridgeObjectToRef, ctx: Context): Array[InstructionDef]
  // protected def visitBridgeObjectToWord(r: Option[SILResult], I: SILOperator.bridgeObjectToWord, ctx: Context): Array[InstructionDef]
  protected def visitThinToThickFunction(r: Option[SILResult], I: SILOperator.thinToThickFunction, ctx: Context): Array[InstructionDef]
  protected def visitThickToObjCMetatype(r: Option[SILResult], I: SILOperator.thickToObjcMetatype, ctx: Context): Array[InstructionDef]
  protected def visitObjCToThickMetatype(r: Option[SILResult], I: SILOperator.objcToThickMetatype, ctx: Context): Array[InstructionDef]
  protected def visitObjCMetatypeToObject(r: Option[SILResult], I: SILOperator.objcMetatypeToObject, ctx: Context): Array[InstructionDef]
  protected def visitObjCExistentialMetatypeToObject(r: Option[SILResult], I: SILOperator.objcExistentialMetatypeToObject, ctx: Context): Array[InstructionDef]

  /* CHECKED CONVERSIONS */
  protected def visitUnconditionalCheckedCast(r: Option[SILResult], I: SILOperator.unconditionalCheckedCast, ctx: Context): Array[InstructionDef]
  protected def visitUnconditionalCheckedCastAddr(r: Option[SILResult], I: SILOperator.unconditionalCheckedCastAddr, ctx: Context): Array[InstructionDef]
  // protected def visitUnconditionalCheckedCastValue(r: Option[SILResult], I: SILOperator.unconditionalCheckedCastValue, ctx: Context): Array[InstructionDef]

  /* RUNTIME FAILURES */

  protected def visitCondFail(r: Option[SILResult], I: SILOperator.condFail, ctx: Context): Array[InstructionDef]

  /* TERMINATORS */
  protected def visitUnreachable() : Array[InstructionDef]
  protected def visitReturn(I: SILTerminator.ret, ctx: Context): Array[InstructionDef]
  protected def visitThrow(I: SILTerminator.thro, ctx: Context): Array[InstructionDef]
  protected def visitYield(I: SILTerminator.yld, ctx: Context): Array[InstructionDef]
  protected def visitUnwind(): Array[InstructionDef]
  protected def visitBr(I: SILTerminator.br, ctx: Context): Array[InstructionDef]
  protected def visitCondBr(I: SILTerminator.condBr, ctx: Context): Array[InstructionDef]

  protected def visitSwitchValue(I: SILTerminator.switchValue, ctx: Context): Array[InstructionDef]
  // protected def visitSelectValue(I: SILTerminator.selectValue, ctx: Context): Array[InstructionDef]
  protected def visitSwitchEnum(I: SILTerminator.switchEnum, ctx: Context): Array[InstructionDef]
  protected def visitSwitchEnumAddr(I: SILTerminator.switchEnumAddr, ctx: Context): Array[InstructionDef]
  protected def visitDynamicMethodBr(I: SILTerminator.dynamicMethodBr, ctx: Context): Array[InstructionDef]
  protected def visitCheckedCastBr(I: SILTerminator.checkedCastBr, ctx: Context): Array[InstructionDef]
  // protected def visitCheckedCastValueBr(I: SILTerminator.checkedCastValueBr, ctx: Context): Array[InstructionDef]
  protected def visitCheckedCastAddrBr(I: SILTerminator.checkedCastAddrBr, ctx: Context): Array[InstructionDef]
  protected def visitTryApply(I: SILTerminator.tryApply, ctx: Context): Array[InstructionDef]
}
