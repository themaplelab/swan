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
import scala.util.control.Breaks.{break, breakable}

/*
 * This is does not include the compilation of each instruction
 * simply to keep it cleaner.
 */
trait ISILToRawSWANIR {

  val NOP = null

  def compileSILModule(silModule: SILModule): Module = {
    val functions = new Array[Function](0)
    silModule.functions.foreach( (silFunction: SILFunction) => {
      functions :+ compileSILFunction(silFunction)
    })
    new Module(functions)
  }

  private def compileSILFunction(silFunction: SILFunction): Function = {
    intermediateSymbols.clear()
    val blocks = new Array[Block](0)
    silFunction.blocks.foreach( (silBlock: SILBlock) => {
      blocks :+ compileSILBlock(silBlock)
    })
    val coroutine = if(isCoroutine(silFunction)) Some(FunctionAttribute.coroutine) else None
    new Function(coroutine, silFunction.name.demangled, Utils.SILTypeToType(silFunction.tpe), blocks)
  }

  private def compileSILBlock(silBlock: SILBlock): Block = {
    val arguments: Array[Argument] = new Array(0)
    silBlock.arguments.foreach( (a: SILArgument) => {
      arguments :+ Utils.SILArgumentToArgument(a)
    })
    val operators = new Array[OperatorDef](0)
    silBlock.operatorDefs.foreach( (silOperatorDef: SILOperatorDef) => {
      breakable {
        val position: Option[Position] = Utils.SILSourceInfoToPosition(silOperatorDef.sourceInfo)
        val instructions: Array[InstructionDef] = compileSILInstruction(SILInstructionDef.operator(silOperatorDef))
        if (instructions == NOP) {
          break()
        }
        instructions.foreach( (inst: InstructionDef) => {
          assert(inst.isInstanceOf[InstructionDef.operator])
          val operator: OperatorDef = Instruction.operator.asInstanceOf[InstructionDef.operator].operatorDef
          operators :+ operator
        })
      }
    })
    val terminator: TerminatorDef = {
      val position: Option[Position] = Utils.SILSourceInfoToPosition(silBlock.terminatorDef.sourceInfo)
      val instructions = compileSILInstruction(
        SILInstructionDef.terminator(silBlock.terminatorDef))
      assert(instructions.length == 1)
      val instruction: InstructionDef = instructions(0)
      assert(instruction.isInstanceOf[InstructionDef.terminator])
      Instruction.terminator.asInstanceOf[InstructionDef.terminator].terminatorDef
    }
    new Block(silBlock.identifier, arguments, operators, terminator)
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
    val ret = "i_" + intermediateSymbols.get(value).toString + "_" + value
    intermediateSymbols(value) = intermediateSymbols(value) + 1
    ret
  }

