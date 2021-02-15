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

import boomerang.scene.ControlFlowGraph.Edge
import boomerang.scene.{Method, Pair, Statement, Type, Val}
import ca.ualberta.maple.swan.ir.{BinaryOperation, Literal, Symbol, UnaryOperation}

abstract class SWANVal(mthd: Method) extends Val(mthd) {
  // Modifiable
  override def isNewExpr: Boolean = false
  override def getNewExprType: Type = null
  override def isArrayAllocationVal: Boolean = false
  override def isNull: Boolean = getType.isNullType
  override def isStringConstant: Boolean = false
  override def getStringValue: String = null
  override def isLongConstant: Boolean = false
  override def isIntConstant: Boolean = false
  override def getIntValue: Int = 0
  override def getLongValue: Long = 0
  override def getArrayBase: Pair[boomerang.scene.Val,Integer] = null
  override def isThrowableAllocationType: Boolean = ???
  override def isArrayRef: Boolean = ???

  // Shared
  final override def isStatic: Boolean = false
  final override def isLocal: Boolean = true
  final override def isStringBufferOrBuilder: Boolean = false
  // TODO: ???
  final override def asUnbalanced(edge: Edge): Val = ???
  final override def isCast: Boolean = false
  final override def getCastOp: Val = null
  final override def isInstanceOfExpr: Boolean = false
  final override def getInstanceOfOp: Val = null
  final override def isLengthExpr: Boolean = false
  final override def getLengthOp: Val = null
  final  override def isClassConstant: Boolean = false
  final override def getClassConstantType: Type = null
  final override def withNewMethod(callee: Method): Val = null
}

object SWANVal {
  case class Simple(delegate: Symbol, method: Method) extends SWANVal(method) {
    private val tpe = SWANType.create(delegate.tpe)
    override def getType: Type = tpe
    override def getVariableName: String = delegate.ref.name
  }
  case class Argument(delegate: Symbol, val index: Int, method: Method) extends SWANVal(method) {
    private val tpe = SWANType.create(delegate.tpe)
    override def getType: Type = tpe
    override def getVariableName: String = delegate.ref.name
  }
  case class NewExpr(delegate: Symbol, method: Method) extends SWANVal(method) {
    private val tpe = SWANType.create(delegate.tpe)
    override def getType: Type = tpe
    override def isNewExpr: Boolean = true
    override def getNewExprType: Type = tpe
    override def isArrayAllocationVal: Boolean = tpe.isArrayType
    override def getVariableName: String = delegate.ref.name
  }
  case class Constant(delegate: Symbol, literal: Literal, method: Method) extends SWANVal(method) {
    private val tpe = SWANType.create(delegate.tpe)
    override def getType: Type = tpe
    override def isStringConstant: Boolean = literal.isInstanceOf[Literal.string]
    override def getStringValue: String = literal.asInstanceOf[Literal.string].value
    override def isIntConstant: Boolean = literal.isInstanceOf[Literal.string]
    // For now, just cast BigInt to Int, maybe long later if needed
    override def getIntValue: Int = literal.asInstanceOf[Literal.int].value.toInt
    override def getVariableName: String = delegate.ref.name
    // TODO: float case
  }
  case class BinaryExpr(resultType: ca.ualberta.maple.swan.ir.Type, lhs: Symbol, rhs: Symbol,
                       operator: BinaryOperation, method: Method) extends SWANVal(method) {
    private val tpe = SWANType.create(resultType)
    override def getType: Type = tpe
    override def getVariableName: String = null // TODO
  }
  case class UnaryExpr(resultType: ca.ualberta.maple.swan.ir.Type, delegate: Symbol,
                       operator: UnaryOperation, method: Method) extends SWANVal(method) {
    private val tpe = SWANType.create(resultType)
    override def getType: Type = tpe
    override def getVariableName: String = null // TODO
  }
  case class FunctionRefExpr()
}