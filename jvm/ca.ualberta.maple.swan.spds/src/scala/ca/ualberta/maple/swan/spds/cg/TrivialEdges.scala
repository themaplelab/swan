/*
 * Copyright (c) 2022 the SWAN project authors. All rights reserved.
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

package ca.ualberta.maple.swan.spds.cg

import boomerang.scene.ControlFlowGraph
import ca.ualberta.maple.swan.ir.{ModuleGroup, Operator, SymbolTableEntry}
import ca.ualberta.maple.swan.spds.cg.CallGraphConstructor.Options
import ca.ualberta.maple.swan.spds.structures.SWANStatement

abstract class TrivialEdges(mg: ModuleGroup, options: Options) extends CallGraphConstructor(mg, options: Options) {

  private def handleOperator(operator: Operator, stmt: SWANStatement.ApplyFunctionRef, edge: ControlFlowGraph.Edge): Unit = {
    operator match {
      case Operator.builtinRef(_, name) => {
        if (methods.contains(name)) {
          if (CallGraphUtils.addCGEdge(stmt.m, methods(name), stmt, edge, cgs)) cgs.trivialCallSites += 1
        }
      }
      case Operator.functionRef(_, name) => {
        if (CallGraphUtils.addCGEdge(stmt.m, methods(name), stmt, edge, cgs)) cgs.trivialCallSites += 1
      }
      case _ =>
    }
  }

  // This must be called after initialized call graph
  final def addTrivialEdges(): Unit = {
    methods.foreach{ case (_, m) => {
      m.applyFunctionRefs.foreach {
        stmt => {
          val edge = new ControlFlowGraph.Edge(m.getCFG.getPredsOf(stmt).iterator().next(), stmt)
          m.delegate.symbolTable(stmt.inst.functionRef.name) match {
            case SymbolTableEntry.operator(_, operator) => {
              handleOperator(operator, stmt, edge)
            }
            case SymbolTableEntry.multiple(_, operators) => operators.foreach { operator =>
              handleOperator(operator, stmt, edge)
            }
            // interproc ref
            case _: SymbolTableEntry.argument =>
          }
        }
      }
    }}
  }
}