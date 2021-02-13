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
import ca.ualberta.maple.swan.ir.{InstructionDef, Literal, Operator, Position, Terminator}

class SWANStatement(val inst: InstructionDef, method: SWANMethod) extends Statement(method) {

  override def containsStaticFieldAccess(): Boolean = {
    inst.isInstanceOf[InstructionDef.canOperator] &&
      (inst.asInstanceOf[InstructionDef.canOperator]
        .operatorDef.operator.isInstanceOf[Operator.singletonWrite] ||
      inst.asInstanceOf[InstructionDef.canOperator]
        .operatorDef.operator.isInstanceOf[Operator.singletonRead])
  }

  override def containsInvokeExpr(): Boolean = {
    inst.isInstanceOf[InstructionDef.canOperator] &&
      inst.asInstanceOf[InstructionDef.canOperator]
        .operatorDef.operator.isInstanceOf[Operator.apply]
  }

  override def getWrittenField: Field = {
    inst match {
      case InstructionDef.canOperator(operatorDef) => {
        operatorDef.operator match {
          case Operator.singletonWrite(_, _, field) => new SWANField(field)
          case Operator.fieldWrite(_, _, field, _) => new SWANField(field)
          case _ => throw new RuntimeException("getWrittenField called on non-field instruction")
        }
      }
      case _ => throw new RuntimeException("getWrittenField called on non-field instruction")
    }
  }

  override def isFieldWriteWithBase(base: Val): Boolean = {
    if (isAssign && isFieldStore) {
      getFieldStore.getX.equals(base)
    } else {
      false
    }
  }

  override def getLoadedField: Field = {
    inst match {
      case InstructionDef.canOperator(operatorDef) => {
        operatorDef.operator match {
          case Operator.singletonRead(_, _, field) => new SWANField(field)
          case Operator.fieldRead(_, _, _, field, _) => new SWANField(field)
          case _ => throw new RuntimeException("getLoadedField called on non-field instruction")
        }}
      case _ =>
        throw new RuntimeException("getLoadedField called on non-field instruction")
    }
  }

  override def isFieldLoadWithBase(base: Val): Boolean = {
    if (isAssign && isFieldLoad) {
      getFieldLoad.getX.equals(base)
    } else {
      false
    }
  }

  override def isAssign: Boolean = {
    inst.isInstanceOf[InstructionDef.canOperator] &&
      inst.asInstanceOf[InstructionDef.canOperator]
        .operatorDef.operator.isInstanceOf[Operator.assign]
  }

  override def getLeftOp: Val = {
    if (!isAssign) {
      throw new RuntimeException("getLeftOp called on non-assign statement")
    }
    val assign = inst.asInstanceOf[InstructionDef.canOperator]
      .operatorDef.operator.asInstanceOf[Operator.assign]
    new SWANVal(assign.result)
  }

  override def getRightOp: Val = {
    if (!isAssign) {
      throw new RuntimeException("getRightOp called on non-assign statement")
    }
    val assign = inst.asInstanceOf[InstructionDef.canOperator]
      .operatorDef.operator.asInstanceOf[Operator.assign]
    new SWANVal(method.delegate.getSymbol(assign.from.name))
  }

  override def isInstanceOfStatement(fact: Val): Boolean = false

  override def isCast: Boolean = false

  override def isPhiStatement: Boolean = false

  override def getInvokeExpr: InvokeExpr = {
    if (!containsInvokeExpr) {
      throw new RuntimeException("getInvokeExpr called on non-invoke statement")
    }
    new SWANInvokeExpr(inst.asInstanceOf[InstructionDef.canOperator].asInstanceOf[Operator.apply], method)
  }

  override def isReturnStmt: Boolean = {
    inst match {
      case terminator: InstructionDef.canTerminator => {
        terminator.terminatorDef.terminator match {
          case Terminator.ret(_) => true
          case Terminator.yld(_, _, _) => true
          case _ => false
        }
      }
      case _ =>
        false
    }
  }

  override def isThrowStmt: Boolean = {
    inst.isInstanceOf[InstructionDef.canTerminator] &&
      inst.asInstanceOf[InstructionDef.canTerminator]
        .terminatorDef.terminator.isInstanceOf[Terminator.thro]
  }

  override def isIfStmt: Boolean = ???

  override def getIfStmt: IfStatement = ???

