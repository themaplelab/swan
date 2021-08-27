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

import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.{IPushdownSystem, Location, State, WPDSUpdateListener}
import ca.ualberta.maple.swan.spds.analysis.wpds.wildcard.Wildcard

import scala.collection.mutable

class WeightedPushdownSystem[N <: Location, D <: State, W <: Weight] extends IPushdownSystem[N, D, W] {

  protected val pushRules = mutable.HashSet.empty[PushRule[N, D, W]]
  protected val popRules = mutable.HashSet.empty[PopRule[N, D, W]]
  protected val normalRules = mutable.HashSet.empty[NormalRule[N, D, W]]
  protected val listeners = mutable.HashSet.empty[WPDSUpdateListener[N, D, W]]

  override def addRule(rule: Rule[N, D, W]): Boolean = {
    if (addRuleInternal(rule)) {
      listeners.foreach(l => l.onRuleAdded(rule))
      true
    } else false
  }

  protected def addRuleInternal(rule: Rule[N, D, W]): Boolean = {
    rule match {
      case rule: NormalRule[N, D, W] => normalRules.add(rule)
      case rule: PopRule[N, D, W] => popRules.add(rule)
      case rule: PushRule[N, D, W] => pushRules.add(rule)
      case _ => throw new RuntimeException("Trying to add a rule of the wrong type")
    }
  }

  override def registerUpdateListener(listener: WPDSUpdateListener[N, D, W]): Unit = {
    if (listeners.add(listener)) {
      getAllRules.foreach(r => listener.onRuleAdded(r))
    }
  }

  override def getStates: mutable.HashSet[D] = {
    val states = mutable.HashSet.empty[D]
    getAllRules.foreach( r => { states.add(r.s1); states.add(r.s2) } )
    states
  }

  override def getNormalRules: mutable.HashSet[NormalRule[N, D, W]] = normalRules

  override def getPopRules: mutable.HashSet[PopRule[N, D, W]] = popRules

  override def getPushRules: mutable.HashSet[PushRule[N, D, W]] = pushRules

  override def getAllRules: mutable.HashSet[Rule[N, D, W]] = {
    val rules = mutable.HashSet.empty[Rule[N, D, W]]
    rules.addAll(normalRules)
    rules.addAll(popRules)
    rules.addAll(pushRules)
    rules
  }

  override def getRulesStarting(start: D, string: N): mutable.HashSet[Rule[N, D, W]] = {
    val rules = mutable.HashSet.empty[Rule[N, D, W]]
    getRulesStartingWithinSet(start, string, normalRules, rules)
    getRulesStartingWithinSet(start, string, popRules, rules)
    getRulesStartingWithinSet(start, string, pushRules, rules)
    rules
  }

  protected def getRulesStartingWithinSet(start: D, string: N, rules: mutable.HashSet[_ <: Rule[N, D, W]], res: mutable.HashSet[Rule[N, D, W]]): Unit = {
    rules.foreach(r => {
      if (r.s1.equals(start) && (r.l1.equals(string) || r.l1.isInstanceOf[Wildcard])) {
        res.add(r)
      }
      if (string.isInstanceOf[Wildcard] && r.s1.equals(start)) {
        res.add(r)
      }
    })
  }

  override def getNormalRulesEnding(start: D, string: N): mutable.HashSet[NormalRule[N, D, W]] = {
    val rules = mutable.HashSet.empty[NormalRule[N, D, W]]
    normalRules.foreach(r => {
      if (r.s2.equals(start) && r.l2.equals(string)) rules.add(r)
    })
    rules
  }

  override def getPushRulesEnding(start: D, string: N): mutable.HashSet[PushRule[N, D, W]] = {
    val rules = mutable.HashSet.empty[PushRule[N, D, W]]
    pushRules.foreach(r => {
      if (r.s2.equals(start) && r.l2.equals(string)) rules.add(r)
    })
    rules
  }

  override def preStar(initialAutomaton: WeightedPAutomaton[N, D, W]): Unit = {
    new PreStar[N, D, W](this, initialAutomaton)
  }

  override def postStar(initialAutomaton: WeightedPAutomaton[N, D, W]): Unit = {
      new PostStar[N, D, W](this, initialAutomaton) {

        override def putSummaryAutomaton(target: D, aut: WeightedPAutomaton[N, D, W]): Unit = {}

        override def getSummaryAutomaton(target: D): WeightedPAutomaton[N, D, W] = initialAutomaton
      }
  }

  override def postStar(initialAutomaton: WeightedPAutomaton[N, D, W], summaries: NestedWeightedPAutomatons[N, D, W]): Unit = {
    new PostStar[N, D, W](this, initialAutomaton) {

      override def putSummaryAutomaton(target: D, aut: WeightedPAutomaton[N, D, W]): Unit = {
        summaries.putSummaryAutomaton(target, aut)
      }

      override def getSummaryAutomaton(target: D): WeightedPAutomaton[N, D, W] = {
        summaries.getSummaryAutomaton(target)
      }
    }
  }

  override def unregisterAllListeners(): Unit = listeners.clear()

  override def toString: String = {
    val sb = new StringBuilder(s"WPDS (#RULES: ${getAllRules.size})\n")
    sb.append("  NormalRules:\n    ")
    normalRules.foreach(r => sb.append(s"$r\n    "))
    sb.append("\n  PopRules:\n    ")
    popRules.foreach(r => sb.append(s"$r\n    "))
    sb.append("\n  PushRules:\n    ")
    pushRules.foreach(r => sb.append(s"$r\n    "))
    sb.toString()
  }
}
