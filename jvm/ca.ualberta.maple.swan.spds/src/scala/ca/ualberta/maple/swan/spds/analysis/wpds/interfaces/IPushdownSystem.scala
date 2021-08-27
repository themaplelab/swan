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

package ca.ualberta.maple.swan.spds.analysis.wpds.interfaces

import ca.ualberta.maple.swan.spds.analysis.wpds.impl._

import scala.collection.mutable

trait IPushdownSystem[N <: Location, D <: State, W <: Weight] {

  def addRule(rule: Rule[N, D, W]): Boolean

  def getStates: mutable.HashSet[D]

  def getNormalRules: mutable.HashSet[NormalRule[N, D, W]]

  def getPopRules: mutable.HashSet[PopRule[N, D, W]]

  def getPushRules: mutable.HashSet[PushRule[N, D, W]]

  def getAllRules: mutable.HashSet[Rule[N, D, W]]

  def getRulesStarting(start: D, string: N): mutable.HashSet[Rule[N, D, W]]

  def getNormalRulesEnding(start: D, string: N): mutable.HashSet[NormalRule[N, D, W]]

  def getPushRulesEnding(start: D, string: N): mutable.HashSet[PushRule[N, D, W]]

  def preStar(initialAutomaton: WeightedPAutomaton[N, D, W]): Unit

  def postStar(initialAutomaton: WeightedPAutomaton[N, D, W]): Unit

  def postStar(initialAutomaton: WeightedPAutomaton[N, D, W], summaries: NestedWeightedPAutomatons[N, D, W]): Unit

  def registerUpdateListener(lister: WPDSUpdateListener[N, D, W]): Unit

  def unregisterAllListeners(): Unit
}