  private def compileSILInstruction(silInstructionDef: SILInstructionDef): Array[InstructionDef] = {
    silInstructionDef.instruction match {
      case operatorDef: SILOperatorDef => {
        val result = operatorDef.result
        val instruction = operatorDef.operator
        instruction match {
          case inst: SILOperator.allocStack => visitAllocStack(result, inst)
          case inst: SILOperator.allocRef => visitAllocRef(result, inst)
          case inst: SILOperator.allocRefDynamic => visitAllocRefDynamic(result, inst)
          case inst: SILOperator.allocBox => visitAllocBox(result, inst)
          case inst: SILOperator.allocValueBuffer => visitAllocValueBuffer(result, inst)
          case inst: SILOperator.allocGlobal => visitAllocGlobal(result, inst)
          case inst: SILOperator.deallocStack => visitDeallocStack(result, inst)
          case inst: SILOperator.deallocBox => visitDeallocBox(result, inst)
          case inst: SILOperator.projectBox => visitProjectBox(result, inst)
          case inst: SILOperator.deallocRef => visitDeallocRef(result, inst)
          case inst: SILOperator.debugValue => visitDebugValue(result, inst)
          case inst: SILOperator.debugValueAddr => visitDebugValueAddr(result, inst)
          case inst: SILOperator.load => visitLoad(result, inst)
          case inst: SILOperator.store => visitStore(result, inst)
          case inst: SILOperator.loadBorrow => visitLoadBorrow(result, inst)
          case inst: SILOperator.beginBorrow => visitBeginBorrow(result, inst)
          case inst: SILOperator.endBorrow => visitEndBorrow(result, inst)
          case inst: SILOperator.copyAddr => visitCopyAddr(result, inst)
          case inst: SILOperator.destroyAddr => visitDestroyAddr(result, inst)
          case inst: SILOperator.indexAddr => visitIndexAddr(result, inst)
          case inst: SILOperator.beginAccess => visitBeginAccess(result, inst)
          case inst: SILOperator.endAccess => visitEndAccess(result, inst)
          case inst: SILOperator.strongRetain => visitStrongRetain(result, inst)
          case inst: SILOperator.strongRelease => visitStrongRelease(result, inst)
          case inst: SILOperator.loadWeak => visitLoadWeak(result, inst)
          case inst: SILOperator.storeWeak => visitStoreWeak(result, inst)
          case inst: SILOperator.loadUnowned => visitLoadUnowned(result, inst)
          case inst: SILOperator.storeUnowned => visitStoreUnowned(result, inst)
          case inst: SILOperator.markDependence => visitMarkDependence(result, inst)
          case inst: SILOperator.isEscapingClosure => visitIsEscapingClosure(result, inst)
          case inst: SILOperator.copyBlock => visitCopyBlock(result, inst)
          case inst: SILOperator.copyBlockWithoutEscaping => visitCopyBlockWithoutEscaping(result, inst)
          case inst: SILOperator.functionRef => visitFunctionRef(result, inst)
          case inst: SILOperator.dynamicFunctionRef => visitDynamicFunctionRef(result, inst)
          case inst: SILOperator.prevDynamicFunctionRef => visitPrevDynamicFunctionRef(result, inst)
          case inst: SILOperator.globalAddr => visitGlobalAddr(result, inst)
          case inst: SILOperator.integerLiteral => visitIntegerLiteral(result, inst)
          case inst: SILOperator.floatLiteral => visitFloatLiteral(result, inst)
          case inst: SILOperator.stringLiteral => visitStringLiteral(result, inst)
          case inst: SILOperator.classMethod => visitClassMethod(result, inst)
          case inst: SILOperator.objcMethod => visitObjCMethod(result, inst)
          case inst: SILOperator.objcSuperMethod => visitObjCSuperMethod(result, inst)
          case inst: SILOperator.witnessMethod => visitWitnessMethod(result, inst)
          case inst: SILOperator.apply => visitApply(result, inst)
          case inst: SILOperator.beginApply => visitBeginApply(result, inst)
          case inst: SILOperator.abortApply => visitAbortApply(result, inst)
          case inst: SILOperator.endApply => visitEndApply(result, inst)
          case inst: SILOperator.partialApply => visitPartialApply(result, inst)
          case inst: SILOperator.builtin => visitBuiltin(result, inst)
          case inst: SILOperator.metatype => visitMetatype(result, inst)
          case inst: SILOperator.valueMetatype => visitValueMetatype(result, inst)
          case inst: SILOperator.existentialMetatype => visitExistentialMetatype(result, inst)
          case inst: SILOperator.objcProtocol => visitObjCProtocol(result, inst)
          case inst: SILOperator.retainValue => visitRetainValue(result, inst)
          case inst: SILOperator.copyValue => visitCopyValue(result, inst)
          case inst: SILOperator.releaseValue => visitReleaseValue(result, inst)
          case inst: SILOperator.destroyValue => visitDestroyValue(result, inst)
          case inst: SILOperator.autoreleaseValue => visitAutoreleaseValue(result, inst)
          case inst: SILOperator.tuple => visitTuple(result, inst)
          case inst: SILOperator.tupleExtract => visitTupleExtract(result, inst)
          case inst: SILOperator.tupleElementAddr => visitTupleElementAddr(result, inst)
          case inst: SILOperator.destructureTuple => visitDestructureTuple(result, inst)
          case inst: SILOperator.struct => visitStruct(result, inst)
          case inst: SILOperator.structExtract => visitStructExtract(result, inst)
          case inst: SILOperator.structElementAddr => visitStructElementAddr(result, inst)
          case inst: SILOperator.refElementAddr => visitRefElementAddr(result, inst)
          case inst: SILOperator.enm => visitEnum(result, inst)
          case inst: SILOperator.uncheckedEnumData => visitUncheckedEnumData(result, inst)
          case inst: SILOperator.initEnumDataAddr => visitInitEnumDataAddr(result, inst)
          case inst: SILOperator.injectEnumAddr => visitInjectEnumAddr(result, inst)
          case inst: SILOperator.uncheckedTakeEnumDataAddr => visitUncheckedTakeEnumDataAddr(result, inst)
          case inst: SILOperator.selectEnum => visitSelectEnum(result, inst)
          case inst: SILOperator.selectEnumAddr => visitSelectEnumAddr(result, inst)
          case inst: SILOperator.initExistentialAddr => visitInitExistentialAddr(result, inst)
          case inst: SILOperator.deinitExistentialAddr => visitDeinitExistentialAddr(result, inst)
          case inst: SILOperator.openExistentialAddr => visitOpenExistentialAddr(result, inst)
          case inst: SILOperator.initExistentialRef => visitInitExistentialRef(result, inst)
          case inst: SILOperator.openExistentialRef => visitOpenExistentialRef(result, inst)
          case inst: SILOperator.initExistentialMetatype => visitInitExistentialMetatype(result, inst)
          case inst: SILOperator.openExistentialMetatype => visitOpenExistentialMetatype(result, inst)
          case inst: SILOperator.allocExistentialBox => visitAllocExistentialBox(result, inst)
          case inst: SILOperator.projectExistentialBox => visitProjectExistentialBox(result, inst)
          case inst: SILOperator.openExistentialBox => visitOpenExistentialBox(result, inst)
          case inst: SILOperator.deallocExistentialBox => visitDeallocExistentialBox(result, inst)
          case inst: SILOperator.projectBlockStorage => visitProjectBlockStorage(result, inst)
          case inst: SILOperator.upcast => visitUpcast(result, inst)
          case inst: SILOperator.addressToPointer => visitAddressToPointer(result, inst)
          case inst: SILOperator.pointerToAddress => visitPointerToAddress(result, inst)
          case inst: SILOperator.uncheckedRefCast => visitUncheckedRefCast(result, inst)
          case inst: SILOperator.uncheckedAddrCast => visitUncheckedAddrCast(result, inst)
          case inst: SILOperator.uncheckedTrivialBitCast => visitUncheckedTrivialBitCast(result, inst)
          case inst: SILOperator.refToUnowned => visitRefToUnowned(result, inst)
          case inst: SILOperator.refToUnmanaged => visitRefToUnmanaged(result, inst)
          case inst: SILOperator.unmanagedToRef => visitUnmanagedToRef(result, inst)
          case inst: SILOperator.convertFunction => visitConvertFunction(result, inst)
          case inst: SILOperator.convertEscapeToNoescape => visitConvertEscapeToNoEscape(result, inst)
          case inst: SILOperator.thinToThickFunction => visitThinToThickFunction(result, inst)
          case inst: SILOperator.thickToObjcMetatype => visitThickToObjCMetatype(result, inst)
          case inst: SILOperator.objcToThickMetatype => visitObjCToThickMetatype(result, inst)
          case inst: SILOperator.objcMetatypeToObject => visitObjCMetatypeToObject(result, inst)
          case inst: SILOperator.objcExistentialMetatypeToObject => visitObjCExistentialMetatypeToObject(result, inst)
          case inst: SILOperator.unconditionalCheckedCast => visitUnconditionalCheckedCast(result, inst)
          case inst: SILOperator.unconditionalCheckedCastAddr => visitUnconditionalCheckedCastAddr(result, inst)
          case inst: SILOperator.condFail => visitCondFail(result, inst)
        }
      }
      case _ => // Terminator
        val terminatorDef = silInstructionDef.instruction.asInstanceOf[SILTerminatorDef]
        val instruction = terminatorDef.terminator
        instruction match {
          case SILTerminator.unreachable => visitUnreachable()
          case inst: SILTerminator.ret => visitReturn(inst)
          case inst: SILTerminator.thro => visitThrow(inst)
          case inst: SILTerminator.yld => visitYield(inst)
          case SILTerminator.unwind => visitUnwind()
          case inst: SILTerminator.br => visitBr(inst)
          case inst: SILTerminator.condBr => visitCondBr(inst)
          case inst: SILTerminator.switchEnum => visitSwitchEnum(inst)
          case inst: SILTerminator.switchEnumAddr => visitSwitchEnumAddr(inst)
          case inst: SILTerminator.dynamicMethodBr => visitDynamicMethodBr(inst)
          case inst: SILTerminator.checkedCastBr => visitCheckedCastBr(inst)
          case inst: SILTerminator.checkedCastAddrBr => visitCheckedCastAddrBr(inst)
          case inst: SILTerminator.tryApply => visitTryApply(inst)
        }
    }

  }

