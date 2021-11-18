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

package ca.ualberta.maple.swan.spds

import boomerang.results.AbstractBoomerangResults
import boomerang.scene.{AllocVal, CallGraph, ControlFlowGraph, DataFlowScope}
import boomerang.{BackwardQuery, Boomerang, DefaultBoomerangOptions, ForwardQuery}
import ca.ualberta.maple.swan.ir.{CanFunction,  CanModule, CanOperator, Constants, Instruction, Module, ModuleGroup, Operator, SymbolTableEntry}
import ca.ualberta.maple.swan.spds.structures.{SWANCallGraph, SWANInvokeExpr, SWANMethod, SWANStatement, SWANVal}
import ca.ualberta.maple.swan.utils.Logging

import java.util
import scala.collection.mutable

class NewCallGraphConstruction(moduleGroup: ModuleGroup) {

  val methods = new mutable.HashMap[String, SWANMethod]()
  val cg = new SWANCallGraph(moduleGroup, methods)
  val mainEntryPoints = new mutable.HashSet[SWANMethod]
  val otherEntryPoints = new mutable.HashSet[SWANMethod]

  val trivial = new mutable.HashSet[SWANStatement.ApplyFunctionRef]()

  val debugInfo = new mutable.HashMap[CanOperator, mutable.HashSet[String]]()

  // stats for entire CG
  var trivialEdges = 0
  var trivialDynamicDispatchEdges = 0
  var queriedEdges = 0
  var boomerangQueries = 0
  var fruitlessQueries = 0

  private def addCGEdge(from: SWANMethod, to: SWANMethod, stmt: SWANStatement.ApplyFunctionRef, cfgEdge: ControlFlowGraph.Edge): Boolean = {
    val edge = new CallGraph.Edge(stmt, to)
    val b = cg.addEdge(edge)
    if (b) {
      cg.getEntryPoints.remove(to) // TODO: This is temporary (*not* sound)
      stmt.invokeExpr.updateDeclaredMethod(to.getName, cfgEdge)
      val op = stmt.delegate.instruction match {
        case Instruction.canOperator(op) => op
        case _ => null // never
      }
      if (!debugInfo.contains(op)) {
        debugInfo.put(op, new mutable.HashSet[String]())
      }
      debugInfo(op).add(to.getName)
    }
    b
  }

