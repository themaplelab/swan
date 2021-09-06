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

package ca.ualberta.maple.swan.spds.analysis.boomerang

import java.util.Objects

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene._
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes._

abstract class Query(val cfgEdge: Edge[Statement, Statement], val variable: Val) {

  def asNode: Node[Edge[Statement, Statement], Val] = new Node(cfgEdge, variable)

  def getType: Type = {
    variable match {
      case allocVal: Val.AllocVal =>
        allocVal.allocVal.getType
      case _ =>
        variable.getType
    }
  }

  override def hashCode(): Int =  Objects.hashCode(cfgEdge, variable)

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: Query => Objects.equals(other.cfgEdge, cfgEdge) && Objects.equals(other.variable, variable)
      case _ => false
    }
  }

  override def toString: String = asNode.toString
}
