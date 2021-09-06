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

package ca.ualberta.maple.swan.spds.analysis.boomerang.flowfunction

import ca.ualberta.maple.swan.spds.analysis.boomerang.BoomerangOptions
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene._
import ca.ualberta.maple.swan.spds.analysis.boomerang.solver.{BackwardBoomerangSolver, Strategies}
import ca.ualberta.maple.swan.spds.analysis.pds.solver.SyncPDSSolver.PDSSystem
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes.{Node, NodeWithLocation, PopNode, PushNode}
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.Weight
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.State

import scala.collection.mutable

class DefaultBackwardFlowFunction(options: BoomerangOptions) extends IBackwardFlowFunction {

  protected var solver: BackwardBoomerangSolver[_] = _
  protected var strategies: Strategies[_] = _

  override def returnFlow(callee: Method, returnStmt: Statement, returnedVal: Val): mutable.HashSet[Val] = {
    val out = mutable.HashSet.empty[Val]
    callee.getParameterLocals.foreach(param => {
      if (param.equals(returnedVal)) out.add(returnedVal)
    })
    out
  }

  override def callFlow(callSite: CallSiteStatement, fact: Val, callee: Method, calleeSp: Statement): mutable.HashSet[Val] = {
    val out = mutable.HashSet.empty[Val]
    val invokeExpr = callSite.getInvokeExpr
    val parameterLocals = callee.getParameterLocals
    invokeExpr.getArgs.zipWithIndex.foreach(arg => {
      if (arg._1.equals(fact) && parameterLocals.length > arg._2) {
        out.add(parameterLocals(arg._2))
      }
    })
    calleeSp match {
      case statement: ReturnStatement if callSite.lhs.equals(fact) =>
        out.add(statement.getReturnOp)
      case _ =>
    }
    out
  }

  override def normalFlow(currEdge: ControlFlowGraph.Edge[Statement, Statement], fact: Val): mutable.HashSet[State] = {
    val curr = currEdge.target
    if (options.getAllocationVal(curr.method, curr, fact).nonEmpty) {
      mutable.HashSet.empty
    } else {
      val out = mutable.HashSet.empty[State]
      var leftSideMatches = false
      curr match {
        case assign: Assignment => {
          val leftOp = assign.lhs
          val rightOp = assign.rhs
          if (leftOp.equals(fact)) {
            leftSideMatches = true
            assign match {
              case fieldLoadStatement: FieldLoadStatement => {
                val ifr = fieldLoadStatement.getFieldLoad
                out.add(new PushNode(currEdge, ifr.x, ifr.y, PDSSystem.Fields))
              }
              case staticFieldLoadStatement: StaticFieldLoadStatement => {
                strategies.staticFieldStrategy.handleBackward(currEdge, staticFieldLoadStatement.lhs, staticFieldLoadStatement.getStaticField, out)
              }
              case _ => out.add(new Node(currEdge, rightOp))
            }
          }
          assign match {
            case fieldStoreStatement: FieldStoreStatement => {
              val ifr = fieldStoreStatement.getFieldStore
              val base = ifr.x
              if (base.equals(fact)) {
                val succNode = new NodeWithLocation(currEdge, rightOp, ifr.y)
                out.add(new PopNode(succNode, PDSSystem.Fields))
              }
            }
            case _ =>
          }
        }
        case _ =>
      }
      if (!leftSideMatches) out.add(new Node(currEdge, fact))
      out
    }
  }

  override def callToReturnFlow(edge: ControlFlowGraph.Edge[Statement, Statement], fact: Val): mutable.HashSet[State] = {
    normalFlow(edge, fact)
  }

  override def setSolver(solver: BackwardBoomerangSolver[_ <: Weight], fieldLoadStatements: mutable.MultiDict[Field, Statement], fieldStoreStatements: mutable.MultiDict[Field, Statement]): Unit = {
    this.solver = solver
    new Strategies(options, solver, fieldLoadStatements, fieldStoreStatements)
  }
}