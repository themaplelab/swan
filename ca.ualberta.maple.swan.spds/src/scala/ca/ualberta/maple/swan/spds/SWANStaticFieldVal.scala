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

import boomerang.scene.{Field, Method, Statement, StaticFieldVal, Type, Val}

class SWANStaticFieldVal(val field: Field, method: SWANMethod) extends StaticFieldVal(method) {

  override def asUnbalanced(stmt: Statement): Val = ???

  override def getType: Type = ???

  override def isStatic: Boolean = ???

  override def isNewExpr: Boolean = ???

  override def getNewExprType: Type = ???

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

  override def withNewMethod(method: Method): Val = ???

  override def isLongConstant: Boolean = ???

  override def getIntValue: Int = ???

  override def getLongValue: Long = ???

  override def getArrayBase: Val = ???

  override def getVariableName: String = ???
}
