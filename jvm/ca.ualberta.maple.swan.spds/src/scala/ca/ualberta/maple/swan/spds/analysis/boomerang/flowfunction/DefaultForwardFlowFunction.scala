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

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.{BoomerangOptions, ForwardQuery}
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{Assignment, CallSiteStatement, ControlFlowGraph, Field, FieldLoadStatement, FieldStoreStatement, Method, ReturnStatement, Statement, StaticFieldLoadStatement, StaticFieldStoreStatement, ThrowStatement, Val}
import ca.ualberta.maple.swan.spds.analysis.boomerang.solver.{ForwardBoomerangSolver, Strategies}
import ca.ualberta.maple.swan.spds.analysis.pds.solver.SyncPDSSolver.PDSSystem
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes.{ExclusionNode, Node, NodeWithLocation, PopNode, PushNode}
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.Weight
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.State

import scala.collection.mutable

class DefaultForwardFlowFunction(options: BoomerangOptions) extends IForwardFlowFunction {

  protected var solver: ForwardBoomerangSolver[_] = _
  protected var strategies: Strategies[_] = _

  override def returnFlow(method: Method, curr: Statement, value: Val): mutable.HashSet[Val] = {
    val out = mutable.HashSet.empty[Val]
    if (curr.isInstanceOf[ThrowStatement]) mutable.HashSet.empty else {
      curr match {
        case statement: ReturnStatement if statement.getReturnOp.equals(value) =>
          out.add(value)
        case _ =>
      }
      method.getParameterLocals.foreach(param => if (param.equals(value)) out.add(value))
      out
    }
  }

  override def callFlow(callSite: CallSiteStatement, fact: Val, callee: Method): mutable.HashSet[Val] = {
    val out = mutable.HashSet.empty[Val]
    val invokeExpr = callSite.getInvokeExpr
    val parameterLocals = callee.getParameterLocals
    invokeExpr.getArgs.zipWithIndex.foreach(arg => {
      if (arg._1.equals(fact) && parameterLocals.length > arg._2) {
        out.add(parameterLocals(arg._2))
      }
    })
    out
  }

  override def normalFlow(query: ForwardQuery, nextEdge: Edge[Statement, Statement], fact: Val): mutable.HashSet[State] = {
    val succ = nextEdge.start
    val out = mutable.HashSet.empty[State]
    if (killFlow(succ, fact)) {
      out
    } else {
      succ match {
        case fieldStoreStatement: FieldStoreStatement => {
          if (!fieldStoreStatement.isFieldWriteWithBase(fact)) {
            out.add(new Node(nextEdge, fact))
          } else {
            out.add(new ExclusionNode(nextEdge, fact, fieldStoreStatement.getWrittenField))
          }
        }
        case _ =>
      }
      succ match {
        case assign: Assignment => {
          val leftOp = assign.lhs
          val rightOp = assign.rhs
          if (rightOp.equals(fact)) {
            assign match {
              case fieldStoreStatement: FieldStoreStatement => {
                val ifr = fieldStoreStatement.getFieldStore
                out.add(new PushNode(nextEdge, ifr.x, ifr.y, PDSSystem.Fields))
              }
              case staticFieldStoreStatement: StaticFieldStoreStatement => {
                val sf = staticFieldStoreStatement.getStaticField
                strategies.staticFieldStrategy.handleForward(nextEdge, rightOp, sf, out)
              }
              case _ => out.add(new Node(nextEdge, leftOp))
            }
          }
          assign match {
            case fieldLoadStatement: FieldLoadStatement => {
              val ifr = fieldLoadStatement.getFieldLoad
              if (ifr.x.equals(fact)) {
                val succNode = new NodeWithLocation(nextEdge, leftOp, ifr.y)
                out.add(new PopNode(succNode, PDSSystem.Fields))
              }
            }
            case _ =>
          }
        }
        case _ =>
      }
      out
    }
  }

  protected def killFlow(curr: Statement, value: Val): Boolean = {
    curr match {
      case fieldLoadStatement: FieldLoadStatement if fieldLoadStatement.getFieldLoad.x.equals(value) => false
      case assignment: Assignment => assignment.lhs.equals(value)
      case _ => false
    }
  }

  override def callToReturnFlow(query: ForwardQuery, edge: Edge[Statement, Statement], fact: Val): mutable.HashSet[State] = {
    normalFlow(query, edge, fact)
  }

  override def setSolver(solver: ForwardBoomerangSolver[_ <: Weight], fieldLoadStatements: mutable.MultiDict[Field, Statement], fieldStoreStatements: mutable.MultiDict[Field, Statement]): Unit = {
    this.solver = solver
    this.strategies = new Strategies(options, solver, fieldLoadStatements, fieldStoreStatements)
  }
}