  /* ALLOCATION AND DEALLOCATION */

  protected def visitAllocStack(r: Option[SILResult], I: SILOperator.allocStack): Array[InstructionDef]

  protected def visitAllocRef(r: Option[SILResult], I: SILOperator.allocRef): Array[InstructionDef]
  protected def visitAllocRefDynamic(r: Option[SILResult], I: SILOperator.allocRefDynamic): Array[InstructionDef]
  protected def visitAllocBox(r: Option[SILResult], I: SILOperator.allocBox): Array[InstructionDef]

  protected def visitAllocValueBuffer(r: Option[SILResult], I: SILOperator.allocValueBuffer): Array[InstructionDef]
  protected def visitAllocGlobal(r: Option[SILResult], I: SILOperator.allocGlobal): Array[InstructionDef]

  protected def visitDeallocStack(r: Option[SILResult], I: SILOperator.deallocStack): Array[InstructionDef]

  protected def visitDeallocBox(r: Option[SILResult], I: SILOperator.deallocBox): Array[InstructionDef]

  protected def visitProjectBox(r: Option[SILResult], I: SILOperator.projectBox): Array[InstructionDef]

  protected def visitDeallocRef(r: Option[SILResult], I: SILOperator.deallocRef): Array[InstructionDef]
  // protected def visitDeallocPartialRef(r: Option[SILResult], I: SILOperator.deallocPartialRef): Array[InstructionDef]
  // protected def visitDeallocValueBuffer(r: Option[SILResult], I: SILOperator.deallocValueBuffer): Array[InstructionDef]
  // protected def visitProjectValueBuffer(r: Option[SILResult], I: SILOperator.projectValueBuffer): Array[InstructionDef]

