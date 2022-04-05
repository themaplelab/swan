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
import ca.ualberta.maple.swan.spds.structures.SWANStatement
import ujson.Value

import scala.collection.mutable

// TODO: This isn't actually pessimistic because it doesn't use CHA
class PRTA(mg: ModuleGroup, sigMatching: Boolean, options: Options) extends CallGraphConstructor(mg, options) {

  override def buildSpecificCallGraph(): Unit = {
    var prtaEdges: Int = 0
    val startTimeMs = System.currentTimeMillis()
    val methods = cgs.cg.methods

    // This type set creation doesn't take long, don't worry
    val types = mutable.HashSet.empty[String]
    val validTypes = mutable.HashSet.empty[String]
    moduleGroup.ddgs.foreach(d => validTypes.addAll(d._2.nodes.keySet))
    methods.foreach(m => {
      m._2.delegate.blocks.foreach(b => b.operators.foreach(op => {
        op.operator match {
          case Operator.neww(_, allocType) => types.add(allocType.name)
          case _ =>
        }
      }))
    })

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
                    if (methods.contains(name)) {
                      cgs.trivialCallSites += 1
                      CallGraphUtils.addCGEdge(m, methods(name), applyStmt, edge, cgs)
                    }
                  }
                  case Operator.functionRef(_, name) => {
                    cgs.trivialCallSites += 1
                    CallGraphUtils.addCGEdge(m, methods(name), applyStmt, edge, cgs)
                  }
                  case Operator.dynamicRef(_, _, index) => {
                    moduleGroup.ddgs.foreach(ddg => {
                      ddg._2.query(index, Some(types)).foreach(target => {
                        if (CallGraphUtils.addCGEdge(m, methods(target), applyStmt, edge, cgs)) prtaEdges += 1
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

    val sigMatchedEdges = if (sigMatching) CallGraphUtils.resolveFunctionPointersWithSigMatching(cgs) else 0

    val stats = new PRTA.PRTAStats(prtaEdges, sigMatchedEdges, (System.currentTimeMillis() - startTimeMs).toInt)
    cgs.specificData.addOne(stats)
  }
}

object PRTA {

  class PRTAStats(val edges: Int, sigMatchedEdges: Int, time: Int) extends SpecificCallGraphStats {

    override def toJSON: Value = {
      val u = ujson.Obj()
      u("prta_edges") = edges
      u("prta_sig_matched_edges") = sigMatchedEdges
      u("prta_time") = time
      u
    }

    override def toString: String = {
      s"PRTA\n  Edges: $edges\nSig Matched Edges: $sigMatchedEdges\n  Time (ms): $time"
    }
  }
}

