/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2021 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.spds.structures

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
  private def getResult: Symbol = {
    delegate.asInstanceOf[CanInstructionDef.operator].operatorDef.operator.asInstanceOf[WithResult].value
  }
  override def getLeftOp: Val = {
    m.allValues(getResult.ref.name)
  }
  override def isStringAllocation: Boolean = false
  final override def isArrayLoad: Boolean = false
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

  override def hashCode: Int = {
    val prime = 31
    var result = 1
    result = prime * result + delegate.hashCode
    result = prime * result + m.hashCode
    result
  }
  override def equals(obj: Any): Boolean = {
    obj match {
      case s: SWANStatement => s.delegate.equals(this.delegate) && s.method.equals(this.method)
      case _ => false
    }
  }
  def getPosition: Option[Position] = {
    delegate match {
      case CanInstructionDef.operator(operatorDef) => operatorDef.position
      case CanInstructionDef.terminator(terminatorDef) => terminatorDef.position
    }
  }
  def getPositionString: String = {
    val pos = getPosition
    if (pos.nonEmpty) {
      pos.get.toString
    } else {
      ""
    }
  }
}

object SWANStatement {
  // *** OPERATORS ***
  // TODO: Strong write handling
  case class FieldWrite(opDef: CanOperatorDef, inst: Operator.fieldWrite,
                        m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getWrittenField: Field = new SWANField(inst.field)
    override def isFieldWriteWithBase(base: Val): Boolean = getFieldStore.getX.equals(base)
    override def getLeftOp: Val = Val.zero()
    override def getRightOp: Val = m.allValues(inst.value.name)
    override def isAssign: Boolean = true
    override def isFieldStore: Boolean = true
    override def getFieldStore: Pair[Val, Field] = new Pair[Val, Field](m.allValues(inst.obj.name), getWrittenField)
    override def toString: String = {
      if (m.hasSwirlSource) {
        "i" + m.swirlLineNum(opDef).toString
      } else {
        "<fwi><l>" + getFieldStore.getX.toString + "." + getFieldStore.getY.toString + "</l><r>" + getRightOp.toString + "</r></fwi>"
      }
    }
  }
  case class FieldLoad(opDef: CanOperatorDef, inst: Operator.fieldRead,
                       m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getLoadedField: Field = new SWANField(inst.field)
    override def isFieldLoadWithBase(base: Val): Boolean = getFieldLoad.getX.equals(base)
    override def getRightOp: Val = Val.zero()
    override def isFieldLoad: Boolean = true
    override def getFieldLoad: Pair[Val, Field] = new Pair[Val, Field](m.allValues(inst.obj.name), getLoadedField)
    override def toString: String = {
      if (m.hasSwirlSource) {
        "i" + m.swirlLineNum(opDef).toString
      } else {
        "<fli><l>" + getLeftOp.toString + "</l><r>" + getFieldLoad.getX.toString + "." + getFieldLoad.getY.toString + "</r></fli>"
      }
    }
  }
  case class Assign(opDef: CanOperatorDef, inst: Operator.assign,
                    m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = m.allValues(inst.from.name)
    override def isIdentityStmt: Boolean = false // ?
    override def toString: String = {
      if (m.hasSwirlSource) {
        "i" + m.swirlLineNum(opDef).toString
      } else {
        "<asi><l>" + getLeftOp.toString + "</l><r>" + getRightOp.toString + "</r></asi>"
      }
    }
  }
  // TODO
  case class StaticFieldLoad(opDef: CanOperatorDef, inst: Operator.singletonRead,
                             m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    val staticField = new SWANStaticFieldVal(new SWANField(inst.tpe + "." + inst.field), m)
    override def containsStaticFieldAccess(): Boolean = true
    override def getRightOp: Val = staticField
    override def isStaticFieldLoad: Boolean = true
    override def getStaticField: StaticFieldVal = staticField
    override def toString: String = {
      if (m.hasSwirlSource) {
        "i" + m.swirlLineNum(opDef).toString
      } else {
        "<sfli><l>" + getLeftOp.toString + "</l><r>" + getRightOp.toString + "</r></sfli>"
      }
    }
  }
  // TODO
  case class StaticFieldStore(opDef: CanOperatorDef, inst: Operator.singletonWrite,
                              m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    val staticField = new SWANStaticFieldVal(new SWANField(inst.tpe + "." + inst.field), m)
    override def containsStaticFieldAccess(): Boolean = true
    override def getLeftOp: Val = staticField
    override def getRightOp: Val = m.allValues(inst.value.name)
    override def isAssign: Boolean = true
    override def isStaticFieldStore: Boolean = true
    override def getStaticField: StaticFieldVal = staticField
    override def toString: String = {
      if (m.hasSwirlSource) {
        "i" + m.swirlLineNum(opDef).toString
      } else {
        "<sfsi><l>" + getLeftOp.toString + "</l><r>" + getRightOp.toString + "</r></sfsi>"
      }
    }
  }
  case class Allocation(opDef: CanOperatorDef, inst: Operator.neww,
                        m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = m.newValues(inst.result.ref.name)
    override def toString: String = {
      if (inst.result.ref.name == "nop") {
        "f" + m.swirlLineNum(m.delegate)
      } else if (m.hasSwirlSource) {
        "i" + m.swirlLineNum(opDef).toString
      } else {
        "<ali><l>" + getLeftOp.toString + "</l><r>" + getRightOp.toString + "</r></ali>"
      }
    }
  }
  case class Literal(opDef: CanOperatorDef, inst: Operator.literal,
                     m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def isStringAllocation: Boolean = inst.literal match {
      case ca.ualberta.maple.swan.ir.Literal.string(_) => true
      case _ => false
    }
    override def getRightOp: Val = m.addVal(SWANVal.Constant(inst.result, inst.literal, m))
    override def toString: String = {
      if (m.hasSwirlSource) {
        "i" + m.swirlLineNum(opDef).toString
      } else {
        "<lii><l>" + getLeftOp.toString + "</l><r>" + getRightOp.toString + "</r></lii>"
      }
    }
  }
  case class DynamicFunctionRef(opDef: CanOperatorDef, inst: Operator.dynamicRef,
                                m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = m.addVal(SWANVal.DynamicFunctionRef(inst.result, inst.index, m))
    override def toString: String = {
      if (m.hasSwirlSource) {
        "i" + m.swirlLineNum(opDef).toString
      } else {
        "<dfri><l>" + getLeftOp.toString + "</l><r>" + getRightOp.toString + "</r></dfri>"
      }
    }
  }
  case class BuiltinFunctionRef(opDef: CanOperatorDef, inst: Operator.builtinRef,
                                m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = m.addVal(SWANVal.BuiltinFunctionRef(inst.result, inst.name, m))
    override def toString: String = {
      if (m.hasSwirlSource) {
        "i" + m.swirlLineNum(opDef).toString
      } else {
        "<bfri><l>" + getLeftOp.toString + "</l><r>" + getRightOp.toString + "</r></bfri>"
      }
    }
  }
  case class FunctionRef(opDef: CanOperatorDef, inst: Operator.functionRef,
                         m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getRightOp: Val = m.addVal(SWANVal.FunctionRef(inst.result, inst.name, m))
    override def toString: String = {
      if (m.hasSwirlSource) {
        "i" + m.swirlLineNum(opDef).toString
      } else {
        "<fri><l>" + getLeftOp.toString + "</l><r>" + getRightOp.toString + "</r></fri>"
      }
    }
  }
  case class ApplyFunctionRef(opDef: CanOperatorDef, inst: Operator.apply,
                              m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def containsInvokeExpr(): Boolean = true
    override def getRightOp: Val = Val.zero()
    override def getInvokeExpr: InvokeExpr = new SWANInvokeExpr(this, m)
    def getFunctionRef: Val = m.allValues(inst.functionRef.name)
    override def toString: String = {
      if (m.hasSwirlSource) {
        "i" + m.swirlLineNum(opDef).toString
      } else {
        "<api><l>" + getLeftOp.toString + "</l><ie>" + getInvokeExpr.toString + "</ie></api>"
      }
    }
  }
  // TODO
  case class ConditionalFatalError(opDef: CanOperatorDef, inst: Operator.condFail,
                                   m: SWANMethod) extends SWANStatement(CanInstructionDef.operator(opDef), m) {
    override def getLeftOp: Val = ???
    override def getRightOp: Val = ??? // T0D0
    override def toString: String = {
      if (m.hasSwirlSource) {
        "i" + m.swirlLineNum(opDef).toString
      } else {
        "<cfaili></<cfaili>"
      }
    }
  }
  // *** TERMINATORS ***
  case class Branch(termDef: CanTerminatorDef, inst: Terminator.br_can,
                    m: SWANMethod) extends SWANStatement(CanInstructionDef.terminator(termDef), m) {
    override def toString: String = {
      if (m.hasSwirlSource) {
        "i" + m.swirlLineNum(termDef).toString
      } else {
        "<bri>" + m.getControlFlowGraph.getSuccsOf(this).toString + "</bri>"
      }
    }
  }
  case class ConditionalBranch(termDef: CanTerminatorDef, inst: Terminator.brIf_can,
                               m: SWANMethod) extends SWANStatement(CanInstructionDef.terminator(termDef), m) {
    val ifStmt = new SWANIfStatement(this)
    override def getRightOp: Val = m.allValues(inst.cond.name)
    override def isIfStmt: Boolean = true
    override def getIfStmt: IfStatement = ifStmt
    override def toString: String = {
      if (m.hasSwirlSource) {
        "i" + m.swirlLineNum(termDef).toString
      } else {
        "<cbri><if>" + getRightOp.toString + "</if>" + m.getControlFlowGraph.getSuccsOf(this).toString + "</cbri>"
      }
    }
  }
  case class Return(termDef: CanTerminatorDef, inst: Terminator.ret,
                    m: SWANMethod) extends SWANStatement(CanInstructionDef.terminator(termDef), m) {
    override def getLeftOp: Val = ???
    override def getRightOp: Val = ???
    override def isReturnStmt: Boolean = true
    override def getReturnOp: Val = m.allValues(inst.value.name)
    override def toString: String = {
      if (m.hasSwirlSource) {
        "i" + m.swirlLineNum(termDef).toString
      } else {
        "<reti>" + getReturnOp.toString + "</reti>"
      }
    }
  }
  case class Throw(termDef: CanTerminatorDef, inst: Terminator.thro,
                   m: SWANMethod) extends SWANStatement(CanInstructionDef.terminator(termDef), m) {
    override def getLeftOp: Val = ???
    override def getRightOp: Val = m.allValues(inst.value.name)
    override def isThrowStmt: Boolean = true
    override def toString: String = {
      if (m.hasSwirlSource) {
        "i" + m.swirlLineNum(termDef).toString
      } else {
        "<throwi>" + getRightOp.toString + "</throwi>"
      }
    }
  }
  case class Unreachable(termDef: CanTerminatorDef,
                         m: SWANMethod) extends SWANStatement(CanInstructionDef.terminator(termDef), m) {
    override def getLeftOp: Val = ???
    override def getRightOp: Val = ???
    override def toString: String = {
      if (m.hasSwirlSource) {
        "i" + m.swirlLineNum(termDef).toString
      } else {
        "<unri></unri>"
      }
    }
  }
  case class Yield(termDef: CanTerminatorDef, inst: Terminator.yld,
                   m: SWANMethod) extends SWANStatement(CanInstructionDef.terminator(termDef), m) {
    override def getLeftOp: Val = ???
    override def getRightOp: Val = ???
    override def isReturnStmt: Boolean = true
    override def getReturnOp: Val = m.allValues(inst.yields(0).name)
    override def toString: String = {
      if (m.hasSwirlSource) {
        "i" + m.swirlLineNum(termDef).toString
      } else {
        "<yi>" + getReturnOp.toString + "</yi>"
      }
    }
  }
}