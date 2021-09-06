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
import ca.ualberta.maple.swan.ir.Position
import ca.ualberta.maple.swan.spds.analysis.boomerang.{BoomerangOptions, DefaultBoomerangOptions, WeightedForwardQuery}
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{CallGraph, DataFlowScope, Statement, Val}
import ca.ualberta.maple.swan.spds.analysis.ideal.{IDEALAnalysis, IDEALAnalysisDefinition, IDEALResultHandler, StoreIDEALResultHandler}
import ca.ualberta.maple.swan.spds.analysis.pds.solver.WeightFunctions
import ca.ualberta.maple.swan.spds.analysis.typestate.{State, TransitionFunction, TypeStateMachineWeightFunctions}
import ca.ualberta.maple.swan.spds.swan.{SWANCallGraph, SWANStatement}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class TypeStateAnalysis(val cg: SWANCallGraph, val fsm: TypeStateMachineWeightFunctions, val spec: TypeStateSpecification) {

  val resultHandler = new StoreIDEALResultHandler[TransitionFunction]

  protected def createAnalysis(): IDEALAnalysis[TransitionFunction] = {
    new IDEALAnalysis[TransitionFunction](
      new IDEALAnalysisDefinition[TransitionFunction] {

        override def generate(stmt: Edge[Statement, Statement]): mutable.HashSet[WeightedForwardQuery[TransitionFunction]] = {
          mutable.HashSet.from(fsm.generateSeed(stmt))
        }

        override def getResultsHandler: IDEALResultHandler[TransitionFunction] = resultHandler

        override def weightFunctions: WeightFunctions[Edge[Statement, Statement], Val, Edge[Statement, Statement], TransitionFunction] = fsm

        override def callGraph: CallGraph = cg

        override def getDataFlowScope: DataFlowScope = DataFlowScope.INCLUDE_ALL

        override def boomerangOptions: BoomerangOptions = {
          new DefaultBoomerangOptions {
            override def allowMultipleQueries: Boolean = true
            override def getStaticFieldStrategy: BoomerangOptions.StaticFieldStrategy = BoomerangOptions.SINGLETON
          }
        }
      }
    )
  }

  def executeAnalysis(): TypeStateResults = {
    this.createAnalysis().run()
    val errors = new ArrayBuffer[(Position, State)]()
    val seedToSolvers = resultHandler.getResults
    seedToSolvers.foreach(e => {
      e._2.getObjectDestructingStatements.cellSet().forEach(s => {
        s.getValue.values.foreach(v => {
          if (v.to.isErrorState) {
            s.getValue.stateChangeStatements.foreach(x => {
              val pos = x.start.asInstanceOf[SWANStatement].getPosition
              if (pos.nonEmpty) errors.addOne((pos.get, v.to))
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