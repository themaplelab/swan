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

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{ControlFlowGraph, Field, Method, Statement, Val}
import ca.ualberta.maple.swan.spds.analysis.boomerang.solver.BackwardBoomerangSolver
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.State

import scala.collection.mutable

trait IBackwardFlowFunction {

  def returnFlow(callee: Method, returnStmt: Statement, returnedVal: Val): mutable.HashSet[Val]

  def callFlow(callSite: Statement, fact: Val, callee: Method, calleeSp: Statement): mutable.HashSet[Val]

  def normalFlow(edge: ControlFlowGraph.Edge, fact: Val): mutable.HashSet[State]

  def callToReturnFlow(edge: ControlFlowGraph.Edge, fact: Val): mutable.HashSet[State]

  def setSolver(solver: BackwardBoomerangSolver[_],
                fieldLoadStatements: mutable.MultiDict[Field, Statement],
                fieldStoreStatements: mutable.MultiDict[Field, Statement]): Unit
}
