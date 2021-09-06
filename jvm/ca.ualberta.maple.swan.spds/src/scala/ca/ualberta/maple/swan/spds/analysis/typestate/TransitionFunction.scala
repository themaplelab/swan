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

package ca.ualberta.maple.swan.spds.analysis.typestate

import java.util.Objects

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.Statement
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.Weight

import scala.collection.mutable

class TransitionFunction(val trans: mutable.HashSet[Transition], val stateChangeStatements: mutable.HashSet[Edge[Statement, Statement]]) extends Weight {

  val values: mutable.HashSet[Transition] = mutable.HashSet.empty

  override def extendWith(other: Weight): Weight = {
    if (other.equals(TransitionFunction.one)) {
      this
    } else if (this.equals(TransitionFunction.one)) {
      other
    } else if (other.equals(TransitionFunction.zero) || this.equals(TransitionFunction.zero)) {
      TransitionFunction.zero
    } else {
      val func = other.asInstanceOf[TransitionFunction]
      val otherTransition = func.values
      val ress = mutable.HashSet.empty[Transition]
      val newStateChangeStatements = mutable.HashSet.empty[Edge[Statement, Statement]]
      values.foreach(first => {
        otherTransition.foreach(second => {
          if (second.equals(Transition.identity)) {
            ress.add(first)
            newStateChangeStatements.addAll(stateChangeStatements)
          } else if (first.equals(Transition.identity)) {
            ress.add(second)
            newStateChangeStatements.addAll(func.stateChangeStatements)
          } else if (first.to.equals(second.from)) {
            ress.add(new Transition(first.from, second.to))
            newStateChangeStatements.addAll(func.stateChangeStatements)
          }
        })
      })
      new TransitionFunction(ress, newStateChangeStatements)
    }
  }

  override def combineWith(other: Weight): Weight = {
    if (this.equals(TransitionFunction.zero)) {
      other
    } else if (other.equals(TransitionFunction.zero)) {
      this
    } else if (other.equals(TransitionFunction.one) && this.equals(TransitionFunction.one)) {
      TransitionFunction.one
    } else {
      val func = other.asInstanceOf[TransitionFunction]
      if (other.equals(TransitionFunction.one) || this.equals(TransitionFunction.one)) {
        val transitions = mutable.HashSet.empty[Transition]
        transitions.addAll(if (other.equals(TransitionFunction.one)) values else func.values)
        val idTransitions = mutable.HashSet.empty[Transition]
        transitions.foreach(t => idTransitions.add(new Transition(t.from, t.from)))
        transitions.addAll(idTransitions)
        new TransitionFunction(transitions,
          mutable.HashSet.from(
            if (other.equals(TransitionFunction.one)) stateChangeStatements else func.stateChangeStatements))
      } else {
        val transitions = mutable.HashSet.empty[Transition]
        transitions.addAll(func.values)
        val newStateChangeStmts = mutable.HashSet.empty[Edge[Statement, Statement]]
        newStateChangeStmts.addAll(stateChangeStatements)
        newStateChangeStmts.addAll(func.stateChangeStatements)
        new TransitionFunction(transitions, newStateChangeStmts)
      }
    }
  }

  override def toString: String = s"Weight: $values"

  override def hashCode(): Int = Objects.hashCode(values)

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: TransitionFunction => Objects.equals(other.values, values)
      case _ => false
    }
  }
}

class StringTransitionFunction(val rep: String) extends TransitionFunction(mutable.HashSet.empty, mutable.HashSet.empty) {

  override def toString: String = rep

  override def hashCode(): Int = rep.hashCode

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: StringTransitionFunction => Objects.equals(other.rep, rep)
      case _ => false
    }
  }
}

object TransitionFunction {

  val one: TransitionFunction = new StringTransitionFunction("ONE")
  val zero: TransitionFunction = new StringTransitionFunction("ZERO")
}