  /* DEBUG INFORMATION */

  protected def visitDebugValue(r: Option[SILResult], I: SILOperator.debugValue): Array[InstructionDef]

  protected def visitDebugValueAddr(r: Option[SILResult], I: SILOperator.debugValueAddr): Array[InstructionDef]

  /* ACCESSING MEMORY */

  protected def visitLoad(r: Option[SILResult], I: SILOperator.load): Array[InstructionDef]
  protected def visitStore(r: Option[SILResult], I: SILOperator.store): Array[InstructionDef]
  // protected def visitStoreBorrow(r: Option[SILResult], I: SILOperator.storeBorrow): Array[InstructionDef]
  protected def visitLoadBorrow(r: Option[SILResult], I: SILOperator.loadBorrow): Array[InstructionDef]
  protected def visitBeginBorrow(r: Option[SILResult], I: SILOperator.beginBorrow): Array[InstructionDef]
  protected def visitEndBorrow(r: Option[SILResult], I: SILOperator.endBorrow): Array[InstructionDef]
  protected def visitCopyAddr(r: Option[SILResult], I: SILOperator.copyAddr): Array[InstructionDef]
  protected def visitDestroyAddr(r: Option[SILResult], I: SILOperator.destroyAddr): Array[InstructionDef]
  protected def visitIndexAddr(r: Option[SILResult], I: SILOperator.indexAddr): Array[InstructionDef]
  // protected def visitTailAddr(r: Option[SILResult], I: SILOperator.tailAddr): Array[InstructionDef]
  // protected def visitIndexRawPointer(r: Option[SILResult], I: SILOperator.indexRawPointer): Array[InstructionDef]
  // protected def visitBindMemory(r: Option[SILResult], I: SILOperator.bindMemory): Array[InstructionDef]
  protected def visitBeginAccess(r: Option[SILResult], I: SILOperator.beginAccess): Array[InstructionDef]
  protected def visitEndAccess(r: Option[SILResult], I: SILOperator.endAccess): Array[InstructionDef]
  // protected def visitBeginUnpairedAccess(r: Option[SILResult], I: SILOperator.beginUnpairedAccess): Array[InstructionDef]
  // protected def visitEndUnpairedAccess(r: Option[SILResult], I: SILOperator.endUnpairedAccess): Array[InstructionDef]

