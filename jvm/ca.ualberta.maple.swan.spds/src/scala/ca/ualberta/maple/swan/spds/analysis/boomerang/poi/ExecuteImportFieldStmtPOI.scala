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

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{CallSiteStatement, Field, FieldStoreStatement, Statement, Val}
import ca.ualberta.maple.swan.spds.analysis.boomerang.solver.{AbstractBoomerangSolver, ControlFlowEdgeBasedCallTransitionListener, ControlFlowEdgeBasedFieldTransitionListener, ForwardBoomerangSolver}
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes.{GeneratedState, INode, Node, SingleNode}
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.{Transition, Weight, WeightedPAutomaton}
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.{WPAStateListener, WPAUpdateListener}

import scala.collection.mutable

abstract class ExecuteImportFieldStmtPOI[W <: Weight](protected val baseSolver: ForwardBoomerangSolver[W],
                                             protected val flowSolver: ForwardBoomerangSolver[W],
                                             protected val poi: AbstractPOI[Edge, Val, Field]) {

  protected val baseAutomaton: WeightedPAutomaton[Field, INode[Node[Edge, Val]], W] = baseSolver.fieldAutomaton
  protected val flowAutomaton: WeightedPAutomaton[Field, INode[Node[Edge, Val]], W] = flowSolver.fieldAutomaton
  protected val reachable: mutable.HashSet[INode[Node[Edge, Val]]] = mutable.HashSet.empty
  protected val delayedTransitions: mutable.MultiDict[INode[Node[Edge, Val]], InsertFieldTransitionCallback] = mutable.MultiDict.empty
  protected val curr: Edge = poi.cfgEdge
  protected val baseVar: Val = poi.baseVar
  protected val storedVar: Val = poi.storedVar
  protected val field: Field = poi.field
  protected var active: Boolean = false
  protected val MAX_IMPORT_DEPTH: Int = -1

  def solve(): Unit = {
    if (!baseSolver.equals(flowSolver)) {
      baseSolver.registerStatementFieldTransitionListener(new BaseVarPointsTo(curr, this))
    }
  }

  protected def flowsTo(): Unit = {
    if (!active) {
      active = true
      handlingAtFieldStatements()
      handlingAtCallSites()
    }
  }

  protected def handlingAtFieldStatements(): Unit = {
    baseSolver.registerStatementFieldTransitionListener(new ImportIndirectAliases(curr, this.flowSolver, this.baseSolver))
    flowSolver.registerStatementCallTransitionListener(new ImportIndirectCallAliases(curr, this.flowSolver))
  }

  protected def handlingAtCallSites(): Unit = {
    flowSolver.callAutomaton.registerListener(new ForAnyCallSiteOrExitStmt(this.baseSolver))
  }

  protected def importFieldTransitionsStartingAt(t: Transition[Field, INode[Node[Edge, Val]]], importDepth: Int): Unit = {
    if (!(MAX_IMPORT_DEPTH > 0 && importDepth > MAX_IMPORT_DEPTH)) {
      if (!t.label.equals(Field.epsilon)) {
        if (t.label.equals(Field.empty)) {
          if (baseSolver.fieldAutomaton.isUnbalancedState(t.target)) activate(t.start)
        } else if (t.target.isInstanceOf[GeneratedState[_, _]]) {
          queueOrAdd(t)
          baseSolver.fieldAutomaton.registerListener(new ImportFieldTransitionsFrom(t.target, flowSolver, importDepth + 1))
        }
      }
    }
  }

  def addReachable(node: INode[Node[Edge, Val]]): Unit = {
    if (reachable.add(node)) {
      delayedTransitions.get(node).foreach(callback => callback.trigger())
    }
  }

  protected def queueOrAdd(t: Transition[Field, INode[Node[Edge, Val]]]): Unit = {
    if (reachable.contains(t.target)) {
      flowSolver.fieldAutomaton.addTransition(t)
      addReachable(t.start)
    } else {
      delayedTransitions.addOne(t.target, new InsertFieldTransitionCallback(t))
    }
  }

  def activate(start: INode[Node[Edge, Val]]): Unit

  def trigger(start: INode[Node[Edge, Val]]): Unit = {
    val intermediateState = flowSolver.fieldAutomaton.createState(new SingleNode(new Node(curr, baseVar)), field)
    val connectingTrans = new Transition(start, field, intermediateState)
    flowSolver.fieldAutomaton.addTransition(connectingTrans)
    addReachable(connectingTrans.start)
  }

  override def hashCode(): Int = Objects.hashCode(baseSolver, flowSolver, curr)

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: ExecuteImportFieldStmtPOI[W] => {
        Objects.equals(other.baseSolver, baseSolver) && Objects.equals(other.flowSolver, flowSolver) && Objects.equals(other.curr, curr)
      }
      case _ => false
    }
  }

  protected class InsertFieldTransitionCallback(protected val trans: Transition[Field, INode[Node[Edge, Val]]]) {

    def trigger(): Unit = {
      flowSolver.fieldAutomaton.addTransition(trans)
      addReachable(trans.start)
    }

    override def hashCode(): Int = Objects.hashCode(trans)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: InsertFieldTransitionCallback => Objects.equals(other.trans, trans)
        case _ => false
      }
    }
  }

  protected class BaseVarPointsTo(curr: Edge, protected val poi: ExecuteImportFieldStmtPOI[W]) extends ControlFlowEdgeBasedFieldTransitionListener[W](curr) {

    override def onAddedTransition(t: Transition[Field, INode[Node[Edge, Val]]]): Unit = {
      val aliasedVariableAtStmt = t.start
      if (!active && !aliasedVariableAtStmt.isInstanceOf[GeneratedState[_, _]]) {
        val alias = aliasedVariableAtStmt.fact().fact
        if (alias.equals(poi.baseVar) && t.label.equals(Field.empty)) flowsTo()
      }
    }

    override def hashCode(): Int = super.hashCode + Objects.hashCode(poi)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: BaseVarPointsTo => super.equals(other) && Objects.equals(other.poi, poi)
        case _ => false
      }
    }
  }

  protected class ImportIndirectAliases(succ: Edge,
                                        protected val flowSolver: ForwardBoomerangSolver[W],
                                        protected val baseSolver: ForwardBoomerangSolver[W]) extends ControlFlowEdgeBasedFieldTransitionListener[W](succ) {

    override def onAddedTransition(t: Transition[Field, INode[Node[Edge, Val]]]): Unit = {
      if (!t.label.equals(Field.epsilon) && !t.start.isInstanceOf[GeneratedState[_, _]]) {
        importFieldTransitionsStartingAt(t, 0)
      }
    }

    override def hashCode(): Int = super.hashCode + Objects.hashCode(baseSolver, flowSolver)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: ImportIndirectAliases => super.equals(other) &&
          Objects.equals(other.baseSolver, baseSolver) && Objects.equals(other.flowSolver, flowSolver)
        case _ => false
      }
    }
  }

  protected class ImportIndirectCallAliases(stmt: Edge, protected val flowSolver: AbstractBoomerangSolver[W]) extends ControlFlowEdgeBasedCallTransitionListener[W](stmt) {

    override def onAddedTransition(t: Transition[Edge, INode[Val]], w: W): Unit = {
      if (t.start.fact().equals(storedVar)) {
        baseSolver.registerStatementCallTransitionListener(new ImportIndirectCallAliasesAtSucc(curr, t.target, w))
      }
    }

    override def hashCode(): Int = super.hashCode() + Objects.hashCode(flowSolver)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: ImportIndirectCallAliases => super.equals(other) && Objects.equals(other.flowSolver, flowSolver)
      }
    }
  }

  protected class ImportIndirectCallAliasesAtSucc(succ: Edge, protected val target: INode[Val],
                                                  protected val w: W) extends ControlFlowEdgeBasedCallTransitionListener[W](succ) {

    override def onAddedTransition(t: Transition[Edge, INode[Val]], w: W): Unit = {
      cfgEdge.start match {
        case statement: FieldStoreStatement if !statement.getFieldWrite.x.equals(t.start.fact()) =>
          flowSolver.callAutomaton.addWeightForTransition(new Transition(t.start, t.label, target), this.w)
        case _ =>
      }
    }

    override def hashCode(): Int = super.hashCode() + Objects.hashCode(target, w)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: ImportIndirectCallAliasesAtSucc => super.equals(other) &&
          Objects.equals(other.target, target) && Objects.equals(other.w, w)
        case _ => false
      }
    }
  }

  protected class ForAnyCallSiteOrExitStmt(protected val baseSolver: ForwardBoomerangSolver[W]) extends WPAUpdateListener[Edge, INode[Val], W] {

    override def onWeightAdded(t: Transition[Edge, INode[Val]], w: W, aut: WeightedPAutomaton[Edge, INode[Val], W]): Unit = {
      if (flowSolver.callAutomaton.isUnbalancedState(t.target) && !t.label.equals(new Edge(Statement.epsilon, Statement.epsilon))) {
        val edge = t.label
        val callSite = edge.start
        callSite match {
          case statement: CallSiteStatement =>
            if (!statement.lhs.equals(t.start.fact()) && callSite.uses(t.start.fact())) {
              importSolvers(edge, t.start, t.target, w)
            }
          case _ =>
        }
      }
    }

    protected def importSolvers(callSiteOrExitStmt: Edge, start: INode[Val], node: INode[Val], w: W): Unit = {
      baseSolver.registerStatementCallTransitionListener(new ImportOnReachStatement(flowSolver, callSiteOrExitStmt))
      baseSolver.registerStatementCallTransitionListener(new ImportTransitionFromCall(flowSolver, callSiteOrExitStmt, start, node, w))
    }

    override def hashCode(): Int = Objects.hashCode(baseSolver)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: ForAnyCallSiteOrExitStmt => Objects.equals(other.baseSolver, baseSolver)
        case _ => false
      }
    }
  }

  protected class ImportFieldTransitionsFrom(target: INode[Node[Edge, Val]],
                                             protected val flowSolver: ForwardBoomerangSolver[W],
                                             importDepth: Int) extends WPAStateListener[Field, INode[Node[Edge, Val]], W](target) {

    override def onOutTransitionAdded(t: Transition[Field, INode[Node[Edge, Val]]], w: W, weightedPAutomaton: WeightedPAutomaton[Field, INode[Node[Edge, Val]], W]): Unit = {
      if (!t.label.equals(Field.epsilon)) {
        importFieldTransitionsStartingAt(t, importDepth)
      }
    }

    override def onInTransitionAdded(t: Transition[Field, INode[Node[Edge, Val]]], w: W, weightedPAutomaton: WeightedPAutomaton[Field, INode[Node[Edge, Val]], W]): Unit = {}

    override def hashCode: Int = super.hashCode + Objects.hashCode(flowSolver)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: ImportFieldTransitionsFrom => super.equals(other) && Objects.equals(other.flowSolver, flowSolver)
        case _ => false
      }
    }
  }

  protected class ImportOnReachStatement(protected val flowSolver: ForwardBoomerangSolver[W],
                                         callSiteOrExitStmt: Edge) extends ControlFlowEdgeBasedCallTransitionListener[W](callSiteOrExitStmt) {

    override def onAddedTransition(t: Transition[Edge, INode[Val]], w: W): Unit = {
      if (!t.start.isInstanceOf[GeneratedState[_, _]] && t.label.equals(cfgEdge)) {
        baseSolver.registerStatementFieldTransitionListener(
          new CallSiteOrExitStmtFieldImport(flowSolver, baseSolver, new Node(t.label, t.start.fact())))
      }
    }

    override def hashCode(): Int = super.hashCode() + Objects.hashCode(flowSolver)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: ImportOnReachStatement => super.equals(other) && Objects.equals(other.flowSolver, flowSolver)
      }
    }
  }

  protected class ImportTransitionFromCall(protected val flowSolver: ForwardBoomerangSolver[W], stmt: Edge,
                                           start: INode[Val], protected val target: INode[Val],
                                           protected val w: W) extends ControlFlowEdgeBasedCallTransitionListener[W](stmt) {

    override def onAddedTransition(t: Transition[Edge, INode[Val]], w: W): Unit = {
      if (!t.start.isInstanceOf[GeneratedState[_, _]] && !t.start.equals(start) && t.start.fact().method.equals(t.label.start.method)) {
        flowSolver.callAutomaton.addWeightForTransition(new Transition(t.start, t.label, target), this.w)
      }
    }

    override def hashCode(): Int = Objects.hashCode(flowSolver, target, w)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: ImportTransitionFromCall => {
          Objects.equals(other.flowSolver, flowSolver) && Objects.equals(other.target, target) && Objects.equals(other.w, w)
        }
        case _ => false
      }
    }
  }

  protected class CallSiteOrExitStmtFieldImport(protected val flowSolver: ForwardBoomerangSolver[W],
                                                protected val baseSolver: ForwardBoomerangSolver[W],
                                                protected val reachableNode: Node[Edge, Val]) extends ControlFlowEdgeBasedFieldTransitionListener[W](reachableNode.stmt) {

    override def onAddedTransition(t: Transition[Field, INode[Node[Edge, Val]]]): Unit = {
      if (!t.label.equals(Field.epsilon) && !t.start.isInstanceOf[GeneratedState[_, _]] && t.start.fact().fact.equals(reachableNode.fact)) {
        importFieldTransitionsStartingAt(t, 0)
      }
    }

    override def hashCode(): Int = super.hashCode() + Objects.hashCode(baseSolver, flowSolver, reachableNode.fact)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: CallSiteOrExitStmtFieldImport => {
          super.equals(other) && Objects.equals(other.baseSolver, baseSolver) &&
            Objects.equals(other.flowSolver, flowSolver) && Objects.equals(other.reachableNode.fact, reachableNode.fact)
        }
        case _ => false
      }
    }
  }
}
