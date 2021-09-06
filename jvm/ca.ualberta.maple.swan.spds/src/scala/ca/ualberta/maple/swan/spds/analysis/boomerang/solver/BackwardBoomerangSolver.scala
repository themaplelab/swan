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

import ca.ualberta.maple.swan.spds.analysis.boomerang.cfg.{ObservableCFG, PredecessorListener, SuccessorListener}
import ca.ualberta.maple.swan.spds.analysis.boomerang.cg.{CalleeListener, CallerListener, ObservableICFG}
import ca.ualberta.maple.swan.spds.analysis.boomerang.flowfunction.IBackwardFlowFunction
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.Val.AllocVal
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene._
import ca.ualberta.maple.swan.spds.analysis.boomerang.{BackwardQuery, BoomerangOptions}
import ca.ualberta.maple.swan.spds.analysis.pds.solver.SyncPDSSolver.PDSSystem
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes._
import ca.ualberta.maple.swan.spds.analysis.wpds.impl._
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.State

import scala.collection.mutable

abstract class BackwardBoomerangSolver[W <: Weight](icfg: ObservableICFG[Statement, Method],
                                                    cfg: ObservableCFG,
                                                    genField: mutable.HashMap[(INode[Node[Edge[Statement, Statement], Val]], Field),
                                                      INode[Node[Edge[Statement, Statement], Val]]],
                                                    query: BackwardQuery,
                                                    options: BoomerangOptions,
                                                    callSummaries: NestedWeightedPAutomatons[Edge[Statement, Statement], INode[Val], W],
                                                    fieldSummaries: NestedWeightedPAutomatons[Field, INode[Node[Edge[Statement, Statement], Val]], W],
                                                    scope: DataFlowScope, flowFunction: IBackwardFlowFunction,
                                                    fieldLoadStatements: mutable.MultiDict[Field, Statement],
                                                    fieldStoreStatements: mutable.MultiDict[Field, Statement],
                                                    propagationType: Type)
  extends AbstractBoomerangSolver[W](icfg, cfg, genField, options, callSummaries, fieldSummaries, scope, propagationType) {

  this.flowFunction.setSolver(this, fieldLoadStatements, fieldStoreStatements)

  override protected def propagateUnbalancedToCallSite(callSiteEdge: Statement, transInCallee: Transition[Edge[Statement, Statement], INode[Val]]): Unit = {

    if (!callSiteEdge.isInstanceOf[CallSiteStatement]) {
      throw new RuntimeException("Invalid propagate unbalanced return")
    }

    val target = transInCallee.getTarget.asInstanceOf[GeneratedState[Val, Edge[Statement, Statement]]]

    if (isMatchingCallSiteCalleePair(callSiteEdge, transInCallee.getLabel.getMethod)) {
      cfg.addSuccsOfListener(new SuccessorListener(callSiteEdge) {
        override def handleSuccessor(succ: Statement): Unit = {
          cfg.addPredsOfListener(new PredecessorListener(callSiteEdge) {
            override def handlePredecessor(pred: Statement): Unit = {
              val curr = new Node(new Edge[Statement, Statement](callSiteEdge, succ), query.variable)
              val callTrans = new Transition(wrap(curr.fact), curr.stmt, generateCallState(wrap(curr.fact), curr.stmt))
              callAutomaton.addTransition(callTrans)
              callAutomaton.addUnbalancedState(generateCallState(wrap(curr.fact), curr.stmt), target)
              val s = new PushNode(target.location, target.node.fact, new Edge[Statement, Statement](pred, callSiteEdge), PDSSystem.Calls)
              propagate(curr, s)
            }
          })
        }
      })
    }
  }

  override protected def computeNormalFlow(method: Method, currEdge: Edge[Statement, Statement], v: Val): mutable.HashSet[State] = {
    flowFunction.normalFlow(currEdge, v)
  }

  override protected def computeReturnFlow(method: Method, callerReturnStatement: Statement, value: Val): mutable.HashSet[_ <: State] = {
    flowFunction.returnFlow(method, callerReturnStatement, value).map(x => new PopNode(x, PDSSystem.Calls))
  }

  override def computeSuccessor(node: Node[Edge[Statement, Statement], Val]): Unit = {
    val edge = node.stmt
    val value = node.fact
    if (value.isInstanceOf[AllocVal]) throw new RuntimeException("value cannot be AllocVal")
    val method = edge.start.method
    if (method != null && !dataFlowScope.isExcluded(method) && !notUsedInMethod(method, edge.start, value)) {
      edge.start match {
        case statement: CallSiteStatement if edge.start.uses(value) =>
          callFlow(method, node, statement)
        case _ => if (icfg.isExitStmt(edge.start)) {
          returnFlow(method, node)
        } else {
          normalFlow(method, node)
        }
      }
    }
  }

  override def applyCallSummary(callSiteEdge: Edge[Statement, Statement], factAtSpInCallee: Val,
                                spInCallee: Edge[Statement, Statement], exitStmt: Edge[Statement, Statement], exitingFact: Val): Unit = {
    val out = mutable.HashSet.empty[Node[Edge[Statement, Statement], Val]]
    val callSite = callSiteEdge.target
    callSite match {
      case css: CallSiteStatement => {
        if (exitingFact.isReturnLocal) {
          out.add(new Node(callSiteEdge, callSite.asInstanceOf[Assignment].lhs))
        }
        css.getInvokeExpr.getArgs.zipWithIndex.foreach(a => {
          if (exitingFact.isParameterLocal(a._2)) {
            out.add(new Node(callSiteEdge, css.getInvokeExpr.getArg(a._2)))
          }
        })
      }
    }
    out.foreach(xs => {
      addNormalCallFlow(new Node(callSiteEdge, exitingFact), xs)
      addNormalFieldFlow(new Node(exitStmt, exitingFact), xs)
    })
  }

  protected def isMatchingCallSiteCalleePair(callSite: Statement, method: Method): Boolean = {
    val callSitesOfCall = mutable.HashSet.empty[Statement]
    icfg.addCallerListener(new CallerListener[Statement, Method] {

      override def getObservedCallee: Method = method

      override def onCallerAdded(stmt: Statement, callee: Method): Unit = callSitesOfCall.add(stmt)
    })
    callSitesOfCall.contains(callSite)
  }

  protected def notUsedInMethod(method: Method, statement: Statement, value: Val): Boolean = {
    !method.getLocals.contains(value)
  }

  protected def callFlow(caller: Method, curr: Node[Edge[Statement, Statement], Val], callSite: CallSiteStatement): Unit = {
    icfg.addCalleeListener(new CallSiteCalleeListener(curr, caller))
    val invokeExpr = callSite.getInvokeExpr
    if (invokeExpr.getResolvedMethod.nonEmpty && dataFlowScope.isExcluded(invokeExpr.getResolvedMethod.get)) {
      bypassFlowAtCallSite(caller, curr)
    }
  }

  protected def normalFlow(method: Method, currNode: Node[Edge[Statement, Statement], Val]): Unit = {
    val curr = currNode.stmt
    val value = currNode.fact
    curr.start.method.getCFG.getPredsOf(curr.start).foreach(pred => {
      val flow = computeNormalFlow(method, new Edge[Statement, Statement](pred, curr.start), value)
      flow.foreach(s => propagate(currNode, s))
    })
  }

  protected def returnFlow(method: Method, currNode: Node[Edge[Statement, Statement], Val]): Unit = {
    val outFlow = computeReturnFlow(method, currNode.stmt.target, currNode.fact)
    outFlow.foreach(s => propagate(currNode, s))
  }

  protected def bypassFlowAtCallSite(caller: Method, curr: Node[Edge[Statement, Statement], Val]): Unit = {
    curr.stmt.start.method.getCFG.getPredsOf(curr.stmt.start).foreach(returnSite => {
      val res = flowFunction.callToReturnFlow(new Edge[Statement, Statement](returnSite, curr.stmt.start), curr.fact)
      res.foreach(s => propagate(curr, s))
    })
  }

  protected def computeCallFlow(callSiteEdge: Edge[Statement, Statement],
                                fact: Val, callee: Method,
                                calleeStartEdge: Edge[Statement, Statement]): mutable.HashSet[_ <: State] = {
    val calleeSp = calleeStartEdge.target
    flowFunction.callFlow(callSiteEdge.target.asInstanceOf[CallSiteStatement], fact, callee, calleeSp).map(x => {
      new PushNode(calleeStartEdge, x, callSiteEdge, PDSSystem.Calls)
    })
  }

  override def toString: String = s"BackwardBoomerangSolver{query=$query}"

  protected class CallSiteCalleeListener(val curr: Node[Edge[Statement, Statement], Val],
                                         protected val caller: Method) extends CalleeListener[Statement, Method] {

    override def getObservedCaller: Statement = curr.stmt.start

    override def onCalleeAdded(callSite: Statement, callee: Method): Unit = {
      icfg.getStartPointsOf(callee).foreach(calleeSp => {
        callSite.method.getCFG.getPredsOf(callSite).foreach(predOfCall => {
          val res = computeCallFlow(
            new Edge[Statement, Statement](predOfCall, callSite),
            curr.fact, callee, new Edge[Statement, Statement](calleeSp, calleeSp))
          res.foreach(o => BackwardBoomerangSolver.this.propagate(curr, o))
        })
      })
    }

    override def onNoCalleeFound(): Unit = bypassFlowAtCallSite(caller, curr)

    def getOuterType: BackwardBoomerangSolver[W] = BackwardBoomerangSolver.this

    override def hashCode(): Int = Objects.hashCode(getOuterType, caller, curr)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: CallSiteCalleeListener => {
          Objects.equals(other.getOuterType, getOuterType) &&
            Objects.equals(other.caller, caller) &&
            Objects.equals(other.curr, curr)
        }
        case _ => false
      }
    }
  }
}