  /* REFERENCE COUNTING */

  protected def visitStrongRetain(r: Option[SILResult], I: SILOperator.strongRetain): Array[InstructionDef]
  protected def visitStrongRelease(r: Option[SILResult], I: SILOperator.strongRelease): Array[InstructionDef]
  // protected def visitSetDeallocating(r: Option[SILResult], I: SILOperator.setDeallocating): Array[InstructionDef]
  // protected def visitStrongRetainUnowned(r: Option[SILResult], I: SILOperator.strongRetainUnowned): Array[InstructionDef]
  // protected def visitUnownedRetain(r: Option[SILResult], I: SILOperator.unownedRetain): Array[InstructionDef]
  // protected def visitUnownedRelease(r: Option[SILResult], I: SILOperator.unownedRelease): Array[InstructionDef]
  protected def visitLoadWeak(r: Option[SILResult], I: SILOperator.loadWeak): Array[InstructionDef]
  protected def visitStoreWeak(r: Option[SILResult], I: SILOperator.storeWeak): Array[InstructionDef]
  protected def visitLoadUnowned(r: Option[SILResult], I: SILOperator.loadUnowned): Array[InstructionDef]
  protected def visitStoreUnowned(r: Option[SILResult], I: SILOperator.storeUnowned): Array[InstructionDef]
  // protected def visitFixLifetime(r: Option[SILResult], I: SILOperator.fixLifetime): Array[InstructionDef]
  protected def visitMarkDependence(r: Option[SILResult], I: SILOperator.markDependence): Array[InstructionDef]
  // protected def visitIsUnique(r: Option[SILResult], I: SILOperator.isUnique): Array[InstructionDef]
  protected def visitIsEscapingClosure(r: Option[SILResult], I: SILOperator.isEscapingClosure): Array[InstructionDef]
  protected def visitCopyBlock(r: Option[SILResult], I: SILOperator.copyBlock): Array[InstructionDef]
  protected def visitCopyBlockWithoutEscaping(r: Option[SILResult], I: SILOperator.copyBlockWithoutEscaping): Array[InstructionDef]

  /* LITERALS */

  protected def visitFunctionRef(r: Option[SILResult], I: SILOperator.functionRef): Array[InstructionDef]
  protected def visitDynamicFunctionRef(r: Option[SILResult], I: SILOperator.dynamicFunctionRef): Array[InstructionDef]
  protected def visitPrevDynamicFunctionRef(r: Option[SILResult], I: SILOperator.prevDynamicFunctionRef): Array[InstructionDef]
  protected def visitGlobalAddr(r: Option[SILResult], I: SILOperator.globalAddr): Array[InstructionDef]
  // protected def visitGlobalValue(r: Option[SILResult], I: SILOperator.globalValue): Array[InstructionDef]
  protected def visitIntegerLiteral(r: Option[SILResult], I: SILOperator.integerLiteral): Array[InstructionDef]
  protected def visitFloatLiteral(r: Option[SILResult], I: SILOperator.floatLiteral): Array[InstructionDef]
  protected def visitStringLiteral(r: Option[SILResult], I: SILOperator.stringLiteral): Array[InstructionDef]

  /* DYNAMIC DISPATCH */
  protected def visitClassMethod(r: Option[SILResult], I: SILOperator.classMethod): Array[InstructionDef]
  protected def visitObjCMethod(r: Option[SILResult], I: SILOperator.objcMethod): Array[InstructionDef]
  // protected def visitSuperMethod(r: Option[SILResult], I: SILOperator.superMethod): Array[InstructionDef]
  protected def visitObjCSuperMethod(r: Option[SILResult], I: SILOperator.objcSuperMethod): Array[InstructionDef]
  protected def visitWitnessMethod(r: Option[SILResult], I: SILOperator.witnessMethod): Array[InstructionDef]

  /* FUNCTION APPLICATION */

  protected def visitApply(r: Option[SILResult], I: SILOperator.apply): Array[InstructionDef]
  protected def visitBeginApply(r: Option[SILResult], I: SILOperator.beginApply): Array[InstructionDef]
  protected def visitAbortApply(r: Option[SILResult], I: SILOperator.abortApply): Array[InstructionDef]
  protected def visitEndApply(r: Option[SILResult], I: SILOperator.endApply): Array[InstructionDef]
  protected def visitPartialApply(r: Option[SILResult], I: SILOperator.partialApply): Array[InstructionDef]
  protected def visitBuiltin(r: Option[SILResult], I: SILOperator.builtin): Array[InstructionDef]

