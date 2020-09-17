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

import ca.ualberta.maple.swan.parser.{SILDeclSubRef, SILOperator, SILPrinter, SILResult, SILTerminator, SILType}

import scala.collection.mutable.ArrayBuffer

// IMPORTANT: For now, boxes are treated as pointers.
class SILToRawSWANIR extends ISILToRawSWANIR {

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

  override protected def visitAllocStack(r: Option[SILResult], I: SILOperator.allocStack, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToPointerType(I.tpe)) // IMPORTANT: $T to $*T
    makeOperator(Operator.neww(result))
  }

  override protected def visitAllocRef(r: Option[SILResult], I: SILOperator.allocRef, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(Operator.neww(result))
  }

  override protected def visitAllocRefDynamic(r: Option[SILResult], I: SILOperator.allocRefDynamic, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(Operator.neww(result))
  }

  override protected def visitAllocBox(r: Option[SILResult], I: SILOperator.allocBox, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToPointerType(I.tpe))
    makeOperator(Operator.neww(result))
  }

  override protected def visitAllocValueBuffer(r: Option[SILResult], I: SILOperator.allocValueBuffer, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToPointerType(I.tpe))
    makeOperator(Operator.neww(result))
  }

  override protected def visitAllocGlobal(r: Option[SILResult], I: SILOperator.allocGlobal, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitDeallocStack(r: Option[SILResult], I: SILOperator.deallocStack, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitDeallocBox(r: Option[SILResult], I: SILOperator.deallocBox, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitProjectBox(r: Option[SILResult], I: SILOperator.projectBox, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitDeallocRef(r: Option[SILResult], I: SILOperator.deallocRef, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitDebugValue(r: Option[SILResult], I: SILOperator.debugValue, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitDebugValueAddr(r: Option[SILResult], I: SILOperator.debugValueAddr, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitLoad(r: Option[SILResult], I: SILOperator.load, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILPointerTypeToType(I.operand.tpe))
    makeOperator(Operator.pointerRead(result, I.operand.value))
  }

  override protected def visitStore(r: Option[SILResult], I: SILOperator.store, ctx: Context): Array[InstructionDef] = {
    makeOperator(Operator.pointerWrite(I.from, I.to.value))
  }

  override protected def visitLoadBorrow(r: Option[SILResult], I: SILOperator.loadBorrow, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILPointerTypeToType(I.operand.tpe))
    makeOperator(Operator.pointerRead(result, I.operand.value))
  }

  override protected def visitBeginBorrow(r: Option[SILResult], I: SILOperator.beginBorrow, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitEndBorrow(r: Option[SILResult], I: SILOperator.endBorrow, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitCopyAddr(r: Option[SILResult], I: SILOperator.copyAddr, ctx: Context): Array[InstructionDef] = {
    val pointerReadResult = new Symbol(generateSymbol(I.value), Utils.SILPointerTypeToType(I.operand.tpe))
    makeOperator(
      Operator.pointerRead(pointerReadResult, I.value),
      Operator.pointerWrite(pointerReadResult.name, I.operand.value))
  }

  override protected def visitDestroyAddr(r: Option[SILResult], I: SILOperator.destroyAddr, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitIndexAddr(r: Option[SILResult], I: SILOperator.indexAddr, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.addr.tpe))
    makeOperator(Operator.arrayRead(result, alias = true, I.addr.value))
  }

  override protected def visitBeginAccess(r: Option[SILResult], I: SILOperator.beginAccess, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitEndAccess(r: Option[SILResult], I: SILOperator.endAccess, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitStrongRetain(r: Option[SILResult], I: SILOperator.strongRetain, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitStrongRelease(r: Option[SILResult], I: SILOperator.strongRelease, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitLoadWeak(r: Option[SILResult], I: SILOperator.loadWeak, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILPointerTypeToType(I.operand.tpe))
    makeOperator(Operator.pointerRead(result, I.operand.value))
  }

  override protected def visitStoreWeak(r: Option[SILResult], I: SILOperator.storeWeak, ctx: Context): Array[InstructionDef] = {
    makeOperator(Operator.pointerWrite(I.from, I.to.value))
  }

  override protected def visitLoadUnowned(r: Option[SILResult], I: SILOperator.loadUnowned, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILPointerTypeToType(I.operand.tpe))
    makeOperator(Operator.pointerRead(result, I.operand.value))
  }

  override protected def visitStoreUnowned(r: Option[SILResult], I: SILOperator.storeUnowned, ctx: Context): Array[InstructionDef] = {
    makeOperator(Operator.pointerWrite(I.from, I.to.value))
  }

  override protected def visitMarkDependence(r: Option[SILResult], I: SILOperator.markDependence, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitIsEscapingClosure(r: Option[SILResult], I: SILOperator.isEscapingClosure, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(SILType.namedType("Builtin.Int1")))
    makeOperator(Operator.unaryOp(result, UnaryOperation.arbitrary, I.operand.value))
  }

  override protected def visitCopyBlock(r: Option[SILResult], I: SILOperator.copyBlock, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.operand.tpe))
    makeOperator(Operator.assign(result, I.operand.value))
  }

  override protected def visitCopyBlockWithoutEscaping(r: Option[SILResult], I: SILOperator.copyBlockWithoutEscaping, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.operand1.tpe))
    makeOperator(Operator.assign(result, I.operand1.value))
  }

  override protected def visitFunctionRef(r: Option[SILResult], I: SILOperator.functionRef, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(Operator.functionRef(result, Array(I.name.demangled)))
  }

  override protected def visitDynamicFunctionRef(r: Option[SILResult], I: SILOperator.dynamicFunctionRef, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(Operator.functionRef(result, Array(I.name.demangled)))
  }

  override protected def visitPrevDynamicFunctionRef(r: Option[SILResult], I: SILOperator.prevDynamicFunctionRef, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(Operator.functionRef(result, Array(I.name.demangled)))
  }

  override protected def visitGlobalAddr(r: Option[SILResult], I: SILOperator.globalAddr, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(Operator.assignGlobal(result, I.name.demangled))
  }

  override protected def visitIntegerLiteral(r: Option[SILResult], I: SILOperator.integerLiteral, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(Operator.literal(result, Literal.int(I.value)))
  }

  override protected def visitFloatLiteral(r: Option[SILResult], I: SILOperator.floatLiteral, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(Operator.literal(result, Literal.float(Utils.SILFloatStringToFloat(I.value))))
  }

  override protected def visitStringLiteral(r: Option[SILResult], I: SILOperator.stringLiteral, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(new SILType.namedType("$Builtin.RawPointer")))
    makeOperator(Operator.literal(result, Literal.string(I.value)))
  }

  override protected def visitClassMethod(r: Option[SILResult], I: SILOperator.classMethod, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    assert(I.declRef.name.length == 2) // #T.method
    val typeName = I.declRef.name(0)
    val method = I.declRef.name(1)
    val functions = ArrayBuffer[String]()
    ctx.silModule.vTables.foreach(vTable => {
      if (vTable.name == typeName) {
        vTable.entries.foreach(entry => {
          if (entry.declRef.name.length > 1 && entry.declRef.name(1) == method) {
            functions.append(entry.functionName.demangled)
          }
        })
      }
    })
    if (functions.nonEmpty) {
      makeOperator(Operator.functionRef(result, functions.toArray))
    } else {
      NOP // No v-table contained information about the method.
    }
  }

  override protected def visitObjCMethod(r: Option[SILResult], I: SILOperator.objcMethod, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    val name = new SILPrinter().print(I.declRef)
    makeOperator(Operator.builtinRef(result, name))
  }

  override protected def visitObjCSuperMethod(r: Option[SILResult], I: SILOperator.objcSuperMethod, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    assert(I.declRef.name.length >= 2)
    makeOperator(Operator.builtinRef(result, I.declRef.name.slice(0,2).mkString(".")))
  }

  override protected def visitWitnessMethod(r: Option[SILResult], I: SILOperator.witnessMethod, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitApply(r: Option[SILResult], I: SILOperator.apply, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitBeginApply(r: Option[SILResult], I: SILOperator.beginApply, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitAbortApply(r: Option[SILResult], I: SILOperator.abortApply, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitEndApply(r: Option[SILResult], I: SILOperator.endApply, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitPartialApply(r: Option[SILResult], I: SILOperator.partialApply, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitBuiltin(r: Option[SILResult], I: SILOperator.builtin, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitMetatype(r: Option[SILResult], I: SILOperator.metatype, ctx: Context): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(I.tpe))
    }
    // `new` for now. Wherever a metatype is used it should be obvious it is a metatype, and, therefore, no additional
    // special handling of metatype is required for metatype allocation.
    makeOperator(new Operator.neww(result))
  }

  override protected def visitValueMetatype(r: Option[SILResult], I: SILOperator.valueMetatype, ctx: Context): Array[InstructionDef] = {
    // The operand here is irrelevant since the return type is already provided.
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(I.tpe))
    }
    makeOperator(new Operator.neww(result))
  }

  override protected def visitExistentialMetatype(r: Option[SILResult], I: SILOperator.existentialMetatype, ctx: Context): Array[InstructionDef] = {
    // The operand here is irrelevant since the return type is already provided.
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(I.tpe))
    }
    makeOperator(new Operator.neww(result))
  }

  override protected def visitObjCProtocol(r: Option[SILResult], I: SILOperator.objcProtocol, ctx: Context): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTypeToType(I.tpe))
    }
    makeOperator(new Operator.neww(result))
  }

  override protected def visitRetainValue(r: Option[SILResult], I: SILOperator.retainValue, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitCopyValue(r: Option[SILResult], I: SILOperator.copyValue, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(new Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitReleaseValue(r: Option[SILResult], I: SILOperator.releaseValue, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitDestroyValue(r: Option[SILResult], I: SILOperator.destroyValue, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitAutoreleaseValue(r: Option[SILResult], I: SILOperator.autoreleaseValue, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitTuple(r: Option[SILResult], I: SILOperator.tuple, ctx: Context): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTupleElementsToType(I.elements))
    }
    makeOperator(new Operator.neww(result))
  }

  override protected def visitTupleExtract(r: Option[SILResult], I: SILOperator.tupleExtract, ctx: Context): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTupleTypeToType(I.operand.tpe, I.declRef, false))
    }
    makeOperator(new Operator.arrayRead(result, false, I.operand.value))
  }

  override protected def visitTupleElementAddr(r: Option[SILResult], I: SILOperator.tupleElementAddr, ctx: Context): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), Utils.SILTupleTypeToType(I.operand.tpe, I.declRef, true))
    }
    makeOperator(new Operator.arrayRead(result, true, I.operand.value))
  }

  override protected def visitDestructureTuple(r: Option[SILResult], I: SILOperator.destructureTuple, ctx: Context): Array[InstructionDef] = {
    // TODO: This requires going through the operand type and breaking it
    //  into the types corresponding to each result, and then creating an
    //  array_read instruction for each result.
    null
  }

  override protected def visitStruct(r: Option[SILResult], I: SILOperator.struct, ctx: Context): Array[InstructionDef] = {
    // TODO: We do not know the field names of a struct so we cannot write to them.
    null
  }

  override protected def visitStructExtract(r: Option[SILResult], I: SILOperator.structExtract, ctx: Context): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), new Type()) // TODO: need type here
    }
    makeOperator(new Operator.fieldRead(result, false, I.operand.value, Utils.SILStructFieldDeclRefToString(I.declRef)))
  }

  override protected def visitStructElementAddr(r: Option[SILResult], I: SILOperator.structElementAddr, ctx: Context): Array[InstructionDef] = {
    val result: Symbol = {
      assertSILResult(r, 1)
      new Symbol(r.get.valueNames(0), new Type()) // TODO: need type here (pointer)
    }
    makeOperator(new Operator.fieldRead(result, false, I.operand.value, Utils.SILStructFieldDeclRefToString(I.declRef)))
  }

  override protected def visitObject(r: Option[SILResult], I: SILOperator.objct, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitRefElementAddr(r: Option[SILResult], I: SILOperator.refElementAddr, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitEnum(r: Option[SILResult], I: SILOperator.enm, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitUncheckedEnumData(r: Option[SILResult], I: SILOperator.uncheckedEnumData, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitInitEnumDataAddr(r: Option[SILResult], I: SILOperator.initEnumDataAddr, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitInjectEnumAddr(r: Option[SILResult], I: SILOperator.injectEnumAddr, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitUncheckedTakeEnumDataAddr(r: Option[SILResult], I: SILOperator.uncheckedTakeEnumDataAddr, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitSelectEnum(r: Option[SILResult], I: SILOperator.selectEnum, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitSelectEnumAddr(r: Option[SILResult], I: SILOperator.selectEnumAddr, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitInitExistentialAddr(r: Option[SILResult], I: SILOperator.initExistentialAddr, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitDeinitExistentialAddr(r: Option[SILResult], I: SILOperator.deinitExistentialAddr, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitOpenExistentialAddr(r: Option[SILResult], I: SILOperator.openExistentialAddr, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitInitExistentialRef(r: Option[SILResult], I: SILOperator.initExistentialRef, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitOpenExistentialRef(r: Option[SILResult], I: SILOperator.openExistentialRef, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitInitExistentialMetatype(r: Option[SILResult], I: SILOperator.initExistentialMetatype, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitOpenExistentialMetatype(r: Option[SILResult], I: SILOperator.openExistentialMetatype, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitAllocExistentialBox(r: Option[SILResult], I: SILOperator.allocExistentialBox, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitProjectExistentialBox(r: Option[SILResult], I: SILOperator.projectExistentialBox, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitOpenExistentialBox(r: Option[SILResult], I: SILOperator.openExistentialBox, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitDeallocExistentialBox(r: Option[SILResult], I: SILOperator.deallocExistentialBox, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitProjectBlockStorage(r: Option[SILResult], I: SILOperator.projectBlockStorage, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitUpcast(r: Option[SILResult], I: SILOperator.upcast, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitAddressToPointer(r: Option[SILResult], I: SILOperator.addressToPointer, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitPointerToAddress(r: Option[SILResult], I: SILOperator.pointerToAddress, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitUncheckedRefCast(r: Option[SILResult], I: SILOperator.uncheckedRefCast, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitUncheckedAddrCast(r: Option[SILResult], I: SILOperator.uncheckedAddrCast, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitUncheckedTrivialBitCast(r: Option[SILResult], I: SILOperator.uncheckedTrivialBitCast, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitRefToUnowned(r: Option[SILResult], I: SILOperator.refToUnowned, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitRefToUnmanaged(r: Option[SILResult], I: SILOperator.refToUnmanaged, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitUnmanagedToRef(r: Option[SILResult], I: SILOperator.unmanagedToRef, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitConvertFunction(r: Option[SILResult], I: SILOperator.convertFunction, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitConvertEscapeToNoEscape(r: Option[SILResult], I: SILOperator.convertEscapeToNoescape, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitValueToBridgeObject(r: Option[SILResult], I: SILOperator.valueToBridgeObject, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitThinToThickFunction(r: Option[SILResult], I: SILOperator.thinToThickFunction, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitThickToObjCMetatype(r: Option[SILResult], I: SILOperator.thickToObjcMetatype, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitObjCToThickMetatype(r: Option[SILResult], I: SILOperator.objcToThickMetatype, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitObjCMetatypeToObject(r: Option[SILResult], I: SILOperator.objcMetatypeToObject, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitObjCExistentialMetatypeToObject(r: Option[SILResult], I: SILOperator.objcExistentialMetatypeToObject, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitUnconditionalCheckedCast(r: Option[SILResult], I: SILOperator.unconditionalCheckedCast, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitUnconditionalCheckedCastAddr(r: Option[SILResult], I: SILOperator.unconditionalCheckedCastAddr, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitCondFail(r: Option[SILResult], I: SILOperator.condFail, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitUnreachable(): Array[InstructionDef] = {
    null
  }

  override protected def visitReturn(I: SILTerminator.ret, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitThrow(I: SILTerminator.thro, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitYield(I: SILTerminator.yld, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitUnwind(): Array[InstructionDef] = {
    null
  }

  override protected def visitBr(I: SILTerminator.br, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitCondBr(I: SILTerminator.condBr, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitSwitchValue(I: SILTerminator.switchValue, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitSwitchEnum(I: SILTerminator.switchEnum, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitSwitchEnumAddr(I: SILTerminator.switchEnumAddr, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitDynamicMethodBr(I: SILTerminator.dynamicMethodBr, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitCheckedCastBr(I: SILTerminator.checkedCastBr, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitCheckedCastAddrBr(I: SILTerminator.checkedCastAddrBr, ctx: Context): Array[InstructionDef] = {
    null
  }

  override protected def visitTryApply(I: SILTerminator.tryApply, ctx: Context): Array[InstructionDef] = {
    null
  }

  def getSingleResult(r: Option[SILResult], tpe: Type): Symbol = {
    assertSILResult(r, 1)
    new Symbol(r.get.valueNames(0), tpe)
  }
}
