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

import boomerang.scene.ControlFlowGraph.Edge
import boomerang.scene.{Field, Method, Pair, StaticFieldVal, Type, Val}
import ca.ualberta.maple.swan.ir.Constants

class SWANStaticFieldVal(val field: Field, method: Method) extends StaticFieldVal(method) {

  def this(field: Field, method: SWANMethod, unbalanced: Edge) = {
    this(field, method)
  }

  def this(field: Field, method: Method, unbalanced: Edge) = {
    this(field, method)
  }

  override def asUnbalanced(edge: Edge): Val = {
    new SWANStaticFieldVal(field, method, edge)
  }

  override def getType: Type = new SWANType(
    new ca.ualberta.maple.swan.ir.Type(Constants.globalsSingleton))

  override def isStatic: Boolean = true

  override def isNewExpr: Boolean = false

  override def getNewExprType: Type = null

  override def isLocal: Boolean = false

  override def isArrayAllocationVal: Boolean = false

  override def isNull: Boolean = false

  override def isStringConstant: Boolean = false

  override def getStringValue: String = null

  override def isStringBufferOrBuilder: Boolean = false

  override def isThrowableAllocationType: Boolean = false

  override def isCast: Boolean = false

  override def getCastOp: Val = null

  override def isArrayRef: Boolean = false

  override def isInstanceOfExpr: Boolean = false

  override def getInstanceOfOp: Val = null

  override def isLengthExpr: Boolean = false

  override def getLengthOp: Val = null

  override def isIntConstant: Boolean = false

  override def isClassConstant: Boolean = false

  override def getClassConstantType: Type = null

  override def withNewMethod(method: Method): Val = {
    new SWANStaticFieldVal(field, method, unbalancedStmt)
  }

  override def isLongConstant: Boolean = false

  override def getIntValue: Int = -1

  override def getLongValue: Long = -1

  override def getArrayBase: Pair[boomerang.scene.Val,Integer] = null

  override def getVariableName: String = field.toString

  // Temporary
  override def toString: String = {
    "(static field) " + field.toString
  }
}
