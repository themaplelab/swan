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

package ca.ualberta.maple.swan.spds.analysis.boomerang.solver

import java.util.Objects

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{Statement, Val}
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes.INode
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.{Transition, Weight, WeightedPAutomaton}
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.WPAUpdateListener

abstract class ControlFlowEdgeBasedCallTransitionListener[W <: Weight](val cfgEdge: Edge[Statement, Statement]) extends WPAUpdateListener[Edge[Statement, Statement], INode[Val], W] {

  override def onWeightAdded(t: Transition[Edge[Statement, Statement], INode[Val]], w: W, aut: WeightedPAutomaton[Edge[Statement, Statement], INode[Val], W]): Unit = {
    onAddedTransition(t, w)
  }

  def onAddedTransition(t: Transition[Edge[Statement, Statement], INode[Val]], w: W): Unit

  override def hashCode(): Int = Objects.hashCode(cfgEdge)

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: ControlFlowEdgeBasedCallTransitionListener[W] => Objects.equals(this.cfgEdge, other.cfgEdge)
      case _ => false
    }
  }
}
