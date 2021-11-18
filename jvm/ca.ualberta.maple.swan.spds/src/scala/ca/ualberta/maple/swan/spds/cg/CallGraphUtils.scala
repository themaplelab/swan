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

object CallGraphUtils {

  trait SpecificCallGraphStats

  class CallGraphData(val cg: SWANCallGraph) {
    var trivialEdges: Int = 0
    val trivialCallSites: mutable.HashSet[SWANStatement.ApplyFunctionRef] = mutable.HashSet.empty
    var msTimeToCreateInitialCG: Long = 0
    val debugInfo = new mutable.HashMap[CanOperator, mutable.HashSet[String]]()
    var msTimeToConstructSpecificCG: Long = 0
    var specificData: Option[SpecificCallGraphStats] = None
    var dynamicModels: Option[(Module, CanModule)] = None
    var finalModuleGroup: ModuleGroup = cg.moduleGroup

    override def toString: String = {
      "Call Graph Stats:\n" +
        s"  Methods: ${cg.methods.size}\n" +
        s"  Total Edges: ${cg.getEdges.size()}\n" +
        s"  Trivial Edges: $trivialEdges\n" +
        s"  Trivial Call Sites: ${trivialCallSites.size}\n" +
        s"  Time to create initial CG (ms): $msTimeToCreateInitialCG\n" +
        s"  Time to create specific CG (ms): $msTimeToConstructSpecificCG\n" +
        ( if (specificData.nonEmpty) s"  Specific stats:\n${specificData.get}" else "" )
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

  private def resolveTrivialEdges(m: SWANMethod, cgs: CallGraphData): Unit = {
    val methods = cgs.cg.methods
    m.getCFG.blocks.foreach(b => {
      b._2.stmts.foreach {
        case applyStmt: SWANStatement.ApplyFunctionRef => {
          val edge = new ControlFlowGraph.Edge(m.getCFG.getPredsOf(applyStmt).iterator().next(), applyStmt)
          m.delegate.symbolTable(applyStmt.inst.functionRef.name) match {
            case SymbolTableEntry.operator(_, operator) => {
              operator match {
                case Operator.builtinRef(_, name) => {
                  if (methods.contains(name)) { // TODO: Why are some builtins missing stubs?
                    cgs.trivialCallSites.add(applyStmt)
                    addCGEdge(m, methods(name), applyStmt, edge, cgs)
                  }
                }
                case Operator.functionRef(_, name) => {
                  cgs.trivialCallSites.add(applyStmt)
                  addCGEdge(m, methods(name), applyStmt, edge, cgs)
                }
                case Operator.dynamicRef(_, _, _) => // No trivial dynamic refs in practice
                case _ =>
              }
            }
            case _ => // multiple requires queries
          }
        }
        case _ =>
      }
    })
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

  def generateInitialCallGraph(moduleGroup: ModuleGroup): CallGraphData = {

    val startTime = System.currentTimeMillis()

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

    val it = entryPoints.iterator
    while (it.hasNext) {
      val e = it.next()
      resolveTrivialEdges(e, cgs)
    }

    cgs.msTimeToCreateInitialCG = System.currentTimeMillis() - startTime
    cgs.trivialEdges = cgs.cg.getEdges.size()

    pruneEntryPoints(cgs)

    cgs
  }

}
