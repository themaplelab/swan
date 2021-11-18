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
import ca.ualberta.maple.swan.spds.structures.{SWANCallGraph, SWANMethod, SWANStatement}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object CallGraphUtils {

  abstract class SpecificCallGraphStats(val name: String, val time: Long) {
    override def toString: String = {
      s"    $name:\n" +
        s"      Time (ms): $time\n" + specificStatsToString
    }

    def specificStatsToString: String
  }

  class CallGraphData(val cg: SWANCallGraph) {
    var trivialEdges: Int = 0
    val trivialCallSites: mutable.HashSet[SWANStatement.ApplyFunctionRef] = mutable.HashSet.empty
    val debugInfo = new mutable.HashMap[CanOperator, mutable.HashSet[String]]()
    var msTimeToConstructCG: Long = 0
    var msTimeToInitializeCG: Long = 0
    var msTimeActualCGConstruction: Long = 0
    var specificData: ArrayBuffer[SpecificCallGraphStats] = mutable.ArrayBuffer.empty
    var dynamicModels: Option[(Module, CanModule)] = None
    var msTimeOverhead: Long = 0
    var finalModuleGroup: ModuleGroup = cg.moduleGroup

    override def toString: String = {
      "Call Graph Stats:\n" +
        s"  Methods: ${cg.methods.size}\n" +
        s"  Total Edges: ${cg.getEdges.size()}\n" +
        s"  Trivial Edges: $trivialEdges\n" +
        s"  Trivial Call Sites: ${trivialCallSites.size}\n" +
        s"  Total time to create CG (ms): $msTimeToConstructCG\n" +
        s"  Time to initialize CG (ms): $msTimeToInitializeCG\n" +
        s"  Actual time to create CG (ms): $msTimeActualCGConstruction\n" +
        s"  Overhead time (ms): $msTimeOverhead\n" +
      ( if (specificData.nonEmpty) s"  Specific stats:\n${specificData.mkString("\n")}" else "" )
    }
  }

  def addCGEdge(from: SWANMethod, to: SWANMethod, stmt: SWANStatement.ApplyFunctionRef,
                        cfgEdge: ControlFlowGraph.Edge, cgs: CallGraphData): Boolean = {
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

  def pruneEntryPoints(cgs: CallGraphData): Unit = {
    cgs.cg.methods.foreach(m => {
      if (cgs.cg.edgesInto(m._2).isEmpty) {
        cgs.cg.getEntryPoints.remove(m._2)
      }
    })
  }

  def initializeCallGraph(moduleGroup: ModuleGroup): CallGraphData = {

    val methods = new mutable.HashMap[String, SWANMethod]()
    val cg = new SWANCallGraph(moduleGroup, methods)
    val cgs = new CallGraphData(cg)
    val entryPoints = new mutable.LinkedHashSet[SWANMethod]

    moduleGroup.functions.foreach(f => {
      val m = new SWANMethod(f, moduleGroup)
      methods.put(f.name, m)
      cg.addEntryPoint(m)
      if (!isUninteresting(m)) {
        entryPoints.add(m)
      }
    })

    cgs
  }

}
