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

package ca.ualberta.maple.swan.spds.analysis.ideal

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{CallSiteStatement, Statement, Val}
import ca.ualberta.maple.swan.spds.analysis.ideal.IDEALSeedSolver.Phases
import ca.ualberta.maple.swan.spds.analysis.pds.solver.WeightFunctions
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes.{Node, PushNode}
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.Weight

import scala.collection.mutable

class IDEALWeightFunctions[W <: Weight](val delegate: WeightFunctions[Edge[Statement, Statement], Val, Edge[Statement, Statement], W],
                                        strongUpdates: Boolean) extends WeightFunctions[Edge[Statement, Statement], Val, Edge[Statement, Statement], W] {

  protected val listeners: mutable.HashSet[NonOneFlowListener] = mutable.HashSet.empty
  protected val nonOneFlowNodes: mutable.HashSet[Node[Edge[Statement, Statement], Val]] = mutable.HashSet.empty
  protected val weakUpdates: mutable.HashSet[Edge[Statement, Statement]] = mutable.HashSet.empty
  protected val potentialStrongUpdates: mutable.HashSet[Edge[Statement, Statement]] = mutable.HashSet.empty
  protected val indirectAlias: mutable.MultiDict[Node[Edge[Statement, Statement], Val], Node[Edge[Statement, Statement], Val]] = mutable.MultiDict.empty
  protected val nodesWithStrongUpdate: mutable.HashSet[Node[Edge[Statement, Statement], Val]] = mutable.HashSet.empty
  protected var phase: Phases = _

  override def push(curr: Node[Edge[Statement, Statement], Val], succ: Node[Edge[Statement, Statement], Val], calleeSp: Edge[Statement, Statement]): W = {
    val weight = delegate.push(curr, succ, calleeSp)
    if (isObjectFlowPhase && !weight.equals(getOne) && succ.isInstanceOf[PushNode[_, _, _]]) {
      addOtherThanOneWeight(
        new Node(
          succ.asInstanceOf[PushNode[_, _, _]].location.asInstanceOf[Edge[Statement, Statement]],
          curr.fact))
    }
    weight
  }

  def isObjectFlowPhase: Boolean = phase.equals(Phases.ObjectFlow)

  def addOtherThanOneWeight(curr: Node[Edge[Statement, Statement], Val]): Unit = {
    if (nonOneFlowNodes.add(curr)) {
      listeners.foreach(_.nonOneFlow(curr))
    }
  }

  override def normal(curr: Node[Edge[Statement, Statement], Val], succ: Node[Edge[Statement, Statement], Val]): W = {
    val weight = delegate.normal(curr, succ)
    if (isObjectFlowPhase && succ.stmt.target.isInstanceOf[CallSiteStatement] && !weight.equals(getOne)) {
      addOtherThanOneWeight(succ)
    }
    weight
  }

  override def pop(curr: Node[Edge[Statement, Statement], Val]): W = {
    delegate.pop(curr)
  }

  def registerListener(listener: NonOneFlowListener): Unit = {
    if (listeners.add(listener)) {
      nonOneFlowNodes.foreach(existing => listener.nonOneFlow(existing))
    }
  }

  override def getOne: W = delegate.getOne

  override def toString: String = s"[IDEAL-Wrapped Weights] $delegate"

  def potentialStrongUpdate(stmt: Edge[Statement, Statement]): Unit = potentialStrongUpdates.add(stmt)

  def weakUpdate(stmt: Edge[Statement, Statement]): Unit = weakUpdates.add(stmt)

  def setPhase(phase: Phases): Unit = this.phase = phase

  def addIndirectFlow(source: Node[Edge[Statement, Statement], Val], target: Node[Edge[Statement, Statement], Val]): Unit = {
    if (!source.equals(target)) {
      indirectAlias.addOne(source, target)
    }
  }

  def getAliasesFor(node: Node[Edge[Statement, Statement], Val]): mutable.HashSet[Node[Edge[Statement, Statement], Val]] = {
    mutable.HashSet.from(indirectAlias.get(node))
  }

  def isStrongUpdateStatement(stmt: Edge[Statement, Statement]): Boolean = {
    potentialStrongUpdates.contains(stmt) && !weakUpdates.contains(stmt) && strongUpdates
  }

  def isKillFlow(node: Node[Edge[Statement, Statement], Val]): Boolean = {
    !nodesWithStrongUpdate.contains(node)
  }

  def addNonKillFlow(curr: Node[Edge[Statement, Statement], Val]): Unit = {
    nodesWithStrongUpdate.add(curr)
  }
}
