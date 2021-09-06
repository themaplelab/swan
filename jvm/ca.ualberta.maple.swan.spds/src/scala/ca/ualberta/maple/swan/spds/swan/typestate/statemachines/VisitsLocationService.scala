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

package ca.ualberta.maple.swan.spds.swan.typestate.statemachines

import java.util
import java.util.Collections
import java.util.regex.Pattern
import ca.ualberta.maple.swan.ir.Literal
import ca.ualberta.maple.swan.spds.analysis.boomerang.{BackwardQuery, Boomerang, DefaultBoomerangOptions, WeightedForwardQuery}
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.Val.AllocVal
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{Assignment, DataFlowScope, InvokeExpr, Statement, Val}
import ca.ualberta.maple.swan.spds.analysis.typestate.{MatcherTransition, State, TransitionFunction, TypeStateMachineWeightFunctions}
import ca.ualberta.maple.swan.spds.swan.typestate.TypeStateAnalysis
import ca.ualberta.maple.swan.spds.swan.typestate.statemachines.VisitsLocationService.{activityTypeMethodMatcher, cached, initialStateName, startMonitoringVisitsMethodMatcher, stateStrings}
import ca.ualberta.maple.swan.spds.swan.{SWANCallGraph, SWANType, SWANVal}

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
        cg, s1._2, activityTypeMethodMatcher, s2._1, 0, s2._2, MatcherTransition.TransitionType.OnCall))
    })
    activeStates.foreach(s2 => {
      if (s1._1 == s2._1) {
        addTransition(new MatcherTransition(
          s1._2, startMonitoringVisitsMethodMatcher, 0, s2._2, MatcherTransition.TransitionType.OnCall))
      }
    })
  })

  // active -> active
  activeStates.foreach(s1 => {
    activeStates.foreach(s2 => {
      addTransition(new VisitsLocationServiceMatcherTransition(
        cg, s1._2, activityTypeMethodMatcher, s2._1, 0, s2._2, MatcherTransition.TransitionType.OnCall))
    })
  })

  override def generateSeed(edge: Edge[Statement, Statement]): mutable.ArrayBuffer[WeightedForwardQuery[TransitionFunction]] = {
    val s: Statement = edge.start
    s match {
      case assignment: Assignment if assignment.rhs.isInstanceOf[Val.NewExpr] =>
        val newExprType = assignment.rhs.getType
        if (Pattern.matches("CLLocationManager", newExprType.asInstanceOf[SWANType].tpe.name)) {
          mutable.ArrayBuffer(new WeightedForwardQuery[TransitionFunction](edge,
            AllocVal(assignment.lhs, s, assignment.rhs), initialTransition))
        } else mutable.ArrayBuffer.empty
      case _ => mutable.ArrayBuffer.empty
    }
  }

  override def initialState: State = nonActiveStates(initialStateName)
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

class VisitsLocationServiceMatcherTransition(cg: SWANCallGraph, from: State, methodMatcher: String, activityType: String, param: Int,
                                             to: State, tpe: MatcherTransition.TransitionType) extends MatcherTransition(from, methodMatcher, param, to, tpe) {

  def checkActivityType(invokeExpr: InvokeExpr, edge: Edge[Statement, Statement]): Boolean = {
    val toQuery = invokeExpr.getArg(1)
    if (cached.contains(toQuery)) {
      cached(toQuery).contains(activityType)
    } else {
      val query = new BackwardQuery(edge, toQuery)
      val solver = new Boomerang(cg, DataFlowScope.INCLUDE_ALL, new DefaultBoomerangOptions)
      val results = solver.solve(query)
      if (results.isEmpty) {
        throw new RuntimeException("Expected argument to have allocation sites")
      }
      results.getAllocationSites.foreach(x => {
        val forwardQuery = x._1
        forwardQuery.variable.allocVal match {
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

  override def matches(invokeExpr: InvokeExpr, edge: Edge[Statement, Statement]): Boolean = {
    super.matches(invokeExpr, edge) && checkActivityType(invokeExpr: InvokeExpr, edge: Edge[Statement, Statement])
  }
}
