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

import java.util.Objects

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.Statement
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{Field, Method, StaticFieldVal, Type, Val}

class SWANStaticFieldVal(val field: Field, method: Method, unbalanced: Edge[Statement, Statement] = null) extends StaticFieldVal(method, unbalanced) {

  override def getType: Type = ???

  override def getName: String = ???

  override def asUnbalanced(edge: Edge[Statement, Statement]): Val = {
    new SWANStaticFieldVal(field, method, edge)
  }

  override def toString: String = {
    "<static_field>" + field.toString + "</static_field>"
  }

  // Type specifically not considered here
  override def hashCode: Int = super.hashCode() + Objects.hashCode(field)

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: SWANStaticFieldVal => super.equals(other) && Objects.equals(other.field, field)
      case _ => false
    }
  }
}
