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

package ca.ualberta.maple.swan.spds.analysis.boomerang.staticfields

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{ControlFlowGraph, Statement, StaticFieldVal, Val}
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.Weight
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.State

import scala.collection.mutable

class IgnoreStaticFieldStrategy[W <: Weight] extends StaticFieldStrategy[W] {

  override def handleForward(storeStmt: ControlFlowGraph.Edge[Statement, Statement], storedVal: Val, staticVal: StaticFieldVal, out: mutable.HashSet[State]): Unit = {}

  override def handleBackward(curr: ControlFlowGraph.Edge[Statement, Statement], leftOp: Val, staticField: StaticFieldVal, out: mutable.HashSet[State]): Unit = {}
}
