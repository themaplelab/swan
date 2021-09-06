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

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge

import java.util.Objects
import java.util.regex.Pattern
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{InvokeExpr, Statement}
import ca.ualberta.maple.swan.spds.analysis.typestate.MatcherTransition.TransitionType

class MatcherTransition(from: State, val methodMatcher: String,
                        val param: Int, to: State, val tpe: TransitionType,
                        val negate: Boolean = false) extends Transition(from, to) {

  def matches(invokeExpr: InvokeExpr, edge: Edge[Statement, Statement]): Boolean = {
    val matches = {
      if (invokeExpr.getResolvedMethod.nonEmpty) {
        val resolvedMethod = invokeExpr.getResolvedMethod.get
        Pattern.matches(methodMatcher, resolvedMethod.getName)
      } else false
    }
    if (negate) !matches else matches
  }

  override def hashCode(): Int = super.hashCode() + Objects.hashCode(methodMatcher, param, tpe, negate)

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: MatcherTransition => {
        super.equals(other) && Objects.equals(other.methodMatcher, methodMatcher) &&
          Objects.equals(other.param, param) && Objects.equals(other.tpe, tpe) &&
          Objects.equals(other.negate, negate)
      }
      case _ => false
    }
  }
}

object MatcherTransition {

  trait TransitionType
  object TransitionType {
    case object OnCall extends TransitionType
    case object OnCallToReturn extends TransitionType
    case object OnCallOrOnCallToReturn extends TransitionType
  }
}
