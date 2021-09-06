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

package ca.ualberta.maple.swan.spds.analysis.typestate

import ca.ualberta.maple.swan.spds.analysis.boomerang.WeightedForwardQuery
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{CallSiteStatement, InvokeExpr, Statement, Val}
import ca.ualberta.maple.swan.spds.analysis.pds.solver.WeightFunctions
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes.Node
import ca.ualberta.maple.swan.spds.analysis.typestate.MatcherTransition.TransitionType

import scala.collection.mutable

abstract class TypeStateMachineWeightFunctions extends WeightFunctions[Edge[Statement, Statement], Val, Edge[Statement, Statement], TransitionFunction] {

  val transitions: mutable.HashSet[MatcherTransition] = mutable.HashSet.empty

  def addTransition(transition: MatcherTransition): Unit = {
    transitions.add(transition)
  }

  override def getOne: TransitionFunction = TransitionFunction.one

  override def pop(curr: Node[Edge[Statement, Statement], Val]): TransitionFunction = {
    getOne
  }

  override def push(curr: Node[Edge[Statement, Statement], Val], succ: Node[Edge[Statement, Statement], Val], push: Edge[Statement, Statement]): TransitionFunction = {
    getMatchingTransitions(
      succ.stmt, succ.fact, push,
      transitions.filter(x => x.tpe.equals(TransitionType.OnCall) || x.tpe.equals(TransitionType.OnCallOrOnCallToReturn)),
      TransitionType.OnCall)
  }

  override def normal(curr: Node[Edge[Statement, Statement], Val], succ: Node[Edge[Statement, Statement], Val]): TransitionFunction = {
    succ.stmt.target match {
      case statement: CallSiteStatement => callToReturn(curr, succ, statement.getInvokeExpr)
      case _ => getOne
    }
  }

  def callToReturn(curr: Node[Edge[Statement, Statement], Val], succ: Node[Edge[Statement, Statement], Val], expr: InvokeExpr): TransitionFunction = {
    getOne
  }

  def getMatchingTransitions(edge: Edge[Statement, Statement],
                             node: Val,
                             transitionEdge: Edge[Statement, Statement],
                             filteredTrans: mutable.HashSet[MatcherTransition],
                             tpe: MatcherTransition.TransitionType): TransitionFunction = {
    val transitionStmt = transitionEdge.start
    val res = mutable.HashSet.empty[Transition]
    if (filteredTrans.isEmpty || !transitionStmt.isInstanceOf[CallSiteStatement]) {
      getOne
    } else {
      filteredTrans.foreach(trans => {
        val invokeExpr = transitionStmt.asInstanceOf[CallSiteStatement].getInvokeExpr
        if (trans.matches(invokeExpr, transitionEdge)) {
          if (edge.getMethod.getParameterLocal(trans.param).equals(node)) {
            res.add(new Transition(trans.from, trans.to))
          }
        }
      })
      if (res.isEmpty) getOne else new TransitionFunction(res, mutable.HashSet(transitionEdge))
    }
  }

  override def toString: String = transitions.mkString("\n")

  def generateSeed(stmt: Edge[Statement, Statement]): mutable.ArrayBuffer[WeightedForwardQuery[TransitionFunction]]

  def initialState: State

  def initialTransition: TransitionFunction = {
    new TransitionFunction(mutable.HashSet(new Transition(initialState, initialState)), mutable.HashSet.empty)
  }
}
