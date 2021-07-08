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

import boomerang.scene._
import boomerang.{BackwardQuery, Boomerang, BoomerangOptions, DefaultBoomerangOptions, WeightedForwardQuery}
import ca.ualberta.maple.swan.ir.Literal
import ca.ualberta.maple.swan.spds.analysis.typestate.TypeStateAnalysis
import ca.ualberta.maple.swan.spds.analysis.typestate.statemachines.StandardLocationService._
import ca.ualberta.maple.swan.spds.structures.{SWANCallGraph, SWANDeclaredMethod, SWANStatement, SWANType, SWANVal}
import typestate.TransitionFunction
import typestate.finiteautomata.MatcherTransition.Parameter
import typestate.finiteautomata.{MatcherTransition, State, TypeStateMachineWeightFunctions}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * Detects nonoptimal configurations of Standard Location Service.
 */
class StandardLocationService(cg: SWANCallGraph) extends TypeStateMachineWeightFunctions {

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
      override def isErrorState: Boolean = errorStates.contains(s)
      override def isInitialState: Boolean = false
      override def isAccepting: Boolean = !isErrorState
      override def toString: String = s + " (active)"
    })
  })

  // non-active -> active
  nonActiveStates.foreach(s1 => {
    activeStates.foreach(s2 => {
      if (s1._1 == s2._1) {
        addTransition(new MatcherTransition(
          s1._2, startUpdatingLocationMethodMatcher, Parameter.Param1, s2._2, MatcherTransition.Type.OnCall))
      }
    })
  })

  // filter transitions for non-active and active
  accuracyStrings.foreach(s => {
    filterStrings.foreach(s1 => {
      val nonActiveFromState = nonActiveStates(s + "_" + s1)
      val activeFromState = activeStates(s + "_" + s1)
      filterStrings.foreach(s2 => {
        val nonActiveToState = nonActiveStates(s + "_" + s2)
        val activeToState = activeStates(s + "_" + s2)
        addTransition(new DistanceFilterTransition(
          cg, nonActiveFromState, distanceFilterMethodMatcher, s2, Parameter.Param1, nonActiveToState, MatcherTransition.Type.OnCall))
        addTransition(new DistanceFilterTransition(
          cg, activeFromState, distanceFilterMethodMatcher, s2, Parameter.Param1, activeToState, MatcherTransition.Type.OnCall))
      })
    })
  })

  // accuracy transitions for non-active and active
  filterStrings.foreach(s => {
    accuracyStrings.foreach(s1 => {
      val nonActiveFromState = nonActiveStates(s1 + "_" + s)
      val activeFromState = activeStates(s1 + "_" + s)
      accuracyStrings.foreach(s2 => {
        val nonActiveToState = nonActiveStates(s2 + "_" + s)
        val activeToState = activeStates(s2 + "_" + s)
        addTransition(new DesiredAccuracyTransition(
          cg, nonActiveFromState, desiredAccuracyMethodMatcher, s2, Parameter.Param1, nonActiveToState, MatcherTransition.Type.OnCall))
        addTransition(new DesiredAccuracyTransition(
          cg, activeFromState, desiredAccuracyMethodMatcher, s2, Parameter.Param1, activeToState, MatcherTransition.Type.OnCall))
      })
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

object StandardLocationService {

  val cached = new mutable.HashMap[Val, mutable.HashSet[String]]

  val initialStateName = "Best_None"

  val desiredAccuracyMethodMatcher = "SWAN.CLLocationManager.setDesiredAccuracy"
  val distanceFilterMethodMatcher = "SWAN.CLLocationManager.setDistantFilter"
  val startUpdatingLocationMethodMatcher = "#CLLocationManager.startUpdatingLocation!foreign"

  val accuracyStrings: Array[String] = Array("Best", "Hundred", "Kilometer", "Navigation")
  val filterStrings: Array[String] = Array("16", "256", "4096", "65536", "None")

  val stateStrings: ArrayBuffer[String] = {
    val a = new ArrayBuffer[String]()
    accuracyStrings.foreach(s1 => {
      filterStrings.foreach(s2 => {
        a.append(s1 + "_" + s2)
      })
    })
    a
  }

  val errorStates: Array[String] = Array(
    "Best_16 (active)", "Best_256 (active)", "Best_4096 (active)", "Best_65536 (active)",
    "Hundred_4096 (active)", "Hundred_65536 (active)",
    "Navigation_16 (active)", "Navigation_256 (active)", "Navigation_4096 (active)", "Navigation_65536 (active)")
}

// TODO: Multiple allocation sites case

// TODO: 3km, nearest 10

class DesiredAccuracyTransition(cg: SWANCallGraph, from: State, methodMatcher: String,
                                desiredAccuracy: String, param: MatcherTransition.Parameter,
                                to: State, tpe: MatcherTransition.Type) extends MatcherTransition(from, methodMatcher, param, to, tpe) {

  def checkDesiredAccuracy(declaredMethod: SWANDeclaredMethod): Boolean = {
    val toQuery = declaredMethod.getInvokeExpr.getArg(1)
    if (cached.contains(toQuery)) {
      cached(toQuery).contains(desiredAccuracy)
    } else {
      val query = BackwardQuery.make(declaredMethod.edge, toQuery)
      val solver = new Boomerang(cg, DataFlowScope.INCLUDE_ALL, new DefaultBoomerangOptions {
        override def getStaticFieldStrategy: BoomerangOptions.StaticFieldStrategy =  BoomerangOptions.StaticFieldStrategy.FLOW_SENSITIVE
      })
      val results = solver.solve(query)
      if (results.isTimedout) {
        throw new RuntimeException(TypeStateAnalysis.spdsTimeoutError)
      }
      if (results.isEmpty) {
        throw new RuntimeException("Expected argument to have allocation sites")
      }
      results.getAllocationSites.forEach((forwardQuery, _) => {
        var found = false
        results.asStatementValWeightTable(forwardQuery).cellSet().forEach(e => {
          e.getRowKey.getStart.asInstanceOf[SWANStatement] match {
            case s: SWANStatement.StaticFieldLoad => {
              if (!found) {
                s.inst.field match {
                  case "kCLLocationAccuracyBest" => cached.put(toQuery, mutable.HashSet("Best"))
                  case "kCLLocationAccuracyHundredMeters" => cached.put(toQuery, mutable.HashSet("Hundred"))
                  case "kCLLocationAccuracyKilometer" => cached.put(toQuery, mutable.HashSet("Kilometer"))
                  case "kCLLocationAccuracyBestForNavigation" => cached.put(toQuery, mutable.HashSet("Navigation"))
                  case _ => throw new RuntimeException("Unexpected desired accuracy configuration")
                }
                found = true
              }
            }
            case _ =>
          }
        })

        if (!found) {
          throw new RuntimeException("Could not find desired accuracy configuration")
        }
      })
      cached(toQuery).contains(desiredAccuracy)
    }
  }

  override def matches(declaredMethod: DeclaredMethod): Boolean = {
    super.matches(declaredMethod) && checkDesiredAccuracy(declaredMethod.asInstanceOf[SWANDeclaredMethod])
  }

}

class DistanceFilterTransition(cg: SWANCallGraph, from: State, methodMatcher: String,
                              distanceFilter: String, param: MatcherTransition.Parameter,
                              to: State, tpe: MatcherTransition.Type) extends MatcherTransition(from, methodMatcher, param, to, tpe) {

  private val possibleValues = Array(16, 256, 4096, 65536)

  def checkDistanceFilter(declaredMethod: SWANDeclaredMethod): Boolean = {
    val toQuery = declaredMethod.getInvokeExpr.getArg(1)
    if (cached.contains(toQuery)) {
      cached(toQuery).contains(distanceFilter)
    } else {
      val query = BackwardQuery.make(declaredMethod.edge, toQuery)
      val solver = new Boomerang(cg, DataFlowScope.INCLUDE_ALL, new DefaultBoomerangOptions)
      val results = solver.solve(query)
      if (results.getAllocationSites.isEmpty) {
        throw new RuntimeException("Expected argument to have allocation sites")
      }
      results.getAllocationSites.forEach((forwardQuery, _) => {
        var found = false
        forwardQuery.`var`().asInstanceOf[AllocVal].getAllocVal match {
          case c: SWANVal.Constant => {
            c.literal match {
              case Literal.float(value) => {
                // Find closest configuration
                possibleValues.minBy(v => math.abs(v - value)) match {
                  case 16 => cached.put(toQuery, mutable.HashSet("16"))
                  case 256 => cached.put(toQuery, mutable.HashSet("256"))
                  case 4096 => cached.put(toQuery, mutable.HashSet("4096"))
                  case 65536 => cached.put(toQuery, mutable.HashSet("65536"))
                  case _ => // impossible
                }
                found = true
              }
              case _ =>
            }
          } case _ =>
        }
        if (!found) {
          results.asStatementValWeightTable(forwardQuery).cellSet().forEach(e => {
            e.getRowKey.getStart.asInstanceOf[SWANStatement] match {
              case s: SWANStatement.StaticFieldLoad => {
                if (s.inst.field == "kCLDistanceFilterNone" && !found) {
                  cached.put(toQuery, mutable.HashSet("None"))
                  found = true
                }
              }
              case _ =>
            }
          })
        }
        if (!found) {
          throw new RuntimeException("Could not find distance filter configuration")
        }
      })
      cached(toQuery).contains(distanceFilter)
    }
  }

  override def matches(declaredMethod: DeclaredMethod): Boolean = {
    super.matches(declaredMethod) && checkDistanceFilter(declaredMethod.asInstanceOf[SWANDeclaredMethod])
  }
}