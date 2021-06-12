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

package ca.ualberta.maple.swan.spds.analysis

import java.io.{File, FileWriter}
import java.util
import java.util.Collections
import java.util.regex.Pattern

import boomerang.WeightedForwardQuery
import boomerang.scene.{AllocVal, ControlFlowGraph, Statement}
import ca.ualberta.maple.swan.spds.analysis.TypeStateAnalysis.TypeStateAnalysisResults
import ca.ualberta.maple.swan.spds.structures.SWANType
import typestate.TransitionFunction
import typestate.finiteautomata.MatcherTransition.{Parameter, Type}
import typestate.finiteautomata.{MatcherTransition, State, TypeStateMachineWeightFunctions}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Source

object StateMachineFactory {
  class Specification(val name: String, val typeNamePattern: String,
                      val states: ArrayBuffer[(String, Boolean, Boolean, Boolean)],
                      val transitions: ArrayBuffer[Specification.Transition]) {
    override def toString: String = {
      val sb = new StringBuilder()
      sb.append("  name:  ")
      sb.append(name)
      sb.append("\n  type:  ")
      sb.append(typeNamePattern)
      sb.append("\n  states:\n")
      states.foreach(s => {
        sb.append("    ")
        sb.append(s._1)
        sb.append(", error: ")
        sb.append(if (s._2) "yes" else "no")
        sb.append(", initial: ")
        sb.append(if (s._3) "yes" else "no")
        sb.append(", accepting: ")
        sb.append(if (s._4) "yes" else "no")
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
  }
  object Specification {
    class Transition(val from: String, val methodMatcher: String, val param: Parameter, val to: String, val tpe: Type) {
      override def toString: String = {
        from + " -> " + to + " for `" + methodMatcher + "`, " + param + ", " + tpe
      }
    }
    def writeResults(f: File, allResults: ArrayBuffer[TypeStateAnalysisResults]): Unit = {
      val fw = new FileWriter(f)
      try {
        val r = new ArrayBuffer[ujson.Obj]
        allResults.foreach(results => {
          val json = ujson.Obj("name" -> results.spec.name)
          json("errors") = results.errors.map(_.toString)
          r.append(json)
        })
        val finalJson = ujson.Value(r)
        fw.write(finalJson.render(2))
      } finally {
        fw.close()
      }
    }
  }

  def parseSpecification(file: File): ArrayBuffer[Specification] = {
    val buffer = Source.fromFile(file)
    val jsonString = buffer.getLines().mkString
    buffer.close()
    val data = ujson.read(jsonString)
    val specs = new ArrayBuffer[Specification]
    data.arr.foreach(v => {
      val name = v("name").str
      val typeNamePattern = v("type").str
      val states: ArrayBuffer[(String, Boolean, Boolean, Boolean)] = v("states").arr.map(f => {
        val stateName = f("name").str
        val error = f("error").bool
        val initial = f("initial").bool
        val accepting = f("accepting").bool
        (stateName, error, initial, accepting)
      })
      val transitions: ArrayBuffer[Specification.Transition] = v("transitions").arr.map(f => {
        val from = f("from").str
        val methodPattern = f("method").str
        val param: Parameter = f("param").str match {
          case "This" => Parameter.This
          case "Param1" => Parameter.Param1
          case "Param2" => Parameter.Param2
          case _ => throw new RuntimeException("Invalid transition param: " + f("param").str)
        }
        val to = f("to").str
        val tpe: Type = f("type").str match {
          case "OnCall" => Type.OnCall
          case "None" => Type.None
          case "OnCallToReturn" => Type.OnCallToReturn
          case "OnCallOrOnCallToReturn" => Type.OnCallOrOnCallToReturn
          case _ => throw new RuntimeException("Invalid transition type: " + f("type").str)
        }
        new Specification.Transition(from, methodPattern, param, to, tpe)
      })
      specs.append(new Specification(name, typeNamePattern, states, transitions))
    })
    specs
  }

  def make(spec : Specification): TypeStateMachineWeightFunctions = {

    val states = new mutable.HashMap[String, State]()

    var initState: State = null

    spec.states.foreach(s => {
      val newState = new State {
        override def toString: String = s._1
        override def isErrorState: Boolean = s._2
        override def isInitialState: Boolean = s._3
        override def isAccepting: Boolean = s._4
      }
      if (newState.isInitialState) {
        if (initState != null) {
          throw new RuntimeException("Multiple initial states")
        } else {
          initState = newState
        }
      }
      states.put(s._1, newState)
    })

    if (initState == null) {
      throw new RuntimeException("No initial state specified")
    }

    def getState(s: String): State = {
      if (!states.contains(s)) {
        throw new RuntimeException("Transition in specification contains undeclared state: " + s)
      }
      states(s)
    }

    new TypeStateMachineWeightFunctions {

      spec.transitions.foreach(t => {
        addTransition(new MatcherTransition(
          getState(t.from), t.methodMatcher, t.param, getState(t.to), t.tpe))
      })

      override def generateSeed(edge: ControlFlowGraph.Edge): util.Collection[WeightedForwardQuery[TransitionFunction]] = {
        val s: Statement = edge.getStart
        if (s.isAssign && s.getRightOp.isNewExpr) {
          val newExprType = s.getRightOp.getNewExprType
          if (Pattern.matches(spec.typeNamePattern, newExprType.asInstanceOf[SWANType].tpe.name)) {
            return Collections.singleton(new WeightedForwardQuery[TransitionFunction](edge,
              new AllocVal(s.getLeftOp, s, s.getRightOp), initialTransition))
          }
        }
        Collections.emptySet
      }

      override def initialState(): State = initState

      override def toString: String = spec.name
    }
  }
}
