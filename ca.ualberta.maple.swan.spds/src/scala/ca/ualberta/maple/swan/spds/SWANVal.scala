/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.spds

import boomerang.scene.{Method, Statement, Type, Val}
import ca.ualberta.maple.swan.ir.raw.SWIRLGen
import ca.ualberta.maple.swan.ir.Symbol

class SWANVal(val symbol: Symbol) extends Val {

  override def getType: Type = {
    new SWANType(symbol.tpe)
  }

  override def isStatic: Boolean = {
    symbol.ref.name == SWIRLGen.GLOBAL_SINGLETON
  }

  override def isNewExpr: Boolean = false

  override def getNewExprType: Type = null

  override def asUnbalanced(stmt: Statement): Val = ???

  override def isLocal: Boolean = ???

  override def isArrayAllocationVal: Boolean = ???

  override def isNull: Boolean = ???

  override def isStringConstant: Boolean = ???

  override def getStringValue: String = ???

  override def isStringBufferOrBuilder: Boolean = ???

  override def isThrowableAllocationType: Boolean = ???

  override def isCast: Boolean = ???

  override def getCastOp: Val = ???

  override def isArrayRef: Boolean = ???

  override def isInstanceOfExpr: Boolean = ???

  override def getInstanceOfOp: Val = ???

  override def isLengthExpr: Boolean = ???

  override def getLengthOp: Val = ???

  override def isIntConstant: Boolean = ???

  override def isClassConstant: Boolean = ???

  override def getClassConstantType: Type = ???

  override def withNewMethod(callee: Method): Val = ???

  override def isLongConstant: Boolean = ???

  override def getIntValue: Int = ???

  override def getLongValue: Long = ???

  override def getArrayBase: Val = ???

  override def getVariableName: String = ???
}
