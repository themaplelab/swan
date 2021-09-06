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

package ca.ualberta.maple.swan.spds.analysis.boomerang.poi

import java.util.Objects

import ca.ualberta.maple.swan.spds.analysis.boomerang.BackwardQuery
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{Assignment, Field, Statement, Val}
import ca.ualberta.maple.swan.spds.analysis.boomerang.solver.{BackwardBoomerangSolver, ForwardBoomerangSolver}
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes.{INode, Node, SingleNode}
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.{Transition, Weight, WeightedPAutomaton}
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.WPAStateListener

import scala.collection.mutable

class CopyAccessPathChain[W <: Weight](forwardSolver: ForwardBoomerangSolver[W],
                                       backwardSolver: BackwardBoomerangSolver[W],
                                       fieldWriteStatement: Edge[Statement, Statement],
                                       killedTransition: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]]) {

  protected val reachable: mutable.HashSet[INode[Node[Edge[Statement, Statement], Val]]] = mutable.HashSet.empty
  protected val delayedTransitions: mutable.MultiDict[INode[Node[Edge[Statement, Statement], Val]], InsertFieldTransitionCallback] = mutable.MultiDict.empty

  def exec(): Unit = {
    forwardSolver.fieldAutomaton.registerListener(
      new WalkForwardSolverListener(
        killedTransition.target,
        new SingleNode(new Node(fieldWriteStatement, fieldWriteStatement.target.asInstanceOf[Assignment].rhs)), 0))
  }

  protected def queueOrAdd(transToInsert: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]]): Unit = {
    if (reachable.contains(transToInsert.target)) {
      backwardSolver.fieldAutomaton.addTransition(transToInsert)
    } else {
      delayedTransitions.addOne(transToInsert.target, new InsertFieldTransitionCallback(transToInsert))
    }
  }

  def addReachable(node: INode[Node[Edge[Statement, Statement], Val]]): Unit = {
    if (reachable.add(node)) {
      delayedTransitions.get(node).foreach(_.trigger())
    }
  }

  protected class WalkForwardSolverListener(target: INode[Node[Edge[Statement, Statement], Val]],
                                            protected val stateInBwSolver: INode[Node[Edge[Statement, Statement], Val]], walkDepth: Int)
    extends WPAStateListener[Field, INode[Node[Edge[Statement, Statement], Val]], W](target) {

    def getOuterType: CopyAccessPathChain[W] = CopyAccessPathChain.this

    override def onOutTransitionAdded(t: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]], w: W, weightedPAutomaton: WeightedPAutomaton[Field, INode[Node[Edge[Statement, Statement], Val]], W]): Unit = {
      if (t.label.equals(Field.empty)) {
        if (forwardSolver.fieldAutomaton.isUnbalancedState(t.target)) {
          if (t.start.equals(CopyAccessPathChain.this.killedTransition.target)) {
            val query = new BackwardQuery(fieldWriteStatement, fieldWriteStatement.target.asInstanceOf[Assignment].rhs)
            val fieldTarget = backwardSolver.createQueryNodeField(query)
            val callTarget = backwardSolver.generateCallState(new SingleNode(query.variable), query.cfgEdge)
            backwardSolver.solve(query.asNode, Field.empty, fieldTarget, query.cfgEdge, callTarget)
          }
        }
      } else {
        val targetState = backwardSolver.generateFieldState(
          new SingleNode(new Node(new Edge[Statement, Statement](Statement.epsilon, Statement.epsilon), Val.zero)), t.label)
        val insert = new Transition(stateInBwSolver, t.label, targetState)
        queueOrAdd(insert)
        forwardSolver.fieldAutomaton.registerListener(new WalkForwardSolverListener(t.target, targetState, walkDepth + 1))
      }
    }

    override def onInTransitionAdded(t: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]], w: W, weightedPAutomaton: WeightedPAutomaton[Field, INode[Node[Edge[Statement, Statement], Val]], W]): Unit = {}

    override def hashCode: Int = Objects.hashCode(getOuterType, stateInBwSolver)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: WalkForwardSolverListener => {
          Objects.equals(getOuterType, other.getOuterType) && Objects.equals(other.stateInBwSolver, stateInBwSolver)
        }
        case _ => false
      }
    }
  }

  protected class InsertFieldTransitionCallback(protected val trans: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]]) {

    def getOuterType: CopyAccessPathChain[W] = CopyAccessPathChain.this

    def trigger(): Unit = {
      backwardSolver.fieldAutomaton.addTransition(trans)
      addReachable(trans.start)
    }

    override def hashCode(): Int = Objects.hashCode(getOuterType, trans)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: InsertFieldTransitionCallback => {
          Objects.equals(getOuterType, other.getOuterType) && Objects.equals(trans, other.trans)
        }
        case _ => false
      }
    }
  }
}
