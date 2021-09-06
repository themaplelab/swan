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

package ca.ualberta.maple.swan.spds.swan.typestate

import java.io.File
import java.util
import java.util.Collections
import java.util.regex.Pattern
import ca.ualberta.maple.swan.ir.Position
import ca.ualberta.maple.swan.spds.analysis.boomerang.WeightedForwardQuery
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.Val.{AllocVal, NewExpr}
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{Assignment, Statement}
import ca.ualberta.maple.swan.spds.analysis.typestate.MatcherTransition.TransitionType
import ca.ualberta.maple.swan.spds.analysis.typestate.{MatcherTransition, State, TransitionFunction, TypeStateMachineWeightFunctions}
import ca.ualberta.maple.swan.spds.swan.typestate.TypeStateSpecification.jsonVal
import ca.ualberta.maple.swan.spds.swan.typestate.statemachines.{StandardLocationService, VisitsLocationService}
import ca.ualberta.maple.swan.spds.swan.{SWANCallGraph, SWANType}
import ujson.Value

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.util.{Failure, Success, Try}

trait TypeStateSpecification {
  def getName: String
  def make(cg: SWANCallGraph): TypeStateMachineWeightFunctions
  def writeResults(v: ujson.Value, errors: ArrayBuffer[(Position, State)]): Unit
}

object TypeStateSpecification {

  def jsonVal(v: ujson.Value, s: String): ujson.Value = {
    Try(v(s)) match {
      case Failure(_) => throw new RuntimeException("JSON does not contain expected \"" + s + "\" field")
      case Success(value) => value
    }
  }

  def parse(file: File): ArrayBuffer[TypeStateSpecification] = {
    val specs = new ArrayBuffer[TypeStateSpecification]
    val buffer = Source.fromFile(file)
    val jsonString = buffer.getLines().mkString
    buffer.close()
    val data = ujson.read(jsonString)
    data.arr.foreach(v => {
      jsonVal(v,"style").num match {
        case 0 => specs.append(TypeStateJSONSpecification.parse(v))
        case 1 => specs.append(TypeStateJSONProgrammaticSpecification.parse(v))
        case _ => throw new RuntimeException("Invalid \"style\" in typestate JSON config (0 or 1 expected)")
      }
    })
    specs
  }
}

class TypeStateJSONSpecification(val name: String,
                                 val typeName: String,
                                 val description: String,
                                 val advice: String,
                                 val states: mutable.HashMap[String, TypeStateJSONSpecification.JSONState],
                                 val transitions: ArrayBuffer[TypeStateJSONSpecification.JSONTransition]) extends TypeStateSpecification {

  override def make(cg: SWANCallGraph): TypeStateMachineWeightFunctions = {

    val generatedStates = new mutable.HashMap[String, State]()

    var initState: State = null

    this.states.values.foreach(s => {
      val newState = new State {
        override def toString: String = s.name
        override def isErrorState: Boolean = s.error
        override def isInitialState: Boolean = s.initial
        override def isAccepting: Boolean = s.accepting
      }
      if (newState.isInitialState) {
        if (initState != null) {
          throw new RuntimeException("Multiple initial states")
        } else {
          initState = newState
        }
      }
      generatedStates.put(s.name, newState)
    })

    if (initState == null) {
      throw new RuntimeException("No initial state specified")
    }

    def getState(s: String): State = {
      if (!states.contains(s)) {
        throw new RuntimeException("Transition in specification contains undeclared state: " + s)
      }
      generatedStates(s)
    }

    new TypeStateMachineWeightFunctions {

      transitions.foreach(t => {
        addTransition(new MatcherTransition(t.from, t.methodMatcher, t.param, t.to, t.tpe))
      })

      override def generateSeed(edge: Edge[Statement, Statement]): mutable.ArrayBuffer[WeightedForwardQuery[TransitionFunction]] = {
        val s: Statement = edge.start
        s match {
          case assignment: Assignment if assignment.rhs.isInstanceOf[NewExpr] =>
            val newExprType = assignment.rhs.getType
            if (Pattern.matches(typeName, newExprType.asInstanceOf[SWANType].tpe.name)) {
              mutable.ArrayBuffer(new WeightedForwardQuery[TransitionFunction](edge,
                AllocVal(assignment.lhs, s, assignment.rhs), initialTransition))
            } else mutable.ArrayBuffer.empty
          case _ => mutable.ArrayBuffer.empty
        }
      }

      override def initialState: State = initState
    }
  }

  override def writeResults(v: Value, errors: ArrayBuffer[(Position, State)]): Unit = {
    v("name") = this.name
    v("description") = this.description
    v("advice") = this.advice
    val errs = new ArrayBuffer[Value]()
    errors.foreach(x => {
      val e = ujson.Obj()
      e("pos") = x._1.toString
      val jsonState = this.states(x._2.toString)
      e("message") = jsonState.message.get
      if (jsonState.severity.nonEmpty) e("severity") = jsonState.severity.get
      errs.append(e)
    })
    v("errors") = errs
  }

  override def toString: String = {
    val sb = new StringBuilder()
    sb.append("  name:  ")
    sb.append(name)
    sb.append("\n  type:  ")
    sb.append(typeName)
    sb.append("\n  description:  ")
    sb.append(description)
    sb.append("\n  advice:  ")
    sb.append(advice)
    sb.append("\n  states:\n")
    states.foreach(s => {
      sb.append("    ")
      sb.append(s._2)
      sb.append("\n")
    })
    sb.append("  transitions:\n")
    transitions.foreach(t => {
      sb.append("    ")
      sb.append(t)
      sb.append("\n")
    })
    sb.toString()
  }

  override def getName: String = name
}

