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

package ca.ualberta.maple.swan.spds.structures

import java.util

import boomerang.scene._
import boomerang.{BackwardQuery, Boomerang, DefaultBoomerangOptions}
import ca.ualberta.maple.swan.ir._
import ca.ualberta.maple.swan.utils.Logging
import com.google.common.collect.Maps

import scala.collection.mutable

// TODO: iOS lifecycle
// can also likely axe all *.__deallocating_deinit and *.deinit functions

class SWANCallGraph(val module: ModuleGroup) extends CallGraph {

  val methods: util.HashMap[String, SWANMethod] = Maps.newHashMap[String, SWANMethod]

  def constructStaticCG(): Unit = {
    Logging.printInfo("Constructing Call Graph")
    val startTime = System.nanoTime()

    def makeMethod(f: CanFunction): SWANMethod = {
      val m = new SWANMethod(f, module)
      methods.put(f.name, m)
      m
    }

    val otherMethods = new mutable.HashSet[SWANMethod]
    otherMethods.addAll(module.functions.filter(f => !f.name.startsWith(Constants.fakeMain)).map(f => makeMethod(f)))
    val entryMethod = makeMethod(module.functions.find(f => f.name.startsWith(Constants.fakeMain)).get)
    this.addEntryPoint(entryMethod)

    var trivialEdges = 0
    var virtualEdges = 0
    // edges that require dataflow queries, not necessarily inter-procedural
    var dynamicEdges = 0

    val visited = new mutable.HashSet[SWANMethod]()
    val rtaTypes = new mutable.HashSet[String]

    def addSWANEdge(from: SWANMethod, to: SWANMethod, stmt: SWANStatement.ApplyFunctionRef): Unit = {
      val edge = new CallGraph.Edge(stmt, to)
      this.addEdge(edge)
      otherMethods.remove(to)
      if (!visited.contains(to)) {
        resolveEdges(to)
      }
    }

    def queryRef(stmt: SWANStatement.ApplyFunctionRef, m: SWANMethod): Unit = {
      val ref = stmt.getInvokeExpr.asInstanceOf[SWANInvokeExpr].getFunctionRef
      val query = BackwardQuery.make(
        new ControlFlowGraph.Edge(m.getControlFlowGraph.getPredsOf(stmt).iterator().next(), stmt), ref)
      val solver = new Boomerang(this, DataFlowScope.INCLUDE_ALL, new DefaultBoomerangOptions)
      val backwardQueryResults = solver.solve(query.asInstanceOf[BackwardQuery])
      backwardQueryResults.getAllocationSites.forEach((forwardQuery, _) => {
        val applyStmt = query.asNode().stmt().getTarget.asInstanceOf[SWANStatement.ApplyFunctionRef]
        forwardQuery.`var`().asInstanceOf[AllocVal].getAllocVal match {
          case v: SWANVal.FunctionRef => {
            val target = this.methods.get(v.ref)
            addSWANEdge(v.method, target, applyStmt)
            dynamicEdges += 1
          }
          case v: SWANVal.DynamicFunctionRef => {
            module.ddgs.foreach(ddg => {
              val functionNames = ddg._2.query(v.index, Some(rtaTypes))
              functionNames.foreach(name => {
                val target = this.methods.get(name)
                addSWANEdge(v.method, target, applyStmt)
                dynamicEdges += 1
              })
            })
          }
          case v: SWANVal.BuiltinFunctionRef => {
            if (this.methods.containsKey(v.ref)) {
              val target = this.methods.get(v.ref)
              addSWANEdge(v.method, target, applyStmt)
              dynamicEdges += 1
            }
          }
          case _ => // likely result of partial_apply (ignore for now)
        }
      })
    }

    def resolveEdges(m: SWANMethod): Unit = {
      rtaTypes.addAll(m.delegate.instantiatedTypes)
      visited.add(m)
      m.getStatements().forEach {
        case applyStmt: SWANStatement.ApplyFunctionRef => {
          m.delegate.symbolTable(applyStmt.inst.functionRef.name) match {
            case SymbolTableEntry.operator(_, operator) => {
              operator match {
                case Operator.functionRef(_, name) => {
                  val target = this.methods.get(name)
                  addSWANEdge(m, target, applyStmt)
                  trivialEdges += 1
                }
                case Operator.dynamicRef(_, index) => {
                  module.ddgs.foreach(ddg => {
                    val functionNames = ddg._2.query(index, Some(rtaTypes))
                    functionNames.foreach(name => {
                      val target = this.methods.get(name)
                      addSWANEdge(m, target, applyStmt)
                      virtualEdges += 1
                    })
                  })
                }
                case Operator.builtinRef(_, name) => {
                  if (this.methods.containsKey(name)) {
                    val target = this.methods.get(name)
                    addSWANEdge(m, target, applyStmt)
                    trivialEdges += 1
                  }
                }
                case _ => queryRef(applyStmt, m)
              }
            }
            case _: SymbolTableEntry.argument => queryRef(applyStmt, m)
            case _: SymbolTableEntry.multiple => {
              throw new RuntimeException("Unexpected application of multiple function references")
            }
          }
        }
        case _ =>
      }
    }

    // 1. First resolve CG starting from entry point
    resolveEdges(entryMethod)
    // 2. Resolve remaining disjoint functions
    val iterator = otherMethods.iterator
    while (iterator.hasNext) {
      val m = iterator.next()
      if (!visited.contains(m)) {
        resolveEdges(m)
      }
    }
    // 3. Add remaining functions with no incoming edge as entry points
    otherMethods.foreach(m => this.addEntryPoint(m))

    Logging.printInfo("  Entry Points:  " + this.getEntryPoints.size().toString)
    Logging.printInfo("  Trivial Edges: " + trivialEdges.toString)
    Logging.printInfo("  Virtual Edges: " + virtualEdges.toString)
    Logging.printInfo("  Dynamic Edges: " + dynamicEdges.toString)
    Logging.printTimeStampSimple(1, startTime, "constructing")
    // System.out.println(this.toDot)
  }

  override def toString: String = {
    val sb = new StringBuilder
    sb.append("CG edges (")
    sb.append(getEdges.size())
    sb.append("): \n")
    getEdges.forEach((e: CallGraph.Edge) => {
      val sp = new SWIRLPrinter
      sp.print(e.src.asInstanceOf[SWANStatement].delegate)
      sb.append(sp)
      sb.append(" -> ")
      sb.append(e.tgt.getName)
      sb.append("\n")
    })
    sb.toString()
  }

  def toDot: String = {
    val sb = new StringBuilder()
    sb.append("\ndigraph G {\n")
    this.getEdges.forEach(edge => {
      sb.append("  \"")
      sb.append(edge.src().getMethod.getName)
      sb.append("\" -> \"")
      sb.append(edge.tgt().getName)
      sb.append("\"\n")
    })
    sb.append("}\n")
    sb.toString()
  }
}
