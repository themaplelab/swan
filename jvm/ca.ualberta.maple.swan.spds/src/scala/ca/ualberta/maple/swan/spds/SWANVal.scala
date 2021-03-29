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
import boomerang.scene.{Method, Pair, Type, Val}
import ca.ualberta.maple.swan.ir.{BinaryOperation, Literal, Symbol, UnaryOperation}

abstract class SWANVal(mthd: Method, unbalanced: Edge) extends Val(mthd, unbalanced) {

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
  override def isThrowableAllocationType: Boolean = false
  override def isArrayRef: Boolean = false

  // Shared
  final override def isStatic: Boolean = false
  final override def isLocal: Boolean = true
  final override def isStringBufferOrBuilder: Boolean = false
  final override def isCast: Boolean = false
  final override def getCastOp: Val = null
  final override def isInstanceOfExpr: Boolean = false
  final override def getInstanceOfOp: Val = null
  final override def isLengthExpr: Boolean = false
  final override def getLengthOp: Val = null
  final override def isClassConstant: Boolean = false
  final override def getClassConstantType: Type = null
  final override def withNewMethod(callee: Method): Val = null
}

object SWANVal {
  case class Simple(delegate: Symbol, method: Method, unbalanced: Edge = null) extends SWANVal(method, unbalanced) {
    private val tpe = SWANType.create(delegate.tpe)
    override def getType: Type = tpe
    override def getVariableName: String = delegate.ref.name
    override def asUnbalanced(edge: Edge): Val = Simple(delegate, method, edge)
    override def hashCode: Int = {
      val prime = 31
      var result = 1
      result = prime * result + delegate.hashCode
      result = prime * result + method.hashCode
      result
    }
    override def equals(obj: Any): Boolean = {
      obj match {
        case other: Simple =>
          delegate.equals(other.delegate) && method.equals(other.m)
        case _ =>
      }
      false
    }
    override def toString: String = {
      "<var name=" + getVariableName + " type=" + getType.toString + " hash=" + hashCode + " />"
    }
  }
  case class Argument(delegate: Symbol, index: Int, method: Method, unbalanced: Edge = null) extends SWANVal(method, unbalanced) {
    private val tpe = SWANType.create(delegate.tpe)
    override def getType: Type = tpe
    override def getVariableName: String = delegate.ref.name
    override def asUnbalanced(edge: Edge): Val = Argument(delegate, index, method, edge)
    override def hashCode: Int = {
      val prime = 31
      var result = 1
      result = prime * result + delegate.hashCode
      result = prime * result + method.hashCode
      result
    }
    override def equals(obj: Any): Boolean = {
      obj match {
        case other: Argument =>
          delegate.equals(other.delegate) && method.equals(other.m)
        case _ =>
      }
      false
    }
    override def toString: String = {
      getVariableName
    }
  }
  case class NewExpr(delegate: Symbol, method: Method, unbalanced: Edge = null) extends SWANVal(method, unbalanced) {
    val tpe: SWANType = SWANType.create(delegate.tpe)
    override def getType: Type = tpe
    override def isNewExpr: Boolean = true
    override def getNewExprType: Type = tpe
    override def isArrayAllocationVal: Boolean = tpe.isArrayType
    override def getVariableName: String = delegate.ref.name
    override def asUnbalanced(edge: Edge): Val = NewExpr(delegate, method, edge)
    override def hashCode: Int = {
      val prime = 31
      var result = 1
      result = prime * result + delegate.hashCode
      result = prime * result + method.hashCode
      result
    }
    override def equals(obj: Any): Boolean = {
      obj match {
        case other: NewExpr =>
          delegate.equals(other.delegate) && method.equals(other.m)
        case _ =>
      }
      false
    }
    override def toString: String = {
      "<new_var name=" + getVariableName + " type=" + this.getType.toString + " hash=" + hashCode + " />"
    }
  }
  case class Constant(delegate: Symbol, literal: Literal, method: Method, unbalanced: Edge = null) extends SWANVal(method, unbalanced) {
    private val tpe = SWANType.create(delegate.tpe)
    override def getType: Type = tpe
    override def isNewExpr: Boolean = true
    override def getNewExprType: Type = tpe
    override def isStringConstant: Boolean = literal.isInstanceOf[Literal.string]
    override def getStringValue: String = literal.asInstanceOf[Literal.string].value
    override def isIntConstant: Boolean = literal.isInstanceOf[Literal.string]
    // For now, just cast BigInt to Int, maybe long later if needed
    override def getIntValue: Int = literal.asInstanceOf[Literal.int].value.toInt
    override def getVariableName: String = delegate.ref.name
    // TODO: float case
    override def asUnbalanced(edge: Edge): Val = Constant(delegate, literal, method, edge)
    override def hashCode: Int = {
      val prime = 31
      var result = 1
      result = prime * result + delegate.hashCode
      result = prime * result + method.hashCode
      result
    }
    override def equals(obj: Any): Boolean = {
      obj match {
        case other: Constant =>
          delegate.equals(other.delegate) && method.equals(other.m)
        case _ =>
      }
      false
    }
    override def toString: String = {
      getVariableName + ": " + literal
    }
  }
  case class BinaryExpr(resultType: ca.ualberta.maple.swan.ir.Type, lhs: Symbol, rhs: Symbol,
                       operator: BinaryOperation, method: Method, unbalanced: Edge = null) extends SWANVal(method, unbalanced) {
    private val tpe = SWANType.create(resultType)
    override def getType: Type = tpe
    override def getVariableName: String = null // TODO
    override def asUnbalanced(edge: Edge): Val = BinaryExpr(resultType, lhs, rhs, operator, method, edge)
    override def hashCode: Int = {
      val prime = 31
      var result = 1
      result = prime * result + resultType.hashCode
      result = prime * result + lhs.hashCode
      result = prime * result + rhs.hashCode
      result = prime * result + operator.hashCode
      result = prime * result + method.hashCode
      result
    }
    override def equals(obj: Any): Boolean = {
      obj match {
        case other: BinaryExpr =>
          resultType.equals(other.resultType) && lhs.equals(other.lhs) && rhs.equals(other.rhs) && operator == other.operator &&
            method.equals(other.m)
        case _ =>
      }
      false
    }
    override def toString: String = {
      lhs.ref.name + " " + operator + " " + rhs.ref.name
    }
  }
  case class UnaryExpr(resultType: ca.ualberta.maple.swan.ir.Type, delegate: Symbol,
                       operator: UnaryOperation, method: Method, unbalanced: Edge = null) extends SWANVal(method, unbalanced) {
    private val tpe = SWANType.create(resultType)
    override def getType: Type = tpe
    override def getVariableName: String = null // TODO
    override def asUnbalanced(edge: Edge): Val = UnaryExpr(resultType, delegate, operator, method, edge)
    override def hashCode: Int = {
      val prime = 31
      var result = 1
      result = prime * result + resultType.hashCode
      result = prime * result + delegate.hashCode
      result = prime * result + operator.hashCode
      result = prime * result + method.hashCode
      result
    }
    override def equals(obj: Any): Boolean = {
      obj match {
        case other: UnaryExpr =>
          resultType.equals(other.resultType) && operator == other.operator && delegate.equals(other.delegate) &&
            method.equals(other.m)
        case _ =>
      }
      false
    }
    override def toString: String = {
      operator.toString + " " + delegate.ref.name
    }
  }
  case class FunctionRef(delegate: Symbol, ref: String, method: Method, unbalanced: Edge = null) extends SWANVal(method, unbalanced) {
    private val tpe = SWANType.create(delegate.tpe)
    override def getType: Type = tpe
    override def isNewExpr: Boolean = true
    override def getNewExprType: Type = tpe
    override def getVariableName: String = delegate.ref.name
    override def asUnbalanced(edge: Edge): Val = FunctionRef(delegate, ref, method, edge)
    override def hashCode: Int = {
      val prime = 31
      var result = 1
      result = prime * result + delegate.hashCode
      result = prime * result + method.hashCode
      result
    }
    override def equals(obj: Any): Boolean = {
      obj match {
        case other: FunctionRef =>
          delegate.equals(other.delegate) && method.equals(other.m)
        case _ =>
      }
      false
    }
    override def toString: String = {
      "<func_ref_var name=" + getVariableName + " type=" + getType.toString + " func=" + ref + " hash=" + hashCode + " />"
    }
  }
  case class BuiltinFunctionRef(delegate: Symbol, ref: String, method: Method, unbalanced: Edge = null) extends SWANVal(method, unbalanced) {
    private val tpe = SWANType.create(delegate.tpe)
    override def getType: Type = tpe
    override def isNewExpr: Boolean = true
    override def getNewExprType: Type = tpe
    override def getVariableName: String = delegate.ref.name
    override def asUnbalanced(edge: Edge): Val = BuiltinFunctionRef(delegate, ref, method, edge)
    override def hashCode: Int = {
      val prime = 31
      var result = 1
      result = prime * result + delegate.hashCode
      result = prime * result + method.hashCode
      result
    }
    override def equals(obj: Any): Boolean = {
      obj match {
        case other: BuiltinFunctionRef =>
          delegate.equals(other.delegate) && method.equals(other.m)
        case _ =>
      }
      false
    }
    override def toString: String = {
      delegate.ref.name + ": @" + ref
    }
  }
  case class DynamicFunctionRef(delegate: Symbol, index: String, method: Method, unbalanced: Edge = null) extends SWANVal(method, unbalanced) {
    private val tpe = SWANType.create(delegate.tpe)
    override def getType: Type = tpe
    override def isNewExpr: Boolean = true
    override def getNewExprType: Type = tpe
    override def getVariableName: String = delegate.ref.name
    override def asUnbalanced(edge: Edge): Val = DynamicFunctionRef(delegate, index, method, edge)
    override def hashCode: Int = {
      val prime = 31
      var result = 1
      result = prime * result + delegate.hashCode
      result = prime * result + method.hashCode
      result
    }
    override def equals(obj: Any): Boolean = {
      obj match {
        case other: DynamicFunctionRef =>
          delegate.equals(other.delegate) && method.equals(other.m)
        case _ =>
      }
      false
    }
    override def toString: String = {
      delegate.ref.name + ": @" + index
    }
  }
}