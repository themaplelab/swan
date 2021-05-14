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

import boomerang.scene.ControlFlowGraph.Edge
import boomerang.scene._
import ca.ualberta.maple.swan.ir.{BinaryOperation, Literal, Symbol, UnaryOperation}

abstract class SWANVal(mthd: Method, unbalanced: Edge) extends Val(mthd, unbalanced) {

  protected val simplified: Boolean = false

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
  override def isStatic: Boolean = false

  // Fixed
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
  case class Simple(delegate: Symbol, method: SWANMethod, unbalanced: Edge = null) extends SWANVal(method, unbalanced) {
    private val tpe = SWANType.create(delegate.tpe)
    override def getType: Type = tpe
    override def getVariableName: String = delegate.ref.name
    override def asUnbalanced(edge: Edge): Val = method.addVal(Simple(delegate, method, edge))
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
          delegate.equals(other.delegate) && method.equals(other.m) &&
            { if (other.unbalanced != null) other.unbalanced.equals(unbalanced) else true }
        case other: Argument =>
          delegate.equals(other.delegate) && method.equals(other.m) &&
            { if (other.unbalanced != null) other.unbalanced.equals(unbalanced) else true }
        case other: Constant =>
          delegate.equals(other.delegate) && method.equals(other.m) &&
            { if (other.unbalanced != null) other.unbalanced.equals(unbalanced) else true }
        case _: AllocVal => false
        case _ => throw new RuntimeException("unexpected")
      }
    }
    override def toString: String = {
      if (method.hasSwirlSource) getVariableName + " v" + method.swirlLineNum(delegate) else
      "<v " + getVariableName + " " + getType.toString + " />"
    }
  }
  case class Argument(delegate: Symbol, index: Int, method: SWANMethod, unbalanced: Edge = null) extends SWANVal(method, unbalanced) {
    private val tpe = SWANType.create(delegate.tpe)
    override def getType: Type = tpe
    override def getVariableName: String = delegate.ref.name
    override def asUnbalanced(edge: Edge): Val = method.addVal(Argument(delegate, index, method, edge))
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
          delegate.equals(other.delegate) && method.equals(other.m) &&
            { if (other.unbalanced != null) other.unbalanced.equals(unbalanced) else true }
        case other: Simple =>
          delegate.equals(other.delegate) && method.equals(other.m) &&
            { if (other.unbalanced != null) other.unbalanced.equals(unbalanced) else true }
        case other: Constant =>
          delegate.equals(other.delegate) && method.equals(other.m) &&
            { if (other.unbalanced != null) other.unbalanced.equals(unbalanced) else true }
        case _: AllocVal => false
        case _ => throw new RuntimeException("unexpected")
      }
    }
    override def toString: String = {
      if (method.hasSwirlSource) getVariableName + " v" + method.swirlLineNum(delegate) else
      "<a " + getVariableName + " " + this.getType.toString + " />"
    }
  }
  case class NewExpr(delegate: Symbol, method: SWANMethod, unbalanced: Edge = null) extends SWANVal(method, unbalanced) {
    val tpe: SWANType = SWANType.create(delegate.tpe)
    override def getType: Type = tpe
    override def isNewExpr: Boolean = true
    override def getNewExprType: Type = tpe
    override def isArrayAllocationVal: Boolean = tpe.isArrayType
    override def getVariableName: String = delegate.ref.name
    override def asUnbalanced(edge: Edge): Val = method.addVal(NewExpr(delegate, method, edge))
    override def hashCode: Int = {
      val prime = 31
      var result = 1
      result = prime * result + delegate.hashCode
      result = prime * result + tpe.hashCode()
      result = prime * result + method.hashCode
      result
    }
    override def equals(obj: Any): Boolean = {
      obj match {
        case other: NewExpr =>
          delegate.equals(other.delegate) && tpe.equals(other.tpe) && method.equals(other.m) &&
            { if (other.unbalanced != null) other.unbalanced.equals(unbalanced) else true }
        case _ => false
      }
    }
    override def toString: String = {
      if (method.hasSwirlSource) getVariableName + " v" + method.swirlLineNum(delegate) else
      "<nv " + getVariableName + " " + this.getType.toString + " />"
    }
  }
  case class Constant(delegate: Symbol, literal: Literal, method: SWANMethod, unbalanced: Edge = null) extends SWANVal(method, unbalanced) {
    private val tpe = SWANType.create(delegate.tpe)
    override def getType: Type = tpe
    override def isNewExpr: Boolean = true
    override def getNewExprType: Type = tpe
    override def isStringConstant: Boolean = literal.isInstanceOf[Literal.string]
    override def getStringValue: String = literal.asInstanceOf[Literal.string].value
    override def isIntConstant: Boolean = literal.isInstanceOf[Literal.int]
    // TODO: float case
    override def getIntValue: Int = literal.asInstanceOf[Literal.int].value.toInt
    override def getVariableName: String = delegate.ref.name
    override def asUnbalanced(edge: Edge): Val = method.addVal(Constant(delegate, literal, method, edge))
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
          delegate.equals(other.delegate) && method.equals(other.m) &&
            { if (other.unbalanced != null) other.unbalanced.equals(unbalanced) else true }
        case _ => false
      }
    }
    override def toString: String = {
      "<nlv " + getVariableName + " " + {
        literal match {
          case Literal.string(value) => value
          case Literal.int(value) => value
          case Literal.float(value) => value
        }
      } + " " + this.getType.toString + " />"

    }
  }
  // TODO
  case class BinaryExpr(resultType: ca.ualberta.maple.swan.ir.Type, lhs: Symbol, rhs: Symbol,
                       operator: BinaryOperation, method: SWANMethod, unbalanced: Edge = null) extends SWANVal(method, unbalanced) {
    private val tpe = SWANType.create(resultType)
    override def getType: Type = tpe
    override def getVariableName: String = null
    override def asUnbalanced(edge: Edge): Val = method.addVal(BinaryExpr(resultType, lhs, rhs, operator, method, edge))
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
            method.equals(other.m) &&
            { if (other.unbalanced != null) other.unbalanced.equals(unbalanced) else true }
        case _ => false
      }
    }
    override def toString: String = {
      lhs.ref.name + " " + operator + " " + rhs.ref.name
    }
  }
  // TODO
  case class UnaryExpr(resultType: ca.ualberta.maple.swan.ir.Type, delegate: Symbol,
                       operator: UnaryOperation, method: SWANMethod, unbalanced: Edge = null) extends SWANVal(method, unbalanced) {
    private val tpe = SWANType.create(resultType)
    override def getType: Type = tpe
    override def getVariableName: String = null
    override def asUnbalanced(edge: Edge): Val = method.addVal(UnaryExpr(resultType, delegate, operator, method, edge))
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
            method.equals(other.m) &&
            { if (other.unbalanced != null) other.unbalanced.equals(unbalanced) else true }
        case _ => false
      }
    }
    override def toString: String = {
      operator.toString + " " + delegate.ref.name
    }
  }
  case class FunctionRef(delegate: Symbol, ref: String, method: SWANMethod, unbalanced: Edge = null) extends SWANVal(method, unbalanced) {
    private val tpe = SWANType.create(delegate.tpe)
    override def getType: Type = tpe
    override def isNewExpr: Boolean = true
    override def getNewExprType: Type = tpe
    override def getVariableName: String = delegate.ref.name
    override def asUnbalanced(edge: Edge): Val = method.addVal(FunctionRef(delegate, ref, method, edge))
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
          delegate.equals(other.delegate) && method.equals(other.m) &&
            { if (other.unbalanced != null) other.unbalanced.equals(unbalanced) else true }
        case _ => false
      }
    }
    override def toString: String = {
      if (method.hasSwirlSource) getVariableName + " v" + method.swirlLineNum(delegate) else
      "<frv " + getVariableName + " " + getType.toString + " f=" + ref + " />"
    }
  }
  case class BuiltinFunctionRef(delegate: Symbol, ref: String, method: SWANMethod, unbalanced: Edge = null) extends SWANVal(method, unbalanced) {
    private val tpe = SWANType.create(delegate.tpe)
    override def getType: Type = tpe
    override def isNewExpr: Boolean = true
    override def getNewExprType: Type = tpe
    override def getVariableName: String = delegate.ref.name
    override def asUnbalanced(edge: Edge): Val = method.addVal(BuiltinFunctionRef(delegate, ref, method, edge))
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
          delegate.equals(other.delegate) && method.equals(other.m) &&
            { if (other.unbalanced != null) other.unbalanced.equals(unbalanced) else true }
        case _ => false
      }
    }
    override def toString: String = {
      if (method.hasSwirlSource) getVariableName + " v" + method.swirlLineNum(delegate) else
      "<bfrv " + getVariableName + " " + getType.toString + " f=" + ref + " />"
    }
  }
  case class DynamicFunctionRef(delegate: Symbol, index: String, method: SWANMethod, unbalanced: Edge = null) extends SWANVal(method, unbalanced) {
    private val tpe = SWANType.create(delegate.tpe)
    override def getType: Type = tpe
    override def isNewExpr: Boolean = true
    override def getNewExprType: Type = tpe
    override def getVariableName: String = delegate.ref.name
    override def asUnbalanced(edge: Edge): Val = method.addVal(DynamicFunctionRef(delegate, index, method, edge))
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
          delegate.equals(other.delegate) && method.equals(other.m) &&
            { if (other.unbalanced != null) other.unbalanced.equals(unbalanced) else true }
        case _ => false
      }
    }
    override def toString: String = {
      if (method.hasSwirlSource) getVariableName + " v" + method.swirlLineNum(delegate) else
      "<dfrv " + getVariableName + " " + getType.toString + " i=" + index + " />"
    }
  }
}