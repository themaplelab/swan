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

import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.{Empty, IPushdownSystem, Location, State, WPAStateListener, WPAUpdateListener, WPDSUpdateListener}
import ca.ualberta.maple.swan.spds.analysis.wpds.wildcard.{ExclusionWildcard, Wildcard}

abstract class PostStar[N <: Location, D <: State, W <: Weight](val pds: IPushdownSystem[N, D, W], val fa: WeightedPAutomaton[N, D, W]) {

  fa.setInitialAutomaton(fa)
  pds.registerUpdateListener(new PostStarUpdateListener(fa))

  protected def update(trans: Transition[N, D], weight: W): Unit = {
    if (!fa.nested) {
      fa.addWeightForTransition(trans, weight)
    } else {
      getSummaryAutomaton(trans.getTarget).addWeightForTransition(trans, weight)
    }
  }

  protected def getWeightFor(trans: Transition[N, D]): W = {
    if (!fa.nested) {
      fa.getWeightFor(trans)
    } else {
      getSummaryAutomaton(trans.getTarget).getWeightFor(trans)
    }
  }

  protected def getOrCreateSummaryAutomaton(target: D, trans: Transition[N, D], w: W, context: WeightedPAutomaton[N, D, W]): WeightedPAutomaton[N, D, W] = {
    var aut = getSummaryAutomaton(target)
    if (aut == null) {
      aut = context.createNestedAutomaton(target)
      putSummaryAutomaton(target, aut)
      aut.setInitialAutomaton(fa)
    } else {
      context.addNestedAutomaton(aut)
    }
    aut.addWeightForTransition(trans, w)
    aut
  }

  def putSummaryAutomaton(target: D, aut: WeightedPAutomaton[N, D, W]): Unit

  def getSummaryAutomaton(target: D): WeightedPAutomaton[N, D, W]

  protected class PostStarUpdateListener(protected val fa: WeightedPAutomaton[N, D, W]) extends WPDSUpdateListener[N, D, W] {

    override def onRuleAdded(rule: Rule[N, D, W]): Unit = {
      rule match {
        case rule: NormalRule[N, D, W] => fa.registerListener(new HandleNormalListener(rule))
        case rule: PopRule[N, D, W] => fa.registerListener(new HandlePopListener(rule.s1, rule.l1, rule.s2, rule.w))
        case rule: PushRule[N, D, W] => fa.registerListener(new HandlePushListener(rule))
        case _ =>
      }
    }

    override def hashCode(): Int = Objects.hashCode(fa)

    override def equals(obj: Any): Boolean = {
      obj match {
        case p: PostStarUpdateListener => Objects.equals(fa, p.fa)
        case _ => false
      }
    }
  }

  private class HandleNormalListener(protected val rule: NormalRule[N, D, W]) extends WPAStateListener[N, D, W](rule.s1) {

    override def onOutTransitionAdded(t: Transition[N, D], w: W, weightedPAutomaton: WeightedPAutomaton[N, D, W]): Unit = {
      if (t.getLabel.equals(rule.l1) || rule.l1.isInstanceOf[Wildcard]) {
        val newWeight = w.extendWith(rule.w).asInstanceOf[W]
        val p = rule.s2
        val l2 = rule.l2.get
        l2 match {
          case w: ExclusionWildcard[N] =>
            if (t.getLabel.equals(w.excludes)) return
          case _ =>
        }
        l2 match {
          case w: Wildcard =>
            if (t.getLabel.equals(fa.epsilon)) return
          case _ =>
        }
        if (!rule.canBeApplied(t, w)) return
        update(new Transition[N, D](p, l2, t.getTarget), newWeight)
      }
    }

    override def onInTransitionAdded(t: Transition[N, D], w: W, weightedPAutomaton: WeightedPAutomaton[N, D, W]): Unit = {}

    override def hashCode: Int = Objects.hashCode(rule) + super.hashCode

    override def equals(obj: Any): Boolean = {
      obj match {
        case l: HandleNormalListener => Objects.equals(rule, l.rule) && super.equals(l)
        case _ => false
      }
    }
  }

