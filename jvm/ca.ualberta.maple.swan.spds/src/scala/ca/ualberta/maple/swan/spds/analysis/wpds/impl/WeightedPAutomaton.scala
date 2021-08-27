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

package ca.ualberta.maple.swan.spds.analysis.wpds.impl

import java.util.Objects

import ca.ualberta.maple.swan.spds.analysis.pathexpression.{Edge, LabeledGraph}
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces._
import com.google.common.base.Stopwatch

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

abstract class WeightedPAutomaton[N <: Location, D <: State, W <: Weight] extends LabeledGraph[D, N] {

  protected val connectedPushListeners: mutable.HashSet[ConnectPushListener[N, D, W]] = mutable.HashSet.empty
  protected var dfsVisitor: ForwardDFSVisitor[N, D, W] = null
  protected var dfsEpsVisitor: ForwardDFSVisitor[N, D, W] = null
  var failedAdditions = 0
  var failedDirectAdditions = 0
  val finalState: mutable.HashSet[D] = mutable.HashSet.empty
  protected val initialStatesToSource: mutable.MultiDict[D, D] = mutable.MultiDict.empty
  protected var initialAutomaton: WeightedPAutomaton[N, D, W] = null
  protected val listeners: mutable.HashSet[WPAUpdateListener[N, D, W]] = mutable.HashSet.empty
  protected val nestedAutomatons: mutable.HashSet[WeightedPAutomaton[N, D, W]] = mutable.HashSet.empty
  protected val nestedAutomataListeners: mutable.HashSet[NestedAutomatonListener[N, D, W]] = mutable.HashSet.empty
  protected val states: mutable.HashSet[D] = mutable.HashSet.empty
  val stateToDFS: mutable.HashMap[D, ForwardDFSVisitor[N, D, W]] = mutable.HashMap.empty
  val stateToEpsilonDFS: mutable.HashMap[D, ForwardDFSVisitor[N, D, W]] = mutable.HashMap.empty
  protected val stateCreatingTransition: mutable.HashMap[D, Transition[N, D]] = mutable.HashMap.empty
  protected val stateToDistanceToInitial: mutable.HashMap[D, Int] = mutable.HashMap.empty
  protected val stateToUnbalancedDistance: mutable.HashMap[D, Int] = mutable.HashMap.empty
  protected val stateListeners: mutable.MultiDict[D, WPAStateListener[N, D, W]] = mutable.MultiDict.empty
  protected val stateToEpsilonReachabilityListener: mutable.HashMap[D, ReachabilityListener[N, D]] = mutable.HashMap.empty
  protected val stateToReachabilityListener: mutable.HashMap[D, ReachabilityListener[N, D]] = mutable.HashMap.empty
  protected val summaryEdges: mutable.HashSet[Transition[N, D]] = mutable.HashSet.empty
  protected val summaryEdgeListener: mutable.HashSet[SummaryListener[N, D]] = mutable.HashSet.empty
  protected val transitionToWeights: mutable.HashMap[Transition[N, D], W] = mutable.HashMap.empty
  protected val transitionsToFinalWeights: mutable.HashMap[Transition[N, D], W] = mutable.HashMap.empty
  protected val transitionsOutOf: mutable.MultiDict[D, Transition[N, D]] = mutable.MultiDict.empty
  protected val transitionsInto: mutable.MultiDict[D, Transition[N, D]] = mutable.MultiDict.empty
  val transitions: mutable.HashSet[Transition[N, D]] = mutable.HashSet.empty
  protected val unbalancedPopListeners: mutable.HashSet[UnbalancedPopListener[N, D, W]] = mutable.HashSet.empty
  protected val unbalancedPops: mutable.HashMap[UnbalancedPopEntry, W] = mutable.HashMap.empty
  protected val watch: Stopwatch = Stopwatch.createUnstarted

  def createState(d: D, loc: N): D

  def getOne: W

  def isGeneratedState(d: D): Boolean

  def defTransitions: ArrayBuffer[Transition[N, D]] = ArrayBuffer.from(transitions)

  def addTransition(trans: Transition[N, D]): Boolean = {
    val w = addWeightForTransition(trans, getOne)
    if (!w) {
      failedDirectAdditions += 1
    }
    w
  }

  def getInitialStates: collection.Set[D] = initialStatesToSource.keySet

