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

import ca.ualberta.maple.swan.spds.analysis.boomerang.{BoomerangOptions, ForwardQuery}
import ca.ualberta.maple.swan.spds.analysis.boomerang.cfg.{ObservableCFG, PredecessorListener, SuccessorListener}
import ca.ualberta.maple.swan.spds.analysis.boomerang.cg.{CalleeListener, CallerListener, ObservableICFG}
import ca.ualberta.maple.swan.spds.analysis.boomerang.flowfunction.IForwardFlowFunction
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.Val.AllocVal
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{Assignment, CallSiteStatement, DataFlowScope, Field, FieldStoreStatement, InvokeExpr, Method, Statement, Type, Val}
import ca.ualberta.maple.swan.spds.analysis.pds.solver.SyncPDSSolver.PDSSystem
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes.{GeneratedState, INode, Node, PopNode, PushNode, SingleNode}
import ca.ualberta.maple.swan.spds.analysis.wpds.impl._
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.{Location, State, WPAStateListener}

import scala.collection.mutable

abstract class ForwardBoomerangSolver[W <: Weight](icfg: ObservableICFG[Statement, Method],
                                                   cfg: ObservableCFG,
                                                   val query: ForwardQuery,
                                                   genField: mutable.HashMap[(INode[Node[Edge[Statement, Statement], Val]], Field),
                                                     INode[Node[Edge[Statement, Statement], Val]]],
                                                   options: BoomerangOptions,
                                                   callSummaries: NestedWeightedPAutomatons[Edge[Statement, Statement], INode[Val], W],
                                                   fieldSummaries: NestedWeightedPAutomatons[Field, INode[Node[Edge[Statement, Statement], Val]], W],
                                                   scope: DataFlowScope, flowFunction: IForwardFlowFunction,
                                                   fieldLoadStatements: mutable.MultiDict[Field, Statement],
                                                   fieldStoreStatements: mutable.MultiDict[Field, Statement],
                                                   propagationType: Type)
  extends AbstractBoomerangSolver[W](icfg, cfg, genField, options, callSummaries, fieldSummaries, scope, propagationType) {

  this.flowFunction.setSolver(this, fieldLoadStatements, fieldStoreStatements)

  protected def overwriteFieldAtStatement(fieldWriteStatementEdge: Edge[Statement, Statement],
                                                   killedTransition: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]]): Unit

  override def processPush(curr: Node[Edge[Statement, Statement], Val], location: Location, succ: PushNode[Edge[Statement, Statement], Val, _], system: PDSSystem): Unit = {
    if (PDSSystem.Calls == system && (
      !succ.asInstanceOf[PushNode[Edge[Statement, Statement], Val, Edge[Statement, Statement]]].location.start.eq(curr.stmt.target) ||
      !curr.stmt.target.isInstanceOf[CallSiteStatement])) {
      throw new RuntimeException("Invalid push rule")
    }
    super.processPush(curr, location, succ, system)
  }

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
              val curr = new Node(new Edge[Statement, Statement](pred, callSiteEdge), query.variable.asInstanceOf[Val])
              val callTrans = new Transition(wrap(curr.fact), curr.stmt, generateCallState(wrap(curr.fact), curr.stmt))
              callAutomaton.addTransition(callTrans)
              callAutomaton.addUnbalancedState(generateCallState(wrap(curr.fact), curr.stmt), target)
              val s = new PushNode(target.location, target.node.fact, new Edge[Statement, Statement](callSiteEdge, succ), PDSSystem.Calls)
              propagate(curr, s)
            }
          })
        }
      })
    }
  }

  override protected def computeNormalFlow(method: Method, nextEdge: Edge[Statement, Statement], fact: Val): mutable.HashSet[State] = {
    flowFunction.normalFlow(query, nextEdge, fact)
  }

  override def computeReturnFlow(method: Method, curr: Statement, value: Val): mutable.HashSet[_ <: State] = {
    flowFunction.returnFlow(method, curr, value).map(x => new PopNode(x, PDSSystem.Calls))
  }

  override def computeSuccessor(node: Node[Edge[Statement, Statement], Val]): Unit = {
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
                  callFlow(method, node, new Edge[Statement, Statement](c.target, succ), statement.getInvokeExpr)
                case _ =>
                  checkForFieldOverwrite(c, value)
                  val out = computeNormalFlow(method, new Edge[Statement, Statement](c.target, succ), value)
                  out.foreach(s => propagate(node, s))
              }
            }
          }
        })
      }
    }
  }

  override def applyCallSummary(returnSiteStatement: Edge[Statement, Statement], factInCallee: Val,
                                spInCallee: Edge[Statement, Statement], lastCfgEdgeInCallee: Edge[Statement, Statement], returnedFact: Val): Unit = {
    val out = mutable.HashSet.empty[Node[Edge[Statement, Statement], Val]]
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

  protected def callFlow(caller: Method, currNode: Node[Edge[Statement, Statement], Val],
                         callSiteEdge: Edge[Statement, Statement], invokeExpr: InvokeExpr): Unit = {
    if (invokeExpr.getResolvedMethod.nonEmpty && dataFlowScope.isExcluded(invokeExpr.getResolvedMethod.get)) {
      bypassFlowAtCallSite(caller, currNode, callSiteEdge.start.asInstanceOf[CallSiteStatement])
    }
    icfg.addCalleeListener(new CallSiteCalleeListener(caller, callSiteEdge, currNode, invokeExpr))
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

  protected def bypassFlowAtCallSite(caller: Method, currNode: Node[Edge[Statement, Statement], Val], callSite: CallSiteStatement): Unit = {
    cfg.addSuccsOfListener(new SuccessorListener(currNode.stmt.target) {
      override def handleSuccessor(returnSite: Statement): Unit = {
        flowFunction.callToReturnFlow(query, new Edge[Statement, Statement](callSite, returnSite), currNode.fact).foreach(s => {
          propagate(currNode, s)
        })
      }
    })
  }

  protected def computeCallFlow(caller: Method, callSite: CallSiteStatement, succOfCallSite: Edge[Statement, Statement],
                                currNode: Node[Edge[Statement, Statement], Val], callee: Method,
                                calleeStartEdge: Edge[Statement, Statement]): mutable.HashSet[_ <: State] = {
    if (dataFlowScope.isExcluded(callee)) {
      bypassFlowAtCallSite(caller, currNode, callSite)
      mutable.HashSet.empty
    } else {
      flowFunction.callFlow(callSite, currNode.fact, callee).map(x => new PushNode(calleeStartEdge, x, succOfCallSite, PDSSystem.Calls))
    }
  }

  protected def checkForFieldOverwrite(curr: Edge[Statement, Statement], value: Val): Unit = {
    curr.target match {
      case statement: FieldStoreStatement if statement.getFieldStore.x.equals(value) =>
        val node = new Node(curr, value)
        fieldAutomaton.registerListener(new OverwriteAtFieldStore(new SingleNode(node), curr))
      case _ =>
    }
  }

  override def toString: String = s"ForwardBoomerangSolver{query=$query}"

  protected class CallSiteCalleeListener(protected val caller: Method,
                                         protected val callSiteEdge: Edge[Statement, Statement],
                                         val currNode: Node[Edge[Statement, Statement], Val],
                                         invokeExpr: InvokeExpr) extends CalleeListener[Statement, Method] {

    private val callSite: CallSiteStatement = callSiteEdge.start.asInstanceOf[CallSiteStatement]

    override def getObservedCaller: CallSiteStatement = callSite

    override def onCalleeAdded(callSite: Statement, callee: Method): Unit = {
      icfg.getStartPointsOf(callee).foreach(calleeSp => {
        val res = computeCallFlow(caller, callSite.asInstanceOf[CallSiteStatement],
          callSiteEdge, currNode, callee, new Edge[Statement, Statement](calleeSp, calleeSp))
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

  protected class OverwriteAtFieldStore(state: INode[Node[Edge[Statement, Statement], Val]],
                                        protected val nextEdge: Edge[Statement, Statement])
      extends WPAStateListener[Field, INode[Node[Edge[Statement, Statement], Val]], W](state) {

    override def onOutTransitionAdded(t: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]], w: W,
                                      weightedPAutomaton: WeightedPAutomaton[Field, INode[Node[Edge[Statement, Statement], Val]], W]): Unit = {
      if (t.getLabel.equals(nextEdge.target.asInstanceOf[FieldStoreStatement].getFieldStore.y)) {
        overwriteFieldAtStatement(nextEdge, t)
      }
    }

    override def onInTransitionAdded(t: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]], w: W,
                                     weightedPAutomaton: WeightedPAutomaton[Field, INode[Node[Edge[Statement, Statement], Val]], W]): Unit = {}

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