  private def resolveTrivialEdges(m: SWANMethod): Unit = {
    m.getCFG.blocks.foreach(b => {
      b._2.stmts.foreach {
        case applyStmt: SWANStatement.ApplyFunctionRef => {
          val edge = new ControlFlowGraph.Edge(m.getCFG.getPredsOf(applyStmt).iterator().next(), applyStmt)
          m.delegate.symbolTable(applyStmt.inst.functionRef.name) match {
            case SymbolTableEntry.operator(_, operator) => {
              operator match {
                case Operator.dynamicRef(_, obj, index) => {
                  trivial.add(applyStmt)
                  /*
                  m.delegate.symbolTable(obj.name) match {
                    case SymbolTableEntry.operator(_, operator) => {
                      operator match {
                        case Operator.neww(result) => {
                          trivial.add(applyStmt)
                          moduleGroup.ddgs.foreach(ddg => {
                            val targets = ddg._2.query(index, Some(mutable.HashSet(result.tpe.name)))
                            targets.foreach(t => {
                              if (addCGEdge(m, methods(t), applyStmt, edge)) trivialDynamicDispatchEdges += 1
                            })
                          })
                        }
                        case _ =>
                      }
                    }
                    case _ => // multiple requires queries
                  }
                   */
                }
                case Operator.builtinRef(_, name) => {
                  trivial.add(applyStmt)
                  if (methods.contains(name)) {
                    if (addCGEdge(m, methods(name), applyStmt, edge)) trivialEdges += 1
                  }
                }
                case Operator.functionRef(_, name) => {
                  trivial.add(applyStmt)
                  if (addCGEdge(m, methods(name), applyStmt, edge)) trivialEdges += 1
                }
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

  private def resolveQueryEdges(m: SWANMethod): Unit = {
    if (m.getName.startsWith("closure ") || m.getName.startsWith("reabstraction thunk")) {
      return
    }
    m.getCFG.blocks.foreach(b => {
      b._2.stmts.foreach {
        case applyStmt: SWANStatement.ApplyFunctionRef => {
          if (!trivial.contains(applyStmt)) {
            val edge = new ControlFlowGraph.Edge(m.getCFG.getPredsOf(applyStmt).iterator().next(), applyStmt)
            queryFunctionRefForTargets(applyStmt, m).foreach(t => {
              if (addCGEdge(m, t, applyStmt, edge)) queriedEdges += 1
            })
          }
        }
        case _ =>
      }
    })
  }

  private def queryAllocationSites(query: BackwardQuery): util.Map[ForwardQuery, AbstractBoomerangResults.Context] = {
    val solver = new Boomerang(cg, DataFlowScope.INCLUDE_ALL, new DefaultBoomerangOptions {
      override def analysisTimeoutMS(): Int = 4000
    })
    val startTime = System.currentTimeMillis()
    val backwardQueryResults = solver.solve(query)
    val endTime = System.currentTimeMillis() - startTime
    boomerangQueries += 1
    if (endTime > 1000) {
      //System.out.println(s"${query.`var`().m().getName}, ${query.`var`().toString}")
    }
    if (backwardQueryResults.getAllocationSites.isEmpty) {
      fruitlessQueries += 1
      // System.out.println(s"${query.`var`().m().getName}, ${query.`var`().toString}")
      // System.out.println(endTime)
    }
    backwardQueryResults.getAllocationSites
  }

  private def queryFunctionRefForTargets(stmt: SWANStatement.ApplyFunctionRef, m: SWANMethod): mutable.ArrayBuffer[SWANMethod] = {
    val targets = mutable.ArrayBuffer.empty[SWANMethod]
    val ref = stmt.getInvokeExpr.asInstanceOf[SWANInvokeExpr].getFunctionRef
    m.getControlFlowGraph.getPredsOf(stmt).forEach(pred => {
      val query = BackwardQuery.make(new ControlFlowGraph.Edge(pred, stmt), ref)
      queryAllocationSites(query).forEach((forwardQuery, _) => {
        forwardQuery.`var`().asInstanceOf[AllocVal].getAllocVal match {
          case v: SWANVal.FunctionRef => {
            targets.append(methods(v.ref))
          }
          case v: SWANVal.BuiltinFunctionRef => {
            targets.append(methods(v.ref))
          }
          case v: SWANVal.DynamicFunctionRef => {
            val dynamicFunctionRefStmt = forwardQuery.cfgEdge().getStart.asInstanceOf[SWANStatement.DynamicFunctionRef]
            val query = BackwardQuery.make(forwardQuery.cfgEdge(), dynamicFunctionRefStmt.m.allValues(dynamicFunctionRefStmt.inst.obj.name))
            val types = mutable.HashSet.empty[String]
            queryAllocationSites(query).forEach((fq, _) => {
              fq.`var`().asInstanceOf[AllocVal].getAllocVal match {
                case v: SWANVal.NewExpr => {
                  types.add(v.tpe.tpe.name)
                }
                case _ =>
              }
            })
            moduleGroup.ddgs.foreach(ddg => {
              val functionNames = ddg._2.query(v.index, Some(types))
              functionNames.foreach(name => {
                targets.append(methods(name))
              })
            })
          }
          case _ =>
        }
      })
    })
    targets
  }

  def construct(): (SWANCallGraph, mutable.HashMap[CanOperator, mutable.HashSet[String]], ModuleGroup, Option[(Module, CanModule)]) = {

    Logging.printInfo("Constructing Call Graph")
    val startTime = System.nanoTime()

    def makeMethod(f: CanFunction): SWANMethod = {
      val m = new SWANMethod(f, moduleGroup)
      methods.put(f.name, m)
      m
    }

    // Populate entry points
    mainEntryPoints.addAll(moduleGroup.functions.filter(f => f.name.startsWith(Constants.fakeMain)).map(f => makeMethod(f)))
    otherEntryPoints.addAll(moduleGroup.functions.filter(f => !f.name.startsWith(Constants.fakeMain)).map(f => makeMethod(f)))
    val allEntryPoints = new mutable.LinkedHashSet[SWANMethod]
    allEntryPoints.filterInPlace(m => !m.getName.startsWith("closure ") && !m.getName.startsWith("reabstraction thunk"))
    allEntryPoints.addAll(mainEntryPoints)
    allEntryPoints.addAll(otherEntryPoints)
    allEntryPoints.foreach(e => cg.addEntryPoint(e))

    allEntryPoints.foreach(m => resolveTrivialEdges(m))
    Logging.printInfo("Resolved trivial edges in " + ((System.nanoTime() - startTime) / 1000000).toString + "ms")

    var s = 0

    var fixedPointIterations = 0
    do {
      Logging.printInfo(s"Running fixed point iteration ${fixedPointIterations + 1}")
      val fpStart = System.nanoTime()
      s = cg.size()
      allEntryPoints.foreach(m => resolveQueryEdges(m))
      fixedPointIterations += 1
      Logging.printInfo("Fixed Point Iteration (" + fixedPointIterations + ") Time: " + ((System.nanoTime() - fpStart) / 1000000).toString + "ms")
      Logging.printInfo("  Queried Edges: " + queriedEdges.toString)
      Logging.printInfo("  Total Queries: " + boomerangQueries.toString)
      Logging.printInfo("  Fruitless Queries: " + fruitlessQueries.toString)

    } while (cg.size() != s)

    Logging.printTimeStampSimple(1, startTime, "constructing")
    Logging.printInfo("  Entry Points:  " + cg.getEntryPoints.size().toString)
    Logging.printInfo("  Trivial Edges: " + trivialEdges.toString)
    Logging.printInfo("  Trivial DDG:   " + trivialDynamicDispatchEdges.toString)
    Logging.printInfo("  Queried Edges: " + queriedEdges.toString)
    Logging.printInfo("  Total Queries: " + boomerangQueries.toString)
    Logging.printInfo("  Fruitless Queries: " + fruitlessQueries.toString)
    Logging.printInfo("  Fixed Pt Iter: " + fixedPointIterations.toString)


    var newValues: Option[(Module, CanModule)] = None
    var retModuleGroup = moduleGroup

    (cg, debugInfo, retModuleGroup, newValues)
  }
}
