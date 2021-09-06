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

package ca.ualberta.maple.swan.spds.analysis.pds.solver

import java.util.Objects

import ca.ualberta.maple.swan.spds.analysis.pds.solver.SyncPDSSolver.PDSSystem
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes._
import ca.ualberta.maple.swan.spds.analysis.wpds.impl._
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.{Location, State, SummaryListener, WPAStateListener, WPAUpdateListener}

import scala.collection.mutable

object SyncPDSSolver {

  trait PDSSystem
  object PDSSystem {
    case object Fields extends PDSSystem
    case object Calls extends PDSSystem
  }
}

abstract class SyncPDSSolver[Stmt <: Location, Fact, Field <: Location, W <: Weight](useCallSummaries: Boolean,
                                                                            callSummaries: NestedWeightedPAutomatons[Stmt, INode[Fact], W],
                                                                            useFieldSummaries: Boolean,
                                                                            fieldSummaries: NestedWeightedPAutomatons[Field, INode[Node[Stmt, Fact]], W],
                                                                            maxCallDepth: Int,
                                                                            maxFieldDepth: Int,
                                                                            maxUnbalancedCallDepth: Int) {

  protected val generatedFieldState: mutable.HashMap[(INode[Node[Stmt, Fact]], Field), INode[Node[Stmt, Fact]]] = mutable.HashMap.empty
  protected val generatedCallState: mutable.HashMap[(INode[Fact], Stmt), INode[Fact]] = mutable.HashMap.empty
  val reachedStates: mutable.HashSet[Node[Stmt, Fact]] = mutable.HashSet.empty
  protected val callingContextReachable: mutable.HashSet[Node[Stmt, Fact]] = mutable.HashSet.empty
  protected val fieldContextReachable: mutable.HashSet[Node[Stmt, Fact]] = mutable.HashSet.empty
  protected val updateListeners: mutable.HashSet[SyncPDSUpdateListener[Stmt, Fact]] = mutable.HashSet.empty
  protected val reachedStateUpdateListeners: mutable.MultiDict[Node[Stmt, Fact], SyncStatePDSUpdateListener[Stmt, Fact]] = mutable.MultiDict.empty
  protected val summaries: mutable.HashSet[Summary] = mutable.HashSet.empty
  protected val summaryListeners: mutable.HashSet[OnAddedSummaryListener] = mutable.HashSet.empty

  protected val callingPDS: WeightedPushdownSystem[Stmt, INode[Fact], W] = new WeightedPushdownSystem[Stmt, INode[Fact], W]() {
    override def toString: String = s"Call ${SyncPDSSolver.this.toString}"
  }

  protected val fieldPDS: WeightedPushdownSystem[Field, INode[Node[Stmt, Fact]], W] = new WeightedPushdownSystem[Field, INode[Node[Stmt, Fact]], W]() {
    override def toString: String = s"Field ${SyncPDSSolver.this.toString}"
  }

  val fieldAutomaton: WeightedPAutomaton[Field, INode[Node[Stmt, Fact]], W] = {
    new WeightedPAutomaton[Field, INode[Node[Stmt, Fact]], W] {

      override def createState(d: INode[Node[Stmt, Fact]], loc: Field): INode[Node[Stmt, Fact]] = {
        if (loc.equals(emptyField)) d else generateFieldState(d, loc)
      }

      override def getOne: W = getFieldWeights.getOne

      override def nested: Boolean = useFieldSummaries

      override def isGeneratedState(d: INode[Node[Stmt, Fact]]): Boolean = d.isInstanceOf[GeneratedState[_, _]]

      override def epsilon: Field = epsilonField

      override def getMaxDepth: Int = maxFieldDepth

      override def addWeightForTransition(trans: Transition[Field, INode[Node[Stmt, Fact]]], weight: W): Boolean = {
        if (preventFieldTransitionAdd(trans, weight)) false else {
          super.addWeightForTransition(trans, weight)
        }
      }
    }
  }

  val callAutomaton: WeightedPAutomaton[Stmt, INode[Fact], W] = {
    new WeightedPAutomaton[Stmt, INode[Fact], W] {

      override def createState(d: INode[Fact], loc: Stmt): INode[Fact] = generateCallState(d, loc)

      override def getOne: W = getCallWeights.getOne

      override def nested: Boolean = useCallSummaries

      override def isGeneratedState(d: INode[Fact]): Boolean = d.isInstanceOf[GeneratedState[_, _]]

      override def epsilon: Stmt = epsilonStmt

      override def addWeightForTransition(trans: Transition[Stmt, INode[Fact]], weight: W): Boolean = {
        if (preventCallTransitionAdd(trans, weight)) false else {
          super.addWeightForTransition(trans, weight)
        }
      }

      override def getMaxDepth: Int = maxCallDepth

      override def getMaxUnbalancedDepth: Int = maxUnbalancedCallDepth
    }
  }

  callAutomaton.registerListener(new CallAutomatonListener())
  fieldAutomaton.registerListener(new FieldUpdateListener())
  if (callAutomaton.nested) callAutomaton.registerNestedAutomatonListener(new CallSummaryListener())
  callingPDS.postStar(callAutomaton, callSummaries)
  fieldPDS.postStar(fieldAutomaton, fieldSummaries)

  def getFieldWeights: WeightFunctions[Stmt, Fact, Field, W]

  def getCallWeights: WeightFunctions[Stmt, Fact, Stmt, W]

  def fieldWildCard: Field

  def exclusionFieldWildCard(exclusion: Field): Field

  def epsilonStmt: Stmt

  def emptyField: Field

  def epsilonField: Field

  def computeSuccessor(node: Node[Stmt, Fact]): Unit

  def applyCallSummary(callSite: Stmt, factInCallee: Fact, spInCallee: Stmt, exitStmt: Stmt, returnedFact: Fact): Unit

  protected def preventFieldTransitionAdd(trans: Transition[Field, INode[Node[Stmt, Fact]]], weight: W): Boolean = false

  protected def preventCallTransitionAdd(trans: Transition[Stmt, INode[Fact]], weight: W): Boolean = false

  def solve(curr: Node[Stmt, Fact], field: Field, fieldTarget: INode[Node[Stmt, Fact]],
            stmt: Stmt, callTarget: INode[Fact], weight: W): Unit = {
    fieldAutomaton.addInitialState(fieldTarget)
    callAutomaton.addInitialState(callTarget)
    val start = asFieldFact(curr)
    if (!field.equals(emptyField)) {
      val gfs = generateFieldState(start, field)
      val fieldTrans = new Transition[Field, INode[Node[Stmt, Fact]]](start, field, gfs)
      fieldAutomaton.addTransition(fieldTrans)
      val fieldTransToInitial = new Transition[Field, INode[Node[Stmt, Fact]]](gfs, emptyField, fieldTarget)
      fieldAutomaton.addTransition(fieldTransToInitial)
    } else {
      val fieldTrans = new Transition[Field, INode[Node[Stmt, Fact]]](start, emptyField, fieldTarget)
      fieldAutomaton.addTransition(fieldTrans)
    }
    val callTrans = new Transition[Stmt, INode[Fact]](wrap(curr.fact), curr.stmt, callTarget)
    callAutomaton.addWeightForTransition(callTrans, weight)
    processNode(curr)
  }

  def solve(curr: Node[Stmt, Fact], field: Field, fieldTarget: INode[Node[Stmt, Fact]],
            stmt: Stmt, callTarget: INode[Fact]): Unit = {
    solve(curr, field, fieldTarget, stmt, callTarget, getCallWeights.getOne)
  }

  def processNode(curr: Node[Stmt, Fact]): Unit = {
    if (addReachableState(curr)) {
      computeSuccessor(curr)
    }
  }

  def propagate(curr: Node[Stmt, Fact], s: State): Unit = {
    s match {
      case node: PushNode[Stmt, Fact, _] => processPush(curr, node.location.asInstanceOf[Location], node, node.system)
      case node: PopNode[_] => processPop(curr, node)
      case node: Node[Stmt, Fact] => processNormal(curr, node)
      case _ => throw new RuntimeException("unexpected node type")
    }
  }

  def addReachableState(curr: Node[Stmt, Fact]): Boolean = {
    if (reachedStates.contains(curr)) false else {
      reachedStates.add(curr)
      updateListeners.foreach(l => l.onReachableNodeAdded(curr))
      reachedStateUpdateListeners.get(curr).foreach(l => l.reachable())
      true
    }
  }

  def registerListener(listener: SyncPDSUpdateListener[Stmt, Fact]): Unit = {
    if (updateListeners.add(listener)) {
      reachedStates.foreach(rn => listener.onReachableNodeAdded(rn))
    }
  }

  def registerListener(listener: SyncStatePDSUpdateListener[Stmt, Fact]): Unit = {
    if (!reachedStateUpdateListeners.containsEntry(listener.node, listener)) {
      reachedStateUpdateListeners.addOne(listener.node, listener)
      if (reachedStates.contains(listener.node)) listener.reachable()
    }
  }

  def wrap(variable: Fact): INode[Fact] = new SingleNode[Fact](variable)

  def addCallRule(rule: Rule[Stmt, INode[Fact], W]): Unit = callingPDS.addRule(rule)

  def addFieldRule(rule: Rule[Field, INode[Node[Stmt, Fact]], W]): Unit = fieldPDS.addRule(rule)

  def processNormal(curr: Node[Stmt, Fact], succ: Node[Stmt, Fact]): Unit = {
    addNormalFieldFlow(curr, succ)
    addNormalCallFlow(curr, succ)
  }

  def addNormalCallFlow(curr: Node[Stmt, Fact], succ: Node[Stmt, Fact]): Unit = {
    addCallRule(
      new NormalRule(
        wrap(curr.fact),
        curr.stmt,
        wrap(succ.fact),
        succ.stmt,
        getCallWeights.normal(curr, succ)))
  }

  def addNormalFieldFlow(curr: Node[Stmt, Fact], succ: Node[Stmt, Fact]): Unit = {
    val field: Field = {
      succ match {
        case node: ExclusionNode[Stmt, Fact, _] => exclusionFieldWildCard(node.exclusion.asInstanceOf[Field])
        case _ => fieldWildCard
      }
    }
    addFieldRule(
      new NormalRule(
        asFieldFact(curr),
        fieldWildCard,
        asFieldFact(succ),
        field,
        getFieldWeights.normal(curr, succ)))
  }

  def asFieldFact(node: Node[Stmt, Fact]): INode[Node[Stmt, Fact]] = {
    new SingleNode(new Node(node.stmt, node.fact))
  }

  def processPop(curr: Node[Stmt, Fact], popNode: PopNode[_]): Unit = {
    val system = popNode.system
    val location = popNode.location
    system match {
      case PDSSystem.Calls => {
        addCallRule(
          new PopRule(
            wrap(curr.fact),
            curr.stmt,
            wrap(location.asInstanceOf[Fact]),
            getCallWeights.pop(curr)))
      }
      case PDSSystem.Fields => {
        val node = location.asInstanceOf[NodeWithLocation[Stmt, Fact, Field]]
        addFieldRule(
          new PopRule(
            asFieldFact(curr),
            node.location,
            asFieldFact(node.fact),
            getFieldWeights.pop(curr)))
      }
      case _ =>
    }
  }

  def processPush(curr: Node[Stmt, Fact], location: Location, succ: PushNode[Stmt, Fact, _], system: PDSSystem): Unit = {
    system match {
      case PDSSystem.Calls => {
        addNormalFieldFlow(curr, succ)
        addCallRule(new PushRule(
          wrap(curr.fact),
          curr.stmt,
          wrap(succ.fact),
          succ.stmt,
          location.asInstanceOf[Stmt],
          getCallWeights.push(curr, succ, location.asInstanceOf[Stmt])))
        applyCallSummary(location.asInstanceOf[Stmt], succ.fact, succ.stmt)
      }
      case PDSSystem.Fields => {
        addFieldRule(new PushRule(
          asFieldFact(curr),
          fieldWildCard,
          asFieldFact(succ),
          location.asInstanceOf[Field],
          fieldWildCard,
          getFieldWeights.push(curr, succ, location.asInstanceOf[Field])))
        addNormalCallFlow(curr, succ)
      }
      case _ =>
    }
  }

  def applyCallSummary(callSite: Stmt, factInCallee: Fact, spInCallee: Stmt): Unit = {
    callAutomaton.addSummaryListener(
      new SummaryListener[Stmt, INode[Fact]] {
        override def addedSummary(t: Transition[Stmt, INode[Fact]]): Unit = {
          val genSt = t.getTarget.asInstanceOf[GeneratedState[Fact, Stmt]]
          val sp = genSt.location
          val v = genSt.node.fact
          val exitStmt = t.getLabel
          val returnedFact = t.getStart.fact
          if (spInCallee.equals(sp) && factInCallee == v) {
            if (summaries.add(new Summary(callSite, factInCallee, spInCallee, exitStmt, returnedFact))) {
              summaryListeners.foreach(s => s.apply(callSite, factInCallee, spInCallee, exitStmt, returnedFact))
            }
            applyCallSummary(callSite, factInCallee, spInCallee, exitStmt, returnedFact)
          }
        }
      })
  }

  def addApplySummaryListener(l: OnAddedSummaryListener): Unit = {
    if (summaryListeners.add(l)) {
      summaries.foreach(s => l.apply(s.callSite, s.factInCallee, s.spInCallee, s.exitStmt, s.returnedFact))
    }
  }

  def generateFieldState(d: INode[Node[Stmt, Fact]], loc: Field): INode[Node[Stmt, Fact]] = {
    val e = (d, loc)
    if (!generatedFieldState.contains(e)) {
      generatedFieldState.put(e, new GeneratedState[Node[Stmt, Fact], Field](d, loc))
    }
    generatedFieldState(e)
  }

  def addGeneratedFieldState(state: GeneratedState[Node[Stmt, Fact], Field]): Unit = {
    generatedFieldState.put((state.node, state.location), state)
  }

  def setCallingContextReachable(node: Node[Stmt, Fact]): Unit = {
    if (callingContextReachable.add(node)) {
      if (fieldContextReachable.contains(node)) processNode(node)
    }
  }

  def setFieldContextReachable(node: Node[Stmt, Fact]): Unit = {
    if (fieldContextReachable.add(node)) {
      if (callingContextReachable.contains(node)) processNode(node)
    }
  }

  def generateCallState(d: INode[Fact], loc: Stmt): INode[Fact] = {
    val e = (d, loc)
    if (!generatedCallState.contains(e)) {
      generatedCallState.put(e, new GeneratedState(d, loc))
    }
    generatedCallState(e)
  }

  trait OnAddedSummaryListener {

    def apply(callSite: Stmt, factInCallee: Fact, spInCallee: Stmt, exitStmt: Stmt, returnedFact: Fact): Unit
  }

  protected class Summary(val callSite: Stmt,
                          val factInCallee: Fact,
                          val spInCallee: Stmt,
                          val exitStmt: Stmt,
                          val returnedFact: Fact) {

    override def hashCode: Int = Objects.hashCode(callSite, factInCallee, spInCallee, exitStmt, returnedFact)

    override def equals(obj: Any): Boolean = {
      obj match {
        case s: Summary => {
          Objects.equals(callSite, s.callSite) &&
            Objects.equals(factInCallee, s.factInCallee) &&
            Objects.equals(spInCallee, s.spInCallee) &&
            Objects.equals(exitStmt, s.exitStmt) &&
            Objects.equals(returnedFact, s.returnedFact)
        }
        case _ => false
      }
    }
  }

  private class CallAutomatonListener extends WPAUpdateListener[Stmt, INode[Fact], W] {

    override def onWeightAdded(t: Transition[Stmt, INode[Fact]], w: W, aut: WeightedPAutomaton[Stmt, INode[Fact], W]): Unit = {
      if (!t.getStart.isInstanceOf[GeneratedState[_, _]] && !t.getLabel.equals(callAutomaton.epsilon)) {
        val node = new Node(t.getLabel, t.getStart.fact)
        setCallingContextReachable(node)
      }
    }
  }

  private class FieldUpdateListener extends WPAUpdateListener[Field, INode[Node[Stmt, Fact]], W] {

    override def onWeightAdded(t: Transition[Field, INode[Node[Stmt, Fact]]], w: W, aut: WeightedPAutomaton[Field, INode[Node[Stmt, Fact]], W]): Unit = {
      val n = t.getStart
      if (!n.isInstanceOf[GeneratedState[_, _]] && !t.getLabel.equals(fieldAutomaton.epsilon)) {
        setFieldContextReachable(new Node(n.fact.stmt, n.fact.fact))
      }
    }
  }

  private class CallSummaryListener extends NestedAutomatonListener[Stmt, INode[Fact], W] {

    override def nestedAutomaton(parent: WeightedPAutomaton[Stmt, INode[Fact], W], child: WeightedPAutomaton[Stmt, INode[Fact], W]): Unit = {
      child.getInitialStates.foreach(s => child.registerListener(new AddEpsilonToInitialStateListener(s, parent)))
    }
  }

  private class AddEpsilonToInitialStateListener(state: INode[Fact],
                                                 protected val parent: WeightedPAutomaton[Stmt, INode[Fact], W]) extends WPAStateListener[Stmt, INode[Fact], W](state) {

    protected def getOuterType: SyncPDSSolver[Stmt, Fact, Field, W] = SyncPDSSolver.this

    override def onOutTransitionAdded(t: Transition[Stmt, INode[Fact]], w: W, weightedPAutomaton: WeightedPAutomaton[Stmt, INode[Fact], W]): Unit = {}

    override def onInTransitionAdded(t: Transition[Stmt, INode[Fact]], w: W, weightedPAutomaton: WeightedPAutomaton[Stmt, INode[Fact], W]): Unit = {
      if (t.getLabel.equals(callAutomaton.epsilon)) {
        parent.registerListener(new OnOutTransitionAddToStateListener(this.state, t))
      }
    }

    override def hashCode: Int = Objects.hashCode(parent, getOuterType) + super.hashCode

    override def equals(obj: Any): Boolean = {
      obj match {
        case a: AddEpsilonToInitialStateListener => {
          Objects.equals(parent, a.parent) && Objects.equals(getOuterType, a.getOuterType) && super.equals(a)
        }
        case _ => false
      }
    }
  }

  private class OnOutTransitionAddToStateListener(state: INode[Fact], protected val trans: Transition[Stmt, INode[Fact]]) extends WPAStateListener[Stmt, INode[Fact], W](state) {

    protected def getOuterType: SyncPDSSolver[Stmt, Fact, Field, W] = SyncPDSSolver.this

    override def onOutTransitionAdded(t: Transition[Stmt, INode[Fact]], w: W, weightedPAutomaton: WeightedPAutomaton[Stmt, INode[Fact], W]): Unit = {
      val returningNode = new Node(t.getLabel, trans.getStart.fact)
      setCallingContextReachable(returningNode)
    }

    override def onInTransitionAdded(t: Transition[Stmt, INode[Fact]], w: W, weightedPAutomaton: WeightedPAutomaton[Stmt, INode[Fact], W]): Unit = {}

    override def hashCode: Int = Objects.hashCode(trans, getOuterType) + super.hashCode

    override def equals(obj: Any): Boolean = {
      obj match {
        case a: OnOutTransitionAddToStateListener => {
          Objects.equals(trans, a.trans) && Objects.equals(getOuterType, a.getOuterType) && super.equals(a)
        }
        case _ => false
      }
    }
  }
}
