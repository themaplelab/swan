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

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{ControlFlowGraph, Field, Method, Val}
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes.{INode, Node}
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.{Transition, Weight, WeightedPAutomaton}
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.WPAUpdateListener

abstract class MethodBasedFieldTransitionListener[W <: Weight](val method: Method) extends WPAUpdateListener[Field, INode[Node[ControlFlowGraph.Edge, Val]], W] {

  override def onWeightAdded(t: Transition[Field, INode[Node[ControlFlowGraph.Edge, Val]]], w: W, aut: WeightedPAutomaton[Field, INode[Node[ControlFlowGraph.Edge, Val]], W]): Unit = {
    onAddedTransition(t)
  }

  def onAddedTransition(t: Transition[Field, INode[Node[ControlFlowGraph.Edge, Val]]]): Unit
}
