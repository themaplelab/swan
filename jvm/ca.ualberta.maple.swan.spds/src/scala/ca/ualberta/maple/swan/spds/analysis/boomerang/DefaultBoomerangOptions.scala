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

import ca.ualberta.maple.swan.spds.analysis.boomerang.flowfunction.{DefaultBackwardFlowFunction, DefaultForwardFlowFunction, IBackwardFlowFunction, IForwardFlowFunction}
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.Val.AllocVal
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{Assignment, Method, Statement, Val}
import ca.ualberta.maple.swan.spds.analysis.boomerang.stats.{IBoomerangStats, SimpleBoomerangStats}
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.Weight

class DefaultBoomerangOptions extends BoomerangOptions {

  override def checkValid(): Unit = {}

  override def allowMultipleQueries: Boolean = false

  override def callSummaries: Boolean = false

  override def fieldSummaries: Boolean = false

  override def analysisTimeoutMS: Int = 10000

  override def maxFieldDepth: Int = -1

  override def maxCallDepth: Int = -1

  override def maxUnbalancedCallDepth: Int = -1

  override def statsFactory[W <: Weight]: IBoomerangStats[W] = new SimpleBoomerangStats

  override def getForwardFlowFunctions: IForwardFlowFunction = new DefaultForwardFlowFunction(this)

  override def getBackwardFlowFunctions: IBackwardFlowFunction = new DefaultBackwardFlowFunction(this)

  override def getAllocationVal(m: Method, stmt: Statement, fact: Val): Option[Val.AllocVal] = {
    stmt match {
      case assignment: Assignment if isAllocationVal(assignment.rhs) => {
        Some(AllocVal(assignment.lhs, stmt, assignment.rhs))
      }
      case _ => None
    }
  }

  def isAllocationVal(value: Val): Boolean = {
    value.isInstanceOf[Val.NewExpr]
  }

  override def getStaticFieldStrategy: BoomerangOptions.StaticFieldStrategy = BoomerangOptions.SINGLETON
}
