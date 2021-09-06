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

import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.{Location, State, WPAStateListener}

import java.util.Objects
import scala.collection.mutable

abstract class StackListener[N <: Location, D <: State, W <: Weight](aut: WeightedPAutomaton[N, D, W],
                                                                     state: D, protected val source: N) extends WPAStateListener[N, D, W](state) {

  protected val notifiedStacks: mutable.HashSet[N] = mutable.HashSet.empty

  override def onOutTransitionAdded(t: Transition[N, D], w: W, weightedPAutomaton: WeightedPAutomaton[N, D, W]): Unit = {
    if (!t.label.equals(aut.epsilon)) {
      if (this.aut.getInitialStates.contains(t.target)) {
        if (t.label.equals(source)) anyContext(source)
      } else if (this.aut.isGeneratedState(t.target)) {
        aut.registerListener(new SubStackListener(t.target, this))
      }
    }
  }

  override def onInTransitionAdded(t: Transition[N, D], w: W, weightedPAutomaton: WeightedPAutomaton[N, D, W]): Unit = {}

  def stackElement(callSite: N): Unit

  def anyContext(end: N): Unit

  override def hashCode: Int = super.hashCode + Objects.hashCode(source)

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: StackListener[N, D, W] => super.equals(other) && Objects.equals(other.source, source)
      case _ => false
    }
  }

  protected class SubStackListener(state: D, protected val parent: StackListener[N, D, W]) extends WPAStateListener[N, D, W](state) {


    override def onOutTransitionAdded(t: Transition[N, D], w: W, weightedPAutomaton: WeightedPAutomaton[N, D, W]): Unit = {
      if (!t.getLabel.equals(aut.epsilon)) {
        stackElement(t.label)
        if (aut.isGeneratedState(t.target) && !t.target.equals(t.start)) {
          aut.registerListener(new SubStackListener(t.target, parent))
        }
      }
    }

    override def onInTransitionAdded(t: Transition[N, D], w: W, weightedPAutomaton: WeightedPAutomaton[N, D, W]): Unit = {}

    def stackElement(parent: N): Unit = {
      if (notifiedStacks.add(parent)) {
        StackListener.this.stackElement(parent)
      }
    }

    override def hashCode: Int = super.hashCode + Objects.hashCode(parent)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: SubStackListener => super.equals(other) && Objects.equals(other.parent, parent)
      }
    }
  }
}
