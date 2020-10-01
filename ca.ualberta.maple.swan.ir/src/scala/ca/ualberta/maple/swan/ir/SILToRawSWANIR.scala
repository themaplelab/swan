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

import ca.ualberta.maple.swan.parser.{Init, SILOperator, SILPrinter, SILResult, SILSwitchEnumCase, SILSwitchValueCase, SILTerminator, SILTupleElements, SILType, SILWitnessEntry}

import scala.collection.mutable.ArrayBuffer

// IMPORTANT: For now, boxes are treated as pointers.
//
// Type information either at apply or reference time may be useful for
// generating stubs later. However, it gets complicated for dynamic dispatch
// so leave it for now.
class SILToRawSWANIR extends ISILToRawSWANIR {

  private def makeOperator(ctx: Context, operator: Operator*): Array[InstructionDef] = {
    val arr: Array[InstructionDef] = new Array[InstructionDef](operator.length)
    operator.zipWithIndex.foreach( (op: (Operator, Int))  => {
      arr(op._2) = InstructionDef.operator(new OperatorDef(op._1, ctx.pos))
    })
    arr
  }

  private def makeTerminator(ctx: Context, terminator: Terminator): Array[InstructionDef] = {
    Array[InstructionDef](InstructionDef.terminator(new TerminatorDef(terminator, ctx.pos)))
  }

  private def assertSILResult(r: Option[SILResult], i: Int): Unit = {
    assert(r.nonEmpty && r.get.valueNames.length == i)
  }

