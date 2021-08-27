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

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene._
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes._

abstract class Query(val cfgEdge: ControlFlowGraph.Edge, val variable: Val) {

  def asNode: Node[ControlFlowGraph.Edge, Val] = new Node(cfgEdge, variable)

  def getType: Type = {
    variable match {
      case allocVal: Val.AllocVal =>
        allocVal.allocVal.getType
      case _ =>
        variable.getType
    }
  }

  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + (if (cfgEdge == null) 0 else cfgEdge.hashCode)
    result = prime * result + (if (variable == null) 0 else variable.hashCode)
    result
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case q: Query => {
        var equal = true
        if (cfgEdge == null) equal = q.cfgEdge == null && equal else equal = cfgEdge.equals(q.cfgEdge) && equal
        if (variable == null) equal = q.variable == null && equal else equal = variable.equals(q.variable) && equal
        equal
      }
      case _ => false
    }
  }

  override def toString: String = asNode.toString
}
