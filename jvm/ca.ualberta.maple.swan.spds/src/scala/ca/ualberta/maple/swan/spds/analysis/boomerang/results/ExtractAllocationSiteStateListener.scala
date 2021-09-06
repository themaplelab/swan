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

package ca.ualberta.maple.swan.spds.analysis.boomerang.results

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.{BackwardQuery, ForwardQuery}
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{Field, Statement, Val}
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes.{INode, Node}
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.{Transition, Weight, WeightedPAutomaton}
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.WPAStateListener

abstract class ExtractAllocationSiteStateListener[W <: Weight](state: INode[Node[Edge[Statement, Statement], Val]],
                                         bwQuery: BackwardQuery, fwQuery: ForwardQuery)
  extends WPAStateListener[Field, INode[Node[Edge[Statement, Statement] , Val]], W](state) {

  protected def allocationSiteFound(allocationSite: ForwardQuery, query: BackwardQuery): Unit

  override def onOutTransitionAdded(t: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]], w: W, weightedPAutomaton: WeightedPAutomaton[Field, INode[Node[Edge[Statement, Statement], Val]], W]): Unit = {}

  override def onInTransitionAdded(t: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]], w: W, weightedPAutomaton: WeightedPAutomaton[Field, INode[Node[Edge[Statement, Statement], Val]], W]): Unit = {
    if (t.getStart.fact.equals(bwQuery.asNode) && t.getLabel.equals(Field.empty)) {
      allocationSiteFound(fwQuery, bwQuery)
    }
  }

  override def hashCode: Int = System.identityHashCode(this)

  override def equals(obj: Any): Boolean = this == obj
}
