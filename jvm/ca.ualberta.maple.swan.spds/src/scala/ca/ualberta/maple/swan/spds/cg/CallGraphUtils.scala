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

package ca.ualberta.maple.swan.spds.cg

import boomerang.scene.{CallGraph, ControlFlowGraph}
import ca.ualberta.maple.swan.ir.{CanModule, CanOperator, Instruction, Module, ModuleGroup, Operator, SymbolTableEntry}
import ca.ualberta.maple.swan.spds.Stats.CallGraphStats
import ca.ualberta.maple.swan.spds.structures.{SWANCallGraph, SWANMethod, SWANStatement}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object CallGraphUtils {

  def addCGEdge(from: SWANMethod, to: SWANMethod, stmt: SWANStatement.ApplyFunctionRef,
                        cfgEdge: ControlFlowGraph.Edge, cgs: CallGraphStats): Boolean = {
    val edge = new CallGraph.Edge(stmt, to)
    val b = cgs.cg.addEdge(edge)
    if (b) {
      stmt.invokeExpr.updateDeclaredMethod(to.getName, cfgEdge)
      val op = stmt.delegate.instruction match {
        case Instruction.canOperator(op) => op
        case _ => null // never
      }
      if (!cgs.debugInfo.contains(op)) {
        cgs.debugInfo.put(op, new mutable.HashSet[String]())
      }
      cgs.debugInfo(op).add(to.getName)
    }
    if (b) cgs.totalEdges += 1
    b
  }

  private def isUninteresting(m: SWANMethod): Boolean = {
    val name = m.getName
    name.startsWith("closure ") ||
      name.startsWith("reabstraction thunk") ||
      name.endsWith(".deinit") ||
      name.endsWith(".modify") ||
      name.endsWith(".__deallocating_deinit")
  }

  def pruneEntryPoints(cgs: CallGraphStats): Unit = {
    val cg = cgs.cg
    cg.methods.foreach(m => {
      if (cg.edgesInto(m._2).isEmpty && cg.getEntryPoints.contains(m._2)) {
        cgs.cg.getEntryPoints.remove(m._2)
        cgs.entryPoints -= 1
      }
    })
  }

  def initializeCallGraph(moduleGroup: ModuleGroup): CallGraphStats = {

    val methods = new mutable.HashMap[String, SWANMethod]()
    val cg = new SWANCallGraph(moduleGroup, methods)
    val cgs = new CallGraphStats(cg)

    moduleGroup.functions.foreach(f => {
      val m = new SWANMethod(f, moduleGroup)
      methods.put(f.name, m)
      if (!isUninteresting(m)) {
        cg.addEntryPoint(m)
        cgs.entryPoints += 1
      }
    })

    cgs
  }

}