  override def getReturnOp: Val = {
    if (!isReturnStmt) {
      throw new RuntimeException("getReturnOp called on non-return statement")
    }
    val sym: ca.ualberta.maple.swan.ir.Symbol = {
      inst.asInstanceOf[InstructionDef.canTerminator].terminatorDef.terminator match {
        case Terminator.ret(value) => method.delegate.getSymbol(value.name)
        // yield can have multiple operands, for now just use first one
        case Terminator.yld(yields, _, _) => method.delegate.getSymbol(yields(0).name)
        case _ => throw new RuntimeException("unexpected: check isReturnStmt implementation")
      }
    }
    new SWANVal(sym)
  }

  override def isMultiArrayAllocation: Boolean = false

  override def isStringAllocation: Boolean = {
    inst match {
      case InstructionDef.canOperator(operatorDef) => {
        operatorDef.operator match {
          case lit: Operator.literal => {
            lit.literal match {
              case Literal.string(_) => true
              case _ => false
            }
          }
          case _ => false
        }
      }
      case InstructionDef.canTerminator(_) => false
    }
  }

  override def isFieldStore: Boolean = {
    inst match {
      case InstructionDef.canOperator(operatorDef) => {
        operatorDef.operator match {
          case Operator.fieldWrite(_, _, _, _) => true
          case _ => false
        }
      }
      case InstructionDef.canTerminator(_) => false
    }
  }

  override def isArrayStore: Boolean = false

  override def isArrayLoad: Boolean = {
    inst.isInstanceOf[InstructionDef.canOperator] &&
      inst.asInstanceOf[InstructionDef.canOperator]
        .operatorDef.operator.isInstanceOf[Operator.arrayRead]
  }

  override def isFieldLoad: Boolean = {
    inst match {
      case InstructionDef.canOperator(operatorDef) => {
        operatorDef.operator match {
          case Operator.fieldRead(_, _, _, _, _) => true
          case _ => false
        }
      }
      case InstructionDef.canTerminator(_) => false
    }
  }

  override def isIdentityStmt: Boolean = false

  override def getFieldStore: Pair[Val, Field] = ???

  override def getFieldLoad: Pair[Val, Field] = ???

  override def isStaticFieldLoad: Boolean = {
    inst.isInstanceOf[InstructionDef.canOperator] &&
      inst.asInstanceOf[InstructionDef.canOperator]
        .operatorDef.operator.isInstanceOf[Operator.singletonRead]
  }

  override def isStaticFieldStore: Boolean = {
    inst.isInstanceOf[InstructionDef.canOperator] &&
      inst.asInstanceOf[InstructionDef.canOperator]
        .operatorDef.operator.isInstanceOf[Operator.singletonWrite]
  }

  override def getStaticField: StaticFieldVal = {
    val field = {
      if (isStaticFieldLoad) {
        inst.asInstanceOf[InstructionDef.canOperator]
          .operatorDef.operator.asInstanceOf[Operator.singletonRead].field
      } else if (isStaticFieldStore) {
        inst.asInstanceOf[InstructionDef.canOperator]
          .operatorDef.operator.asInstanceOf[Operator.singletonWrite].field
      } else {
        throw new RuntimeException("getStaticField called on non-static statement")
      }
    }
    new SWANStaticFieldVal(new SWANField(field), method)
  }

  override def killAtIfStmt(fact: Val, successor: Statement): Boolean = false

  override def getPhiVals: util.Collection[Val] = util.Collections.emptyList

  override def getArrayBase: Val = {
    if (/*!isArrayStore && */!isArrayLoad) {
      throw new RuntimeException("getArrayBase called on non-array statement")
    }
    new SWANVal(method.delegate.getSymbol(
      inst.asInstanceOf[InstructionDef.canOperator]
      .operatorDef.operator.asInstanceOf[Operator.arrayRead].arr.name))
  }

  def getPosition: Option[Position] = {
    inst match {
      case InstructionDef.canOperator(operatorDef) =>
        operatorDef.position
      case InstructionDef.canTerminator(terminatorDef) =>
        terminatorDef.position
    }
  }

  override def getStartLineNumber: Int = {
    val pos = getPosition
    if (pos.nonEmpty) {
      pos.get.line
    } else {
      -1
    }
  }

  override def getStartColumnNumber: Int = {
    val pos = getPosition
    if (pos.nonEmpty) {
      pos.get.col
    } else {
      -1
    }
  }

  override def getEndLineNumber: Int = getStartLineNumber

  override def getEndColumnNumber: Int = getStartColumnNumber

  override def isCatchStmt: Boolean = false
}
