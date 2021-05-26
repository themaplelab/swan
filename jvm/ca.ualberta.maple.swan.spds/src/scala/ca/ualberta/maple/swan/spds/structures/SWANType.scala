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

package ca.ualberta.maple.swan.spds.structures

import boomerang.scene.{Type, Val, WrappedClass}

class SWANType(val tpe: ca.ualberta.maple.swan.ir.Type) extends Type {

  override def isRefType: Boolean = false
  override def isNullType: Boolean = false
  override def isArrayType: Boolean = false
  override def getArrayBaseType: Type = null
  override def getWrappedClass: WrappedClass = null
  override def doesCastFail(targetVal: Type, target: Val): Boolean = false
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
