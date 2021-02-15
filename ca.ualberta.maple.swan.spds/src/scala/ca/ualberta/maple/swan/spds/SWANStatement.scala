/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2021 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.spds

import java.util

import boomerang.scene.{Field, IfStatement, InvokeExpr, Pair, Statement, StaticFieldVal, Val}
import ca.ualberta.maple.swan.ir.{Symbol, CanInstructionDef, CanOperatorDef, CanTerminatorDef, Literal, Operator, Position, Terminator, WithResult}

abstract class SWANStatement(val delegate: CanInstructionDef, m: SWANMethod) extends Statement(m) {
  // Modifiable
  override def containsStaticFieldAccess(): Boolean = false
  override def containsInvokeExpr(): Boolean = false
  override def getWrittenField: Field = null
  override def isFieldWriteWithBase(base: Val): Boolean = false
  override def getLoadedField: Field = null
  override def isFieldLoadWithBase(base: Val): Boolean = false
  override def getRightOp: Val = null
  override def getInvokeExpr: InvokeExpr = null
  override def isReturnStmt: Boolean = false
  override def isThrowStmt: Boolean = false
  override def isIfStmt: Boolean = false
  override def getIfStmt: IfStatement = null
  override def getReturnOp: Val = null
  override def isFieldStore: Boolean = false
  override def isArrayLoad: Boolean = false
  override def isFieldLoad: Boolean = false
  override def isIdentityStmt: Boolean = false
  override def getFieldStore: Pair[Val, Field] = null
  override def getFieldLoad: Pair[Val, Field] = null
  override def isStaticFieldLoad: Boolean = false
  override def isStaticFieldStore: Boolean = false
  override def getStaticField: StaticFieldVal = null
  override def getArrayBase: Pair[boomerang.scene.Val,Integer] = null
  // Not limited to WithResult, but this takes care of most cases.
  override def isAssign: Boolean = {
    delegate match {
      case CanInstructionDef.operator(operatorDef) =>
        operatorDef.operator.isInstanceOf[WithResult]
      case CanInstructionDef.terminator(_) => false
    }
  }
  // Shared
  private def getPosition: Option[Position] = {
    delegate match {
      case CanInstructionDef.operator(operatorDef) => operatorDef.position
      case CanInstructionDef.terminator(terminatorDef) => terminatorDef.position
    }
  }
  private def getResult: Symbol = {
    delegate.asInstanceOf[CanInstructionDef.operator].operatorDef.operator.asInstanceOf[WithResult].value
  }
  override def getLeftOp: Val = {
    SWANVal.Simple(getResult, m)
  }
  final override def isStringAllocation: Boolean = false
  final override def isArrayStore: Boolean = false
  final override def isInstanceOfStatement(fact: Val): Boolean = false
  final override def isCast: Boolean = false
  final override def isPhiStatement: Boolean = false
  final override def isMultiArrayAllocation: Boolean = false
  final override def killAtIfStmt(fact: Val, successor: Statement): Boolean = false
  final override def getPhiVals: util.Collection[Val] = util.Collections.emptyList
  final override def getStartLineNumber: Int = if (getPosition.nonEmpty) getPosition.get.line else -1
  final override def getStartColumnNumber: Int = if (getPosition.nonEmpty) getPosition.get.col else -1
  final override def getEndLineNumber: Int = getStartLineNumber
  final override def getEndColumnNumber: Int = getStartColumnNumber
  final override def isCatchStmt: Boolean = false
}

