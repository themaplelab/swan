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

package ca.ualberta.maple.swan.spds.analysis.wpds.impl

import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.{Location, State}

import scala.collection.mutable

class SummaryNestedWeightedPAutomatons[N <: Location, D <: State, W <: Weight] extends NestedWeightedPAutomatons[N, D, W] {

  protected val summaries: mutable.HashMap[D, WeightedPAutomaton[N, D, W]] = mutable.HashMap.empty

  override def putSummaryAutomaton(target: D, aut: WeightedPAutomaton[N, D, W]): Unit = summaries.put(target, aut)

  override def getSummaryAutomaton(target: D): WeightedPAutomaton[N, D, W] = summaries(target)
}
