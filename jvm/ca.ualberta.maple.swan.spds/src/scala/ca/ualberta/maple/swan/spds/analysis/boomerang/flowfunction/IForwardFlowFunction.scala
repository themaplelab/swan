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

package ca.ualberta.maple.swan.spds.analysis.boomerang.flowfunction

import ca.ualberta.maple.swan.spds.analysis.boomerang.ForwardQuery
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene._
import ca.ualberta.maple.swan.spds.analysis.boomerang.solver.ForwardBoomerangSolver
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.Weight
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.State

import scala.collection.mutable

trait IForwardFlowFunction {

  def returnFlow(callee: Method, returnStmt: Statement, returnedVal: Val): mutable.HashSet[Val]

  def callFlow(callSite: CallSiteStatement, factAtCallSite: Val, callee: Method): mutable.HashSet[Val]

  def normalFlow(query: ForwardQuery, edge: Edge[Statement, Statement], fact: Val): mutable.HashSet[State]

  def callToReturnFlow(query: ForwardQuery, edge: Edge[Statement, Statement], fact: Val): mutable.HashSet[State]

  def setSolver(solver: ForwardBoomerangSolver[_ <: Weight],
                fieldLoadStatements: mutable.MultiDict[Field, Statement],
                fieldStoreStatements: mutable.MultiDict[Field, Statement]): Unit
}
