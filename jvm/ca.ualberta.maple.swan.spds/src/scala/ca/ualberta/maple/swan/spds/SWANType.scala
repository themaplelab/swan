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
import boomerang.scene.{Type, Val, WrappedClass}

class SWANType(val tpe: ca.ualberta.maple.swan.ir.Type) extends Type {

  override def isNullType: Boolean = false

  override def isRefType: Boolean = true

  override def isArrayType: Boolean = false

  override def getArrayBaseType: Type = null

  override def getWrappedClass: WrappedClass = null

  override def doesCastFail(targetVal: Type, target: Val): Boolean = false

  // TODO: Use type hierarchy?
  override def isSubtypeOf(tpe: String): Boolean = false

  override def isBooleanType: Boolean = false

  override def toString: String = tpe.name

  override def equals(obj: Any): Boolean = {
    obj match {
      case t: SWANType => tpe.equals(t.tpe)
      case _ => false
    }
  }
}

class SWANNullType(tpe: ca.ualberta.maple.swan.ir.Type) extends SWANType(tpe) {
  override def isNullType: Boolean = {
    true
  }
  override def toString: String = {
    "(null)" + tpe.name
  }
}

class SWANBoolType(tpe: ca.ualberta.maple.swan.ir.Type) extends SWANType(tpe) {
  override def isBooleanType: Boolean = {
    true
  }
  override def toString: String = {
    "(bool)" + tpe.name
  }
}

class SWANArrayType(tpe: ca.ualberta.maple.swan.ir.Type) extends SWANType(tpe) {
  override def isArrayType: Boolean = {
    true
  }
  override def toString: String = {
    "(array)" + tpe.name
  }
}

object SWANType {
  def create(tpe: ca.ualberta.maple.swan.ir.Type): SWANType = {
    if (tpe.name == "()") {
      new SWANNullType(tpe)
    } else if (tpe.name == "Builtin.Int1") {
      new SWANBoolType(tpe)
    } else if (tpe.name.startsWith("Array") || tpe.name.startsWith("[")) {
      new SWANArrayType(tpe)
    } else {
      new SWANType(tpe)
    }
  }
}