  /* METATYPES */

  protected def visitMetatype(r: Option[SILResult], I: SILOperator.metatype): Array[InstructionDef]
  protected def visitValueMetatype(r: Option[SILResult], I: SILOperator.valueMetatype): Array[InstructionDef]
  protected def visitExistentialMetatype(r: Option[SILResult], I: SILOperator.existentialMetatype): Array[InstructionDef]
  protected def visitObjCProtocol(r: Option[SILResult], I: SILOperator.objcProtocol): Array[InstructionDef]

  /* AGGREGATE TYPES */
  protected def visitRetainValue(r: Option[SILResult], I: SILOperator.retainValue): Array[InstructionDef]
  // protected def visitRetainValueAddr(r: Option[SILResult], I: SILOperator.retainValueAddr): Array[InstructionDef]
  // protected def visitUnmanagedRetainValue(r: Option[SILResult], I: SILOperator.unmanagedRetainValue): Array[InstructionDef]
  protected def visitCopyValue(r: Option[SILResult], I: SILOperator.copyValue): Array[InstructionDef]
  protected def visitReleaseValue(r: Option[SILResult], I: SILOperator.releaseValue): Array[InstructionDef]
  // protected def visitReleaseValueAddr(r: Option[SILResult], I: SILOperator.releaseValueAddr): Array[InstructionDef]
  // protected def visitUnmanagedReleaseValue(r: Option[SILResult], I: SILOperator.unmanagedReleaseValue): Array[InstructionDef]
  protected def visitDestroyValue(r: Option[SILResult], I: SILOperator.destroyValue): Array[InstructionDef]
  protected def visitAutoreleaseValue(r: Option[SILResult], I: SILOperator.autoreleaseValue): Array[InstructionDef]
  protected def visitTuple(r: Option[SILResult], I: SILOperator.tuple): Array[InstructionDef]
  protected def visitTupleExtract(r: Option[SILResult], I: SILOperator.tupleExtract): Array[InstructionDef]
  protected def visitTupleElementAddr(r: Option[SILResult], I: SILOperator.tupleElementAddr): Array[InstructionDef]
  protected def visitDestructureTuple(r: Option[SILResult], I: SILOperator.destructureTuple): Array[InstructionDef]
  protected def visitStruct(r: Option[SILResult], I: SILOperator.struct): Array[InstructionDef]
  protected def visitStructExtract(r: Option[SILResult], I: SILOperator.structExtract): Array[InstructionDef]
  protected def visitStructElementAddr(r: Option[SILResult], I: SILOperator.structElementAddr): Array[InstructionDef]
  // protected def visitDestructureStruct(r: Option[SILResult], I: SILOperator.destructureStruct): Array[InstructionDef]
  // protected def visitObject(r: Option[SILResult], I: SILOperator.obj): Array[InstructionDef]
  protected def visitRefElementAddr(r: Option[SILResult], I: SILOperator.refElementAddr): Array[InstructionDef]
  // protected def visitRefTailAddr(r: Option[SILResult], I: SILOperator.refTailAddr): Array[InstructionDef]

  /* ENUMS */

  protected def visitEnum(r: Option[SILResult], I: SILOperator.enm): Array[InstructionDef]
  protected def visitUncheckedEnumData(r: Option[SILResult], I: SILOperator.uncheckedEnumData): Array[InstructionDef]
  protected def visitInitEnumDataAddr(r: Option[SILResult], I: SILOperator.initEnumDataAddr): Array[InstructionDef]
  protected def visitInjectEnumAddr(r: Option[SILResult], I: SILOperator.injectEnumAddr): Array[InstructionDef]
  protected def visitUncheckedTakeEnumDataAddr(r: Option[SILResult], I: SILOperator.uncheckedTakeEnumDataAddr): Array[InstructionDef]
  protected def visitSelectEnum(r: Option[SILResult], I: SILOperator.selectEnum): Array[InstructionDef]
  protected def visitSelectEnumAddr(r: Option[SILResult], I: SILOperator.selectEnumAddr): Array[InstructionDef]