object SWANStatement {
  // *** OPERATORS ***
  case class FieldWrite(val opDef: CanOperatorDef, val inst: Operator.fieldWrite,
                        val m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getWrittenField: Field = ???
    override def isFieldWriteWithBase(base: Val): Boolean = ???
    override def getLoadedField: Field = ???
    override def isFieldLoadWithBase(base: Val): Boolean = ???
    override def getLeftOp: Val = ???
    override def getRightOp: Val = ???
    override def isFieldStore: Boolean = ???
    override def isFieldLoad: Boolean = ???
    override def getFieldStore: Pair[Val, Field] = ???
    override def getFieldLoad: Pair[Val, Field] = ???
  }
  case class FieldLoad(val opDef: CanOperatorDef, val inst: Operator.fieldRead,
                       val m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getWrittenField: Field = ???
    override def isFieldWriteWithBase(base: Val): Boolean = ???
    override def getLoadedField: Field = ???
    override def isFieldLoadWithBase(base: Val): Boolean = ???
    override def getLeftOp: Val = ???
    override def getRightOp: Val = ???
    override def isFieldStore: Boolean = ???
    override def isFieldLoad: Boolean = ???
    override def getFieldStore: Pair[Val, Field] = ???
    override def getFieldLoad: Pair[Val, Field] = ???
  }
  case class Assign(val opDef: CanOperatorDef, val inst: Operator.assign,
                    val m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = ???
    override def isIdentityStmt: Boolean = ???
  }
  case class ArrayLoad(val opDef: CanOperatorDef, val inst: Operator.arrayRead,
                       val m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = ???
    override def isArrayLoad: Boolean = ???
    override def getArrayBase: Pair[boomerang.scene.Val,Integer] = ???
  }
  case class StaticFieldLoad(val opDef: CanOperatorDef, val inst: Operator.singletonRead,
                             val m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def containsStaticFieldAccess(): Boolean = ???
    override def getWrittenField: Field = ???
    override def isFieldWriteWithBase(base: Val): Boolean = ???
    override def getLoadedField: Field = ???
    override def isFieldLoadWithBase(base: Val): Boolean = ???
    override def getLeftOp: Val = ???
    override def getRightOp: Val = ???
    override def isFieldStore: Boolean = ???
    override def isFieldLoad: Boolean = ???
    override def getFieldStore: Pair[Val, Field] = ???
    override def getFieldLoad: Pair[Val, Field] = ???
    override def isStaticFieldLoad: Boolean = ???
    override def isStaticFieldStore: Boolean = ???
    override def getStaticField: StaticFieldVal = ???
  }
  case class StaticFieldStore(val opDef: CanOperatorDef, val inst: Operator.singletonWrite,
                              val m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def containsStaticFieldAccess(): Boolean = ???
    override def getWrittenField: Field = ???
    override def isFieldWriteWithBase(base: Val): Boolean = ???
    override def getLoadedField: Field = ???
    override def isFieldLoadWithBase(base: Val): Boolean = ???
    override def getLeftOp: Val = ???
    override def getRightOp: Val = ???
    override def isFieldStore: Boolean = ???
    override def isFieldLoad: Boolean = ???
    override def getFieldStore: Pair[Val, Field] = ???
    override def getFieldLoad: Pair[Val, Field] = ???
    override def isStaticFieldLoad: Boolean = ???
    override def isStaticFieldStore: Boolean = ???
    override def getStaticField: StaticFieldVal = ???
  }
  case class Allocation(val opDef: CanOperatorDef, val inst: Operator.neww,
                        val m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = ???
  }
  case class Literal(val opDef: CanOperatorDef, val inst: Operator.literal,
                     val m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = ???
  }
  case class DynamicFunctionRef(val opDef: CanOperatorDef, val inst: Operator.dynamicRef,
                                val m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = ???
  }
  case class BuiltinFunctionRef(val opDef: CanOperatorDef, val inst: Operator.builtinRef,
                                val m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = ???
  }
  case class FunctionRef(val opDef: CanOperatorDef, val inst: Operator.functionRef,
                         val m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = ???
  }
  case class ApplyFunctionRef(val opDef: CanOperatorDef, val inst: Operator.apply,
                              val m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def containsInvokeExpr(): Boolean = true
    override def getRightOp: Val = ???
    override def getInvokeExpr: InvokeExpr = new SWANInvokeExpr(this, m)
    def getFunctionRef: Val = m.allValues(inst.functionRef.name)
  }
  case class BinaryOperation(val opDef: CanOperatorDef, val inst: Operator.binaryOp,
                             val m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = SWANVal.BinaryExpr(inst.result.tpe,
      m.delegate.getSymbol(inst.lhs.name), m.delegate.getSymbol(inst.rhs.name), inst.operation, m)
  }
  case class UnaryOperation(val opDef: CanOperatorDef, val inst: Operator.unaryOp,
                            val m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = SWANVal.UnaryExpr(inst.result.tpe, m.delegate.getSymbol(inst.operand.name), inst.operation, m)
  }
  case class ConditionalFatalError(val opDef: CanOperatorDef, val inst: Operator.condFail,
                                   val m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m)
  // *** TERMINATORS ***
  case class Branch(val termDef: CanTerminatorDef, val inst: Terminator.br_can,
                    val m: SWANMethod) extends SWANStatement(CanInstructionDef.terminator(termDef), m)
  case class ConditionalBranch(val termDef: CanTerminatorDef, val inst: Terminator.brIf_can,
                    val m: SWANMethod) extends SWANStatement(CanInstructionDef.terminator(termDef), m) {
    val ifStmt = new SWANIfStatement(this)
    override def getRightOp: Val = m.allValues(inst.cond.name)
    override def isIfStmt: Boolean = true
    override def getIfStmt: IfStatement = ifStmt
  }
  case class Return(val termDef: CanTerminatorDef, val inst: Terminator.ret,
                    val m: SWANMethod) extends SWANStatement(CanInstructionDef.terminator(termDef), m) {
    override def isReturnStmt: Boolean = true
    override def getReturnOp: Val = m.allValues(inst.value.name)
  }
  case class Throw(val termDef: CanTerminatorDef, val inst: Terminator.thro,
                   val m: SWANMethod) extends SWANStatement(CanInstructionDef.terminator(termDef), m) {
    override def isThrowStmt: Boolean = true
  }
  case class Unreachable(val termDef: CanTerminatorDef,
                         val m: SWANMethod) extends SWANStatement(CanInstructionDef.terminator(termDef), m)
  case class Yield(val termDef: CanTerminatorDef, val inst: Terminator.yld,
                   val m: SWANMethod) extends SWANStatement(CanInstructionDef.terminator(termDef), m) {
    override def isReturnStmt: Boolean = true
    override def getReturnOp: Val = m.allValues(inst.yields(0).name)
  }
}