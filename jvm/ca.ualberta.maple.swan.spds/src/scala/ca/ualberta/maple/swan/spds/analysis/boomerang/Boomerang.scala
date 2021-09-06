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

package ca.ualberta.maple.swan.spds.analysis.boomerang

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.Statement
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{CallGraph, ControlFlowGraph, DataFlowScope, Field, Val}
import ca.ualberta.maple.swan.spds.analysis.pds.solver.{OneWeightFunctions, WeightFunctions}
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.Weight

class Boomerang(cg: CallGraph, scope: DataFlowScope, options: BoomerangOptions) extends WeightedBoomerang[Weight.NoWeight](cg, scope, options) {

  protected val fieldWeights: OneWeightFunctions[Edge[Statement, Statement], Val, Field, Weight.NoWeight] = new OneWeightFunctions(Weight.NO_WEIGHT_ONE)

  protected val callWeights: OneWeightFunctions[Edge[Statement, Statement], Val, Edge[Statement, Statement], Weight.NoWeight] = new OneWeightFunctions(Weight.NO_WEIGHT_ONE)

  override protected def getForwardCallWeights(sourceQuery: ForwardQuery): WeightFunctions[Edge[Statement, Statement], Val, Edge[Statement, Statement], Weight.NoWeight] = callWeights

  override protected def getForwardFieldWeights: WeightFunctions[Edge[Statement, Statement], Val, Field, Weight.NoWeight] = fieldWeights

  override protected def getBackwardCallWeights: WeightFunctions[Edge[Statement, Statement], Val, Edge[Statement, Statement], Weight.NoWeight] = callWeights

  override protected def getBackwardFieldWeights: WeightFunctions[Edge[Statement, Statement], Val, Field, Weight.NoWeight] = fieldWeights
}