  def epsilon: N

  def hasMaxDepth: Boolean = getMaxDepth > 0

  def getMaxDepth: Int = -1

  def getMaxUnbalancedDepth: Int = -1

  def getUnbalancedStartOf(target: D): collection.Set[D] = initialStatesToSource.get(target)

  def getTransitionsToFinalWeights: mutable.HashMap[Transition[N, D], W] = {
    initialStatesToSource.keySet.foreach(s => registerListener(new ValueComputationListener(s, getOne)))
    transitionsToFinalWeights
  }

  override def getEdges: mutable.HashSet[Edge[D, N]] = {
    val trans = mutable.HashSet.empty[Edge[D, N]]
    transitions.foreach(tran => {
      if (!tran.getLabel.equals(epsilon)) {
        trans.add(new Transition[N, D](tran.getTarget, tran.getLabel, tran.getStart))
      }
    })
    trans
  }

  override def getNodes: mutable.HashSet[D] = states

  def addWeightForTransition(trans: Transition[N, D], weight: W): Boolean = {
    if (trans.start.equals(trans.getTarget) && trans.label.equals(epsilon)) {
      failedAdditions += 1
      false
    } else {
      val distanceToInitial = computeDistance(trans)
      if (hasMaxDepth && distanceToInitial > getMaxDepth) {
        false
      } else {
        if (!watch.isRunning) watch.start()
        transitionsOutOf.get(trans.getStart).union(Set(trans))
        transitionsInto.get(trans.getTarget).union(Set(trans))
        if (states.add(trans.getTarget)) {
          stateCreatingTransition.addOne(trans.getTarget, trans)
        }
        states.add(trans.getStart)
        var added = transitions.add(trans)
        val oldWeight = transitionToWeights(trans)
        val newWeight = (if (oldWeight == null) weight else oldWeight.combineWith(weight)).asInstanceOf[W]
        if (!newWeight.equals(oldWeight)) {
          transitionToWeights.addOne(trans, newWeight)
          listeners.foreach(_.onWeightAdded(trans, newWeight, this))
          stateListeners.get(trans.getStart).foreach(_.onOutTransitionAdded(trans, newWeight, this))
          stateListeners.get(trans.getTarget).foreach(_.onInTransitionAdded(trans, newWeight, this))
          added = true
        }
        if (watch.isRunning) watch.stop()
        if (!added) failedAdditions += 1
        added
      }
    }
  }

  protected def computeDistance(trans: Transition[N, D]): Int = {
    val distance: Option[Int] = {
      if (isUnbalancedState(trans.getTarget)) {
        Some(0)
      } else {
        stateToDistanceToInitial.get(trans.getTarget)
      }
    }
    if (distance.isEmpty) -1 else {
      val integer = distance.get + 1
      val currentDistance = stateToDistanceToInitial.get(trans.getStart)
      if (currentDistance.isEmpty || integer < currentDistance.get) {
        stateToDistanceToInitial.addOne(trans.getStart, integer)
        integer
      } else {
        currentDistance.get
      }
    }
  }

  def getWeightFor(trans: Transition[N, D]): W = transitionToWeights(trans)

  def registerListener(listener: WPAUpdateListener[N, D, W]): Unit = {
    if (listeners.add(listener)) {
      transitionToWeights.foreach(t => listener.onWeightAdded(t._1, t._2, this))
    }
    nestedAutomatons.foreach(n => n.registerListener(listener))
  }

  protected def increaseListenerCount(l: WPAStateListener[N, D, W]): Unit = {
    WeightedPAutomaton.listenerCount += 1
    if (WeightedPAutomaton.listenerCount % 10000 == 0) {
      onManyStateListenerRegister()
    }
  }

  def onManyStateListenerRegister(): Unit = {}

  def registerListener(l: WPAStateListener[N, D, W]): Unit = {
    if (!stateListeners.containsEntry(l.state, l)) {
      stateListeners.addOne(l.state, l)
      increaseListenerCount(l)
      transitionsOutOf.get(l.state).foreach(t => l.onOutTransitionAdded(t, transitionToWeights(t), this))
      transitionsInto.get(l.state).foreach(t => l.onInTransitionAdded(t, transitionToWeights(t), this))
      nestedAutomatons.foreach(n => n.registerListener(l))
    }
  }

