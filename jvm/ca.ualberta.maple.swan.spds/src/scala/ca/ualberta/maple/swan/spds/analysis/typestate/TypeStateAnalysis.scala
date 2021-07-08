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

import java.io.File
import java.util

import boomerang.BoomerangOptions.StaticFieldStrategy
import boomerang.debugger.Debugger
import boomerang.scene.{CallGraph, ControlFlowGraph, DataFlowScope, Val}
import boomerang.{BoomerangOptions, DefaultBoomerangOptions, WeightedForwardQuery}
import ca.ualberta.maple.swan.ir.Position
import ca.ualberta.maple.swan.spds.structures.{SWANCallGraph, SWANStatement}
import ideal._
import sync.pds.solver.WeightFunctions
import typestate.TransitionFunction
import typestate.finiteautomata.{State, TypeStateMachineWeightFunctions}
import wpds.impl.Weight

import scala.collection.mutable.ArrayBuffer

class TypeStateAnalysis(val cg: SWANCallGraph, val fsm: TypeStateMachineWeightFunctions, val spec: TypeStateSpecification) {

  val resultHandler = new StoreIDEALResultHandler[TransitionFunction]

  protected def createAnalysis(): IDEALAnalysis[TransitionFunction] = {
    new IDEALAnalysis[TransitionFunction](
      new IDEALAnalysisDefinition[TransitionFunction] {
        override def generate(stmt: ControlFlowGraph.Edge): util.Collection[WeightedForwardQuery[TransitionFunction]] = {
          fsm.generateSeed(stmt)
        }

        override def weightFunctions(): WeightFunctions[ControlFlowGraph.Edge, Val, ControlFlowGraph.Edge, TransitionFunction] = fsm

        override def callGraph(): CallGraph = cg

        override def debugger(idealSeedSolver: IDEALSeedSolver[TransitionFunction]): Debugger[TransitionFunction] = {
          new Debugger[TransitionFunction]
        }

        override def getResultHandler: IDEALResultHandler[_ <: Weight] = resultHandler

        override def getDataFlowScope: DataFlowScope = DataFlowScope.INCLUDE_ALL

        override def boomerangOptions(): BoomerangOptions = {
          new DefaultBoomerangOptions {
            override def allowMultipleQueries(): Boolean = true
            override def getStaticFieldStrategy: BoomerangOptions.StaticFieldStrategy = StaticFieldStrategy.SINGLETON
          }
        }
      }
    )
  }

  def executeAnalysis(): TypeStateResults = {
    this.createAnalysis().run()
    val errors = new ArrayBuffer[(Position, State)]()
    val seedToSolvers = resultHandler.getResults
    seedToSolvers.entrySet().forEach(e => {
      e.getValue.getObjectDestructingStatements.cellSet().forEach(s => {
        s.getValue.values().forEach(v => {
          if (v.to().isErrorState) {
            s.getValue.getLastStateChangeStatements.forEach(x => {
              val pos = x.getStart.asInstanceOf[SWANStatement].getPosition
              if (pos.nonEmpty) errors.append((pos.get, v.to()))
            })
          }
        })
      })
    })
    new TypeStateResults(errors, spec)
  }
}

object TypeStateAnalysis {

  val spdsTimeoutError = "SPDS timed out. Enable logging (remove log4j.properties) to see issue."
  val spdsError = "SPDS error. Enable logging (remove log4j.properties) to see issue or try increasing stack size with -Xss."

  def parse(file: File): ArrayBuffer[TypeStateSpecification] = {
    val fsms = new ArrayBuffer[TypeStateSpecification]
    TypeStateSpecification.parse(file).foreach(s => {
      fsms.append(s)
    })
    fsms
  }
}