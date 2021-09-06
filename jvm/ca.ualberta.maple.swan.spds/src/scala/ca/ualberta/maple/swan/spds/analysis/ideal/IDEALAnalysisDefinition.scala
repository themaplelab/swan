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

package ca.ualberta.maple.swan.spds.analysis.ideal

import ca.ualberta.maple.swan.spds.analysis.boomerang.{BoomerangOptions, DefaultBoomerangOptions, WeightedForwardQuery}
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{CallGraph, DataFlowScope, Statement, Val}
import ca.ualberta.maple.swan.spds.analysis.pds.solver.WeightFunctions
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.Weight

import scala.collection.mutable

abstract class IDEALAnalysisDefinition[W <: Weight] {

  def generate(stmt: Edge[Statement, Statement]): mutable.HashSet[WeightedForwardQuery[W]]

  def weightFunctions: WeightFunctions[Edge[Statement, Statement], Val, Edge[Statement, Statement], W]

  def callGraph: CallGraph

  def enableStrongUpdates: Boolean = true

  def boomerangOptions: BoomerangOptions = {
    new DefaultBoomerangOptions {

      override def getStaticFieldStrategy: BoomerangOptions.StaticFieldStrategy = BoomerangOptions.FLOW_SENSITIVE

      override def allowMultipleQueries: Boolean = true
    }
  }

  def getResultsHandler: IDEALResultHandler[W] = new IDEALResultHandler[W]

  def getDataFlowScope: DataFlowScope
}