  def unregisterAllListeners(): Unit = {
    this.connectedPushListeners.clear()
    this.nestedAutomataListeners.clear()
    this.stateListeners.clear()
    this.listeners.clear()
    this.stateToEpsilonReachabilityListener.clear()
    this.stateToReachabilityListener.clear()
    this.summaryEdgeListener.clear()
    this.unbalancedPopListeners.clear()
  }

  def createNestedAutomaton(initialState: D): WeightedPAutomaton[N, D, W] = {
    val nested = new WeightedPAutomaton[N, D, W] {

      override def createState(d: D, loc: N): D = WeightedPAutomaton.this.createState(d, loc)

      override def getOne: W = WeightedPAutomaton.this.getOne

      override def isGeneratedState(d: D): Boolean = WeightedPAutomaton.this.isGeneratedState(d)

      override def epsilon: N = WeightedPAutomaton.this.epsilon

      override def nested: Boolean = true

      override def toString: String = s"NESTED: \n${super.toString}"
    }
    addNestedAutomaton(nested)
    nested
  }

  def registerDFSListener(state: D, l: ReachabilityListener[N, D]): Unit = {
    stateToReachabilityListener.put(state, l)
    if (dfsVisitor == null) {
      dfsVisitor = new ForwardDFSVisitor[N, D, W](this)
      this.registerListener(dfsVisitor)
    }
    dfsVisitor.registerListener(state, l)
  }

  def registerDFSEpsilonListener(state: D, l: ReachabilityListener[N, D]): Unit = {
    stateToEpsilonReachabilityListener.put(state, l)
    if (dfsEpsVisitor == null) {
      dfsEpsVisitor = new ForwardDFSEpsilonVisitor[N, D, W](this)
      this.registerListener(dfsEpsVisitor)
    }
    dfsEpsVisitor.registerListener(state, l)
  }

  def registerUnbalancedPopListener(l: UnbalancedPopListener[N, D, W]): Unit = {
    if (unbalancedPopListeners.add(l)) {
      unbalancedPops.foreach(e => l.unbalancedPop(e._1.targetState, e._1.trans, e._2))
    }
  }

  def unbalancedPop(targetState: D, trans: Transition[N, D], weight: W): Unit = {
    val t = new UnbalancedPopEntry(targetState, trans)
    val oldVal = unbalancedPops.get(t)
    val newVal = (if (oldVal.isEmpty) weight else oldVal.get.combineWith(weight)).asInstanceOf[W]
    if (!newVal.equals(oldVal)) {
      unbalancedPops.put(t, newVal)
      unbalancedPopListeners.foreach(l => l.unbalancedPop(targetState, trans, newVal))
    }
  }

  def addSummaryListener(l: SummaryListener[N, D]): Unit = {
    if (summaryEdgeListener.add(l)) {
      summaryEdges.foreach(edge => l.addedSummary(edge))
      nestedAutomatons.foreach(nested => nested.addSummaryListener(l))
    }
  }

  def registerSummaryEdge(t: Transition[N, D]): Unit = {
    if (summaryEdges.add(t)) {
      summaryEdgeListener.foreach(l => l.addedSummary(t))
    }
  }

  def nested: Boolean = false

  def addNestedAutomaton(nested: WeightedPAutomaton[N, D, W]): Unit = {
    if (nestedAutomatons.add(nested)) {
      stateListeners.values.foreach(e => nested.registerListener(e))
      listeners.foreach(e => nested.registerListener(e))
      summaryEdgeListener.foreach(e => nested.addSummaryListener(e))
      unbalancedPopListeners.foreach(e => nested.registerUnbalancedPopListener(e))
      stateToEpsilonReachabilityListener.foreach(e => nested.registerDFSEpsilonListener(e._1, e._2))
      stateToReachabilityListener.foreach(e => nested.registerDFSListener(e._1, e._2))
      nestedAutomataListeners.foreach(e => {
        e.nestedAutomaton(this, nested)
        nested.registerNestedAutomatonListener(e)
      })
    }
  }

  def registerNestedAutomatonListener(l: NestedAutomatonListener[N, D, W]): Unit = {
    if (nestedAutomataListeners.add(l)) {
      nestedAutomatons.foreach(nested => l.nestedAutomaton(this, nested))
    }
  }

  def setInitialAutomaton(aut: WeightedPAutomaton[N, D, W]): Unit = this.initialAutomaton = aut

