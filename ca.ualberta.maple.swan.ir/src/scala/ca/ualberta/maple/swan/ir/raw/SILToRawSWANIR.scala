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

import java.util.logging.Logger

import ca.ualberta.maple.swan.ir.Exceptions.{ExperimentalException, UnexpectedSILFormatException, UnexpectedSILTypeBehaviourException}
import ca.ualberta.maple.swan.ir.{BinaryOperation, BlockRef, Context, EnumAssignCase, InstructionDef, Literal, Operator, OperatorDef, SwitchCase, SwitchEnumCase, Symbol, SymbolRef, Terminator, TerminatorDef, Type, UnaryOperation}
import ca.ualberta.maple.swan.parser._

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

  private def stringArrayToSymbolRefArray(arr: Array[String], ctx: Context): Array[SymbolRef] = {
    val ret = new ArrayBuffer[SymbolRef]()
    arr.foreach(e => {
      ret.append(makeSymbolRef(e, ctx))
    })
    ret.toArray
  }

  private def makeTerminator(ctx: Context, terminator: Terminator): Array[InstructionDef] = {
    Array[InstructionDef](InstructionDef.terminator(new TerminatorDef(terminator, ctx.pos)))
  }

  private def copySymbol(from: String, to: String, ctx: Context) = {
    ctx.refTable.symbols.put(to, makeSymbolRef(from, ctx))
  }

  @throws[UnexpectedSILFormatException]
  private def verifySILResult(r: Option[SILResult], i: Int): Unit = {
    if (!(r.nonEmpty && r.get.valueNames.length == i)) {
      throw new UnexpectedSILFormatException("Expected " + i.toString + " result")
    }
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitAllocStack(r: Option[SILResult], I: SILOperator.allocStack, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToPointerType(I.tpe), ctx) // IMPORTANT: $T to $*T
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitAllocRef(r: Option[SILResult], I: SILOperator.allocRef, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitAllocRefDynamic(r: Option[SILResult], I: SILOperator.allocRefDynamic, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitAllocBox(r: Option[SILResult], I: SILOperator.allocBox, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToPointerType(I.tpe), ctx)
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitAllocValueBuffer(r: Option[SILResult], I: SILOperator.allocValueBuffer, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToPointerType(I.tpe), ctx)
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

  @throws[UnexpectedSILFormatException]
  override protected def visitProjectBox(r: Option[SILResult], I: SILOperator.projectBox, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  override protected def visitDeallocRef(r: Option[SILResult], I: SILOperator.deallocRef, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitDeallocPartialRef(r: Option[SILResult], I: SILOperator.deallocPartialRef, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitDebugValue(r: Option[SILResult], I: SILOperator.debugValue, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitDebugValueAddr(r: Option[SILResult], I: SILOperator.debugValueAddr, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  @throws[UnexpectedSILFormatException]
  @throws[UnexpectedSILTypeBehaviourException]
  override protected def visitLoad(r: Option[SILResult], I: SILOperator.load, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILPointerTypeToType(I.operand.tpe), ctx)
    makeOperator(ctx, Operator.pointerRead(result, makeSymbolRef(I.operand.value, ctx)))
  }

  override protected def visitStore(r: Option[SILResult], I: SILOperator.store, ctx: Context): Array[InstructionDef] = {
    makeOperator(ctx, Operator.pointerWrite(makeSymbolRef(I.from, ctx), makeSymbolRef(I.to.value, ctx)))
  }

  @throws[UnexpectedSILFormatException]
  @throws[UnexpectedSILTypeBehaviourException]
  @throws[UnexpectedSILTypeBehaviourException]
  override protected def visitLoadBorrow(r: Option[SILResult], I: SILOperator.loadBorrow, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILPointerTypeToType(I.operand.tpe), ctx)
    makeOperator(ctx, Operator.pointerRead(result, makeSymbolRef(I.operand.value, ctx)))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitBeginBorrow(r: Option[SILResult], I: SILOperator.beginBorrow, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  override protected def visitEndBorrow(r: Option[SILResult], I: SILOperator.endBorrow, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  @throws[UnexpectedSILTypeBehaviourException]
  override protected def visitCopyAddr(r: Option[SILResult], I: SILOperator.copyAddr, ctx: Context): Array[InstructionDef] = {
    val pointerReadResult = new Symbol(generateSymbolName(I.value, ctx), Utils.SILPointerTypeToType(I.operand.tpe))
    makeOperator(
      ctx,
      Operator.pointerRead(pointerReadResult, makeSymbolRef(I.value, ctx)),
      Operator.pointerWrite(pointerReadResult.ref, makeSymbolRef(I.operand.value, ctx)))
  }

  override protected def visitDestroyAddr(r: Option[SILResult], I: SILOperator.destroyAddr, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitIndexAddr(r: Option[SILResult], I: SILOperator.indexAddr, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.addr.tpe), ctx)
    makeOperator(ctx, Operator.arrayRead(result, alias = true, makeSymbolRef(I.addr.value, ctx)))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitBeginAccess(r: Option[SILResult], I: SILOperator.beginAccess, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
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

  @throws[UnexpectedSILFormatException]
  @throws[UnexpectedSILTypeBehaviourException]
  override protected def visitLoadWeak(r: Option[SILResult], I: SILOperator.loadWeak, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILPointerTypeToType(I.operand.tpe), ctx)
    makeOperator(ctx, Operator.pointerRead(result, makeSymbolRef(I.operand.value, ctx)))
  }

  override protected def visitStoreWeak(r: Option[SILResult], I: SILOperator.storeWeak, ctx: Context): Array[InstructionDef] = {
    makeOperator(ctx, Operator.pointerWrite(makeSymbolRef(I.from, ctx), makeSymbolRef(I.to.value, ctx)))
  }

  @throws[UnexpectedSILFormatException]
  @throws[UnexpectedSILTypeBehaviourException]
  override protected def visitLoadUnowned(r: Option[SILResult], I: SILOperator.loadUnowned, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILPointerTypeToType(I.operand.tpe), ctx)
    makeOperator(ctx, Operator.pointerRead(result, makeSymbolRef(I.operand.value, ctx)))
  }

  override protected def visitStoreUnowned(r: Option[SILResult], I: SILOperator.storeUnowned, ctx: Context): Array[InstructionDef] = {
    makeOperator(ctx, Operator.pointerWrite(makeSymbolRef(I.from, ctx), makeSymbolRef(I.to.value, ctx)))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitMarkDependence(r: Option[SILResult], I: SILOperator.markDependence, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitIsEscapingClosure(r: Option[SILResult], I: SILOperator.isEscapingClosure, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(SILType.namedType("Builtin.Int1")), ctx)
    makeOperator(ctx, Operator.unaryOp(result, UnaryOperation.arbitrary, makeSymbolRef(I.operand.value, ctx)))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitCopyBlock(r: Option[SILResult], I: SILOperator.copyBlock, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.operand.tpe), ctx)
    makeOperator(ctx, Operator.assign(result, makeSymbolRef(I.operand.value, ctx)))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitCopyBlockWithoutEscaping(r: Option[SILResult], I: SILOperator.copyBlockWithoutEscaping, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.operand1.tpe), ctx)
    makeOperator(ctx, Operator.assign(result, makeSymbolRef(I.operand1.value, ctx)))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitFunctionRef(r: Option[SILResult], I: SILOperator.functionRef, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.functionRef(result, Array(I.name.demangled)))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitDynamicFunctionRef(r: Option[SILResult], I: SILOperator.dynamicFunctionRef, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.functionRef(result, Array(I.name.demangled)))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitPrevDynamicFunctionRef(r: Option[SILResult], I: SILOperator.prevDynamicFunctionRef, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.functionRef(result, Array(I.name.demangled)))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitGlobalAddr(r: Option[SILResult], I: SILOperator.globalAddr, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.assignGlobal(result, I.name.demangled))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitIntegerLiteral(r: Option[SILResult], I: SILOperator.integerLiteral, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.literal(result, Literal.int(I.value)))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitFloatLiteral(r: Option[SILResult], I: SILOperator.floatLiteral, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.literal(result, Literal.float(Utils.SILFloatStringToFloat(I.value))))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitStringLiteral(r: Option[SILResult], I: SILOperator.stringLiteral, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(SILType.namedType("$Builtin.RawPointer")), ctx)
    makeOperator(ctx, Operator.literal(result, Literal.string(I.value)))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitClassMethod(r: Option[SILResult], I: SILOperator.classMethod, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    if (I.declRef.name.length < 2) { // #T.method[...]
      throw new UnexpectedSILFormatException("Expected decl ref of class_method to have at least two components: " + Utils.print(I.declRef))
    }
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
      // No v-table contained information about the method.
      // For now, just create a new value
      makeOperator(ctx, Operator.neww(result))
    }
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitObjCMethod(r: Option[SILResult], I: SILOperator.objcMethod, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    val name = new SILPrinter().print(I.declRef)
    makeOperator(ctx, Operator.builtinRef(result, decl = true, name))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitObjCSuperMethod(r: Option[SILResult], I: SILOperator.objcSuperMethod, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    if (I.declRef.name.length < 2) { // #T.method[...]
      throw new UnexpectedSILFormatException("Expected decl ref of objc_super_method to have at least two components: " + Utils.print(I.declRef))
    }
    makeOperator(ctx, Operator.builtinRef(result, decl = true, I.declRef.name.slice(0,2).mkString(".")))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitWitnessMethod(r: Option[SILResult], I: SILOperator.witnessMethod, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    if (I.declRef.name.length < 2) { // #T.method[...]
      throw new UnexpectedSILFormatException("Expected decl ref of witness_method to have at least two components: " + Utils.print(I.declRef))
    }
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
      // No v-table contained information about the method.
      // For now, just create a new value
      makeOperator(ctx, Operator.neww(result))
    }
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitApply(r: Option[SILResult], I: SILOperator.apply, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.apply(result, makeSymbolRef(I.value, ctx), stringArrayToSymbolRefArray(I.arguments, ctx)))
  }

  override protected def visitBeginApply(r: Option[SILResult], I: SILOperator.beginApply, ctx: Context): Array[InstructionDef] = {
    null // TODO
  }

  override protected def visitAbortApply(r: Option[SILResult], I: SILOperator.abortApply, ctx: Context): Array[InstructionDef] = {
    makeOperator(ctx, Operator.abortCoroutine(makeSymbolRef(I.value, ctx)))
  }

  override protected def visitEndApply(r: Option[SILResult], I: SILOperator.endApply, ctx: Context): Array[InstructionDef] = {
    makeOperator(ctx, Operator.endCoroutine(makeSymbolRef(I.value, ctx)))
  }

  override protected def visitPartialApply(r: Option[SILResult], I: SILOperator.partialApply, ctx: Context): Array[InstructionDef] = {
    // TODO: Need to first understand partial apply dataflow semantics.
    // For now, just create a new value
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitBuiltin(r: Option[SILResult], I: SILOperator.builtin, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    // Use Any type because we don't know the type of the function ref value.
    val functionRef = new Symbol(generateSymbolName(r.get.valueNames(0), ctx), new Type())
    val arguments = ArrayBuffer[SymbolRef]()
    I.operands.foreach(op => {
      arguments.append(makeSymbolRef(op.value, ctx))
    })
    makeOperator(
      ctx,
      Operator.builtinRef(functionRef, decl = false, I.name),
      Operator.apply(result, functionRef.ref, arguments.toArray)
    )
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitMetatype(r: Option[SILResult], I: SILOperator.metatype, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitValueMetatype(r: Option[SILResult], I: SILOperator.valueMetatype, ctx: Context): Array[InstructionDef] = {
    // The operand here is irrelevant since the return type is already provided.
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitExistentialMetatype(r: Option[SILResult], I: SILOperator.existentialMetatype, ctx: Context): Array[InstructionDef] = {
    // The operand here is irrelevant since the return type is already provided.
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitObjCProtocol(r: Option[SILResult], I: SILOperator.objcProtocol, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.neww(result))
  }

  override protected def visitRetainValue(r: Option[SILResult], I: SILOperator.retainValue, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitCopyValue(r: Option[SILResult], I: SILOperator.copyValue, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
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

  @throws[UnexpectedSILFormatException]
  override protected def visitTuple(r: Option[SILResult], I: SILOperator.tuple, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTupleElementsToType(I.elements), ctx)
    val operators = ArrayBuffer[InstructionDef]()
    operators.append(makeOperator(ctx, Operator.neww(result))(0))
    I.elements match {
      case SILTupleElements.labeled(_, values) => {
        values.foreach(value => {
          operators.append(makeOperator(ctx, Operator.arrayWrite(makeSymbolRef(value, ctx), result.ref))(0))
        })
      }
      case SILTupleElements.unlabeled(operands) => {
        operands.foreach(operand => {
          operators.append(makeOperator(ctx, Operator.arrayWrite(makeSymbolRef(operand.value, ctx), result.ref))(0))
        })
      }
    }
    operators.toArray
  }

  @throws[UnexpectedSILFormatException]
  @throws[UnexpectedSILTypeBehaviourException]
  override protected def visitTupleExtract(r: Option[SILResult], I: SILOperator.tupleExtract, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTupleTypeToType(I.operand.tpe, I.declRef, pointer = false), ctx)
    makeOperator(ctx, Operator.arrayRead(result, alias = false, makeSymbolRef(I.operand.value, ctx)))
  }

  @throws[UnexpectedSILFormatException]
  @throws[UnexpectedSILTypeBehaviourException]
  override protected def visitTupleElementAddr(r: Option[SILResult], I: SILOperator.tupleElementAddr, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTupleTypeToType(I.operand.tpe, I.declRef, pointer = true), ctx)
    makeOperator(ctx, Operator.arrayRead(result, alias = true, makeSymbolRef(I.operand.value, ctx)))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitDestructureTuple(r: Option[SILResult], I: SILOperator.destructureTuple, ctx: Context): Array[InstructionDef] = {
    if (!I.operand.tpe.isInstanceOf[SILType.tupleType]) {
      throw new UnexpectedSILFormatException("Expected destructure_tuple operand type to be of tuple type")
    }
    val tupleType = I.operand.tpe.asInstanceOf[SILType.tupleType]
    verifySILResult(r, tupleType.parameters.length)
    val operators = ArrayBuffer[InstructionDef]()
    tupleType.parameters.zipWithIndex.foreach(param => {
      operators.append(makeOperator(ctx, Operator.arrayRead(
        new Symbol(makeSymbolRef(r.get.valueNames(param._2), ctx), Utils.SILTypeToType(param._1)),
        alias = false, makeSymbolRef(I.operand.value, ctx)))(0))
    })
    operators.toArray
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitStruct(r: Option[SILResult], I: SILOperator.struct, ctx: Context): Array[InstructionDef] = {
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
    val operators = ArrayBuffer[InstructionDef]()
    operators.append(makeOperator(ctx, Operator.neww(result))(0))
    if (init.nonEmpty) {
      if (init.get.args.length < I.operands.length) {
        Logger.getGlobal.warning("raspen: " + Utils.print(I.tpe))
      }
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
        Logger.getGlobal.warning("Missing struct init definition for " + Utils.print(I.tpe))
        this.missingStructs.add(structName)
      }
    }
    operators.toArray
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitStructExtract(r: Option[SILResult], I: SILOperator.structExtract, ctx: Context): Array[InstructionDef] = {
    // Type is statically unknown, at least for now
    val result = getSingleResult(r, new Type("Any"), ctx)
    makeOperator(ctx, Operator.fieldRead(result, alias = false, makeSymbolRef(I.operand.value, ctx), Utils.SILStructFieldDeclRefToString(I.declRef)))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitStructElementAddr(r: Option[SILResult], I: SILOperator.structElementAddr, ctx: Context): Array[InstructionDef] = {
    // Type is statically unknown, at least for now
    val result = getSingleResult(r, new Type("*Any"), ctx)
    makeOperator(ctx, Operator.fieldRead(result, alias = true, makeSymbolRef(I.operand.value, ctx), Utils.SILStructFieldDeclRefToString(I.declRef)))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitObject(r: Option[SILResult], I: SILOperator.objct, ctx: Context): Array[InstructionDef] = {
    // Only new instruction because field names are not known.
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitRefElementAddr(r: Option[SILResult], I: SILOperator.refElementAddr, ctx: Context): Array[InstructionDef] = {
    // Type is statically unknown, at least for now
    val result = getSingleResult(r, new Type("*Any"), ctx)
    makeOperator(ctx, Operator.fieldRead(result, alias = false, makeSymbolRef(I.operand.value, ctx), Utils.SILStructFieldDeclRefToString(I.declRef)))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitRefTailAddr(r: Option[SILResult], I: SILOperator.refTailAddr, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToPointerType(I.tpe), ctx)
    makeOperator(ctx, Operator.arrayRead(result, alias = false, makeSymbolRef(I.operand.value, ctx)))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitEnum(r: Option[SILResult], I: SILOperator.enm, ctx: Context): Array[InstructionDef] = {
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
  override protected def visitUncheckedEnumData(r: Option[SILResult], I: SILOperator.uncheckedEnumData, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, new Type("Any"), ctx)
    // No alias for now, but this might change.
    makeOperator(ctx, Operator.fieldRead(result, alias = false, makeSymbolRef(I.operand.value, ctx), "data"))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitInitEnumDataAddr(r: Option[SILResult], I: SILOperator.initEnumDataAddr, ctx: Context): Array[InstructionDef] = {
    // Type is statically unknown, at least for now.
    val result = getSingleResult(r, new Type("*Any"), ctx)
    makeOperator(ctx, Operator.fieldRead(result, alias = true, makeSymbolRef(I.operand.value, ctx), "data"))
  }

  override protected def visitInjectEnumAddr(r: Option[SILResult], I: SILOperator.injectEnumAddr, ctx: Context): Array[InstructionDef] = {
    val typeString = new Symbol(generateSymbolName(I.operand.value, ctx), new Type("Builtin.RawPointer"))
    makeOperator(ctx,
      Operator.literal(typeString, Literal.string(Utils.print(I.declRef))),
      Operator.fieldWrite(typeString.ref, makeSymbolRef(I.operand.value, ctx), "type"))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitUncheckedTakeEnumDataAddr(r: Option[SILResult], I: SILOperator.uncheckedTakeEnumDataAddr, ctx: Context): Array[InstructionDef] = {
    // Type is statically unknown, at least for now.
    val result = getSingleResult(r, new Type("*Any"), ctx)
    makeOperator(ctx, Operator.fieldRead(result, alias = true, makeSymbolRef(I.operand.value, ctx), "data"))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitSelectEnum(r: Option[SILResult], I: SILOperator.selectEnum, ctx: Context): Array[InstructionDef] = {
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
      arr.toArray
    }
    makeOperator(ctx, Operator.switchEnumAssign(result, makeSymbolRef(I.operand.value, ctx), cases, default))
  }

  @throws[UnexpectedSILFormatException]
  @throws[UnexpectedSILTypeBehaviourException]
  override protected def visitSelectEnumAddr(r: Option[SILResult], I: SILOperator.selectEnumAddr, ctx: Context): Array[InstructionDef] = {
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
      arr.toArray
    }
    val intermediateResult = new Symbol(generateSymbolName(I.operand.value, ctx), Utils.SILPointerTypeToType(I.operand.tpe))
    makeOperator(ctx,
      Operator.pointerRead(intermediateResult, makeSymbolRef(I.operand.value, ctx)),
      Operator.switchEnumAssign(result, intermediateResult.ref, cases, default))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitInitExistentialAddr(r: Option[SILResult], I: SILOperator.initExistentialAddr, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  override protected def visitDeinitExistentialAddr(r: Option[SILResult], I: SILOperator.deinitExistentialAddr, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitOpenExistentialAddr(r: Option[SILResult], I: SILOperator.openExistentialAddr, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitInitExistentialRef(r: Option[SILResult], I: SILOperator.initExistentialRef, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitOpenExistentialRef(r: Option[SILResult], I: SILOperator.openExistentialRef, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitInitExistentialMetatype(r: Option[SILResult], I: SILOperator.initExistentialMetatype, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitOpenExistentialMetatype(r: Option[SILResult], I: SILOperator.openExistentialMetatype, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitAllocExistentialBox(r: Option[SILResult], I: SILOperator.allocExistentialBox, ctx: Context): Array[InstructionDef] = {
    val result = getSingleResult(r, Utils.SILTypeToPointerType(I.tpeP), ctx)
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitProjectExistentialBox(r: Option[SILResult], I: SILOperator.projectExistentialBox, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitOpenExistentialBox(r: Option[SILResult], I: SILOperator.openExistentialBox, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  override protected def visitDeallocExistentialBox(r: Option[SILResult], I: SILOperator.deallocExistentialBox, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitProjectBlockStorage(r: Option[SILResult], I: SILOperator.projectBlockStorage, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitInitBlockStorageHeader(r: Option[SILResult], I: SILOperator.initBlockStorageHeader, ctx: Context): Array[InstructionDef] = {
    // TODO: This is a complicated instruction. There isn't any SIL.rst
    //  documentation on it. It seems to call a thunk (I.invoke) with the
    //  I.operand as the first argument. In the called thunk, a function ref
    //  is read from I.operand (function ref to a closure) and called.
    // For now, just create a new value
    val result = getSingleResult(r, Utils.SILTypeToType(I.tpe), ctx)
    makeOperator(ctx, Operator.neww(result))
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitUpcast(r: Option[SILResult], I: SILOperator.upcast, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitAddressToPointer(r: Option[SILResult], I: SILOperator.addressToPointer, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitPointerToAddress(r: Option[SILResult], I: SILOperator.pointerToAddress, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitUncheckedRefCast(r: Option[SILResult], I: SILOperator.uncheckedRefCast, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitUncheckedAddrCast(r: Option[SILResult], I: SILOperator.uncheckedAddrCast, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitUncheckedTrivialBitCast(r: Option[SILResult], I: SILOperator.uncheckedTrivialBitCast, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitRawPointerToRef(r: Option[SILResult], I: SILOperator.rawPointerToRef, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
    COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitRefToUnowned(r: Option[SILResult], I: SILOperator.refToUnowned, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitRefToUnmanaged(r: Option[SILResult], I: SILOperator.refToUnmanaged, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitUnmanagedToRef(r: Option[SILResult], I: SILOperator.unmanagedToRef, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitConvertFunction(r: Option[SILResult], I: SILOperator.convertFunction, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitConvertEscapeToNoEscape(r: Option[SILResult], I: SILOperator.convertEscapeToNoescape, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitValueToBridgeObject(r: Option[SILResult], I: SILOperator.valueToBridgeObject, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitBridgeObjectToRef(r: Option[SILResult], I: SILOperator.bridgeObjectToRef, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
    COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitThinToThickFunction(r: Option[SILResult], I: SILOperator.thinToThickFunction, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitThickToObjCMetatype(r: Option[SILResult], I: SILOperator.thickToObjcMetatype, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitObjCToThickMetatype(r: Option[SILResult], I: SILOperator.objcToThickMetatype, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitObjCMetatypeToObject(r: Option[SILResult], I: SILOperator.objcMetatypeToObject, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitObjCExistentialMetatypeToObject(r: Option[SILResult], I: SILOperator.objcExistentialMetatypeToObject, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
		COPY
  }

  @throws[UnexpectedSILFormatException]
  override protected def visitUnconditionalCheckedCast(r: Option[SILResult], I: SILOperator.unconditionalCheckedCast, ctx: Context): Array[InstructionDef] = {
    verifySILResult(r, 1)
    copySymbol(I.operand.value, r.get.valueNames(0), ctx)
    null
  }

  override protected def visitUnconditionalCheckedCastAddr(r: Option[SILResult], I: SILOperator.unconditionalCheckedCastAddr, ctx: Context): Array[InstructionDef] = {
    NOP
  }

  override protected def visitCondFail(r: Option[SILResult], I: SILOperator.condFail, ctx: Context): Array[InstructionDef] = {
    makeOperator(ctx, Operator.condFail(makeSymbolRef(I.operand.value, ctx)))
  }

  override protected def visitUnreachable(ctx: Context): Array[InstructionDef] = {
    makeTerminator(ctx, Terminator.unreachable)
  }

  override protected def visitReturn(I: SILTerminator.ret, ctx: Context): Array[InstructionDef] = {
    makeTerminator(ctx, Terminator.ret(makeSymbolRef(I.operand.value, ctx)))
  }

  override protected def visitThrow(I: SILTerminator.thro, ctx: Context): Array[InstructionDef] = {
    makeTerminator(ctx, Terminator.thro(makeSymbolRef(I.operand.value, ctx)))
  }

  override protected def visitYield(I: SILTerminator.yld, ctx: Context): Array[InstructionDef] = {
    val yields: Array[SymbolRef] = {
      val arr = new ArrayBuffer[SymbolRef]()
      I.operands.foreach(o => {
        arr.append(makeSymbolRef(o.value, ctx))
      })
      arr.toArray
    }
    makeTerminator(ctx, Terminator.yld(yields, makeBlockRef(I.resumeLabel, ctx), makeBlockRef(I.unwindLabel, ctx)))
  }

  override protected def visitUnwind(ctx: Context): Array[InstructionDef] = {
    makeTerminator(ctx, Terminator.unwind)
  }

  override protected def visitBr(I: SILTerminator.br, ctx: Context): Array[InstructionDef] = {
    val args: Array[SymbolRef] = {
      val arr = new ArrayBuffer[SymbolRef]()
      I.operands.foreach(o => {
        arr.append(makeSymbolRef(o.value, ctx))
      })
      arr.toArray
    }
    makeTerminator(ctx, Terminator.br(makeBlockRef(I.label, ctx), args))
  }

  override protected def visitCondBr(I: SILTerminator.condBr, ctx: Context): Array[InstructionDef] = {
    val trueArgs: Array[SymbolRef] = {
      val arr = new ArrayBuffer[SymbolRef]()
      I.trueOperands.foreach(o => {
        arr.append(makeSymbolRef(o.value, ctx))
      })
      arr.toArray
    }
    val falseArgs: Array[SymbolRef] = {
      val arr = new ArrayBuffer[SymbolRef]()
      I.falseOperands.foreach(o => {
        arr.append(makeSymbolRef(o.value, ctx))
      })
      arr.toArray
    }
    makeTerminator(ctx, Terminator.condBr(makeSymbolRef(I.cond, ctx),
      makeBlockRef(I.trueLabel, ctx), trueArgs, makeBlockRef(I.falseLabel, ctx), falseArgs))
  }

  override protected def visitSwitchValue(I: SILTerminator.switchValue, ctx: Context): Array[InstructionDef] = {
    var default: Option[BlockRef] = None
    val cases = {
      val arr = new ArrayBuffer[SwitchCase]()
      I.cases.foreach {
        case SILSwitchValueCase.cs(value, label) => {
          arr.append(new SwitchCase(value, makeBlockRef(label, ctx)))
        }
        case SILSwitchValueCase.default(label) => {
          default = Some(makeBlockRef(label, ctx))
        }
      }
      arr.toArray
    }
    makeTerminator(ctx, Terminator.switch(makeSymbolRef(I.operand.value, ctx), cases, default))
  }

  override protected def visitSwitchEnum(I: SILTerminator.switchEnum, ctx: Context): Array[InstructionDef] = {
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
      arr.toArray
    }
    makeTerminator(ctx, Terminator.switchEnum(makeSymbolRef(I.operand.value, ctx), cases, default))
  }

  @throws[UnexpectedSILTypeBehaviourException]
  override protected def visitSwitchEnumAddr(I: SILTerminator.switchEnumAddr, ctx: Context): Array[InstructionDef] = {
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
      arr.toArray
    }
    val instructions = new ArrayBuffer[InstructionDef]()
    instructions.appendAll(makeOperator(ctx, Operator.pointerRead(readResult, makeSymbolRef(I.operand.value, ctx))))
    instructions.appendAll(makeTerminator(ctx, Terminator.switchEnum(readResult.ref, cases, default)))
    instructions.toArray
  }

  override protected def visitDynamicMethodBr(I: SILTerminator.dynamicMethodBr, ctx: Context): Array[InstructionDef] = {
    val result = new Symbol(generateSymbolName(I.operand.value, ctx), new Type("Builtin.Int1"))
    val instructions = new ArrayBuffer[InstructionDef]()
    instructions.appendAll(makeOperator(ctx, Operator.unaryOp(result, UnaryOperation.arbitrary, makeSymbolRef(I.operand.value, ctx))))
    instructions.appendAll(makeTerminator(ctx,
      Terminator.condBr(result.ref,
        makeBlockRef(I.namedLabel, ctx), Array(), makeBlockRef(I.notNamedLabel, ctx), Array())))
    instructions.toArray
  }

  override protected def visitCheckedCastBr(I: SILTerminator.checkedCastBr, ctx: Context): Array[InstructionDef] = {
    val result = new Symbol(generateSymbolName(I.operand.value, ctx), new Type("Builtin.Int1"))
    val instructions = new ArrayBuffer[InstructionDef]()
    instructions.appendAll(makeOperator(ctx, Operator.unaryOp(result, UnaryOperation.arbitrary, makeSymbolRef(I.operand.value, ctx))))
    instructions.appendAll(makeTerminator(ctx,
      Terminator.condBr(result.ref,
        makeBlockRef(I.succeedLabel, ctx), Array(), makeBlockRef(I.failureLabel, ctx), Array())))
    instructions.toArray
  }

  override protected def visitCheckedCastAddrBr(I: SILTerminator.checkedCastAddrBr, ctx: Context): Array[InstructionDef] = {
    val result = new Symbol(generateSymbolName(I.fromOperand.value, ctx), new Type("Builtin.Int1"))
    val instructions = new ArrayBuffer[InstructionDef]()
    instructions.appendAll(makeOperator(ctx,
      Operator.binaryOp(result, BinaryOperation.arbitrary,
        makeSymbolRef(I.fromOperand.value, ctx), makeSymbolRef(I.toOperand.value, ctx))))
    instructions.appendAll(makeTerminator(ctx,
      Terminator.condBr(result.ref,
        makeBlockRef(I.succeedLabel, ctx), Array(), makeBlockRef(I.failureLabel, ctx), Array())))
    instructions.toArray
  }

  override protected def visitTryApply(I: SILTerminator.tryApply, ctx: Context): Array[InstructionDef] = {
    makeTerminator(ctx, Terminator.tryApply(makeSymbolRef(I.value, ctx), stringArrayToSymbolRefArray(I.arguments, ctx),
      makeBlockRef(I.normalLabel, ctx), makeBlockRef(I.errorLabel, ctx)))
  }

  @throws[UnexpectedSILFormatException]
  def getSingleResult(r: Option[SILResult], tpe: Type, ctx: Context): Symbol = {
    verifySILResult(r, 1)
    new Symbol(makeSymbolRef(r.get.valueNames(0), ctx), tpe)
  }

}
