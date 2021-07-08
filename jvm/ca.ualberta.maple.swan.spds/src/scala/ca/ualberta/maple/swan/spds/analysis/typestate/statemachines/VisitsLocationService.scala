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

package ca.ualberta.maple.swan.spds.analysis.typestate.statemachines

import java.util
import java.util.Collections
import java.util.regex.Pattern

import boomerang.results.{BackwardBoomerangResults, ForwardBoomerangResults}
import boomerang.scene._
import boomerang.{BackwardQuery, Boomerang, DefaultBoomerangOptions, ForwardQuery, WeightedForwardQuery}
import ca.ualberta.maple.swan.ir.Literal
import ca.ualberta.maple.swan.spds.analysis.typestate.TypeStateAnalysis
import ca.ualberta.maple.swan.spds.analysis.typestate.statemachines.VisitsLocationService.{activityTypeMethodMatcher, cached, initialStateName, startMonitoringVisitsMethodMatcher, stateStrings}
import ca.ualberta.maple.swan.spds.structures.{SWANCallGraph, SWANDeclaredMethod, SWANType, SWANVal}
import typestate.TransitionFunction
import typestate.finiteautomata.MatcherTransition.Parameter
import typestate.finiteautomata.{MatcherTransition, State, TypeStateMachineWeightFunctions}
import wpds.impl.Weight

import scala.collection.mutable

/**
 * Detects nonoptimal configurations of Visits Location Service.
 */
class VisitsLocationService(cg: SWANCallGraph) extends TypeStateMachineWeightFunctions {

  val nonActiveStates = new mutable.HashMap[String, State]
  val activeStates = new mutable.HashMap[String, State]

  stateStrings.foreach(s => {
    nonActiveStates.put(s, new State {
      override def isErrorState: Boolean = false
      override def isInitialState: Boolean = s == initialStateName
      override def isAccepting: Boolean = !isErrorState
      override def toString: String = s
    })
    activeStates.put(s, new State {
      override def isErrorState: Boolean = s != "#CLActivityType.airborne!enumelt"
      override def isInitialState: Boolean = false
      override def isAccepting: Boolean = !isErrorState
      override def toString: String = s + " (active)"
    })
  })

  // non-active -> non-active, non-active -> active
  nonActiveStates.foreach(s1 => {
    nonActiveStates.foreach(s2 => {
      addTransition(new VisitsLocationServiceMatcherTransition(
        cg, s1._2, activityTypeMethodMatcher, s2._1, Parameter.Param1, s2._2, MatcherTransition.Type.OnCall))
    })
    activeStates.foreach(s2 => {
      if (s1._1 == s2._1) {
        addTransition(new MatcherTransition(
          s1._2, startMonitoringVisitsMethodMatcher, Parameter.Param1, s2._2, MatcherTransition.Type.OnCall))
      }
    })
  })

  // active -> active
  activeStates.foreach(s1 => {
    activeStates.foreach(s2 => {
      addTransition(new VisitsLocationServiceMatcherTransition(
        cg, s1._2, activityTypeMethodMatcher, s2._1, Parameter.Param1, s2._2, MatcherTransition.Type.OnCall))
    })
  })

  override def generateSeed(edge: ControlFlowGraph.Edge): util.Collection[WeightedForwardQuery[TransitionFunction]] = {
    val s: Statement = edge.getStart
    if (s.isAssign && s.getRightOp.isNewExpr) {
      val newExprType = s.getRightOp.getNewExprType
      if (Pattern.matches("CLLocationManager", newExprType.asInstanceOf[SWANType].tpe.name)) {
        return Collections.singleton(new WeightedForwardQuery[TransitionFunction](edge,
          new AllocVal(s.getLeftOp, s, s.getRightOp), initialTransition))
      }
    }
    Collections.emptySet
  }

  override def initialState(): State = nonActiveStates(initialStateName)
}

object VisitsLocationService {

  val initialStateName = "#CLActivityType.other!enumelt"

  val activityTypeMethodMatcher = "SWAN.CLLocationManager.setActivityType"
  val startMonitoringVisitsMethodMatcher = "#CLLocationManager.startMonitoringVisits!foreign"

  val stateStrings: Array[String] = Array(
    "#CLActivityType.airborne!enumelt",
    "#CLActivityType.automotiveNavigation!enumelt",
    "#CLActivityType.fitness!enumelt",
    "#CLActivityType.other!enumelt",
    "#CLActivityType.otherNavigation!enumelt")

  val cached = new mutable.HashMap[Val, mutable.HashSet[String]]
}

class VisitsLocationServiceMatcherTransition(cg: SWANCallGraph, from: State, methodMatcher: String, activityType: String, param: MatcherTransition.Parameter,
                                             to: State, tpe: MatcherTransition.Type) extends MatcherTransition(from, methodMatcher, param, to, tpe) {

  def checkActivityType(declaredMethod: SWANDeclaredMethod): Boolean = {
    val toQuery = declaredMethod.getInvokeExpr.getArg(1)
    if (cached.contains(toQuery)) {
      cached(toQuery).contains(activityType)
    } else {
      val query = BackwardQuery.make(declaredMethod.edge, toQuery)
      val solver = new Boomerang(cg, DataFlowScope.INCLUDE_ALL, new DefaultBoomerangOptions)
      val results = solver.solve(query)
      if (results.isTimedout) {
        throw new RuntimeException(TypeStateAnalysis.spdsTimeoutError)
      }
      if (results.isEmpty) {
        throw new RuntimeException("Expected argument to have allocation sites")
      }
      results.getAllocationSites.forEach((forwardQuery, _) => {
        forwardQuery.`var`().asInstanceOf[AllocVal].getAllocVal match {
          case c: SWANVal.Constant => {
            c.literal match {
              case Literal.string(value) => cached.put(toQuery, mutable.HashSet(value))
              case _ => throw new RuntimeException("Expected string literal")
            }
          } case _ => throw new RuntimeException("Expected literal")
        }
      })
      cached(toQuery).contains(activityType)
    }
  }

  override def matches(declaredMethod: DeclaredMethod): Boolean = {
    super.matches(declaredMethod) && checkActivityType(declaredMethod.asInstanceOf[SWANDeclaredMethod])
  }
}
