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

import java.util.Objects

import ca.ualberta.maple.swan.spds.analysis.boomerang.ForwardQuery
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{ControlFlowGraph, Statement, Val}
import ca.ualberta.maple.swan.spds.analysis.boomerang.solver.ForwardBoomerangSolver
import ca.ualberta.maple.swan.spds.analysis.boomerang.util.DefaultValueMap
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes.{GeneratedState, INode, Node, SingleNode}
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.{PAutomaton, Transition, Weight, WeightedPAutomaton}
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.WPAStateListener

abstract class AbstractBoomerangResults[W <: Weight](queryToSolvers: DefaultValueMap[ForwardQuery, ForwardBoomerangSolver[W]] ) {

  protected def constructContextGraph(forwardQuery: ForwardQuery, targetFact: Node[Edge, Val]): Context = {
    val context = new Context(targetFact, forwardQuery)
    val forwardSolver = queryToSolvers(forwardQuery)
    computeUnmatchedOpeningContext(context, forwardSolver, targetFact)
    computeUnmatchedClosingContext(context, forwardSolver)
    context
  }

  def computeUnmatchedOpeningContext(context: AbstractBoomerangResults.this.Context, forwardSolver: ForwardBoomerangSolver[W],
                                     node: Node[ControlFlowGraph.Edge, Val]): Unit = {
    val initialState = new SingleNode(node.fact)
    forwardSolver.callAutomaton.registerListener(new OpeningCallStackExtractor(initialState, initialState, context, forwardSolver))
  }

  def computeUnmatchedClosingContext(context: Context, forwardSolver: ForwardBoomerangSolver[W]): Unit = {
    forwardSolver.callAutomaton.transitions.foreach(t => {
      if (t.target.fact().isUnbalanced) {
        forwardSolver.callAutomaton.registerListener(new ClosingCallStackExtractor(t.target, t.target, context, forwardSolver))
      }
    })
  }

  class OpeningCallStackExtractor(state: INode[Val],
                                  protected val source: INode[Val],
                                  protected val context: AbstractBoomerangResults.this.Context,
                                  protected val solver: ForwardBoomerangSolver[W]) extends WPAStateListener[Edge, INode[Val], W](state) {

    override def onOutTransitionAdded(t: Transition[Edge, INode[Val]], w: W, weightedPAutomaton: WeightedPAutomaton[Edge, INode[Val], W]): Unit = {
      if (!weightedPAutomaton.getInitialStates.contains(t.target)) {
        if (t.label.getMethod != null) {
          if (t.start.isInstanceOf[GeneratedState[_, _]]) {
            context.openingContext.addTransition(new Transition(source, t.getLabel, t.getTarget))
          } else {
            weightedPAutomaton.registerListener(new OpeningCallStackExtractor(t.getTarget, source, context, solver))
            return
          }
        }
        weightedPAutomaton.registerListener(new OpeningCallStackExtractor(t.target, t.target, context, solver))
      }
    }

    override def onInTransitionAdded(t: Transition[Edge, INode[Val]], w: W, weightedPAutomaton: WeightedPAutomaton[Edge, INode[Val], W]): Unit = {}

    override def hashCode: Int = super.hashCode + Objects.hashCode(context, solver, source)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: OpeningCallStackExtractor => {
          super.equals(other) && Objects.equals(context, other.context) &&
            Objects.equals(solver, other.solver) && Objects.equals(source, other.source)
        }
        case _ => false
      }
    }
  }

  class ClosingCallStackExtractor(state: INode[Val],
                                  protected val source: INode[Val],
                                  protected val context: AbstractBoomerangResults.this.Context,
                                  protected val solver: ForwardBoomerangSolver[W]) extends WPAStateListener[Edge, INode[Val], W](state) {

    override def onOutTransitionAdded(t: Transition[Edge, INode[Val]], w: W, weightedPAutomaton: WeightedPAutomaton[Edge, INode[Val], W]): Unit = {}

    override def onInTransitionAdded(t: Transition[Edge, INode[Val]], w: W, weightedPAutomaton: WeightedPAutomaton[Edge, INode[Val], W]): Unit = {}

    override def hashCode: Int = super.hashCode + Objects.hashCode(context, solver, source)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: ClosingCallStackExtractor => {
          super.equals(other) && Objects.equals(context, other.context) &&
            Objects.equals(solver, other.solver) && Objects.equals(source, other.source)
        }
        case _ => false
      }
    }
  }

  class Context(val node: Node[ControlFlowGraph.Edge, Val], val forwardQuery: ForwardQuery) {

    val openingContext: PAutomaton[Edge, INode[Val]] = new PAutomaton[ControlFlowGraph.Edge, INode[Val]] {

      override def createState(d: INode[Val], loc: Edge): INode[Val] = ???

      override def isGeneratedState(d: INode[Val]): Boolean = ???

      override def epsilon: Edge = new Edge(Statement.epsilon, Statement.epsilon)
    }

    val closingContext: PAutomaton[Edge, INode[Val]] = new PAutomaton[Edge, INode[Val]] {

      override def createState(d: INode[Val], loc: Edge): INode[Val] = ???

      override def isGeneratedState(d: INode[Val]): Boolean = ???

      override def epsilon: Edge = new Edge(Statement.epsilon, Statement.epsilon)
    }

    override def hashCode(): Int = Objects.hashCode(closingContext, node, openingContext)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: Context => Objects.equals(other.node, node)
        case _ => false
      }
    }
  }
}