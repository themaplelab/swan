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
import boomerang.scene._

class SWANStaticFieldVal(val field: Field, method: Method, unbalanced: Edge = null) extends StaticFieldVal(method, unbalanced) {


  override def asUnbalanced(edge: Edge): Val = {
    new SWANStaticFieldVal(field, method, edge)
  }

  override def getType: Type = ???

  override def isStatic: Boolean = true

  override def withNewMethod(method: Method): Val = {
    new SWANStaticFieldVal(field, method, unbalancedStmt)
  }

  override def getVariableName: String = field.toString

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
  override def isLongConstant: Boolean = false
  override def getIntValue: Int = -1
  override def getLongValue: Long = -1
  override def getArrayBase: Pair[boomerang.scene.Val,Integer] = null

  override def toString: String = {
    "<static_field>" + field.toString + "</static_field>"
  }

  // Type specifically not considered here
  override def hashCode: Int = {
    val prime = 31
    var result = 1
    result = prime * result + field.hashCode
    result
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case sfv: SWANStaticFieldVal => sfv.field.equals(this.field)
      case _ => false
    }
  }
}