  private class HandlePopListener(state: D,
                                  protected val popLabel: N,
                                  protected val targetState: D,
                                  protected val ruleWeight: W) extends WPAStateListener[N, D, W](state) {

    override def onOutTransitionAdded(t: Transition[N, D], w: W, weightedPAutomaton: WeightedPAutomaton[N, D, W]): Unit = {
      if (t.getLabel.accepts(popLabel) || popLabel.accepts(t.getLabel)) {
        if (fa.isGeneratedState(t.getTarget)) {
          if (popLabel.isInstanceOf[Empty]) throw new RuntimeException("Illegal State")
          val newWeight = w.extendWith(ruleWeight).asInstanceOf[W]
          update(new Transition[N, D](targetState, fa.epsilon, t.getTarget), newWeight)
          fa.registerListener(new UpdateTransitivePopListener(targetState, t.getLabel, t.getTarget, newWeight))
          weightedPAutomaton.registerSummaryEdge(t)
        } else if (fa.isUnbalancedState(t.getTarget)) {
          if (popLabel.isInstanceOf[Empty]) throw new RuntimeException("Illegal State")
          fa.unbalancedPop(targetState, t, w)
        }
      }

      if (t.getLabel.isInstanceOf[Empty]) fa.registerListener(
        new HandlePopListener(t.getTarget, popLabel, targetState, ruleWeight))
    }

    override def onInTransitionAdded(t: Transition[N, D], w: W, weightedPAutomaton: WeightedPAutomaton[N, D, W]): Unit = {}

    override def hashCode: Int = Objects.hashCode(popLabel, ruleWeight, targetState) + super.hashCode

    override def equals(obj: Any): Boolean = {
      obj match {
        case p: HandlePopListener => {
          Objects.equals(popLabel, p.popLabel) && Objects.equals(ruleWeight, p.ruleWeight) &&
            Objects.equals(targetState, p.targetState) && super.equals(p)
        }
        case _ => false
      }
    }
  }

  private class HandlePushListener(protected val rule: PushRule[N, D, W]) extends WPAStateListener[N, D, W](rule.s1) {

    override def onOutTransitionAdded(t: Transition[N, D], w: W, weightedPAutomaton: WeightedPAutomaton[N, D, W]): Unit = {
      if (t.getLabel.equals(rule.l1) || rule.l1.isInstanceOf[Wildcard]) {
        if (!(rule.callSite.isInstanceOf[Wildcard] && t.getLabel.equals(fa.epsilon))) {
          val p = rule.s2
          val gammaPrime = rule.l2.get
          val irState = fa.createState(p, gammaPrime)
          val transitionLabel = if (rule.callSite.isInstanceOf[Wildcard]) t.getLabel else rule.callSite
          val callSiteTransition = new Transition[N, D](irState, transitionLabel, t.getTarget)
          val calleeTransition = new Transition[N, D](p, gammaPrime, irState)
          val weightAtCallSite = w.extendWith(rule.w).asInstanceOf[W]
          update(callSiteTransition, weightAtCallSite)
          if (!fa.nested) {
            update(calleeTransition, fa.getOne)
          } else {
            if (!fa.isGeneratedState(irState)) throw new RuntimeException("State must be generated")
            val summary = getOrCreateSummaryAutomaton(irState, calleeTransition, fa.getOne, weightedPAutomaton)
            summary.registerListener(new WPAUpdateListener[N, D, W] {

              override def onWeightAdded(t: Transition[N, D], w: W, aut: WeightedPAutomaton[N, D, W]): Unit = {
                if (t.getLabel.equals(fa.epsilon) && t.getTarget.equals(irState)) {
                  update(t, w)
                  update(new Transition[N, D](
                    t.getStart, callSiteTransition.getLabel,
                    callSiteTransition.getTarget), getWeightFor(callSiteTransition).extendWith(w).asInstanceOf[W])
                }
              }
            })
          }
        }
      }
    }

    override def onInTransitionAdded(t: Transition[N, D], w: W, weightedPAutomaton: WeightedPAutomaton[N, D, W]): Unit = {}

    override def hashCode: Int = Objects.hashCode(rule) + super.hashCode

    override def equals(obj: Any): Boolean = {
      obj match {
        case l: HandlePushListener => Objects.equals(rule, l.rule) && super.equals(obj)
        case _ => false
      }
    }
  }

  private class UpdateTransitivePopListener(protected val start: D,
                                            protected val label: N,
                                            target: D,
                                            protected val newWeight: W) extends WPAStateListener[N, D, W](target) {

    override def onOutTransitionAdded(t: Transition[N, D], w: W, weightedPAutomaton: WeightedPAutomaton[N, D, W]): Unit = {
      update(new Transition[N, D](start, t.getLabel, t.getTarget), w.extendWith(newWeight).asInstanceOf[W])
    }

    override def onInTransitionAdded(t: Transition[N, D], w: W, weightedPAutomaton: WeightedPAutomaton[N, D, W]): Unit = {}

    override def hashCode: Int = Objects.hashCode(start, newWeight, label) + super.hashCode

    override def equals(obj: Any): Boolean = {
      obj match {
        case p: UpdateTransitivePopListener => {
          Objects.equals(start, p.start) && Objects.equals(label, p.label) &&
            Objects.equals(newWeight, p.newWeight) && super.equals(p)
        }
        case _ => false
      }
    }
  }
}