  override protected def visitAllocStack(r: Option[SILResult], I: SILOperator.allocStack, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToPointerType(I.tpe)) // IMPORTANT: $T to $*T
    makeOperator(ctx, Operator.neww(result))
  }

  override protected def visitAllocRef(r: Option[SILResult], I: SILOperator.allocRef, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(ctx, Operator.neww(result))
  }

  override protected def visitAllocRefDynamic(r: Option[SILResult], I: SILOperator.allocRefDynamic, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(ctx, Operator.neww(result))
  }

  override protected def visitAllocBox(r: Option[SILResult], I: SILOperator.allocBox, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToPointerType(I.tpe))
    makeOperator(ctx, Operator.neww(result))
  }

  override protected def visitAllocValueBuffer(r: Option[SILResult], I: SILOperator.allocValueBuffer, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToPointerType(I.tpe))
    makeOperator(ctx, Operator.neww(result))
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
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
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
    makeOperator(ctx, Operator.pointerRead(result, I.operand.value))
  }

  override protected def visitStore(r: Option[SILResult], I: SILOperator.store, ctx: Context): Array[InstructionDef] = {
    makeOperator(ctx, Operator.pointerWrite(I.from, I.to.value))
  }

  override protected def visitLoadBorrow(r: Option[SILResult], I: SILOperator.loadBorrow, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILPointerTypeToType(I.operand.tpe))
    makeOperator(ctx, Operator.pointerRead(result, I.operand.value))
  }

  override protected def visitBeginBorrow(r: Option[SILResult], I: SILOperator.beginBorrow, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitEndBorrow(r: Option[SILResult], I: SILOperator.endBorrow, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitCopyAddr(r: Option[SILResult], I: SILOperator.copyAddr, ctx: Context): Array[InstructionDef] = {
    val pointerReadResult = new Symbol(generateSymbolName(I.value), Utils.SILPointerTypeToType(I.operand.tpe))
    makeOperator(
      ctx,
      Operator.pointerRead(pointerReadResult, I.value),
      Operator.pointerWrite(pointerReadResult.name, I.operand.value))
  }

  override protected def visitDestroyAddr(r: Option[SILResult], I: SILOperator.destroyAddr, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitIndexAddr(r: Option[SILResult], I: SILOperator.indexAddr, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.addr.tpe))
    makeOperator(ctx, Operator.arrayRead(result, alias = true, I.addr.value))
  }

  override protected def visitBeginAccess(r: Option[SILResult], I: SILOperator.beginAccess, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
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
    makeOperator(ctx, Operator.pointerRead(result, I.operand.value))
  }

  override protected def visitStoreWeak(r: Option[SILResult], I: SILOperator.storeWeak, ctx: Context): Array[InstructionDef] = {
    makeOperator(ctx, Operator.pointerWrite(I.from, I.to.value))
  }

  override protected def visitLoadUnowned(r: Option[SILResult], I: SILOperator.loadUnowned, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILPointerTypeToType(I.operand.tpe))
    makeOperator(ctx, Operator.pointerRead(result, I.operand.value))
  }

  override protected def visitStoreUnowned(r: Option[SILResult], I: SILOperator.storeUnowned, ctx: Context): Array[InstructionDef] = {
    makeOperator(ctx, Operator.pointerWrite(I.from, I.to.value))
  }

  override protected def visitMarkDependence(r: Option[SILResult], I: SILOperator.markDependence, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitIsEscapingClosure(r: Option[SILResult], I: SILOperator.isEscapingClosure, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(SILType.namedType("Builtin.Int1")))
    makeOperator(ctx, Operator.unaryOp(result, UnaryOperation.arbitrary, I.operand.value))
  }

  override protected def visitCopyBlock(r: Option[SILResult], I: SILOperator.copyBlock, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.operand.tpe))
    makeOperator(ctx, Operator.assign(result, I.operand.value))
  }

  override protected def visitCopyBlockWithoutEscaping(r: Option[SILResult], I: SILOperator.copyBlockWithoutEscaping, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.operand1.tpe))
    makeOperator(ctx, Operator.assign(result, I.operand1.value))
  }

  override protected def visitFunctionRef(r: Option[SILResult], I: SILOperator.functionRef, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(ctx, Operator.functionRef(result, Array(I.name.demangled)))
  }

  override protected def visitDynamicFunctionRef(r: Option[SILResult], I: SILOperator.dynamicFunctionRef, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(ctx, Operator.functionRef(result, Array(I.name.demangled)))
  }

  override protected def visitPrevDynamicFunctionRef(r: Option[SILResult], I: SILOperator.prevDynamicFunctionRef, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(ctx, Operator.functionRef(result, Array(I.name.demangled)))
  }

  override protected def visitGlobalAddr(r: Option[SILResult], I: SILOperator.globalAddr, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(ctx, Operator.assignGlobal(result, I.name.demangled))
  }

  override protected def visitIntegerLiteral(r: Option[SILResult], I: SILOperator.integerLiteral, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(ctx, Operator.literal(result, Literal.int(I.value)))
  }

  override protected def visitFloatLiteral(r: Option[SILResult], I: SILOperator.floatLiteral, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(ctx, Operator.literal(result, Literal.float(Utils.SILFloatStringToFloat(I.value))))
  }

  override protected def visitStringLiteral(r: Option[SILResult], I: SILOperator.stringLiteral, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(new SILType.namedType("$Builtin.RawPointer")))
    makeOperator(ctx, Operator.literal(result, Literal.string(I.value)))
  }

  override protected def visitClassMethod(r: Option[SILResult], I: SILOperator.classMethod, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    assert(I.declRef.name.length >= 2) // #T.method
    val typeName = I.declRef.name(0)
    val method = I.declRef.name(1)
    val functions = ArrayBuffer[String]()
    ctx.silModule.vTables.foreach(vTable => {
      if (vTable.name == typeName) {
        vTable.entries.foreach(entry => {
          if (entry.declRef.name.length >= 2 && entry.declRef.name(1) == method) {
            functions.append(entry.functionName.demangled)
          }
        })
      }
    })
    if (functions.nonEmpty) {
      makeOperator(ctx, Operator.functionRef(result, functions.toArray))
    } else {
      NOP // No v-table contained information about the method.
    }
  }

  override protected def visitObjCMethod(r: Option[SILResult], I: SILOperator.objcMethod, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    val name = new SILPrinter().print(I.declRef)
    makeOperator(ctx, Operator.builtinRef(result, decl = true, name))
  }

  override protected def visitObjCSuperMethod(r: Option[SILResult], I: SILOperator.objcSuperMethod, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    assert(I.declRef.name.length >= 2)
    makeOperator(ctx, Operator.builtinRef(result, decl = true, I.declRef.name.slice(0,2).mkString(".")))
  }

  override protected def visitWitnessMethod(r: Option[SILResult], I: SILOperator.witnessMethod, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    assert(I.declRef.name.length >= 2)
    val typeName = I.declRef.name(0)
    val method = I.declRef.name(1)
    val functions = ArrayBuffer[String]()
    ctx.silModule.witnessTables.foreach(witnessTable => {
      if (witnessTable.normalProtocolConformance.protocol == typeName) {
        witnessTable.entries.foreach {
          case e: SILWitnessEntry.method =>
            if (e.declRef.name.length >= 2 && e.declRef.name(1) == method) {
              functions.append(e.functionName.demangled)
            }
          case _ =>
        }
      }
    })
    if (functions.nonEmpty) {
      makeOperator(ctx, Operator.functionRef(result, functions.toArray))
    } else {
      NOP // No v-table contained information about the method.
    }
  }

  override protected def visitApply(r: Option[SILResult], I: SILOperator.apply, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(ctx, Operator.apply(result, I.value, I.arguments))
  }

  override protected def visitBeginApply(r: Option[SILResult], I: SILOperator.beginApply, ctx: Context): Array[InstructionDef] = {
    null // TODO
  }

  override protected def visitAbortApply(r: Option[SILResult], I: SILOperator.abortApply, ctx: Context): Array[InstructionDef] = {
    makeOperator(ctx, Operator.abortCoroutine(I.value))
  }

  override protected def visitEndApply(r: Option[SILResult], I: SILOperator.endApply, ctx: Context): Array[InstructionDef] = {
    makeOperator(ctx, Operator.endCoroutine(I.value))
  }

  override protected def visitPartialApply(r: Option[SILResult], I: SILOperator.partialApply, ctx: Context): Array[InstructionDef] = {
    null // TODO: Need to first understand partial apply dataflow semantics.
  }

  override protected def visitBuiltin(r: Option[SILResult], I: SILOperator.builtin, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    // Use Any type because we don't know the type of the function ref value.
    val functionRef = new Symbol(generateSymbolName(r.get.valueNames(0)), new Type())
    val arguments = ArrayBuffer[String]()
    I.operands.foreach(op => {
      arguments.append(op.value)
    })
    makeOperator(
      ctx,
      Operator.builtinRef(functionRef, decl = false, I.name),
      Operator.apply(result, functionRef.name, arguments.toArray)
    )
  }

  override protected def visitMetatype(r: Option[SILResult], I: SILOperator.metatype, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(ctx, Operator.neww(result))
  }

  override protected def visitValueMetatype(r: Option[SILResult], I: SILOperator.valueMetatype, ctx: Context): Array[InstructionDef] = {
    // The operand here is irrelevant since the return type is already provided.
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(ctx, Operator.neww(result))
  }

  override protected def visitExistentialMetatype(r: Option[SILResult], I: SILOperator.existentialMetatype, ctx: Context): Array[InstructionDef] = {
    // The operand here is irrelevant since the return type is already provided.
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(ctx, Operator.neww(result))
  }

  override protected def visitObjCProtocol(r: Option[SILResult], I: SILOperator.objcProtocol, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(ctx, Operator.neww(result))
  }

  override protected def visitRetainValue(r: Option[SILResult], I: SILOperator.retainValue, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitCopyValue(r: Option[SILResult], I: SILOperator.copyValue, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
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
    val result = getSingleResult(r, Utils.SILTupleElementsToType(I.elements))
    val operators = ArrayBuffer[InstructionDef]()
    operators.append(makeOperator(ctx, Operator.neww(result))(0))
    I.elements match {
      case SILTupleElements.labeled(_, values) => {
        values.foreach(value => {
          operators.append(makeOperator(ctx, Operator.arrayWrite(value, result.name))(0))
        })
      }
      case SILTupleElements.unlabeled(operands) => {
        operands.foreach(operand => {
          operators.append(makeOperator(ctx, Operator.arrayWrite(operand.value, result.name))(0))
        })
      }
    }
    operators.toArray
  }

  override protected def visitTupleExtract(r: Option[SILResult], I: SILOperator.tupleExtract, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTupleTypeToType(I.operand.tpe, I.declRef, pointer = false))
    makeOperator(ctx, Operator.arrayRead(result, alias = false, I.operand.value))
  }

  override protected def visitTupleElementAddr(r: Option[SILResult], I: SILOperator.tupleElementAddr, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTupleTypeToType(I.operand.tpe, I.declRef, pointer = true))
    makeOperator(ctx, Operator.arrayRead(result, alias = true, I.operand.value))
  }

  override protected def visitDestructureTuple(r: Option[SILResult], I: SILOperator.destructureTuple, ctx: Context): Array[InstructionDef] = {
    assert(I.operand.tpe.isInstanceOf[SILType.tupleType])
    val tupleType = I.operand.tpe.asInstanceOf[SILType.tupleType]
    assertSILResult(r, tupleType.parameters.length)
    val operators = ArrayBuffer[InstructionDef]()
    tupleType.parameters.zipWithIndex.foreach(param => {
      operators.append(makeOperator(ctx, Operator.arrayRead(
        new Symbol(r.get.valueNames(param._2), Utils.SILTypeToType(param._1)),
        alias = false, I.operand.value))(0))
    })
    operators.toArray
  }

  override protected def visitStruct(r: Option[SILResult], I: SILOperator.struct, ctx: Context): Array[InstructionDef] = {
    val init: Option[Init] = {
      var ret: Option[Init] = None
      ctx.silModule.inits.foreach(init => {
        if (init.name == new SILPrinter().naked(I.tpe)) {
          ret = Some(init)
        }
      })
      ret
    }
    if (init.isEmpty) { return NOP } // Ideally shouldn't happen
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    val operators = ArrayBuffer[InstructionDef]()
    operators.append(makeOperator(ctx, Operator.neww(result))(0))
    assert(init.get.args.length >= I.operands.length)
    init.get.args.zipWithIndex.foreach(init => {
      operators.append(makeOperator(ctx,
        Operator.fieldWrite(I.operands(init._2).value, result.name, init._1))(0))
    })
    operators.toArray
  }

  override protected def visitStructExtract(r: Option[SILResult], I: SILOperator.structExtract, ctx: Context): Array[InstructionDef] = {
    // Type is statically unknown, at least for now
    val result = getSingleResult(r, new Type("Any"))
    makeOperator(ctx, Operator.fieldRead(result, alias = false, I.operand.value, Utils.SILStructFieldDeclRefToString(I.declRef)))
  }

  override protected def visitStructElementAddr(r: Option[SILResult], I: SILOperator.structElementAddr, ctx: Context): Array[InstructionDef] = {
    // Type is statically unknown, at least for now
    val result = getSingleResult(r, new Type("*Any"))
    makeOperator(ctx, Operator.fieldRead(result, alias = true, I.operand.value, Utils.SILStructFieldDeclRefToString(I.declRef)))
  }

  override protected def visitObject(r: Option[SILResult], I: SILOperator.objct, ctx: Context): Array[InstructionDef] = {
    // Only new instruction because field names are not known.
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    makeOperator(ctx, Operator.neww(result))
  }

  override protected def visitRefElementAddr(r: Option[SILResult], I: SILOperator.refElementAddr, ctx: Context): Array[InstructionDef] = {
    // Type is statically unknown, at least for now
    val result = getSingleResult(r, new Type("*Any"))
    makeOperator(ctx, Operator.fieldRead(result, alias = false, I.operand.value, Utils.SILStructFieldDeclRefToString(I.declRef)))
  }

  override protected def visitEnum(r: Option[SILResult], I: SILOperator.enm, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    val typeString = new Symbol(generateSymbolName(result.name), new Type("Builtin.RawPointer"))
    var instructions = makeOperator(ctx,
      Operator.neww(result),
      Operator.literal(typeString, Literal.string(Utils.print(I.declRef))),
      Operator.fieldWrite(typeString.name, result.name, "type"))
    if (I.operand.nonEmpty) {
      instructions :+= makeOperator(ctx, Operator.fieldWrite(I.operand.get.value, result.name, "data"))(0)
    }
    instructions
  }

  override protected def visitUncheckedEnumData(r: Option[SILResult], I: SILOperator.uncheckedEnumData, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, new Type("Any"))
    // No alias for now, but this might change.
    makeOperator(ctx, Operator.fieldRead(result, alias = false, I.operand.value, "data"))
  }

  override protected def visitInitEnumDataAddr(r: Option[SILResult], I: SILOperator.initEnumDataAddr, ctx: Context): Array[InstructionDef] = {
    // Type is statically unknown, at least for now.
    val result = getSingleResult(r, new Type("*Any"))
    makeOperator(ctx, Operator.fieldRead(result, alias = true, I.operand.value, "data"))
  }

  override protected def visitInjectEnumAddr(r: Option[SILResult], I: SILOperator.injectEnumAddr, ctx: Context): Array[InstructionDef] = {
    val typeString = new Symbol(generateSymbolName(I.operand.value),  new Type("Builtin.RawPointer"))
    makeOperator(ctx,
      Operator.literal(typeString, Literal.string(Utils.print(I.declRef))),
      Operator.fieldWrite(typeString.name, I.operand.value, "type"))
  }

  override protected def visitUncheckedTakeEnumDataAddr(r: Option[SILResult], I: SILOperator.uncheckedTakeEnumDataAddr, ctx: Context): Array[InstructionDef] = {
    // Type is statically unknown, at least for now.
    val result = getSingleResult(r, new Type("*Any"))
    makeOperator(ctx, Operator.fieldRead(result, alias = true, I.operand.value, "data"))
  }

  override protected def visitSelectEnum(r: Option[SILResult], I: SILOperator.selectEnum, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    var default: Option[String] = None
    val cases = {
      val arr = new ArrayBuffer[EnumAssignCase]()
      I.cases.foreach {
        case SILSwitchEnumCase.cs(declRef, result) => {
          arr.append(new EnumAssignCase(Utils.print(declRef), result))
        }
        case SILSwitchEnumCase.default(result) => {
          default = Some(result)
        }
      }
      arr.toArray
    }
    makeOperator(ctx, Operator.switchEnumAssign(result, I.operand.value, cases, default))
  }

  override protected def visitSelectEnumAddr(r: Option[SILResult], I: SILOperator.selectEnumAddr, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe))
    var default: Option[String] = None
    val cases = {
      val arr = new ArrayBuffer[EnumAssignCase]()
      I.cases.foreach {
        case SILSwitchEnumCase.cs(declRef, result) => {
          arr.append(new EnumAssignCase(Utils.print(declRef), result))
        }
        case SILSwitchEnumCase.default(result) => {
          default = Some(result)
        }
      }
      arr.toArray
    }
    val intermediateResult = new Symbol(generateSymbolName(I.operand.value), Utils.SILPointerTypeToType(I.operand.tpe))
    makeOperator(ctx,
      Operator.pointerRead(intermediateResult, I.operand.value),
      Operator.switchEnumAssign(result, intermediateResult.name, cases, default))
  }

  override protected def visitInitExistentialAddr(r: Option[SILResult], I: SILOperator.initExistentialAddr, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitDeinitExistentialAddr(r: Option[SILResult], I: SILOperator.deinitExistentialAddr, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitOpenExistentialAddr(r: Option[SILResult], I: SILOperator.openExistentialAddr, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitInitExistentialRef(r: Option[SILResult], I: SILOperator.initExistentialRef, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitOpenExistentialRef(r: Option[SILResult], I: SILOperator.openExistentialRef, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitInitExistentialMetatype(r: Option[SILResult], I: SILOperator.initExistentialMetatype, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitOpenExistentialMetatype(r: Option[SILResult], I: SILOperator.openExistentialMetatype, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitAllocExistentialBox(r: Option[SILResult], I: SILOperator.allocExistentialBox, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToPointerType(I.tpeP))
    makeOperator(ctx, Operator.neww(result))
  }

  override protected def visitProjectExistentialBox(r: Option[SILResult], I: SILOperator.projectExistentialBox, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitOpenExistentialBox(r: Option[SILResult], I: SILOperator.openExistentialBox, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitDeallocExistentialBox(r: Option[SILResult], I: SILOperator.deallocExistentialBox, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitProjectBlockStorage(r: Option[SILResult], I: SILOperator.projectBlockStorage, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitInitBlockStorageHeader(r: Option[SILResult], I: SILOperator.initBlockStorageHeader, ctx: Context): Array[InstructionDef] = {
    null // TODO
  }

  override protected def visitUpcast(r: Option[SILResult], I: SILOperator.upcast, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitAddressToPointer(r: Option[SILResult], I: SILOperator.addressToPointer, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitPointerToAddress(r: Option[SILResult], I: SILOperator.pointerToAddress, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitUncheckedRefCast(r: Option[SILResult], I: SILOperator.uncheckedRefCast, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitUncheckedAddrCast(r: Option[SILResult], I: SILOperator.uncheckedAddrCast, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitUncheckedTrivialBitCast(r: Option[SILResult], I: SILOperator.uncheckedTrivialBitCast, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitRefToUnowned(r: Option[SILResult], I: SILOperator.refToUnowned, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitRefToUnmanaged(r: Option[SILResult], I: SILOperator.refToUnmanaged, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitUnmanagedToRef(r: Option[SILResult], I: SILOperator.unmanagedToRef, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitConvertFunction(r: Option[SILResult], I: SILOperator.convertFunction, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitConvertEscapeToNoEscape(r: Option[SILResult], I: SILOperator.convertEscapeToNoescape, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitValueToBridgeObject(r: Option[SILResult], I: SILOperator.valueToBridgeObject, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitThinToThickFunction(r: Option[SILResult], I: SILOperator.thinToThickFunction, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitThickToObjCMetatype(r: Option[SILResult], I: SILOperator.thickToObjcMetatype, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitObjCToThickMetatype(r: Option[SILResult], I: SILOperator.objcToThickMetatype, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitObjCMetatypeToObject(r: Option[SILResult], I: SILOperator.objcMetatypeToObject, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitObjCExistentialMetatypeToObject(r: Option[SILResult], I: SILOperator.objcExistentialMetatypeToObject, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitUnconditionalCheckedCast(r: Option[SILResult], I: SILOperator.unconditionalCheckedCast, ctx: Context): Array[InstructionDef] = {
    assertSILResult(r, 1)
    makeOperator(ctx, Operator.symbolCopy(I.operand.value, r.get.valueNames(0)))
  }

  override protected def visitUnconditionalCheckedCastAddr(r: Option[SILResult], I: SILOperator.unconditionalCheckedCastAddr, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitCondFail(r: Option[SILResult], I: SILOperator.condFail, ctx: Context): Array[InstructionDef] = {
    makeOperator(ctx, Operator.condFail(I.operand.value))
  }

  override protected def visitUnreachable(ctx: Context): Array[InstructionDef] = {
    makeTerminator(ctx, Terminator.unreachable)
  }

  override protected def visitReturn(I: SILTerminator.ret, ctx: Context): Array[InstructionDef] = {
    makeTerminator(ctx, Terminator.ret(I.operand.value))
  }

  override protected def visitThrow(I: SILTerminator.thro, ctx: Context): Array[InstructionDef] = {
    makeTerminator(ctx, Terminator.thro(I.operand.value))
  }

  override protected def visitYield(I: SILTerminator.yld, ctx: Context): Array[InstructionDef] = {
    val yields: Array[String] = {
      val arr = new ArrayBuffer[String]()
      I.operands.foreach(o => {
        arr.append(o.value)
      })
      arr.toArray
    }
    makeTerminator(ctx, Terminator.yld(yields, I.resumeLabel, I.unwindLabel))
  }

  override protected def visitUnwind(ctx: Context): Array[InstructionDef] = {
    makeTerminator(ctx, Terminator.unwind)
  }

  override protected def visitBr(I: SILTerminator.br, ctx: Context): Array[InstructionDef] = {
    val args: Array[String] = {
      val arr = new ArrayBuffer[String]()
      I.operands.foreach(o => {
        arr.append(o.value)
      })
      arr.toArray
    }
    makeTerminator(ctx, Terminator.br(I.label, args))
  }

  override protected def visitCondBr(I: SILTerminator.condBr, ctx: Context): Array[InstructionDef] = {
    val trueArgs: Array[String] = {
      val arr = new ArrayBuffer[String]()
      I.trueOperands.foreach(o => {
        arr.append(o.value)
      })
      arr.toArray
    }
    val falseArgs: Array[String] = {
      val arr = new ArrayBuffer[String]()
      I.falseOperands.foreach(o => {
        arr.append(o.value)
      })
      arr.toArray
    }
    makeTerminator(ctx, Terminator.condBr(I.cond, I.trueLabel, trueArgs, I.falseLabel, falseArgs))
  }

  override protected def visitSwitchValue(I: SILTerminator.switchValue, ctx: Context): Array[InstructionDef] = {
    var default: Option[String] = None
    val cases = {
      val arr = new ArrayBuffer[SwitchCase]()
      I.cases.foreach {
        case SILSwitchValueCase.cs(value, label) => {
          arr.append(new SwitchCase(value, label))
        }
        case SILSwitchValueCase.default(label) => {
          default = Some(label)
        }
      }
      arr.toArray
    }
    makeTerminator(ctx, Terminator.switch(I.operand.value, cases, default))
  }

  override protected def visitSwitchEnum(I: SILTerminator.switchEnum, ctx: Context): Array[InstructionDef] = {
    var default: Option[String] = None
    val cases = {
      val arr = new ArrayBuffer[SwitchEnumCase]()
      I.cases.foreach {
        case SILSwitchEnumCase.cs(declRef, label) => {
          arr.append(new SwitchEnumCase(Utils.print(declRef), label))
        }
        case SILSwitchEnumCase.default(result) => {
          default = Some(result)
        }
      }
      arr.toArray
    }
    makeTerminator(ctx, Terminator.switchEnum(I.operand.value, cases, default))
  }

  override protected def visitSwitchEnumAddr(I: SILTerminator.switchEnumAddr, ctx: Context): Array[InstructionDef] = {
    val readResult = new Symbol(generateSymbolName(I.operand.value), Utils.SILPointerTypeToType(I.operand.tpe))
    var default: Option[String] = None
    val cases = {
      val arr = new ArrayBuffer[SwitchEnumCase]()
      I.cases.foreach {
        case SILSwitchEnumCase.cs(declRef, label) => {
          arr.append(new SwitchEnumCase(Utils.print(declRef), label))
        }
        case SILSwitchEnumCase.default(result) => {
          default = Some(result)
        }
      }
      arr.toArray
    }
    val instructions = new ArrayBuffer[InstructionDef]()
    instructions.appendAll(makeOperator(ctx, Operator.pointerRead(readResult, I.operand.value)))
    instructions.appendAll(makeTerminator(ctx, Terminator.switchEnum(readResult.name, cases, default)))
    instructions.toArray
  }

  override protected def visitDynamicMethodBr(I: SILTerminator.dynamicMethodBr, ctx: Context): Array[InstructionDef] = {
    val result = new Symbol(generateSymbolName(I.operand.value), new Type("Builtin.Int1"))
    val instructions = new ArrayBuffer[InstructionDef]()
    instructions.appendAll(makeOperator(ctx, Operator.unaryOp(result, UnaryOperation.arbitrary, I.operand.value)))
    instructions.appendAll(makeTerminator(ctx,
      Terminator.condBr(result.name, I.namedLabel, Array(), I.notNamedLabel, Array())))
    instructions.toArray
  }

  override protected def visitCheckedCastBr(I: SILTerminator.checkedCastBr, ctx: Context): Array[InstructionDef] = {
    val result = new Symbol(generateSymbolName(I.operand.value), new Type("Builtin.Int1"))
    val instructions = new ArrayBuffer[InstructionDef]()
    instructions.appendAll(makeOperator(ctx, Operator.unaryOp(result, UnaryOperation.arbitrary, I.operand.value)))
    instructions.appendAll(makeTerminator(ctx,
      Terminator.condBr(result.name, I.succeedLabel, Array(), I.failureLabel, Array())))
    instructions.toArray
  }

  override protected def visitCheckedCastAddrBr(I: SILTerminator.checkedCastAddrBr, ctx: Context): Array[InstructionDef] = {
    val result = new Symbol(generateSymbolName(I.fromOperand.value), new Type("Builtin.Int1"))
    val instructions = new ArrayBuffer[InstructionDef]()
    instructions.appendAll(makeOperator(ctx,
      Operator.binaryOp(result, BinaryOperation.arbitrary, I.fromOperand.value, I.toOperand.value)))
    instructions.appendAll(makeTerminator(ctx,
      Terminator.condBr(result.name, I.succeedLabel, Array(), I.failureLabel, Array())))
    instructions.toArray
  }

  override protected def visitTryApply(I: SILTerminator.tryApply, ctx: Context): Array[InstructionDef] = {
    makeTerminator(ctx, Terminator.tryApply(I.value, I.arguments, I.normalLabel, I.errorLabel))
  }

  def getSingleResult(r: Option[SILResult], tpe: Type): Symbol = {
    assertSILResult(r, 1)
    new Symbol(r.get.valueNames(0), tpe)
  }

}