  /* PROTOCOL AND PROTOCOL COMPOSITION TYPES */
  protected def visitInitExistentialAddr(r: Option[SILResult], I: SILOperator.initExistentialAddr): Array[InstructionDef]
  // protected def visitInitExistentialValue(r: Option[SILResult], I: SILOperator.initExistentialValue): Array[InstructionDef]
  protected def visitDeinitExistentialAddr(r: Option[SILResult], I: SILOperator.deinitExistentialAddr): Array[InstructionDef]
  // protected def visitDeinitExistentialValue(r: Option[SILResult], I: SILOperator.deinitExistentialValue): Array[InstructionDef]
  protected def visitOpenExistentialAddr(r: Option[SILResult], I: SILOperator.openExistentialAddr): Array[InstructionDef]
  // protected def visitOpenExistentialValue(r: Option[SILResult], I: SILOperator.openExistentialValue): Array[InstructionDef]
  protected def visitInitExistentialRef(r: Option[SILResult], I: SILOperator.initExistentialRef): Array[InstructionDef]
  protected def visitOpenExistentialRef(r: Option[SILResult], I: SILOperator.openExistentialRef): Array[InstructionDef]
  protected def visitInitExistentialMetatype(r: Option[SILResult], I: SILOperator.initExistentialMetatype): Array[InstructionDef]
  protected def visitOpenExistentialMetatype(r: Option[SILResult], I: SILOperator.openExistentialMetatype): Array[InstructionDef]
  protected def visitAllocExistentialBox(r: Option[SILResult], I: SILOperator.allocExistentialBox): Array[InstructionDef]
  protected def visitProjectExistentialBox(r: Option[SILResult], I: SILOperator.projectExistentialBox): Array[InstructionDef]
  protected def visitOpenExistentialBox(r: Option[SILResult], I: SILOperator.openExistentialBox): Array[InstructionDef]
  // protected def visitOpenExistentialBoxValue(r: Option[SILResult], I: SILOperator.openExistentialBoxValue): Array[InstructionDef]
  protected def visitDeallocExistentialBox(r: Option[SILResult], I: SILOperator.deallocExistentialBox): Array[InstructionDef]

  /* BLOCKS */
  protected def visitProjectBlockStorage(r: Option[SILResult], I: SILOperator.projectBlockStorage): Array[InstructionDef]
  // protected def visitInitBlockStorageHeader(r: Option[SILResult], I: SILOperator.initBlockStorageHeader): Array[InstructionDef]

