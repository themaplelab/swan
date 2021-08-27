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

import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.{IPushdownSystem, Location, State}
import ca.ualberta.maple.swan.spds.analysis.wpds.wildcard.Wildcard

import scala.collection.mutable

class PreStar[N <: Location, D <: State, W <: Weight](val pds: IPushdownSystem[N, D, W], val initialAutomaton: WeightedPAutomaton[N, D, W]) {

  val worklist = new mutable.Queue[Transition[N, D]]()

  initialAutomaton.transitions.foreach(t => {
    initialAutomaton.addWeightForTransition(t, initialAutomaton.getOne)
  })

  pds.getPopRules.foreach(r => update(new Transition[N, D](r.s1, r.l1, r.s2), r.w, new mutable.Queue[Transition[N, D]]()))

  while(worklist.nonEmpty) {
    val t = worklist.dequeue()

    pds.getNormalRulesEnding(t.getStart, t.getLabel).foreach(r => {
      val previous = new mutable.Queue[Transition[N, D]]()
      previous.addOne(t)
      update(new Transition[N, D](r.s1, r.l1, t.getTarget), r.w, previous)
    })

    pds.getPushRulesEnding(t.getStart, t.getLabel).foreach(r => {
      initialAutomaton.transitions.foreach(tdash => {
        val previous = new mutable.Queue[Transition[N, D]]()
        previous.addOne(t)
        previous.addOne(tdash)
        if (tdash.getLabel.equals(r.callSite)) {
          update(new Transition[N, D](r.s1, r.l1, tdash.getTarget), r.w, previous)
        } else if (r.callSite.isInstanceOf[Wildcard]) {
          update(new Transition[N, D](r.s1, tdash.getLabel, tdash.getTarget), r.w, previous)
        }
      })
    })

    pds.getPushRules.foreach(r => {
      if (r.callSite.isInstanceOf[Wildcard] || r.callSite.equals(t.getLabel)) {
        val tdash = new Transition[N, D](r.s2, r.l2.get, t.getTarget)
        if (initialAutomaton.transitions.contains(tdash)) {
          val previous = new mutable.Queue[Transition[N, D]]()
          previous.addOne(tdash)
          previous.addOne(t)
          val label = if (r.callSite.isInstanceOf[Wildcard]) t.getLabel else r.l1
          update(new Transition[N, D](r.s1, label, t.getTarget), r.w, previous)
        }
      }
    })
  }

  protected def update(trans: Transition[N, D], weight: W, previous: mutable.Queue[Transition[N, D]]): Unit = {
    if (trans.getLabel.isInstanceOf[Wildcard]) throw new RuntimeException("INVALID TRANSITION")
    initialAutomaton.addTransition(trans)
    val lt = getOrCreateWeight(trans)
    var fr = weight
    previous.foreach(prev => fr = fr.extendWith(getOrCreateWeight(prev)).asInstanceOf[W])
    val newLt = lt.combineWith(fr).asInstanceOf[W]
    initialAutomaton.addWeightForTransition(trans, newLt)
    if (!lt.equals(newLt)) worklist.addOne(trans)
  }

  protected def getOrCreateWeight(trans: Transition[N, D]): W = {
    initialAutomaton.getWeightFor(trans)
  }
}
