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

import ca.ualberta.maple.swan.spds.analysis.boomerang.cg.{CallerListener, ObservableICFG}
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene._
import ca.ualberta.maple.swan.spds.analysis.boomerang.solver._
import ca.ualberta.maple.swan.spds.analysis.boomerang.util.DefaultValueMap
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes.{GeneratedState, INode, Node, SingleNode}
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.{Transition, Weight, WeightedPAutomaton}
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.WPAStateListener

import scala.collection.mutable

class QueryGraph[W <: Weight](boomerang: WeightedBoomerang[W]) {

  val icfg: ObservableICFG[Statement, Method] = boomerang.icfg
  val roots: mutable.HashSet[Query] = mutable.HashSet.empty
  val sourceToQueryEdgeLookup: mutable.MultiDict[Query, QueryEdge] = mutable.MultiDict.empty
  val targetToQueryEdgeLookup: mutable.MultiDict[Query, QueryEdge] = mutable.MultiDict.empty
  val edgeAddListener: mutable.MultiDict[Query, AddTargetEdgeListener] = mutable.MultiDict.empty
  val forwardSolvers: DefaultValueMap[ForwardQuery, ForwardBoomerangSolver[W]] = boomerang.queryToSolvers
  val backwardSolvers: DefaultValueMap[BackwardQuery, BackwardBoomerangSolver[W]] = boomerang.queryToBackwardSolvers

  def addRoot(root: Query): Unit = roots.add(root)

  def addEdge(parent: Query, node: Node[Edge, Val], child: Query): Unit = {
    val queryEdge = new QueryEdge(parent, node, child)
    sourceToQueryEdgeLookup.addOne(parent, queryEdge)
    if (!targetToQueryEdgeLookup.containsEntry(child, queryEdge)) {
      targetToQueryEdgeLookup.addOne(child, queryEdge)
      edgeAddListener.get(child).foreach(l => l.edgeAdded(queryEdge))
    }
    getSolver(parent).callAutomaton
      .registerListener(new SourceListener(new SingleNode(node.fact), parent, child, null))
  }

  def isRoot(q: Query): Boolean = roots.contains(q)

  def getSolver(query: Query): AbstractBoomerangSolver[W] = {
    query match {
      case b: BackwardQuery => backwardSolvers(b)
      case f: ForwardQuery => forwardSolvers(f)
    }
  }

  def unregisterAllListeners(): Unit = this.edgeAddListener.clear()

  def getNodes: mutable.HashSet[Query] = {
    val nodes = mutable.HashSet.from(sourceToQueryEdgeLookup.keySet)
    nodes.addAll(targetToQueryEdgeLookup.keySet)
    nodes
  }

  override def toString: String = {
    val sb = new StringBuilder()
    roots.zipWithIndex.foreach(r => {
      sb.append(s"Root:${r._1}\n")
      sb.append(visit(r._1, "", r._2, mutable.HashSet.empty))
    })
    sb.toString()
  }

  protected def visit(parent: Query, s: String, i: Int, visited: mutable.HashSet[Query]): String = {
    val sb = new StringBuilder(s)
    sourceToQueryEdgeLookup.get(parent).foreach(child => {
      if (visited.add(child.target)) {
        sb.append(" " * i)
        sb.append(s"$i$child\n${visit(child.target, "", i + 1, visited)}")
      }
    })
    sb.toString()
  }

  def registerEdgeListener(l: QueryGraph.this.UnbalancedContextListener): Unit = {
    if (!edgeAddListener.containsEntry(l.getTarget, l)) {
      edgeAddListener.addOne(l.getTarget, l)
      val edges = targetToQueryEdgeLookup.get(l.getTarget)
      edges.foreach(edge => l.edgeAdded(edge))
      if (edges.isEmpty) l.noParentEdge()
    }
  }

  trait AddTargetEdgeListener {

    def getTarget: Query

    def edgeAdded(queryEdge: QueryEdge): Unit

    def noParentEdge(): Unit
  }

  class SourceListener(state: INode[Val],
                       protected val parent: Query,
                       protected val child: Query,
                       protected val callee: Method) extends WPAStateListener[Edge, INode[Val], W](state) {

    override def onOutTransitionAdded(t: Transition[Edge, INode[Val]], w: W, weightedPAutomaton: WeightedPAutomaton[Edge, INode[Val], W]): Unit = {
      if (t.start.isInstanceOf[GeneratedState[_, _]] && callee != null) {
        getSolver(child).allowUnbalanced(callee, if (parent.isInstanceOf[BackwardQuery]) t.label.target else t.label.start)
      }
      if (t.target.isInstanceOf[GeneratedState[_, _]]) {
        getSolver(parent).callAutomaton.registerListener(new SourceListener(t.target, parent, child, t.label.getMethod))
      }
      if (weightedPAutomaton.isUnbalancedState(t.target)) {
        registerEdgeListener(new UnbalancedContextListener(child, parent, t))
      }
    }

    override def onInTransitionAdded(t: Transition[Edge, INode[Val]], w: W, weightedPAutomaton: WeightedPAutomaton[Edge, INode[Val], W]): Unit = {}

    def getOuterType: QueryGraph[W] = QueryGraph.this

    override def hashCode: Int = super.hashCode + Objects.hashCode(getOuterType, callee, child, parent)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: SourceListener => super.equals(other) && Objects.equals(other.getOuterType, getOuterType) &&
          Objects.equals(other.callee, callee) && Objects.equals(other.child, child) && Objects.equals(other.parent, parent)
        case _ => false
      }
    }
  }

  protected class UnbalancedContextListener(protected val child: Query,
                                            protected val parent: Query,
                                            protected val t: Transition[ControlFlowGraph.Edge, INode[Val]]) extends AddTargetEdgeListener {

    override def getTarget: Query = parent

    override def edgeAdded(queryEdge: QueryEdge): Unit = {
      getSolver(queryEdge.source).callAutomaton.registerListener(
        new SourceListener(new SingleNode(queryEdge.node.fact), queryEdge.source, child, null))
    }

    override def noParentEdge(): Unit = {
      if (child.isInstanceOf[BackwardQuery]) {
        val callee = t.target.fact().method
        icfg.addCallerListener(new CallerListener[Statement, Method] {

          override def getObservedCallee: Method = callee

          override def onCallerAdded(callSite: Statement, method: Method): Unit = {
            getSolver(child).allowUnbalanced(callee, callSite)
          }
        })
      }
    }

    def getOuterType: QueryGraph[W] = QueryGraph.this

    override def hashCode(): Int = Objects.hashCode(getOuterType, child, parent, t)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: UnbalancedContextListener => Objects.equals(other.getOuterType, getOuterType) &&
          Objects.equals(other.child, child) && Objects.equals(other.parent, parent) && Objects.equals(other.t, t)
        case _ => false
      }
    }
  }
}