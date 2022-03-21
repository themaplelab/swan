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

import boomerang.scene.{AllocVal, ControlFlowGraph}
import boomerang.{BackwardQuery, Boomerang, DefaultBoomerangOptions}
import ca.ualberta.maple.swan.ir.{ModuleGroup, Operator, SymbolTableEntry}
import ca.ualberta.maple.swan.spds.Stats.SpecificCallGraphStats
import ca.ualberta.maple.swan.spds.cg.CallGraphConstructor.Options
import ca.ualberta.maple.swan.spds.structures.{SWANStatement, SWANVal}
import ujson.Value

import scala.collection.mutable

/** SPDS-only CG construction using Kleene's fixed-point iteration style. */
class SPDS(mg: ModuleGroup, spdsOptions: SPDS.Options.Value, options: Options) extends CallGraphConstructor(mg, options) {

  override def buildSpecificCallGraph(): Unit = {
    val startTimeMs = System.currentTimeMillis()
    val scope = CallGraphUtils.getDataFlowScope(cgs.options)
    // stats
    var intraproceduralDynamicEdges = 0
    var queriedDynamicEdges = 0
    var queriedEdges = 0

    val wpTypes = new mutable.HashSet[String]()
    if (spdsOptions.equals(SPDS.Options.WP_FILTER)) {
      cgs.cg.methods.foreach(m => m._2.getStatements.forEach {
        case statement: SWANStatement.Allocation => wpTypes.add(statement.inst.allocType.name)
        case _ =>
      })
    }
    val trivialAndResolvedCallSites = new mutable.HashSet[SWANStatement.ApplyFunctionRef]
    var edges = -1
    while (edges != cgs.cg.getEdges.size()) {
      edges = cgs.cg.getEdges.size()
      cgs.cg.methods.values.foreach(m => {
        m.applyFunctionRefs.foreach(apply => {
          if (!trivialAndResolvedCallSites.contains(apply)) {
            val edge = new ControlFlowGraph.Edge(m.getCFG.getPredsOf(apply).iterator().next(), apply)
            var isTrivial = false
            m.delegate.symbolTable(apply.inst.functionRef.name) match {
              case SymbolTableEntry.operator(_, operator) => {
                operator match {
                  case Operator.builtinRef(_, name) => {
                    if (methods.contains(name)) {
                      isTrivial = true
                      if (CallGraphUtils.addCGEdge(m, methods(name), apply, edge, cgs)) cgs.trivialCallSites += 1
                    }
                  }
                  case Operator.functionRef(_, name) => {
                    isTrivial = true
                    if (CallGraphUtils.addCGEdge(m, methods(name), apply, edge, cgs)) cgs.trivialCallSites += 1
                  }
                  case Operator.dynamicRef(_, _, index) => {
                    isTrivial = !spdsOptions.equals(SPDS.Options.QUERY_FILTER)
                    moduleGroup.ddgs.foreach(ddg => {
                      ddg._2.query(index, {
                        spdsOptions match {
                          case SPDS.Options.NO_FILTER => None
                          case SPDS.Options.WP_FILTER => Some(wpTypes)
                          case SPDS.Options.QUERY_FILTER => {
                            val solver = new Boomerang(cgs.cg, scope, new DefaultBoomerangOptions)
                            val foundTypes = new mutable.HashSet[String]()
                            val query = BackwardQuery.make(edge, apply.getInvokeExpr.getArgs.get(apply.getInvokeExpr.getArgs.size() - 1))
                            val allocSites = solver.solve(query).getAllocationSites
                            allocSites.keySet().forEach(allocSite => {
                              allocSite.`var`().asInstanceOf[AllocVal].getAllocVal match {
                                case alloc: SWANVal.NewExpr => foundTypes.add(alloc.tpe.tpe.name)
                                case _ =>
                              }
                            })
                            Some(foundTypes)
                          }
                        }
                      }).foreach(target => {
                        if (CallGraphUtils.addCGEdge(m, methods(target), apply, edge, cgs)) intraproceduralDynamicEdges += 1
                      })
                    })
                  }
                  case _ =>
                }
              } case _ =>
            }
            if (isTrivial) {
              trivialAndResolvedCallSites.add(apply)
            } else {
              val edge = new ControlFlowGraph.Edge(m.getControlFlowGraph.getPredsOf(apply).iterator().next(), apply)
              val query = BackwardQuery.make(edge, apply.getFunctionRef)
              val solver = new Boomerang(cgs.cg, scope, new DefaultBoomerangOptions {
                override def allowMultipleQueries(): Boolean = true
              })
              val backwardQueryResults = solver.solve(query)
              val allocSites = backwardQueryResults.getAllocationSites
              val functions = new mutable.HashSet[String]()
              val indices = new mutable.HashSet[String]()
              allocSites.keySet().forEach(allocSite => {
                allocSite.`var`().asInstanceOf[AllocVal].getAllocVal match {
                  case fr: SWANVal.FunctionRef => functions.add(fr.ref)
                  case fr: SWANVal.BuiltinFunctionRef => functions.add(fr.ref)
                  case fr: SWANVal.DynamicFunctionRef => indices.add(fr.index)
                  case _ =>
                }
              })
              if (functions.nonEmpty && indices.nonEmpty) throw new RuntimeException("Unexpected: apply site is both func ref and dynamic")
              if (indices.nonEmpty) {
                val types: Option[mutable.HashSet[String]] = spdsOptions match {
                  case SPDS.Options.NO_FILTER => None
                  case SPDS.Options.WP_FILTER => Some(wpTypes)
                  case SPDS.Options.QUERY_FILTER => {
                    val foundTypes = new mutable.HashSet[String]()
                    solver.unregisterAllListeners()
                    val query = BackwardQuery.make(edge, apply.getInvokeExpr.getArgs.get(apply.getInvokeExpr.getArgs.size() - 1))
                    val allocSites = solver.solve(query).getAllocationSites
                    allocSites.keySet().forEach(allocSite => {
                      allocSite.`var`().asInstanceOf[AllocVal].getAllocVal match {
                        case alloc: SWANVal.NewExpr => foundTypes.add(alloc.tpe.tpe.name)
                        case _ =>
                      }
                    })
                    Some(foundTypes)
                  }
                }
                indices.foreach(i => {
                  cgs.cg.moduleGroup.ddgs.foreach(ddg => {
                    val targets = ddg._2.query(i, types)
                    targets.foreach(t => {
                      if (CallGraphUtils.addCGEdge(m, methods(t), apply, edge, cgs)) queriedDynamicEdges += 1
                    })
                  })
                })
              } else {
                functions.foreach(f => {
                  if (CallGraphUtils.addCGEdge(m, methods(f), apply, edge, cgs)) queriedEdges += 1
                })
              }
            }
          }
        })
      })
    }

    val stats = new SPDS.SPDSStats(intraproceduralDynamicEdges, queriedDynamicEdges, queriedEdges, (System.currentTimeMillis() - startTimeMs).toInt)
    cgs.specificData.addOne(stats)
  }
}

object SPDS {

  object Options extends Enumeration {
    type Filter = Value

    val NO_FILTER: Options.Value = Value
    val WP_FILTER: Options.Value = Value // PRTA-style filter
    val QUERY_FILTER: Options.Value = Value
  }

  class SPDSStats(val intraproceduralDynamicEdges: Int, val queriedDynamicEdges: Int,
                  val queriedEdges: Int, val time: Int) extends SpecificCallGraphStats {

    override def toJSON: Value = {
      val u = ujson.Obj()
      u("spds_intra_dynamic_edges") = intraproceduralDynamicEdges
      u("spds_queried_dynamic_edges") = queriedDynamicEdges
      u("spds_queried_edges") = queriedEdges
      u("spds_time") = time
      u
    }

    override def toString: String = {
      val sb = new StringBuilder("SPDS\n")
      sb.append(s"  Intraprocedural Dynamic Edges: $intraproceduralDynamicEdges\n")
      sb.append(s"  Queried Dynamic Edges: $queriedDynamicEdges\n")
      sb.append(s"  Queried Edges: $queriedEdges\n")
      sb.append(s"  Time (ms): $time\n")
      sb.toString()
    }
  }
}


