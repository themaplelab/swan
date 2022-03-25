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
import ca.ualberta.maple.swan.spds.Stats.SpecificCallGraphStats
import ca.ualberta.maple.swan.spds.cg.CallGraphConstructor.Options
import ca.ualberta.maple.swan.spds.structures.SWANMethod
import ujson.Value

class CHA(mg: ModuleGroup, sigMatching: Boolean, options: Options) extends CallGraphConstructor(mg, options) {

  override def buildSpecificCallGraph(): Unit = {
    var chaEdges: Int = 0
    val startTimeMs = System.currentTimeMillis()
    methods.iterator.filter{ case (_,m) => CallGraphUtils.isEntryOrLibrary(m, cgs)}.foreach(x => {
      val m = x._2
      m.applyFunctionRefs.foreach(applyStmt => {
        val edge = new ControlFlowGraph.Edge(m.getCFG.getPredsOf(applyStmt).iterator().next(), applyStmt)
        m.delegate.symbolTable(applyStmt.inst.functionRef.name) match {
          case SymbolTableEntry.operator(_, operator) => {
            operator match {
              case Operator.builtinRef(_, name) => {
                if (methods.contains(name)) {
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
      })
    })

    val sigMatchedEdges = if (sigMatching) CallGraphUtils.resolveFunctionPointersWithSigMatching(cgs) else 0

    val stats = new CHA.CHAStats(chaEdges, sigMatchedEdges, (System.currentTimeMillis() - startTimeMs).toInt)
    cgs.specificData.addOne(stats)
  }
}

object CHA {

  class CHAStats(val chaEdges: Int, val sigMatchedEdges: Int, val time: Int) extends SpecificCallGraphStats {

    override def toJSON: Value = {
      val u = ujson.Obj()
      u("cha_edges") = chaEdges
      u("cha_sig_matched_edges") = sigMatchedEdges
      u("cha_time") = time
      u
    }

    override def toString: String = {
      s"CHA\n  Edges: $chaEdges\nSig Matched Edges: $sigMatchedEdges\n  Time (ms): $time"
    }
  }
}
