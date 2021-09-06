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

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{Assignment, ControlFlowGraph, Field, Statement, StaticFieldVal, Val}
import ca.ualberta.maple.swan.spds.analysis.boomerang.solver.AbstractBoomerangSolver
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes.Node
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.Weight
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.State

import scala.collection.mutable

class SingletonStaticFieldStrategy[W <: Weight](solver: AbstractBoomerangSolver[W],
                                                fieldLoadStatements: mutable.MultiDict[Field, Statement],
                                                fieldStoreStatements: mutable.MultiDict[Field, Statement]) extends StaticFieldStrategy[W] {

  override def handleForward(storeStmt: ControlFlowGraph.Edge[Statement, Statement], storedVal: Val, staticVal: StaticFieldVal, out: mutable.HashSet[State]): Unit = {
    fieldLoadStatements.get(staticVal.field).foreach {
      case assignment: Assignment => {
        assignment.method.getCFG.getSuccsOf(assignment).foreach(succ => {
          solver.processNormal(new Node(storeStmt, storedVal), new Node(new Edge(assignment, succ), assignment.lhs))
        })
      }
      case _ =>
    }
  }

  override def handleBackward(curr: ControlFlowGraph.Edge[Statement, Statement], leftOp: Val, staticField: StaticFieldVal, out: mutable.HashSet[State]): Unit = {
    fieldStoreStatements.get(staticField.field).foreach {
      case assignment: Assignment => {
        assignment.method.getCFG.getPredsOf(assignment).foreach(pred => {
          solver.processNormal(new Node(curr, leftOp), new Node(new Edge(pred, assignment), assignment.rhs))
        })
      }
      case _ =>
    }
  }
}
