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

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.Val.AllocVal
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{Method, Statement, Val}
import ca.ualberta.maple.swan.spds.analysis.boomerang.stats.IBoomerangStats
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.Weight

trait BoomerangOptions {

  def checkValid(): Unit

  def allowMultipleQueries: Boolean

  def callSummaries: Boolean

  def fieldSummaries: Boolean

  def analysisTimeoutMS: Int

  def maxFieldDepth: Int

  def maxCallDepth: Int

  def maxUnbalancedCallDepth: Int

  def statsFactory[W <: Weight]: IBoomerangStats[W]

  def getForwardFlowFunctions: IForwardFlowFunction

  def getBackwardFlowFunctions: IBackwardFlowFunction

  def getAllocationVal(m: Method, stmt: Statement, fact: Val): Option[AllocVal]
}