  def isInitialAutomaton(aut: WeightedPAutomaton[N, D, W]): Boolean = this.initialAutomaton.equals(aut)

  def getOrCreate(pathReachingD: mutable.HashMap[D, mutable.HashSet[N]], pop: D): mutable.HashSet[N] = {
    var collection = pathReachingD.get(pop)
    if (collection.isEmpty) {
      collection = Some(mutable.HashSet.empty[N])
      pathReachingD.put(pop, collection.get)
    }
    collection.get
  }

  def isUnbalancedState(target: D): Boolean = initialStatesToSource.containsKey(target)

  def addUnbalancedState(state: D, parent: D): Boolean = {
    var distance = 0
    val parents = mutable.HashSet.empty[D]
    if (!initialStatesToSource.containsKey(parent)) {
      distance = stateToUnbalancedDistance(parent)
      parents.add(parent)
    } else {
      parents.addAll(initialStatesToSource.get(parent))
    }
    val newDistance = distance + 1
    stateToUnbalancedDistance.put(state, newDistance)
    if (getMaxUnbalancedDepth > 0 && newDistance > getMaxUnbalancedDepth) {
      false
    } else {
      parents.foreach(p => initialStatesToSource.addOne(state, p))
      true
    }
  }

  def addInitialState(state: D): Boolean = {
    val ret = initialStatesToSource.containsEntry(state, state)
    initialStatesToSource.addOne(state, state)
    ret
  }

  def addFinalState(state: D): Unit = finalState.add(state)

  def wrapFinalState(s: D): String = if (finalState.contains(s)) s"TO: $s" else s.toString

  def wrapIfInitialOrFinalState(s: D): String = {
    if (initialStatesToSource.containsKey(s)) s"ENTRY: ${wrapFinalState(s)}" else wrapFinalState(s)
  }

  def toDotString(visited: mutable.HashSet[WeightedPAutomaton[N, D, W]]): String = ???

  override def toString: String = {
    val sb = new StringBuilder("PAutomaton\n")
    sb.append(s"  InitialStates:${initialStatesToSource.keySet}\n")
    sb.append(s"  FinalStates:$finalState\n")
    sb.append(s"  WeightToTransitions:\n")
    transitionToWeights.foreach(t => sb.append(s"    ${t}\n"))
    nestedAutomatons.foreach(a => sb.append(s"\n$a"))
    sb.toString()
  }

  protected class ValueComputationListener(state: D, protected val weight: W) extends WPAStateListener[N, D, W](state) {

    private def getOuterType: WeightedPAutomaton[N, D, W] = WeightedPAutomaton.this

    override def onOutTransitionAdded(t: Transition[N, D], w: W, weightedPAutomaton: WeightedPAutomaton[N, D, W]): Unit = {}

    override def onInTransitionAdded(t: Transition[N, D], w: W, weightedPAutomaton: WeightedPAutomaton[N, D, W]): Unit = {
      val newWeight = weight.extendWith(w).asInstanceOf[W]
      val weightAtTarget = transitionsToFinalWeights.get(t)
      val newVal = if (weightAtTarget.isEmpty) newWeight else weightAtTarget.get.combineWith(newWeight).asInstanceOf[W]
      transitionsToFinalWeights.addOne(t, newVal)
      if (isGeneratedState(t.start)) registerListener(new ValueComputationListener(t.start, newVal))
    }

    override def hashCode: Int = super.hashCode + Objects.hashCode(weight)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: ValueComputationListener => {
          super.equals(other) && Objects.equals(other.weight, weight) && Objects.equals(other.getOuterType, getOuterType)
        }
        case _ => false
      }
    }
  }

  protected class UnbalancedPopEntry(val targetState: D, val trans: Transition[N, D]) {

    protected def getOuterType: WeightedPAutomaton[N, D, W] = WeightedPAutomaton.this

    override def hashCode: Int = Objects.hashCode(getOuterType, targetState, trans)

    override def equals(obj: Any): Boolean = {
      obj match {
        case u: UnbalancedPopEntry => {
          Objects.equals(getOuterType, u.getOuterType) && Objects.equals(targetState, u.targetState) &&
            Objects.equals(trans, u.trans)
        }
        case _ => false
      }
    }
  }
}

object WeightedPAutomaton {
  var listenerCount = 0
}