  /* UNCHECKED CONVERSIONS */
  protected def visitUpcast(r: Option[SILResult], I: SILOperator.upcast): Array[InstructionDef]
  protected def visitAddressToPointer(r: Option[SILResult], I: SILOperator.addressToPointer): Array[InstructionDef]
  protected def visitPointerToAddress(r: Option[SILResult], I: SILOperator.pointerToAddress): Array[InstructionDef]
  protected def visitUncheckedRefCast(r: Option[SILResult], I: SILOperator.uncheckedRefCast): Array[InstructionDef]
  // protected def visitUncheckedRefCastAddr(r: Option[SILResult], I: SILOperator.uncheckedRefCastAddr): Array[InstructionDef]
  protected def visitUncheckedAddrCast(r: Option[SILResult], I: SILOperator.uncheckedAddrCast): Array[InstructionDef]
  protected def visitUncheckedTrivialBitCast(r: Option[SILResult], I: SILOperator.uncheckedTrivialBitCast): Array[InstructionDef]
  // protected def visitUncheckedBitwiseCast(r: Option[SILResult], I: SILOperator.uncheckedBitwiseCast): Array[InstructionDef]
  // protected def visitRefToRawPointer(r: Option[SILResult], I: SILOperator.refToRawPointer): Array[InstructionDef]
  // protected def visitRawPointerToRef(r: Option[SILResult], I: SILOperator.rawPointerToRef): Array[InstructionDef]
  protected def visitRefToUnowned(r: Option[SILResult], I: SILOperator.refToUnowned): Array[InstructionDef]
  // protected def visitUnownedToRef(r: Option[SILResult], I: SILOperator.unownedToRef): Array[InstructionDef]
  protected def visitRefToUnmanaged(r: Option[SILResult], I: SILOperator.refToUnmanaged): Array[InstructionDef]
  protected def visitUnmanagedToRef(r: Option[SILResult], I: SILOperator.unmanagedToRef): Array[InstructionDef]
  protected def visitConvertFunction(r: Option[SILResult], I: SILOperator.convertFunction): Array[InstructionDef]
  protected def visitConvertEscapeToNoEscape(r: Option[SILResult], I: SILOperator.convertEscapeToNoescape): Array[InstructionDef]
  // protected def visitThinFunctionToPointer(r: Option[SILResult], I: SILOperator.thinFunctionToPointer): Array[InstructionDef]
  // protected def visitPointerToThinFunction(r: Option[SILResult], I: SILOperator.pointerToThinFunction): Array[InstructionDef]
  // protected def visitClassifyBridgeObject(r: Option[SILResult], I: SILOperator.classifyBridgeObject): Array[InstructionDef]
  // protected def visitValueToBridgeObject(r: Option[SILResult], I: SILOperator.valueToBridgeObject): Array[InstructionDef]
  // protected def visitRefToBridgeObject(r: Option[SILResult], I: SILOperator.refToBridgeObject): Array[InstructionDef]
  // protected def visitBridgeObjectToRef(r: Option[SILResult], I: SILOperator.bridgeObjectToRef): Array[InstructionDef]
  // protected def visitBridgeObjectToWord(r: Option[SILResult], I: SILOperator.bridgeObjectToWord): Array[InstructionDef]
  protected def visitThinToThickFunction(r: Option[SILResult], I: SILOperator.thinToThickFunction): Array[InstructionDef]
  protected def visitThickToObjCMetatype(r: Option[SILResult], I: SILOperator.thickToObjcMetatype): Array[InstructionDef]
  protected def visitObjCToThickMetatype(r: Option[SILResult], I: SILOperator.objcToThickMetatype): Array[InstructionDef]
  protected def visitObjCMetatypeToObject(r: Option[SILResult], I: SILOperator.objcMetatypeToObject): Array[InstructionDef]
  protected def visitObjCExistentialMetatypeToObject(r: Option[SILResult], I: SILOperator.objcExistentialMetatypeToObject): Array[InstructionDef]

  /* CHECKED CONVERSIONS */
  protected def visitUnconditionalCheckedCast(r: Option[SILResult], I: SILOperator.unconditionalCheckedCast): Array[InstructionDef]
  protected def visitUnconditionalCheckedCastAddr(r: Option[SILResult], I: SILOperator.unconditionalCheckedCastAddr): Array[InstructionDef]
  // protected def visitUnconditionalCheckedCastValue(r: Option[SILResult], I: SILOperator.unconditionalCheckedCastValue): Array[InstructionDef]

  /* RUNTIME FAILURES */

  protected def visitCondFail(r: Option[SILResult], I: SILOperator.condFail): Array[InstructionDef]

  /* TERMINATORS */
  protected def visitUnreachable() : Array[InstructionDef]
  protected def visitReturn(I: SILTerminator.ret): Array[InstructionDef]
  protected def visitThrow(I: SILTerminator.thro): Array[InstructionDef]
  protected def visitYield(I: SILTerminator.yld): Array[InstructionDef]
  protected def visitUnwind(): Array[InstructionDef]
  protected def visitBr(I: SILTerminator.br): Array[InstructionDef]
  protected def visitCondBr(I: SILTerminator.condBr): Array[InstructionDef]

  // protected def visitSwitchValue(I: SILTerminator.switchValue): Array[InstructionDef]
  // protected def visitSelectValue(I: SILTerminator.selectValue): Array[InstructionDef]
  protected def visitSwitchEnum(I: SILTerminator.switchEnum): Array[InstructionDef]
  protected def visitSwitchEnumAddr(I: SILTerminator.switchEnumAddr): Array[InstructionDef]
  protected def visitDynamicMethodBr(I: SILTerminator.dynamicMethodBr): Array[InstructionDef]
  protected def visitCheckedCastBr(I: SILTerminator.checkedCastBr): Array[InstructionDef]
  // protected def visitCheckedCastValueBr(I: SILTerminator.checkedCastValueBr): Array[InstructionDef]
  protected def visitCheckedCastAddrBr(I: SILTerminator.checkedCastAddrBr): Array[InstructionDef]
  protected def visitTryApply(I: SILTerminator.tryApply): Array[InstructionDef]
}
