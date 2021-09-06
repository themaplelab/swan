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

package ca.ualberta.maple.swan.spds.swan

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.Type

class SWANType(val tpe: ca.ualberta.maple.swan.ir.Type) extends Type(tpe.name)

object SWANType {

  case class SWANNullType(private val t: ca.ualberta.maple.swan.ir.Type) extends SWANType(t)

  case class SWANBoolType(private val t: ca.ualberta.maple.swan.ir.Type) extends SWANType(t)

  case class SWANArrayType(private val t: ca.ualberta.maple.swan.ir.Type) extends SWANType(t)

  def create(tpe: ca.ualberta.maple.swan.ir.Type): SWANType = {
    if (tpe.name == "()") {
      SWANNullType(tpe)
    } else if (tpe.name == "Builtin.Int1") {
      SWANBoolType(tpe)
    } else if (tpe.name.startsWith("Array") || tpe.name.startsWith("[")) {
      SWANArrayType(tpe)
    } else {
      new SWANType(tpe)
    }
  }
}