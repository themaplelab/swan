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

package ca.ualberta.maple.swan.spds.analysis.wpds.interfaces

import java.util.Objects

import ca.ualberta.maple.swan.spds.analysis.wpds.impl.{Transition, Weight, WeightedPAutomaton}
import com.google.common.collect.{HashBasedTable, Table}

import scala.collection.mutable

class ForwardDFSVisitor[N <: Location, D <: State, W <: Weight](protected val aut: WeightedPAutomaton[N, D, W]) extends WPAUpdateListener[N, D, W] {

  protected val adjacent: mutable.MultiDict[D, D] = mutable.MultiDict.empty
  protected val listeners: mutable.MultiDict[D, ReachabilityListener[N, D]] = mutable.MultiDict.empty
  protected val reaches: mutable.MultiDict[D, D] = mutable.MultiDict.empty
  protected val inverseReaches: mutable.MultiDict[D, D] = mutable.MultiDict.empty
  protected val refCount: Table[D, D, Int] = HashBasedTable.create

  def registerListener(state: D, l: ReachabilityListener[N, D]): Unit = {
    if (!listeners.containsEntry(state, l)) {
      listeners.addOne(state, l)
      inverseReaches.get(state).foreach(d => aut.registerListener(new TransitiveClosure(d, state, l)))
    }
  }

  protected def continueWith(t: Transition[N, D]): Boolean = true

  override def onWeightAdded(t: Transition[N, D], w: W, aut: WeightedPAutomaton[N, D, W]): Unit = {
    val a = t.getStart
    val b = t.getTarget
    inverseReaches(a, a)
    if (continueWith(t)) {
      insertEdge(a, b)
    }
  }

  protected def insertEdge(a: D, b: D): Unit = {
    val worklist = new mutable.Queue[Edge]()
    if (!refCount.contains(a, a)) {
      makeClosure(a, b)
      worklist.addOne(new Edge(a, b))
      refCount.put(a, b, 1)
    }
    makeEdge(a, b)
    reaches.get(a).foreach(x => {
      if (!refCount.contains(x, b)) {
        makeClosure(x, b)
        worklist.addOne(new Edge(x, b))
        refCount.put(x, b, 1)
      }
    })
    while (worklist.nonEmpty) {
      val e = worklist.dequeue()
      val x = e.from
      val y = e.to
      adjacent.get(y).foreach(z => {
        if (!refCount.contains(x, z)) {
          makeClosure(x, z)
          worklist.addOne(new Edge(x, z))
          refCount.put(x, z, 1)
        }
      })
    }
  }

  protected def makeEdge(from: D, to: D): Unit = {
    adjacent.addOne(from, to)
    inverseReaches(from, to)
  }

  protected def inverseReaches(from: D, to: D): Unit = {
    if (!inverseReaches.containsEntry(from, to)) {
      inverseReaches.addOne(from, to)
      listeners.get(from).foreach(l => aut.registerListener(new TransitiveClosure(to, from, l)))
    }
  }

  protected def makeClosure(from: D, to: D): Unit = {
    if (!reaches.containsEntry(from, to)) {
      reaches.addOne(from, to)
      inverseReaches(from, to)
    }
  }

  override def hashCode: Int = Objects.hashCode(aut)

  override def equals(obj: Any): Boolean = {
    obj match {
      case f: ForwardDFSVisitor[N, D, W] => Objects.equals(aut, f.aut)
      case _ => false
    }
  }

  protected class TransitiveClosure(state: D, protected val s: D, protected val listener: ReachabilityListener[N, D]) extends WPAStateListener[N, D, W](state) {

    protected def getOuterType: ForwardDFSVisitor[N, D, W] = ForwardDFSVisitor.this

    override def onOutTransitionAdded(t: Transition[N, D], w: W, weightedPAutomaton: WeightedPAutomaton[N, D, W]): Unit = listener.reachable(t)

    override def onInTransitionAdded(t: Transition[N, D], w: W, weightedPAutomaton: WeightedPAutomaton[N, D, W]): Unit = {}

    override def hashCode: Int = Objects.hashCode(getOuterType, listener, s) + super.hashCode

    override def equals(obj: Any): Boolean = {
      obj match {
        case t: TransitiveClosure => {
          Objects.equals(listener, t.listener) && Objects.equals(s, t.s) &&
            Objects.equals(getOuterType, t.getOuterType) && super.equals(t)
        }
        case _ => false
      }
    }
  }

  protected class Edge(val from: D, val to: D) {

    override def hashCode: Int = Objects.hashCode(from, to)

    override def equals(obj: Any): Boolean = {
      obj match {
        case e: Edge => Objects.equals(from, e.from) && Objects.equals(to, e.to)
        case _ => false
      }
    }
  }
}
