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

import boomerang.scene.ControlFlowGraph
import ca.ualberta.maple.swan.ir.{ModuleGroup, Operator, SymbolTableEntry}
import ca.ualberta.maple.swan.spds.Stats.{CallGraphStats, SpecificCallGraphStats}
import ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle
import ca.ualberta.maple.swan.spds.cg.pa.PointerAnalysis
import ca.ualberta.maple.swan.spds.structures.SWANStatement
import ujson.Value

class CHA(mg: ModuleGroup, pas: PointerAnalysisStyle.Style) extends CallGraphConstructor(mg) {

  val pa: Option[PointerAnalysis] = {
    pas match {
      case PointerAnalysisStyle.None => None
      case ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle.SWPA => None
      case ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle.SOD => None
      case ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle.SPDS => None
      case ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle.VTA => {
        throw new RuntimeException("VTA pointer analysis should only be used with VTA CG construction")
      }
    }
  }

  // TODO: Pointer analysis integration
  override def buildSpecificCallGraph(cgs: CallGraphStats): Unit = {
    var chaEdges: Int = 0
    val startTimeMs = System.currentTimeMillis()
    val methods = cgs.cg.methods
    methods.foreach(x => {
      val m = x._2
      m.getCFG.blocks.foreach(b => {
        b._2.stmts.foreach {
          case applyStmt: SWANStatement.ApplyFunctionRef => {
            val edge = new ControlFlowGraph.Edge(m.getCFG.getPredsOf(applyStmt).iterator().next(), applyStmt)
            m.delegate.symbolTable(applyStmt.inst.functionRef.name) match {
              case SymbolTableEntry.operator(_, operator) => {
                operator match {
                  case Operator.builtinRef(_, name) => {
                    if (methods.contains(name)) { // TODO: Why are some builtins missing stubs?
                      if (CallGraphUtils.addCGEdge(m, methods(name), applyStmt, edge, cgs)) cgs.trivialCallSites += 1
                    }
                  }
                  case Operator.functionRef(_, name) => {
                    if (CallGraphUtils.addCGEdge(m, methods(name), applyStmt, edge, cgs)) cgs.trivialCallSites += 1
                  }
                  case Operator.dynamicRef(_, _, index) => {
                    moduleGroup.ddgs.foreach(ddg => {
                      ddg._2.query(index, None).foreach(target => {
                        if (CallGraphUtils.addCGEdge(m, methods(target), applyStmt, edge, cgs)) chaEdges += 1
                      })
                    })
                  }
                  case _ =>
                }
              }
              case _ =>
            }
          }
          case _ =>
        }
      })
    })
    val stats = new CHA.CHAStats(chaEdges, (System.currentTimeMillis() - startTimeMs).toInt)
    cgs.specificData.addOne(stats)
  }
}

object CHA {

  class CHAStats(val chaEdges: Int, val time: Int) extends SpecificCallGraphStats {

    override def toJSON: Value = {
      val u = ujson.Obj()
      u("cha_edges") = chaEdges
      u("cha_time") = time
      u
    }

    override def toString: String = {
      s"CHA\n  Edges: $chaEdges\n  Time (ms): $time"
    }
  }
}