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

import ca.ualberta.maple.swan.spds.analysis.boomerang.{BoomerangOptions, ForwardQuery, IForwardFlowFunction}
import ca.ualberta.maple.swan.spds.analysis.boomerang.cfg.{ObservableCFG, PredecessorListener, SuccessorListener}
import ca.ualberta.maple.swan.spds.analysis.boomerang.cg.{CalleeListener, CallerListener, ObservableICFG}
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.Val.AllocVal
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{Assignment, CallSiteStatement, ControlFlowGraph, DataFlowScope, Field, FieldStoreStatement, InvokeExpr, Method, Statement, Type, Val}
import ca.ualberta.maple.swan.spds.analysis.pds.solver.SyncPDSSolver.PDSSystem
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes.{GeneratedState, INode, Node, PopNode, PushNode, SingleNode}
import ca.ualberta.maple.swan.spds.analysis.wpds.impl._
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.{Location, State, WPAStateListener}

import scala.collection.mutable

abstract class ForwardBoomerangSolver[W <: Weight](icfg: ObservableICFG[Statement, Method],
                                                   cfg: ObservableCFG,
                                                   val query: ForwardQuery,
                                                   genField: mutable.HashMap[(INode[Node[ControlFlowGraph.Edge, Val]], Field),
                                                     INode[Node[ControlFlowGraph.Edge, Val]]],
                                                   options: BoomerangOptions,
                                                   callSummaries: NestedWeightedPAutomatons[ControlFlowGraph.Edge, INode[Val], W],
                                                   fieldSummaries: NestedWeightedPAutomatons[Field, INode[Node[ControlFlowGraph.Edge, Val]], W],
                                                   scope: DataFlowScope, flowFunction: IForwardFlowFunction,
                                                   fieldLoadStatements: mutable.MultiDict[Field, Statement],
                                                   fieldStoreStatements: mutable.MultiDict[Field, Statement],
                                                   propagationType: Type)
  extends AbstractBoomerangSolver[W](icfg, cfg, genField, options, callSummaries, fieldSummaries, scope, propagationType) {

  this.flowFunction.setSolver(this, fieldLoadStatements, fieldStoreStatements)

  protected def overwriteFieldAtStatement(fieldWriteStatementEdge: ControlFlowGraph.Edge,
                                                   killedTransition: Transition[Field, INode[Node[ControlFlowGraph.Edge, Val]]]): Unit

  override def processPush(curr: Node[ControlFlowGraph.Edge, Val], location: Location, succ: PushNode[ControlFlowGraph.Edge, Val, _], system: PDSSystem): Unit = {
    if (PDSSystem.Calls == system && (
      !succ.asInstanceOf[PushNode[ControlFlowGraph.Edge, Val, ControlFlowGraph.Edge]].location.start.eq(curr.stmt.target) ||
      !curr.stmt.target.isInstanceOf[CallSiteStatement])) {
      throw new RuntimeException("Invalid push rule")
    }
    super.processPush(curr, location, succ, system)
  }

  override protected def propagateUnbalancedToCallSite(callSiteEdge: Statement, transInCallee: Transition[ControlFlowGraph.Edge, INode[Val]]): Unit = {

    if (!callSiteEdge.isInstanceOf[CallSiteStatement]) {
      throw new RuntimeException("Invalid propagate unbalanced return")
    }

    val target = transInCallee.getTarget.asInstanceOf[GeneratedState[Val, ControlFlowGraph.Edge]]

    if (isMatchingCallSiteCalleePair(callSiteEdge, transInCallee.getLabel.getMethod)) {
      cfg.addSuccsOfListener(new SuccessorListener(callSiteEdge) {
        override def handleSuccessor(succ: Statement): Unit = {
          cfg.addPredsOfListener(new PredecessorListener(callSiteEdge) {
            override def handlePredecessor(pred: Statement): Unit = {
              val curr = new Node(new ControlFlowGraph.Edge(pred, callSiteEdge), query.variable)
              val callTrans = new Transition(wrap(curr.fact), curr.stmt, generateCallState(wrap(curr.fact), curr.stmt))
              callAutomaton.addTransition(callTrans)
              callAutomaton.addUnbalancedState(generateCallState(wrap(curr.fact), curr.stmt), target)
              val s = new PushNode(target.location, target.node.fact(), new ControlFlowGraph.Edge(callSiteEdge, succ), PDSSystem.Calls)
              propagate(curr, s)
            }
          })
        }
      })
    }
  }

  override protected def computeNormalFlow(method: Method, nextEdge: ControlFlowGraph.Edge, fact: Val): mutable.HashSet[State] = {
    flowFunction.normalFlow(query, nextEdge, fact)
  }

  override def computeReturnFlow(method: Method, curr: Statement, value: Val): mutable.HashSet[_ <: State] = {
    flowFunction.returnFlow(method, curr, value).map(x => new PopNode(x, PDSSystem.Calls))
  }

  override def computeSuccessor(node: Node[ControlFlowGraph.Edge, Val]): Unit = {
    val c = node.stmt
    val value = node.fact
    if (value.isInstanceOf[AllocVal]) throw new RuntimeException("value cannot be AllocVal")
    val method = c.target.method
    if (method != null && !dataFlowScope.isExcluded(method)) {
      if (icfg.isExitStmt(c.target)) {
        returnFlow(method, node)
      } else {
        cfg.addSuccsOfListener(new SuccessorListener(c.target) {

          override def handleSuccessor(succ: Statement): Unit = {
            if (method.getLocals.contains(value)) {
              c.target match {
                case statement: CallSiteStatement if statement.isParameter(value) =>
                  callFlow(method, node, new ControlFlowGraph.Edge(c.target, succ), statement.getInvokeExpr)
                case _ =>
                  checkForFieldOverwrite(c, value)
                  val out = computeNormalFlow(method, new ControlFlowGraph.Edge(c.target, succ), value)
                  out.foreach(s => propagate(node, s))
              }
            }
          }
        })
      }
    }
  }

  override def applyCallSummary(returnSiteStatement: ControlFlowGraph.Edge, factInCallee: Val,
                                spInCallee: ControlFlowGraph.Edge, lastCfgEdgeInCallee: ControlFlowGraph.Edge, returnedFact: Val): Unit = {
    val out = mutable.HashSet.empty[Node[ControlFlowGraph.Edge, Val]]
    val callSite = returnSiteStatement.start
    callSite match {
      case css: CallSiteStatement => {
        if (returnedFact.isReturnLocal) {
          out.add(new Node(returnSiteStatement, callSite.asInstanceOf[Assignment].lhs))
        }
        css.getInvokeExpr.getArgs.zipWithIndex.foreach(a => {
          if (returnedFact.isParameterLocal(a._2)) {
            out.add(new Node(returnSiteStatement, css.getInvokeExpr.getArg(a._2)))
          }
        })
      }
    }
    out.foreach(xs => {
      addNormalCallFlow(new Node(returnSiteStatement, returnedFact), xs)
      addNormalFieldFlow(new Node(lastCfgEdgeInCallee, returnedFact), xs)
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

  protected def callFlow(caller: Method, currNode: Node[ControlFlowGraph.Edge, Val],
                         callSiteEdge: ControlFlowGraph.Edge, invokeExpr: InvokeExpr): Unit = {
    if (!icfg.isCallStmt(callSiteEdge.start)) throw new RuntimeException
    if (dataFlowScope.isExcluded(invokeExpr.getMethod)) {
      bypassFlowAtCallSite(caller, currNode, callSiteEdge.start)
    }
    icfg.addCalleeListener(new CallSiteCalleeListener(caller, callSiteEdge, currNode, invokeExpr))
  }

  protected def normalFlow(method: Method, currNode: Node[ControlFlowGraph.Edge, Val]): Unit = {
    val curr = currNode.stmt
    val value = currNode.fact
    curr.start.method.getCFG.getPredsOf(curr.start).foreach(pred => {
      val flow = computeNormalFlow(method, new ControlFlowGraph.Edge(pred, curr.start), value)
      flow.foreach(s => propagate(currNode, s))
    })
  }

  protected def returnFlow(method: Method, currNode: Node[ControlFlowGraph.Edge, Val]): Unit = {
    val outFlow = computeReturnFlow(method, currNode.stmt.target, currNode.fact)
    outFlow.foreach(s => propagate(currNode, s))
  }

  protected def bypassFlowAtCallSite(caller: Method, currNode: Node[ControlFlowGraph.Edge, Val], callSite: Statement): Unit = {
    cfg.addSuccsOfListener(new SuccessorListener(currNode.stmt.target) {
      override def handleSuccessor(returnSite: Statement): Unit = {
        flowFunction.callToReturnFlow(query, new ControlFlowGraph.Edge(callSite, returnSite), currNode.fact).foreach(s => {
          propagate(currNode, s)
        })
      }
    })
  }

  protected def computeCallFlow(caller: Method, callSite: Statement, succOfCallSite: ControlFlowGraph.Edge,
                                currNode: Node[ControlFlowGraph.Edge, Val], callee: Method,
                                calleeStartEdge: ControlFlowGraph.Edge): mutable.HashSet[_ <: State] = {
    if (dataFlowScope.isExcluded(callee)) {
      bypassFlowAtCallSite(caller, currNode, callSite)
      mutable.HashSet.empty
    } else {
      flowFunction.callFlow(callSite, currNode.fact, callee).map(x => new PushNode(calleeStartEdge, x, succOfCallSite, PDSSystem.Calls))
    }
  }

  protected def checkForFieldOverwrite(curr: ControlFlowGraph.Edge, value: Val): Unit = {
    curr.target match {
      case statement: FieldStoreStatement if statement.getFieldWrite.x.equals(value) =>
        val node = new Node(curr, value)
        fieldAutomaton.registerListener(new OverwriteAtFieldStore(new SingleNode(node), curr))
      case _ =>
    }
  }

  override def toString: String = s"ForwardBoomerangSolver{query=$query}"

  protected class CallSiteCalleeListener(protected val caller: Method,
                                         protected val callSiteEdge: ControlFlowGraph.Edge,
                                         val currNode: Node[ControlFlowGraph.Edge, Val], invokeExpr: InvokeExpr) extends CalleeListener[Statement, Method] {

    private val callSite = callSiteEdge.start

    override def getObservedCaller: Statement = callSite

    override def onCalleeAdded(callSite: Statement, callee: Method): Unit = {
      icfg.getStartPointsOf(callee).foreach(calleeSp => {
        val res = computeCallFlow(caller, callSite, callSiteEdge, currNode, callee, new ControlFlowGraph.Edge(calleeSp, calleeSp))
        res.foreach(s => propagate(currNode, s))
      })
    }

    override def onNoCalleeFound(): Unit = bypassFlowAtCallSite(caller, currNode, callSite)

    def getOuterType: ForwardBoomerangSolver[W] = ForwardBoomerangSolver.this

    override def hashCode(): Int = {
      getOuterType.hashCode() + Objects.hashCode(getOuterType, callSiteEdge, caller, currNode)
    }

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: CallSiteCalleeListener => {
          Objects.equals(other.getOuterType, getOuterType) &&
            Objects.equals(other.callSiteEdge, callSiteEdge) &&
            Objects.equals(other.caller, caller) &&
            Objects.equals(other.currNode, currNode)
        }
        case _ => false
      }
    }
  }

  protected class OverwriteAtFieldStore(state: INode[Node[ControlFlowGraph.Edge, Val]],
                                        protected val nextEdge: ControlFlowGraph.Edge)
      extends WPAStateListener[Field, INode[Node[ControlFlowGraph.Edge, Val]], W](state) {

    override def onOutTransitionAdded(t: Transition[Field, INode[Node[ControlFlowGraph.Edge, Val]]], w: W,
                                      weightedPAutomaton: WeightedPAutomaton[Field, INode[Node[ControlFlowGraph.Edge, Val]], W]): Unit = {
      if (t.getLabel.equals(nextEdge.target.asInstanceOf[FieldStoreStatement].getFieldWrite.y)) {
        overwriteFieldAtStatement(nextEdge, t)
      }
    }

    override def onInTransitionAdded(t: Transition[Field, INode[Node[ControlFlowGraph.Edge, Val]]], w: W,
                                     weightedPAutomaton: WeightedPAutomaton[Field, INode[Node[ControlFlowGraph.Edge, Val]], W]): Unit = {}

    def getOuterType: ForwardBoomerangSolver[W] = ForwardBoomerangSolver.this

    override def hashCode: Int = super.hashCode + Objects.hashCode(getOuterType, nextEdge)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: OverwriteAtFieldStore => {
          super.equals(other) && Objects.equals(getOuterType, other.getOuterType) && Objects.equals(nextEdge, other.nextEdge)
        }
        case _ => false
      }
    }
  }
}
