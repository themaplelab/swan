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

package ca.ualberta.maple.swan.spds.analysis.boomerang.stats

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{Method, Val}
import ca.ualberta.maple.swan.spds.analysis.boomerang._
import ca.ualberta.maple.swan.spds.analysis.boomerang.results.{BackwardBoomerangResults, ForwardBoomerangResults}
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.solver.AbstractBoomerangSolver
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes.Node
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.Weight

import scala.collection.mutable

trait IBoomerangStats[W <: Weight] {

  def registerSolver(key: Query, solver: AbstractBoomerangSolver[W]): Unit

  def registerFieldWritePOI(key: WeightedBoomerang[W]#FieldWritePOI): Unit

  def getCallVisitedMethods: mutable.HashSet[Method]

  def getForwardReachesNodes: mutable.HashSet[Node[Edge, Val]]

  def terminated(query: ForwardQuery, results: ForwardBoomerangResults[W]): Unit

  def terminated(query: BackwardQuery, results: BackwardBoomerangResults[W]): Unit
}