object TypeStateJSONSpecification {

  class JSONState(val name: String,
                  val error: Boolean,
                  val initial: Boolean,
                  val accepting: Boolean,
                  val message: Option[String],
                  val severity: Option[Int]) {
    override def toString: String = {
      s"$name, error: $error, initial: $initial, accepting: $accepting" +
        { if (message.nonEmpty) s", ${message.get}" else "" } +
        { if (severity.nonEmpty) s", severity: ${severity.get}" else "" }
    }
  }
  class JSONTransition(val from: String,
                        val method: String,
                        val param: Int,
                        val to: String,
                        val tpe: TransitionType) {
    override def toString: String = {
      from + " -> " + to + " for `" + method + "`, " + param + ", " + tpe
    }
  }

  def parse(v: ujson.Value): TypeStateJSONSpecification = {
    val name = jsonVal(v, "name").str
    val typeNamePattern = jsonVal(v, "type").str
    val description = jsonVal(v, "description").str
    val advice = jsonVal(v, "advice").str
    val states = mutable.HashMap.from[String, TypeStateJSONSpecification.JSONState](jsonVal(v, "states").arr.map(f => {
      val stateName = jsonVal(f, "name").str
      val error = jsonVal(f, "error").bool
      val initial = jsonVal(f, "initial").bool
      val accepting = jsonVal(f, "accepting").bool
      val message: Option[String] = Try(Some(f("message").str)).getOrElse(None)
      val severity: Option[Int] = Try(Some(f("severity").num.toInt)).getOrElse(None)
      if (message.isEmpty && error) throw new RuntimeException("error state requires a \"message\" for state " + stateName)
      (stateName, new JSONState(stateName, error, initial, accepting, message, severity))
    }))
    val transitions: ArrayBuffer[TypeStateJSONSpecification.JSONTransition] = jsonVal(v, "transitions").arr.map(f => {
      val from = jsonVal(f, "from").str
      val methodPattern = jsonVal(f, "method").str
      val param = jsonVal(f, "param").num
      val to = jsonVal(f, "to").str
      val tpe: TransitionType = jsonVal(f, "type").str match {
        case "OnCall" => TransitionType.OnCall
        case "OnCallToReturn" => TransitionType.OnCallToReturn
        case "OnCallOrOnCallToReturn" => TransitionType.OnCallOrOnCallToReturn
        case _ => throw new RuntimeException("Invalid transition type: " + f("type").str)
      }
      new TypeStateJSONSpecification.JSONTransition(from, methodPattern, param.toInt, to, tpe)
    })
    new TypeStateJSONSpecification(name, typeNamePattern, description, advice, states, transitions)
  }
}

class TypeStateJSONProgrammaticSpecification(val name: String,
                                             val description: String,
                                             val advice: String,
                                             val states: mutable.HashMap[String, TypeStateJSONProgrammaticSpecification.JSONState]) extends TypeStateSpecification {

  override def make(cg: SWANCallGraph): TypeStateMachineWeightFunctions = {
    name match {
      case "VisitsLocationService" => new VisitsLocationService(cg)
      case "StandardLocationService" => new StandardLocationService(cg)
      case _ => throw new RuntimeException(s"No programmatic typestate analysis exists with name: $name")
    }
  }

  override def writeResults(v: Value, errors: ArrayBuffer[(Position, State)]): Unit = {
    v("name") = this.name
    v("description") = this.description
    v("advice") = this.advice
    val errs = new ArrayBuffer[Value]()
    errors.foreach(x => {
      val e = ujson.Obj()
      e("pos") = x._1.toString
      val jsonState = this.states(x._2.toString)
      e("message") = jsonState.message
      if (jsonState.severity.nonEmpty) e("severity") = jsonState.severity.get
      errs.append(e)
    })
    v("errors") = errs
  }

  override def toString: String = {
    val sb = new StringBuilder()
    sb.append("  (programmatic typestate configuration)\n")
    sb.append("  name:  ")
    sb.append(name)
    sb.append("\n  description:  ")
    sb.append(description)
    sb.append("\n  advice:  ")
    sb.append(advice)
    sb.append("\n  states:\n")
    states.foreach(s => {
      sb.append("    ")
      sb.append(s._2)
      sb.append("\n")
    })
    sb.toString()
  }

  override def getName: String = name
}

object TypeStateJSONProgrammaticSpecification {

  class JSONState(val name: String,
                  val message: String,
                  val severity: Option[Int]) {
    override def toString: String = {
      s"$name, message: $message" + { if (severity.nonEmpty) s", severity: ${severity.get}" else "" }
    }
  }

  def parse(v: Value): TypeStateJSONProgrammaticSpecification = {
    val name = jsonVal(v, "name").str
    val description = jsonVal(v, "description").str
    val advice = jsonVal(v, "advice").str
    val states = mutable.HashMap.from[String, JSONState](jsonVal(v, "states").arr.map(f => {
      val stateName = jsonVal(f, "name").str
      val message = jsonVal(f, "message").str
      val severity = Try(Some(f("severity").num.toInt)).getOrElse(None)
      (stateName, new JSONState(stateName, message, severity))
    }))
    new TypeStateJSONProgrammaticSpecification(name, description, advice, states)
  }
}