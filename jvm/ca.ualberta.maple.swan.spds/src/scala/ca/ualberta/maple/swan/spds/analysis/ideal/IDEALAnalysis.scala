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

import ca.ualberta.maple.swan.spds.analysis.boomerang.results.ForwardBoomerangResults
import ca.ualberta.maple.swan.spds.analysis.boomerang.{ForwardQuery, Query, WeightedForwardQuery}
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{AnalysisScope, Statement}
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.Weight

import scala.collection.mutable

class IDEALAnalysis[W <: Weight](analysisDefinition: IDEALAnalysisDefinition[W]) {

  protected var seedCount = 0

  protected val seedFactory: AnalysisScope = new AnalysisScope(analysisDefinition.callGraph) {

    override def generate(stmt: Edge[Statement, Statement]): mutable.HashSet[_ <: Query] = {
      analysisDefinition.generate(stmt)
    }
  }

  def run(): Unit = {
    seedFactory.computeSeeds.foreach {
      case seed1: WeightedForwardQuery[W] =>
        seedCount += 1
        run(seed1)
      case _ =>
    }
  }

  def run(seed: WeightedForwardQuery[W]): ForwardBoomerangResults[W] = {
    val analysis = new IDEALSeedSolver(analysisDefinition, seed)
    val res = analysis.run()
    analysisDefinition.getResultsHandler.report(seed, res)
    res
  }
}
