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

import boomerang.scene._
import ca.ualberta.maple.swan.ir.{CanInstructionDef, CanOperatorDef, CanTerminatorDef, Operator, Position, Symbol, Terminator, WithResult}

// toString implementations are not necessarily accurate
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
  case class FieldWrite(opDef: CanOperatorDef, inst: Operator.fieldWrite,
                        m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getWrittenField: Field = new SWANField(inst.field)
    override def isFieldWriteWithBase(base: Val): Boolean = getFieldStore.getX.equals(base)
    override def getLeftOp: Val = ???
    override def getRightOp: Val = m.allValues(inst.value.name)
    override def isAssign: Boolean = true
    override def isFieldStore: Boolean = true
    override def getFieldStore: Pair[Val, Field] = new Pair[Val, Field](m.allValues(inst.obj.name), getWrittenField)
    override def toString: String = {
      getFieldStore.getX.toString + "." + getFieldStore.getY.toString + " = " + getRightOp.toString
    }
  }
  case class FieldLoad(opDef: CanOperatorDef, inst: Operator.fieldRead,
                       m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getLoadedField: Field = new SWANField(inst.field)
    override def isFieldLoadWithBase(base: Val): Boolean = getFieldLoad.getX.equals(base)
    override def getRightOp: Val = ???
    override def isFieldLoad: Boolean = true
    override def getFieldLoad: Pair[Val, Field] = new Pair[Val, Field](m.allValues(inst.obj.name), getLoadedField)
    override def toString: String = {
      getLeftOp.toString + " = " + getFieldLoad.getX.toString + "." + getFieldLoad.getY.toString
    }
  }
  case class Assign(opDef: CanOperatorDef, inst: Operator.assign,
                    m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = m.allValues(inst.from.name)
    override def isIdentityStmt: Boolean = true
    override def toString: String = {
      getLeftOp.toString + " = " + getRightOp.toString
    }
  }
  case class ArrayLoad(opDef: CanOperatorDef, inst: Operator.arrayRead,
                       m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = ???
    override def isArrayLoad: Boolean = true
    override def getArrayBase: Pair[Val,Integer] = ???
    override def toString: String = {
      getLeftOp.toString + " = " + getArrayBase.getX.toString + "[*]"
    }
  }
  case class StaticFieldLoad(opDef: CanOperatorDef, inst: Operator.singletonRead,
                             m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def containsStaticFieldAccess(): Boolean = true
    override def getRightOp: Val = ???
    override def isStaticFieldLoad: Boolean = true
    override def getStaticField: StaticFieldVal = new SWANStaticFieldVal(new SWANField(inst.field), m)
    override def toString: String = {
      getLeftOp.toString + " = " + getStaticField.toString
    }
  }
  case class StaticFieldStore(opDef: CanOperatorDef, inst: Operator.singletonWrite,
                              m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def containsStaticFieldAccess(): Boolean = true
    override def getLeftOp: Val = ???
    override def getRightOp: Val = m.allValues(inst.value.name)
    override def isAssign: Boolean = true
    override def isStaticFieldStore: Boolean = true
    override def getStaticField: StaticFieldVal = new SWANStaticFieldVal(new SWANField(inst.field), m)
    override def toString: String = {
      getStaticField.toString + " = " + getRightOp.toString
    }
  }
  case class Allocation(opDef: CanOperatorDef, inst: Operator.neww,
                        m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = SWANVal.NewExpr(inst.result, m)
    override def toString: String = {
      getLeftOp.toString + " = " + getRightOp.toString
    }
  }
  case class Literal(opDef: CanOperatorDef, inst: Operator.literal,
                     m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = SWANVal.Constant(inst.result, inst.literal, m)
    override def toString: String = {
      getLeftOp.toString + " = " + getRightOp.toString
    }
  }
  case class DynamicFunctionRef(opDef: CanOperatorDef, inst: Operator.dynamicRef,
                                m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = SWANVal.DynamicFunctionRef(inst.result, inst.index, m)
    override def toString: String = {
      getLeftOp.toString + " = " + getRightOp.toString
    }
  }
  case class BuiltinFunctionRef(opDef: CanOperatorDef, inst: Operator.builtinRef,
                                m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = SWANVal.BuiltinFunctionRef(inst.result, inst.name, m)
    override def toString: String = {
      getLeftOp.toString + " = " + getRightOp.toString
    }
  }
  case class FunctionRef(opDef: CanOperatorDef, inst: Operator.functionRef,
                         m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = SWANVal.FunctionRef(inst.result, inst.name, m)
    override def toString: String = {
      getLeftOp.toString + " = " + getRightOp.toString
    }
  }
  case class ApplyFunctionRef(opDef: CanOperatorDef, inst: Operator.apply,
                              m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def containsInvokeExpr(): Boolean = true
    override def getRightOp: Val = null
    override def getInvokeExpr: InvokeExpr = new SWANInvokeExpr(this, m)
    def getFunctionRef: Val = m.allValues(inst.functionRef.name)
    override def toString: String = {
      getLeftOp.toString + " = " + getInvokeExpr.toString
    }
  }
  case class BinaryOperation(opDef: CanOperatorDef, inst: Operator.binaryOp,
                             m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = {
      inst.operation match {
        case ca.ualberta.maple.swan.ir.BinaryOperation.arbitrary =>
          SWANVal.BinaryExpr(inst.result.tpe,
            m.delegate.getSymbol(inst.lhs.name), m.delegate.getSymbol(inst.rhs.name), inst.operation, m)
        case ca.ualberta.maple.swan.ir.BinaryOperation.equals =>
          SWANVal.BinaryExpr(inst.result.tpe,
            m.delegate.getSymbol(inst.lhs.name), m.delegate.getSymbol(inst.rhs.name), inst.operation, m)
        // case operator that transfers operand properties =>
          // TODO ?
      }

    }
    override def toString: String = {
      getLeftOp.toString + " = " + getRightOp.toString
    }
  }
  case class UnaryOperation(opDef: CanOperatorDef, inst: Operator.unaryOp,
                            m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = {
      inst.operation match {
        case ca.ualberta.maple.swan.ir.UnaryOperation.arbitrary =>
          SWANVal.UnaryExpr(inst.result.tpe, m.delegate.getSymbol(inst.operand.name), inst.operation, m)
        // case operator that transfers operand properties =>
          // SWANVal.Simple(inst.result, m)
      }
    }

    override def toString: String = {
      getLeftOp.toString + " = " + getRightOp.toString
    }
  }
  case class ConditionalFatalError(opDef: CanOperatorDef, inst: Operator.condFail,
                                   m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m)
  override def toString: String = {
    "conditional_fail"
  }
  // *** TERMINATORS ***
  case class Branch(termDef: CanTerminatorDef, inst: Terminator.br_can,
                    m: SWANMethod) extends SWANStatement(CanInstructionDef.terminator(termDef), m) {
    override def toString: String = {
      "branch to " + m.getControlFlowGraph.getSuccsOf(this).toString
    }
  }
  case class ConditionalBranch(termDef: CanTerminatorDef, inst: Terminator.brIf_can,
                    m: SWANMethod) extends SWANStatement(CanInstructionDef.terminator(termDef), m) {
    val ifStmt = new SWANIfStatement(this)
    override def getRightOp: Val = m.allValues(inst.cond.name)
    override def isIfStmt: Boolean = true
    override def getIfStmt: IfStatement = ifStmt
    override def toString: String = {
      "if " + getRightOp.toString + ", branch to " + m.getControlFlowGraph.getSuccsOf(this).toString
    }
  }
  case class Return(termDef: CanTerminatorDef, inst: Terminator.ret,
                    m: SWANMethod) extends SWANStatement(CanInstructionDef.terminator(termDef), m) {
    override def isReturnStmt: Boolean = true
    override def getReturnOp: Val = m.allValues(inst.value.name)
    override def toString: String = {
      "return " + getReturnOp.toString
    }
  }
  case class Throw(termDef: CanTerminatorDef, inst: Terminator.thro,
                   m: SWANMethod) extends SWANStatement(CanInstructionDef.terminator(termDef), m) {
    override def isThrowStmt: Boolean = true
    override def toString: String = {
      "throw"
    }
  }
  case class Unreachable(termDef: CanTerminatorDef,
                         m: SWANMethod) extends SWANStatement(CanInstructionDef.terminator(termDef), m) {
    override def toString: String = {
      "unreachable"
    }
  }
  case class Yield(termDef: CanTerminatorDef, inst: Terminator.yld,
                   m: SWANMethod) extends SWANStatement(CanInstructionDef.terminator(termDef), m) {
    override def isReturnStmt: Boolean = true
    override def getReturnOp: Val = m.allValues(inst.yields(0).name)
    override def toString: String = {
      "yield (return) " + getReturnOp.toString
    }
  }
}