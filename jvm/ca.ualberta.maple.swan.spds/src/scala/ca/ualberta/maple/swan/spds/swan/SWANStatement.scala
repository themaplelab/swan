/*
 * Copyright (c) 2021 the SWAN project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This software has dependencies with other licenses.
 * See https://github.com/themaplelab/swan/doc/LICENSE.md.
 */

package ca.ualberta.maple.swan.spds.swan

import ca.ualberta.maple.swan.ir.{CanInstructionDef, CanOperatorDef, CanTerminatorDef, Operator, Position, Symbol, Terminator, WithResult}
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene._

trait SWANStatement extends Statement {

  def getDelegate: CanInstructionDef

  def getResult: Symbol = {
    getDelegate.asInstanceOf[CanInstructionDef.operator].operatorDef.operator.asInstanceOf[WithResult].value
  }

  def getPosition: Option[Position] = {
    getDelegate match {
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

  case class FieldStore(opDef: CanOperatorDef, inst: Operator.fieldWrite,
                        m: SWANMethod) extends FieldStoreStatement(m) with SWANStatement {

    private val delegate: CanInstructionDef = CanInstructionDef.operator(opDef)

    override def getDelegate: CanInstructionDef = delegate

    override def getWrittenField: Field = new SWANField(inst.field)

    override def isFieldWriteWithBase(base: Val): Boolean = getFieldStore.x.equals(base)

    override def getFieldStore: Pair[Val, Field] = new Pair[Val, Field](m.allValues(inst.obj.name), getWrittenField)

    override def lhs: Val = Val.zero

    override def rhs: Val = m.allValues(inst.value.name)

    override def toString: String = {
      if (m.hasSwirlSource) s"i${m.swirlLineNum(opDef)}" else
        s"<fwi><l>${getFieldStore.x.toString}.${getFieldStore.y}</l><r>$rhs</r></fwi>"
    }

    override def uses(value: Val): Boolean = getFieldStore.x.equals(value)
  }

  case class FieldLoad(opDef: CanOperatorDef, inst: Operator.fieldRead,
                       m: SWANMethod) extends FieldLoadStatement(m) with SWANStatement {

    private val delegate: CanInstructionDef = CanInstructionDef.operator(opDef)

    override def getDelegate: CanInstructionDef = delegate

    override def getLoadedField: Field = new SWANField(inst.field)

    override def isFieldLoadWithBase(base: Val): Boolean = getFieldLoad.x.equals(base)

    override def getFieldLoad: Pair[Val, Field] = new Pair[Val, Field](m.allValues(inst.obj.name), getLoadedField)

    override def lhs: Val = m.allValues(getResult.ref.name)

    override def rhs: Val = Val.zero

    override def toString: String = {
      if (m.hasSwirlSource) s"i${m.swirlLineNum(opDef)}" else
        s"<fli><l>$lhs</l><r>${getFieldLoad.x}.${getFieldLoad.y}</r></fli>"
    }

    override def uses(value: Val): Boolean = lhs.equals(value)
  }

  case class Assignment(opDef: CanOperatorDef, inst: Operator.assign,
                        m: SWANMethod) extends AssignStatement(m) with SWANStatement {

    private val delegate: CanInstructionDef = CanInstructionDef.operator(opDef)

    override def getDelegate: CanInstructionDef = delegate

    override def lhs: Val = m.allValues(getResult.ref.name)

    override def rhs: Val = m.allValues(inst.from.name)

    override def toString: String = {
      if (m.hasSwirlSource) s"i${m.swirlLineNum(opDef)}" else
        s"<asi><l>$lhs</l><r>$rhs</r></asi>"
    }

    override def uses(value: Val): Boolean = lhs.equals(value)
  }

  case class StaticFieldLoad(opDef: CanOperatorDef, inst: Operator.singletonRead,
                             m: SWANMethod) extends StaticFieldLoadStatement(m) with SWANStatement {

    private val delegate: CanInstructionDef = CanInstructionDef.operator(opDef)
    private val staticField = new SWANStaticFieldVal(new SWANField(inst.tpe + "." + inst.field), m)

    override def getDelegate: CanInstructionDef = delegate

    override def lhs: Val = m.allValues(getResult.ref.name)

    override def rhs: Val = staticField

    override def getStaticField: StaticFieldVal = staticField

    override def toString: String = {
      if (m.hasSwirlSource) s"i${m.swirlLineNum(opDef)}" else s"<sfli><l>$lhs</l><r>$rhs</r></sfli>"
    }

    override def uses(value: Val): Boolean = lhs.equals(value)
  }

  case class StaticFieldStore(opDef: CanOperatorDef, inst: Operator.singletonWrite,
                              m: SWANMethod) extends StaticFieldStoreStatement(m) with SWANStatement {

    private val delegate: CanInstructionDef = CanInstructionDef.operator(opDef)
    private val staticField = new SWANStaticFieldVal(new SWANField(inst.tpe + "." + inst.field), m)

    override def getDelegate: CanInstructionDef = delegate

    override def lhs: Val = staticField

    override def rhs: Val = m.allValues(inst.value.name)

    override def getStaticField: StaticFieldVal = staticField

    override def toString: String = {
      if (m.hasSwirlSource) s"i${m.swirlLineNum(opDef)}" else s"<sfsi><l>$lhs</l><r>$rhs</r></sfsi>"
    }

    override def uses(value: Val): Boolean = false
  }

  case class Allocation(opDef: CanOperatorDef, inst: Operator.neww,
                        m: SWANMethod) extends NewStatement(m) with SWANStatement {

    private val delegate: CanInstructionDef = CanInstructionDef.operator(opDef)

    override def getDelegate: CanInstructionDef = delegate

    override def lhs: Val = m.allValues(getResult.ref.name)

    override def rhs: Val = m.newValues(inst.result.ref.name)

    override def toString: String = {
      if (inst.result.ref.name == "nop") {
        s"f${m.swirlLineNum(m.delegate)}"
      } else if (m.hasSwirlSource) {
        s"i${m.swirlLineNum(opDef)}"
      } else {
        s"<ali><l>$lhs</l><r>$rhs</r></ali>"
      }
    }

    override def uses(value: Val): Boolean = false
  }

  case class Literal(opDef: CanOperatorDef, inst: Operator.literal,
                     m: SWANMethod) extends LiteralStatement(m) with SWANStatement {

    private val delegate: CanInstructionDef = CanInstructionDef.operator(opDef)

    override def getDelegate: CanInstructionDef = delegate

    override def lhs: Val = m.allValues(getResult.ref.name)

    override def rhs: Val = m.addVal(SWANVal.Constant(inst.result, inst.literal, m))

    override def toString: String = {
      if (m.hasSwirlSource) s"i$m.swirlLineNum(opDef)}" else s"<lii><l>$lhs</l><r>$rhs</r></lii>"
    }

    override def uses(value: Val): Boolean = false
  }

  case class DynamicFunctionRef(opDef: CanOperatorDef, inst: Operator.dynamicRef,
                                m: SWANMethod) extends NewStatement(m) with SWANStatement {

    private val delegate: CanInstructionDef = CanInstructionDef.operator(opDef)

    override def getDelegate: CanInstructionDef = delegate

    override def lhs: Val = m.allValues(getResult.ref.name)

    override def rhs: Val = m.addVal(SWANVal.DynamicFunctionRef(inst.result, inst.index, m))

    override def toString: String = {
      if (m.hasSwirlSource) s"i${m.swirlLineNum(opDef)}" else s"<dfri><l>$lhs</l><r>$rhs</r></dfri>"
    }

    override def uses(value: Val): Boolean = false
  }

  case class BuiltinFunctionRef(opDef: CanOperatorDef, inst: Operator.builtinRef,
                                m: SWANMethod) extends NewStatement(m) with SWANStatement {

    private val delegate: CanInstructionDef = CanInstructionDef.operator(opDef)

    override def getDelegate: CanInstructionDef = delegate

    override def lhs: Val = m.allValues(getResult.ref.name)

    override def rhs: Val = m.addVal(SWANVal.BuiltinFunctionRef(inst.result, inst.name, m))

    override def toString: String = {
      if (m.hasSwirlSource) s"i${m.swirlLineNum(opDef)}" else s"<bfri><l>$lhs</l><r>$rhs</r></bfri>"
    }

    override def uses(value: Val): Boolean = false
  }

  case class FunctionRef(opDef: CanOperatorDef, inst: Operator.functionRef,
                         m: SWANMethod) extends NewStatement(m) with SWANStatement {

    private val delegate: CanInstructionDef = CanInstructionDef.operator(opDef)

    override def getDelegate: CanInstructionDef = delegate

    override def lhs: Val = m.allValues(getResult.ref.name)

    override def rhs: Val = m.addVal(SWANVal.FunctionRef(inst.result, inst.name, m))

    override def toString: String = {
      if (m.hasSwirlSource) s"i${m.swirlLineNum(opDef)}" else s"<fri><l>$lhs</l><r>$rhs</r></fri>"
    }

    override def uses(value: Val): Boolean = false
  }

  case class CallSite(opDef: CanOperatorDef, inst: Operator.apply,
                      m: SWANMethod) extends CallSiteStatement(m) with SWANStatement {

    private val delegate: CanInstructionDef = CanInstructionDef.operator(opDef)
    private val invokeExpr = new SWANInvokeExpr(this, m)

    override def getDelegate: CanInstructionDef = delegate

    override def lhs: Val = m.allValues(getResult.ref.name)

    override def rhs: Val = Val.zero

    override def getInvokeExpr: InvokeExpr = invokeExpr

    def updateInvokeExpr(name: String, cg: SWANCallGraph): Unit = {
      invokeExpr.updateResolvedMethod(name, cg)
    }

    def getFunctionRef: Val = m.allValues(inst.functionRef.name)

    override def isParameter(value: Val): Boolean = invokeExpr.args.contains(value)

    override def toString: String = {
      if (m.hasSwirlSource) s"i${m.swirlLineNum(opDef)}" else s"<api><l>$lhs</l><ie>$getInvokeExpr</ie></api>"
    }

    override def uses(value: Val): Boolean = isParameter(value)
  }

  // TODO
  case class CondFail(opDef: CanOperatorDef, inst: Operator.condFail,
                      m: SWANMethod) extends Statement(m) with SWANStatement {

    private val delegate: CanInstructionDef = CanInstructionDef.operator(opDef)

    override def getDelegate: CanInstructionDef = delegate

    override def toString: String = {
      if (m.hasSwirlSource) s"i${m.swirlLineNum(opDef)}" else "<cfaili></<cfaili>"
    }

    override def uses(value: Val): Boolean = false

  }

  // *** TERMINATORS ***

  case class Branch(termDef: CanTerminatorDef, inst: Terminator.br_can,
                    m: SWANMethod) extends Statement(m) with SWANStatement {

    private val delegate: CanInstructionDef = CanInstructionDef.terminator(termDef)

    override def getDelegate: CanInstructionDef = delegate

    override def toString: String = {
      if (m.hasSwirlSource) s"i${m.swirlLineNum(termDef)}" else
        s"<bri>${m.getCFG.getSuccsOf(this)}</bri>"
    }

    override def uses(value: Val): Boolean = false
  }

  case class CondBranch(termDef: CanTerminatorDef, inst: Terminator.brIf_can,
                        m: SWANMethod) extends Statement(m) with SWANStatement {

    private val delegate: CanInstructionDef = CanInstructionDef.terminator(termDef)

    override def getDelegate: CanInstructionDef = delegate

    override def toString: String = {
      if (m.hasSwirlSource) s"i${m.swirlLineNum(termDef)}" else
        s"<cbri>${m.getCFG.getSuccsOf(this)}</cbri>"
    }

    override def uses(value: Val): Boolean = false
  }

  case class Return(termDef: CanTerminatorDef, inst: Terminator.ret,
                    m: SWANMethod) extends ReturnStatement(m) with SWANStatement {

    private val delegate: CanInstructionDef = CanInstructionDef.terminator(termDef)

    override def getDelegate: CanInstructionDef = delegate

    override def getReturnOp: Val = m.allValues(inst.value.name)

    override def toString: String = {
      if (m.hasSwirlSource) s"i${m.swirlLineNum(termDef)}" else s"<reti>$getReturnOp</reti>"
    }

    override def uses(value: Val): Boolean = false
  }

  case class Throw(termDef: CanTerminatorDef, inst: Terminator.thro,
                   m: SWANMethod) extends ThrowStatement(m) with SWANStatement {

    private val delegate: CanInstructionDef = CanInstructionDef.terminator(termDef)

    override def getDelegate: CanInstructionDef = delegate

    override def toString: String = {
      if (m.hasSwirlSource) s"i${m.swirlLineNum(termDef)}" else "<throwi></throwi>"
    }

    override def uses(value: Val): Boolean = false
  }

  case class Unreachable(termDef: CanTerminatorDef,
                         m: SWANMethod) extends Statement(m) with SWANStatement {

    private val delegate: CanInstructionDef = CanInstructionDef.terminator(termDef)

    override def getDelegate: CanInstructionDef = delegate

    override def toString: String = {
      if (m.hasSwirlSource) s"i${m.swirlLineNum(termDef)}" else "<unri></unri>"
    }

    override def uses(value: Val): Boolean = false
  }

  case class Yield(termDef: CanTerminatorDef, inst: Terminator.yld,
                   m: SWANMethod) extends ReturnStatement(m) with SWANStatement {

    private val delegate: CanInstructionDef = CanInstructionDef.terminator(termDef)

    override def getDelegate: CanInstructionDef = delegate

    override def getReturnOp: Val = m.allValues(inst.yields(0).name)

    override def toString: String = {
      if (m.hasSwirlSource) s"i${m.swirlLineNum(termDef)}" else s"<yi>${getReturnOp}</yi>"
    }

    override def uses(value: Val): Boolean = false
  }
}