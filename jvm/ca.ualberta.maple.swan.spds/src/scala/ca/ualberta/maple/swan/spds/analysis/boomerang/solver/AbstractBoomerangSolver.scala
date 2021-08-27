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

import ca.ualberta.maple.swan.spds.analysis.boomerang.{BoomerangOptions, Query}
import ca.ualberta.maple.swan.spds.analysis.boomerang.cfg.ObservableCFG
import ca.ualberta.maple.swan.spds.analysis.boomerang.cg.{BackwardsObservableICFG, CallerListener, ObservableICFG}
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene._
import ca.ualberta.maple.swan.spds.analysis.pds.solver.SyncPDSSolver
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes._
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.{NestedWeightedPAutomatons, Transition, Weight, WeightedPAutomaton}
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.{State, WPAUpdateListener}
import com.google.common.collect.{HashBasedTable, Table}

import scala.collection.mutable

abstract class AbstractBoomerangSolver[W <: Weight](val icfg: ObservableICFG[Statement, Method],
                                                    val cfg: ObservableCFG,
                                                    override val generatedFieldState: mutable.HashMap[(INode[Node[ControlFlowGraph.Edge, Val]], Field),
                                                      INode[Node[ControlFlowGraph.Edge, Val]]],
                                                    val options: BoomerangOptions,
                                                    val callSummaries: NestedWeightedPAutomatons[ControlFlowGraph.Edge, INode[Val], W],
                                                    val fieldSummaries: NestedWeightedPAutomatons[Field, INode[Node[ControlFlowGraph.Edge, Val]], W],
                                                    val dataFlowScope: DataFlowScope,
                                                    val propagationType: Type)
  extends SyncPDSSolver[ControlFlowGraph.Edge, Val, Field, W](
    if (icfg.isInstanceOf[BackwardsObservableICFG]) false else options.callSummaries,
    callSummaries,
    options.fieldSummaries,
    fieldSummaries,
    options.maxCallDepth,
    options.maxFieldDepth,
    options.maxUnbalancedCallDepth) {

  protected val perMethodFieldTransitions: mutable.MultiDict[Method, Transition[Field, INode[Node[ControlFlowGraph.Edge, Val]]]] = mutable.MultiDict.empty
  protected val perMethodFieldTransitionsListener: mutable.MultiDict[Method, MethodBasedFieldTransitionListener[W]] = mutable.MultiDict.empty
  protected val perStatementFieldTransitions: mutable.MultiDict[ControlFlowGraph.Edge, Transition[Field, INode[Node[ControlFlowGraph.Edge, Val]]]] = mutable.MultiDict.empty
  protected val perStatementFieldTransitionsListener: mutable.MultiDict[ControlFlowGraph.Edge, ControlFlowEdgeBasedFieldTransitionListener[W]] = mutable.MultiDict.empty
  protected val perStatementCallTransitions: mutable.HashMap[ControlFlowGraph.Edge, mutable.HashMap[Transition[ControlFlowGraph.Edge, INode[Val]], W]] = mutable.HashMap.empty
  protected val perStatementCallTransitionsListener: mutable.MultiDict[ControlFlowGraph.Edge, ControlFlowEdgeBasedCallTransitionListener[W]] = mutable.MultiDict.empty
  protected val unbalancedDataFlows: mutable.MultiDict[Method, UnbalancedDataFlow] = mutable.MultiDict.empty
  protected val unbalancedDataFlowListeners: mutable.MultiDict[Method, UnbalancedDataFlowListener] = mutable.MultiDict.empty

  this.fieldAutomaton.registerListener((t: Transition[Field, INode[Node[ControlFlowGraph.Edge, Val]]],
                                        w: W,
                                        aut: WeightedPAutomaton[Field, INode[Node[ControlFlowGraph.Edge, Val]], W]) => {
    addTransitionToMethod(t.getStart.fact().stmt.start.method, t)
    addTransitionToMethod(t.getTarget.fact().stmt.start.method, t)
    addTransitionToStatement(t.getStart.fact().stmt, t)
  })

  this.callAutomaton.registerListener((t: Transition[ControlFlowGraph.Edge, INode[Val]],
                                       w: W,
                                       aut: WeightedPAutomaton[ControlFlowGraph.Edge, INode[Val], W]) => {
    addCallTransitionToStatement(t.getLabel, t, w)
  })

  this.callAutomaton.registerListener(new UnbalancedListener)

  def registerStatementFieldTransitionListener(l: ControlFlowEdgeBasedFieldTransitionListener[W]): Unit = {
    if (!perStatementFieldTransitionsListener.containsEntry(l.cfgEdge, l)) {
      perStatementFieldTransitionsListener.addOne(l.cfgEdge, l)
      perStatementFieldTransitions.get(l.cfgEdge).foreach(t => l.onAddedTransition(t))
    }
  }

  def registerStatementCallTransitionListener(l: ControlFlowEdgeBasedCallTransitionListener[W]): Unit = {
    if (!perStatementCallTransitionsListener.containsEntry(l.cfgEdge, l)) {
      perStatementCallTransitionsListener.addOne(l.cfgEdge, l)
      perStatementCallTransitions(l.cfgEdge).foreach(t => l.onAddedTransition(t._1, t._2))
    }
  }

  protected def propagateUnbalancedToCallSite(callSiteEdge: Statement, transInCallee: Transition[ControlFlowGraph.Edge, INode[Val]]): Unit

  protected def computeNormalFlow(method: Method, currEdge: ControlFlowGraph.Edge, v: Val): mutable.HashSet[State]

  protected def computeReturnFlow(method: Method, curr: Statement, value: Val): mutable.HashSet[_ <: State]

  protected def forceUnbalanced(iNode: INode[Val], collection: scala.collection.Set[INode[Val]]): Boolean = false

  protected def addPotentialUnbalancedFlow(callee: Method, trans: Transition[ControlFlowGraph.Edge, INode[Val]], weight: W): Unit = {
    val u = new UnbalancedDataFlow(callee, trans)
    if (!unbalancedDataFlows.containsEntry(callee, u)) {
      unbalancedDataFlows.addOne(callee, u)
      unbalancedDataFlowListeners.get(callee).foreach(l => propagateUnbalancedToCallSite(l.callSite, trans))
    }

    if (forceUnbalanced(trans.getTarget, callAutomaton.getUnbalancedStartOf(trans.getTarget))) {
      icfg.addCallerListener(new CallerListener[Statement, Method] {

        override def getObservedCallee: Method = callee

        override def onCallerAdded(n: Statement, m: Method): Unit = propagateUnbalancedToCallSite(n, trans)
      })
    }
  }

  def unregisterAllListeners(): Unit = {
    callAutomaton.unregisterAllListeners()
    fieldAutomaton.unregisterAllListeners()
    perMethodFieldTransitionsListener.clear()
    perStatementCallTransitionsListener.clear()
    perStatementFieldTransitionsListener.clear()
    unbalancedDataFlowListeners.clear()
    unbalancedDataFlows.clear()
    callingPDS.unregisterAllListeners()
    fieldPDS.unregisterAllListeners()
  }

  def allowUnbalanced(callee: Method, callSite: Statement): Unit = {
    if (!dataFlowScope.isExcluded(callee)) {
      val l = new UnbalancedDataFlowListener(callee, callSite)
      if (!unbalancedDataFlowListeners.containsEntry(callee, l)) {
        unbalancedDataFlowListeners.addOne(callee, l)
        unbalancedDataFlows.get(callee).foreach(e => {
          propagateUnbalancedToCallSite(callSite, e.transition)
        })
      }
    }
  }

  def createQueryNodeField(query: Query): INode[Node[Edge, Val]] = {
    new SingleNode(new Node(query.cfgEdge, query.asNode.fact.asUnbalanced(query.cfgEdge)))
  }

  protected def addTransitionToMethod(method: Method, t: Transition[Field, INode[Node[ControlFlowGraph.Edge, Val]]]): Unit = {
    if (!perMethodFieldTransitions.containsEntry(method, t)) {
      perMethodFieldTransitions.addOne(method, t)
      perMethodFieldTransitionsListener.get(method).foreach(l => l.onAddedTransition(t))
    }
  }

  protected def addCallTransitionToStatement(s: ControlFlowGraph.Edge, t: Transition[ControlFlowGraph.Edge, INode[Val]], w: W): Unit = {
    if (perStatementCallTransitions.contains(s) && perStatementCallTransitions(s).contains(t)) {
      val put: W = perStatementCallTransitions(s)(t)
      val combineWith = put.combineWith(w).asInstanceOf[W]
      if (!combineWith.equals(put)) {
        perStatementCallTransitions(s).put(t, combineWith)
        perStatementCallTransitionsListener.get(s).foreach(l => l.onAddedTransition(t, w))
      }
    } else {
      if (!perStatementCallTransitions.contains(s)) perStatementCallTransitions.addOne(s, mutable.HashMap.empty)
      perStatementCallTransitions(s).addOne(t, w)
      perStatementCallTransitionsListener.get(s).foreach(l => l.onAddedTransition(t, w))
    }
  }

  protected def addTransitionToStatement(s: ControlFlowGraph.Edge, t: Transition[Field, INode[Node[ControlFlowGraph.Edge, Val]]]): Unit = {
    if (!perStatementFieldTransitions.containsEntry(s, t)) {
      perStatementFieldTransitions.addOne(s, t)
      perStatementFieldTransitionsListener.get(s).foreach(l => l.onAddedTransition(t))
    }
  }

  def reachesNodeWithEmptyField(node: Node[ControlFlowGraph.Edge, Val]): Boolean = {
    var res = false
    fieldAutomaton.transitions.foreach(t => {
      if (!t.getStart.isInstanceOf[GeneratedState[_, _]] && t.getStart.fact().equals(node) && t.getLabel.equals(Field.empty)) {
        res = true
      }
    })
    res
  }

  def asStatementValWeightTable: Table[ControlFlowGraph.Edge, Val, W] = {
    val results = HashBasedTable.create[ControlFlowGraph.Edge, Val, W]
    callAutomaton.getTransitionsToFinalWeights.foreach(e => {
      val t = e._1
      val w = e._2
      if (!t.getLabel.equals(new Edge(Statement.epsilon, Statement.epsilon)) || t.getLabel.getMethod.equals(t.start.fact().method)) {
        results.put(t.getLabel, t.getStart.fact(), w)
      }
    })
    results
  }

  override def fieldWildCard: Field = Field.wildcard

  override def exclusionFieldWildCard(exclusion: Field): Field = Field.exclusionWildcard(exclusion)

  override def epsilonField: Field = Field.epsilon

  override def emptyField: Field = Field.empty

  override def epsilonStmt: Edge = new ControlFlowGraph.Edge(Statement.epsilon, Statement.epsilon)

  protected class UnbalancedDataFlow(protected val callee: Method, val transition: Transition[ControlFlowGraph.Edge, INode[Val]]) {

    override def hashCode(): Int = Objects.hashCode(callee, transition)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: UnbalancedDataFlow => Objects.equals(other.callee, this.callee) && Objects.equals(other.transition, this.transition)
        case _ => false
      }
    }
  }

  protected class UnbalancedDataFlowListener(protected val callee: Method, val callSite: Statement) {

    override def hashCode(): Int = Objects.hashCode(callee, callSite)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: UnbalancedDataFlowListener => Objects.equals(other.callee, this.callee) && Objects.equals(other.callSite, this.callSite)
        case _ => false
      }
    }
  }

  private class UnbalancedListener extends WPAUpdateListener[ControlFlowGraph.Edge, INode[Val], W] {

    override def onWeightAdded(t: Transition[ControlFlowGraph.Edge, INode[Val]],
                               w: W, aut: WeightedPAutomaton[ControlFlowGraph.Edge, INode[Val], W]): Unit = {
      if (!t.getLabel.equals(new Edge(Statement.epsilon, Statement.epsilon)) && icfg.isExitStmt(
          if (AbstractBoomerangSolver.this.isInstanceOf[ForwardBoomerangSolver[W]]) t.getLabel.target else t.getLabel.start)) {
        if (callAutomaton.getInitialStates.contains(t.getTarget)) {
          addPotentialUnbalancedFlow(t.getLabel.target.method, t, w)
        }
      }
    }
  }
}
