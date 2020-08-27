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

import ca.ualberta.maple.swan.parser.{SILOperator, SILResult, SILTerminator, SILType}

// TODO: "box type issue" -> Are boxes just another form of pointer? Is special handling needed?
class SILToRawSWANIRCompiler extends ISILToRawSWANIRCompiler {

  private def makeOperator(operator: Operator*): Array[InstructionDef] = {
    val arr: Array[InstructionDef] = new Array[InstructionDef](operator.length)
    operator.zipWithIndex.foreach( (op: (Operator, Int))  => {
      arr(op._2) = new InstructionDef.operator(new OperatorDef(op._1))
    })
    arr
  }

  private def makeTerminator(terminator: Terminator*): Array[InstructionDef] = {
    val arr: Array[InstructionDef] = new Array[InstructionDef](terminator.length)
    terminator.zipWithIndex.foreach( (ter: (Terminator, Int))  => {
      arr(ter._2) = new InstructionDef.terminator(new TerminatorDef(ter._1))
    })
    arr
  }

  private def assertSILResult(r: Option[SILResult], i: Int): Unit = {
    assert(r.nonEmpty && r.get.valueNames.length == i)
  }

  override protected def visitAllocStack(r: Option[SILResult], I: SILOperator.allocStack): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToPointerType(I.tpe)) // IMPORTANT: $T to $*T
    }
    makeOperator(new Operator.neww(result))
  }

  override protected def visitAllocRef(r: Option[SILResult], I: SILOperator.allocRef): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(I.tpe))
    }
    makeOperator(new Operator.neww(result))
  }

  override protected def visitAllocRefDynamic(r: Option[SILResult], I: SILOperator.allocRefDynamic): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(I.tpe))
    }
    makeOperator(new Operator.neww(result))
  }

  override protected def visitAllocBox(r: Option[SILResult], I: SILOperator.allocBox): Array[InstructionDef] = {
    null // TODO: box type issue
  }

  override protected def visitAllocValueBuffer(r: Option[SILResult], I: SILOperator.allocValueBuffer): Array[InstructionDef] = {
    val result: Symbol = {
      assert(r.nonEmpty && r.get.valueNames.length == 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToPointerType(I.tpe)) // IMPORTANT: $T to $*T
    }
    makeOperator(new Operator.neww(result))
  }

  override protected def visitAllocGlobal(r: Option[SILResult], I: SILOperator.allocGlobal): Array[InstructionDef] = {
    makeOperator(new Operator.newGlobal(I.name))
  }

  override protected def visitDeallocStack(r: Option[SILResult], I: SILOperator.deallocStack): Array[InstructionDef] = {
    NOP
  }

  override protected def visitDeallocBox(r: Option[SILResult], I: SILOperator.deallocBox): Array[InstructionDef] = {
    NOP
  }

  override protected def visitProjectBox(r: Option[SILResult], I: SILOperator.projectBox): Array[InstructionDef] = {
    null // TODO: box type issue
  }

  override protected def visitDeallocRef(r: Option[SILResult], I: SILOperator.deallocRef): Array[InstructionDef] = {
    NOP
  }

  override protected def visitDebugValue(r: Option[SILResult], I: SILOperator.debugValue): Array[InstructionDef] = {
    NOP
  }

  override protected def visitDebugValueAddr(r: Option[SILResult], I: SILOperator.debugValueAddr): Array[InstructionDef] = {
    NOP
  }

  override protected def visitLoad(r: Option[SILResult], I: SILOperator.load): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILPointerTypeToType(I.operand.tpe))
    }
    makeOperator(new Operator.pointerRead(result, I.operand.value))
  }

  override protected def visitStore(r: Option[SILResult], I: SILOperator.store): Array[InstructionDef] = {
    makeOperator(new Operator.pointerWrite(I.from, I.to.value))
  }

  override protected def visitLoadBorrow(r: Option[SILResult], I: SILOperator.loadBorrow): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILPointerTypeToType(I.operand.tpe))
    }
    makeOperator(new Operator.pointerRead(result, I.operand.value))
  }

  override protected def visitBeginBorrow(r: Option[SILResult], I: SILOperator.beginBorrow): Array[InstructionDef] = {
    null // TODO: Unclear
  }

  override protected def visitEndBorrow(r: Option[SILResult], I: SILOperator.endBorrow): Array[InstructionDef] = {
    NOP
  }

  override protected def visitAssign(r: Option[SILResult], I: SILOperator.assign): Array[InstructionDef] = {
    makeOperator(new Operator.pointerWrite(I.from, I.to.value))
  }

  override protected def visitAssignByWrapper(r: Option[SILResult], I: SILOperator.assignByWrapper): Array[InstructionDef] = {
    null // TODO: Complex
  }

  override protected def visitMarkUninitialized(r: Option[SILResult], I: SILOperator.markUninitialized): Array[InstructionDef] = {
    NOP
  }

  override protected def visitMarkFunctionEscape(r: Option[SILResult], I: SILOperator.markFunctionEscape): Array[InstructionDef] = {
    NOP
  }

  override protected def visitCopyAddr(r: Option[SILResult], I: SILOperator.copyAddr): Array[InstructionDef] = {
    val pointerReadResult: Symbol = {
      new Symbol(generateSymbol(r.get.valueNames(0)), Utils.SILPointerTypeToType(I.operand.tpe))
    }
    makeOperator(
      new Operator.pointerRead(pointerReadResult, I.value),
      new Operator.pointerWrite(pointerReadResult.name, I.operand.value))
  }

  override protected def visitDestroyAddr(r: Option[SILResult], I: SILOperator.destroyAddr): Array[InstructionDef] = {
    NOP
  }

  override protected def visitIndexAddr(r: Option[SILResult], I: SILOperator.indexAddr): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(I.addr.tpe))
    }
    makeOperator(new Operator.arrayRead(result, true, I.addr.value))
  }

  override protected def visitBeginAccess(r: Option[SILResult], I: SILOperator.beginAccess): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(new Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitEndAccess(r: Option[SILResult], I: SILOperator.endAccess): Array[InstructionDef] = {
    NOP
  }

  override protected def visitStrongRetain(r: Option[SILResult], I: SILOperator.strongRetain): Array[InstructionDef] = {
    NOP
  }

  override protected def visitStrongRelease(r: Option[SILResult], I: SILOperator.strongRelease): Array[InstructionDef] = {
    NOP
  }

  override protected def visitLoadWeak(r: Option[SILResult], I: SILOperator.loadWeak): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILWeakOptionalToType(I.operand.tpe))
    }
    makeOperator(new Operator.pointerRead(result, I.operand.value))
  }

  override protected def visitStoreWeak(r: Option[SILResult], I: SILOperator.storeWeak): Array[InstructionDef] = {
    makeOperator(new Operator.pointerWrite(I.from, I.to.value))
  }

  override protected def visitLoadUnowned(r: Option[SILResult], I: SILOperator.loadUnowned): Array[InstructionDef] = {
    null // TODO: Result type is unknown (could be similar to load_weak)
  }

  override protected def visitStoreUnowned(r: Option[SILResult], I: SILOperator.storeUnowned): Array[InstructionDef] = {
    makeOperator(new Operator.pointerWrite(I.from, I.to.value))
  }

  override protected def visitMarkDependence(r: Option[SILResult], I: SILOperator.markDependence): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(new Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitIsEscapingClosure(r: Option[SILResult], I: SILOperator.isEscapingClosure): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(new SILType.namedType("Builtin.Int1")))
    }
    makeOperator(new Operator.unaryOp(result, UnaryOperation.arbitrary, I.operand.value))
  }

  override protected def visitCopyBlock(r: Option[SILResult], I: SILOperator.copyBlock): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(I.operand.tpe))
    }
    makeOperator(new Operator.assign(result, I.operand.value))
  }

  override protected def visitCopyBlockWithoutEscaping(r: Option[SILResult], I: SILOperator.copyBlockWithoutEscaping): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(I.operand1.tpe))
    }
    makeOperator(new Operator.assign(result, I.operand1.value))
  }

  override protected def visitFunctionRef(r: Option[SILResult], I: SILOperator.functionRef): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(I.tpe))
    }
    makeOperator(new Operator.functionRef(result, I.name))
  }

  override protected def visitDynamicFunctionRef(r: Option[SILResult], I: SILOperator.dynamicFunctionRef): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(I.tpe))
    }
    makeOperator(new Operator.functionRef(result, I.name))
  }

  override protected def visitPrevDynamicFunctionRef(r: Option[SILResult], I: SILOperator.prevDynamicFunctionRef): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(I.tpe))
    }
    makeOperator(new Operator.functionRef(result, I.name))
  }

  override protected def visitGlobalAddr(r: Option[SILResult], I: SILOperator.globalAddr): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(I.tpe))
    }
    makeOperator(new Operator.assignGlobal(result, I.name))
  }

  override protected def visitIntegerLiteral(r: Option[SILResult], I: SILOperator.integerLiteral): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(I.tpe))
    }
    makeOperator(new Operator.literal(result, new Literal.int(I.value)))
  }

  override protected def visitFloatLiteral(r: Option[SILResult], I: SILOperator.floatLiteral): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(I.tpe))
    }
    makeOperator(new Operator.literal(result, new Literal.float(Utils.SILFloatStringToFloat(I.value))))
  }

  override protected def visitStringLiteral(r: Option[SILResult], I: SILOperator.stringLiteral): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(new SILType.namedType("$Builtin.RawPointer")))
    }
    makeOperator(new Operator.literal(result, new Literal.string(I.value)))
  }

  override protected def visitClassMethod(r: Option[SILResult], I: SILOperator.classMethod): Array[InstructionDef] = {
    null
  }

  override protected def visitObjCMethod(r: Option[SILResult], I: SILOperator.objcMethod): Array[InstructionDef] = {
    null
  }

  override protected def visitObjCSuperMethod(r: Option[SILResult], I: SILOperator.objcSuperMethod): Array[InstructionDef] = {
    null
  }

  override protected def visitWitnessMethod(r: Option[SILResult], I: SILOperator.witnessMethod): Array[InstructionDef] = {
    null
  }

  override protected def visitApply(r: Option[SILResult], I: SILOperator.apply): Array[InstructionDef] = {
    null
  }

  override protected def visitBeginApply(r: Option[SILResult], I: SILOperator.beginApply): Array[InstructionDef] = {
    null
  }

  override protected def visitAbortApply(r: Option[SILResult], I: SILOperator.abortApply): Array[InstructionDef] = {
    null
  }

  override protected def visitEndApply(r: Option[SILResult], I: SILOperator.endApply): Array[InstructionDef] = {
    null
  }

  override protected def visitPartialApply(r: Option[SILResult], I: SILOperator.partialApply): Array[InstructionDef] = {
    null
  }

  override protected def visitBuiltin(r: Option[SILResult], I: SILOperator.builtin): Array[InstructionDef] = {
    null
  }

  override protected def visitMetatype(r: Option[SILResult], I: SILOperator.metatype): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(I.tpe))
    }
    // `new` for now. Wherever a metatype is used it should be obvious it is a metatype, and, therefore, no additional
    // special handling of metatype is required for metatype allocation.
    makeOperator(new Operator.neww(result))
  }

  override protected def visitValueMetatype(r: Option[SILResult], I: SILOperator.valueMetatype): Array[InstructionDef] = {
    // The operand here is irrelevant since the return type is already provided.
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(I.tpe))
    }
    makeOperator(new Operator.neww(result))
  }

  override protected def visitExistentialMetatype(r: Option[SILResult], I: SILOperator.existentialMetatype): Array[InstructionDef] = {
    // The operand here is irrelevant since the return type is already provided.
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(I.tpe))
    }
    makeOperator(new Operator.neww(result))
  }

  override protected def visitObjCProtocol(r: Option[SILResult], I: SILOperator.objcProtocol): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(I.tpe))
    }
    makeOperator(new Operator.neww(result))
  }

  override protected def visitRetainValue(r: Option[SILResult], I: SILOperator.retainValue): Array[InstructionDef] = {
    NOP
  }

  override protected def visitCopyValue(r: Option[SILResult], I: SILOperator.copyValue): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(new Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitReleaseValue(r: Option[SILResult], I: SILOperator.releaseValue): Array[InstructionDef] = {
    NOP
  }

  override protected def visitDestroyValue(r: Option[SILResult], I: SILOperator.destroyValue): Array[InstructionDef] = {
    NOP
  }

  override protected def visitAutoreleaseValue(r: Option[SILResult], I: SILOperator.autoreleaseValue): Array[InstructionDef] = {
    NOP
  }

  override protected def visitTuple(r: Option[SILResult], I: SILOperator.tuple): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTupleElementsToType(I.elements))
    }
    makeOperator(new Operator.neww(result))
  }

  override protected def visitTupleExtract(r: Option[SILResult], I: SILOperator.tupleExtract): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTupleTypeToType(I.operand.tpe, I.declRef, false))
    }
    makeOperator(new Operator.arrayRead(result, false, I.operand.value))
  }

  override protected def visitTupleElementAddr(r: Option[SILResult], I: SILOperator.tupleElementAddr): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTupleTypeToType(I.operand.tpe, I.declRef, true))
    }
    makeOperator(new Operator.arrayRead(result, true, I.operand.value))
  }

  override protected def visitDestructureTuple(r: Option[SILResult], I: SILOperator.destructureTuple): Array[InstructionDef] = {
    // TODO: This requires going through the operand type and breaking it
    //  into the types corresponding to each result, and then creating an
    //  array_read instruction for each result.
    null
  }

  override protected def visitStruct(r: Option[SILResult], I: SILOperator.struct): Array[InstructionDef] = {
    // TODO: We do not know the field names of a struct so we cannot write to them.
    null
  }

  override protected def visitStructExtract(r: Option[SILResult], I: SILOperator.structExtract): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), new Type()) // TODO: need type here
    }
    makeOperator(new Operator.fieldRead(result, false, I.operand.value, Utils.SILStructFieldDeclRefToString(I.declRef)))
  }

  override protected def visitStructElementAddr(r: Option[SILResult], I: SILOperator.structElementAddr): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), new Type()) // TODO: need type here (pointer)
    }
    makeOperator(new Operator.fieldRead(result, false, I.operand.value, Utils.SILStructFieldDeclRefToString(I.declRef)))
  }

  override protected def visitRefElementAddr(r: Option[SILResult], I: SILOperator.refElementAddr): Array[InstructionDef] = {
    null
  }

  override protected def visitEnum(r: Option[SILResult], I: SILOperator.enm): Array[InstructionDef] = {
    null
  }

  override protected def visitUncheckedEnumData(r: Option[SILResult], I: SILOperator.uncheckedEnumData): Array[InstructionDef] = {
    null
  }

  override protected def visitInitEnumDataAddr(r: Option[SILResult], I: SILOperator.initEnumDataAddr): Array[InstructionDef] = {
    null
  }

  override protected def visitInjectEnumAddr(r: Option[SILResult], I: SILOperator.injectEnumAddr): Array[InstructionDef] = {
    null
  }

  override protected def visitUncheckedTakeEnumDataAddr(r: Option[SILResult], I: SILOperator.uncheckedTakeEnumDataAddr): Array[InstructionDef] = {
    null
  }

  override protected def visitSelectEnum(r: Option[SILResult], I: SILOperator.selectEnum): Array[InstructionDef] = {
    null
  }

  override protected def visitSelectEnumAddr(r: Option[SILResult], I: SILOperator.selectEnumAddr): Array[InstructionDef] = {
    null
  }

  override protected def visitInitExistentialAddr(r: Option[SILResult], I: SILOperator.initExistentialAddr): Array[InstructionDef] = {
    null
  }

  override protected def visitDeinitExistentialAddr(r: Option[SILResult], I: SILOperator.deinitExistentialAddr): Array[InstructionDef] = {
    null
  }

  override protected def visitOpenExistentialAddr(r: Option[SILResult], I: SILOperator.openExistentialAddr): Array[InstructionDef] = {
    null
  }

  override protected def visitInitExistentialRef(r: Option[SILResult], I: SILOperator.initExistentialRef): Array[InstructionDef] = {
    null
  }

  override protected def visitOpenExistentialRef(r: Option[SILResult], I: SILOperator.openExistentialRef): Array[InstructionDef] = {
    null
  }

  override protected def visitInitExistentialMetatype(r: Option[SILResult], I: SILOperator.initExistentialMetatype): Array[InstructionDef] = {
    null
  }

  override protected def visitOpenExistentialMetatype(r: Option[SILResult], I: SILOperator.openExistentialMetatype): Array[InstructionDef] = {
    null
  }

  override protected def visitAllocExistentialBox(r: Option[SILResult], I: SILOperator.allocExistentialBox): Array[InstructionDef] = {
    null
  }

  override protected def visitProjectExistentialBox(r: Option[SILResult], I: SILOperator.projectExistentialBox): Array[InstructionDef] = {
    null
  }

  override protected def visitOpenExistentialBox(r: Option[SILResult], I: SILOperator.openExistentialBox): Array[InstructionDef] = {
    null
  }

  override protected def visitDeallocExistentialBox(r: Option[SILResult], I: SILOperator.deallocExistentialBox): Array[InstructionDef] = {
    null
  }

  override protected def visitProjectBlockStorage(r: Option[SILResult], I: SILOperator.projectBlockStorage): Array[InstructionDef] = {
    null
  }

  override protected def visitUpcast(r: Option[SILResult], I: SILOperator.upcast): Array[InstructionDef] = {
    null
  }

  override protected def visitAddressToPointer(r: Option[SILResult], I: SILOperator.addressToPointer): Array[InstructionDef] = {
    null
  }

  override protected def visitPointerToAddress(r: Option[SILResult], I: SILOperator.pointerToAddress): Array[InstructionDef] = {
    null
  }

  override protected def visitUncheckedRefCast(r: Option[SILResult], I: SILOperator.uncheckedRefCast): Array[InstructionDef] = {
    null
  }

  override protected def visitUncheckedAddrCast(r: Option[SILResult], I: SILOperator.uncheckedAddrCast): Array[InstructionDef] = {
    null
  }

  override protected def visitUncheckedTrivialBitCast(r: Option[SILResult], I: SILOperator.uncheckedTrivialBitCast): Array[InstructionDef] = {
    null
  }

  override protected def visitRefToUnowned(r: Option[SILResult], I: SILOperator.refToUnowned): Array[InstructionDef] = {
    null
  }

  override protected def visitRefToUnmanaged(r: Option[SILResult], I: SILOperator.refToUnmanaged): Array[InstructionDef] = {
    null
  }

  override protected def visitUnmanagedToRef(r: Option[SILResult], I: SILOperator.unmanagedToRef): Array[InstructionDef] = {
    null
  }

  override protected def visitConvertFunction(r: Option[SILResult], I: SILOperator.convertFunction): Array[InstructionDef] = {
    null
  }

  override protected def visitConvertEscapeToNoEscape(r: Option[SILResult], I: SILOperator.convertEscapeToNoescape): Array[InstructionDef] = {
    null
  }

  override protected def visitThinToThickFunction(r: Option[SILResult], I: SILOperator.thinToThickFunction): Array[InstructionDef] = {
    null
  }

  override protected def visitThickToObjCMetatype(r: Option[SILResult], I: SILOperator.thickToObjcMetatype): Array[InstructionDef] = {
    null
  }

  override protected def visitObjCToThickMetatype(r: Option[SILResult], I: SILOperator.objcToThickMetatype): Array[InstructionDef] = {
    null
  }

  override protected def visitObjCMetatypeToObject(r: Option[SILResult], I: SILOperator.objcMetatypeToObject): Array[InstructionDef] = {
    null
  }

  override protected def visitObjCExistentialMetatypeToObject(r: Option[SILResult], I: SILOperator.objcExistentialMetatypeToObject): Array[InstructionDef] = {
    null
  }

  override protected def visitUnconditionalCheckedCast(r: Option[SILResult], I: SILOperator.unconditionalCheckedCast): Array[InstructionDef] = {
    null
  }

  override protected def visitUnconditionalCheckedCastAddr(r: Option[SILResult], I: SILOperator.unconditionalCheckedCastAddr): Array[InstructionDef] = {
    null
  }

  override protected def visitCondFail(r: Option[SILResult], I: SILOperator.condFail): Array[InstructionDef] = {
    null
  }

  override protected def visitUnreachable(): Array[InstructionDef] = {
    null
  }

  override protected def visitReturn(I: SILTerminator.ret): Array[InstructionDef] = {
    null
  }

  override protected def visitThrow(I: SILTerminator.thro): Array[InstructionDef] = {
    null
  }

  override protected def visitYield(I: SILTerminator.yld): Array[InstructionDef] = {
    null
  }

  override protected def visitUnwind(): Array[InstructionDef] = {
    null
  }

  override protected def visitBr(I: SILTerminator.br): Array[InstructionDef] = {
    null
  }

  override protected def visitCondBr(I: SILTerminator.condBr): Array[InstructionDef] = {
    null
  }

  override protected def visitSwitchEnum(I: SILTerminator.switchEnum): Array[InstructionDef] = {
    null
  }

  override protected def visitSwitchEnumAddr(I: SILTerminator.switchEnumAddr): Array[InstructionDef] = {
    null
  }

  override protected def visitDynamicMethodBr(I: SILTerminator.dynamicMethodBr): Array[InstructionDef] = {
    null
  }

  override protected def visitCheckedCastBr(I: SILTerminator.checkedCastBr): Array[InstructionDef] = {
    null
  }

  override protected def visitCheckedCastAddrBr(I: SILTerminator.checkedCastAddrBr): Array[InstructionDef] = {
    null
  }

  override protected def visitTryApply(I: SILTerminator.tryApply): Array[InstructionDef] = {
    null
  }
}
