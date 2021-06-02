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

import scala.collection.convert.ImplicitConversions.`map AsScala`
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

// TODO: iOS lifecycle
// TODO: Do separate RTA pass because it is not deterministic currently due
//  to not only starting at entry point (fake main)
// can also likely axe all *.__deallocating_deinit, *.deinit functions, and *.modify

class SWANCallGraph(val module: ModuleGroup) extends CallGraph {

  val methods: util.HashMap[String, SWANMethod] = Maps.newHashMap[String, SWANMethod]

  private final val uninterestingEntryPoints = Array(
    ".__deallocating_deinit",
    ".deinit",
    ".modify"
  )

  // TODO: verify if deterministic (usage of HashSets)
  def constructStaticCG(): Unit = {
    Logging.printInfo("Constructing Call Graph")
    val startTime = System.nanoTime()

    def makeMethod(f: CanFunction): SWANMethod = {
      val m = new SWANMethod(f, module)
      methods.put(f.name, m)
      m
    }

    // Populate entry points
    val mainEntryPoints = new mutable.HashSet[SWANMethod]
    mainEntryPoints.addAll(module.functions.filter(f => f.name.startsWith(Constants.fakeMain)).map(f => makeMethod(f)))
    val otherEntryPoints = new mutable.HashSet[SWANMethod]
    otherEntryPoints.addAll(module.functions.filter(f => !f.name.startsWith(Constants.fakeMain)).map(f => makeMethod(f)))
    // Eliminate functions that get called (referenced)
    this.methods.foreach(m => {
      val f = m._2.delegate
      f.blocks.foreach(b => {
        b.operators.foreach(opDef => {
          opDef.operator match {
            case Operator.dynamicRef(_, index) => {
              module.ddgs.foreach(ddg => {
                ddg._2.query(index, None).foreach(functionName => {
                  otherEntryPoints.remove(this.methods.get(functionName))
                })
              })
            }
            case Operator.builtinRef(_, name) => {
              if (this.methods.containsKey(name)) {
                otherEntryPoints.remove(this.methods.get(name))
              }
            }
            case Operator.functionRef(_, name) => otherEntryPoints.remove(this.methods.get(name))
            case _ =>
          }
        })
      })
    })
    // TODO: This creates a problem if the entry points call something
    // Eliminate uninteresting functions
    /*val iterator = otherEntryPoints.iterator
    while (iterator.hasNext) {
      val m = iterator.next()
      uninterestingEntryPoints.foreach(s => {
        if (m.getName.endsWith(s)) {
          otherEntryPoints.remove(m)
          this.methods.remove(m.getName)
        }
      })
    }*/
    // Combine entry points, with the first being the main entry point
    val allEntryPoints = new ArrayBuffer[SWANMethod]
    allEntryPoints.appendAll(mainEntryPoints)
    allEntryPoints.appendAll(otherEntryPoints)
    allEntryPoints.foreach(e => this.addEntryPoint(e))
    // Build CG for every entry point
    var trivialEdges = 0
    var virtualEdges = 0
    var queriedEdges = 0
    allEntryPoints.foreach(entryPoint => {
      val instantiatedTypes = new mutable.HashSet[String]()
      // Mapping of methods to # of instantiated types
      val methodCount = new mutable.HashMap[SWANMethod, Int]
      // Mapping of block start stmts to # of instantiated types
      val blockCount = new mutable.HashMap[Statement, Int]
      def addCGEdge(from: SWANMethod, to: SWANMethod, stmt: SWANStatement.ApplyFunctionRef): Boolean = {
        val edge = new CallGraph.Edge(stmt, to)
        this.addEdge(edge)
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
              if (addCGEdge(m, target, applyStmt)) queriedEdges += 1
              traverseMethod(target)
            }
            case v: SWANVal.DynamicFunctionRef => {
              module.ddgs.foreach(ddg => {
                val functionNames = ddg._2.query(v.index, Some(instantiatedTypes))
                functionNames.foreach(name => {
                  val target = this.methods.get(name)
                  if (addCGEdge(m, target, applyStmt)) queriedEdges += 1
                  traverseMethod(target)
                })
              })
            }
            case v: SWANVal.BuiltinFunctionRef => {
              if (this.methods.containsKey(v.ref)) {
                val target = this.methods.get(v.ref)
                if (addCGEdge(m, target, applyStmt)) queriedEdges += 1
                traverseMethod(target)
              }
            }
            case _ => // likely result of partial_apply (ignore for now)
          }
        })
      }
      def traverseMethod(m: SWANMethod): Unit = {
        if (methodCount.contains(m)) {
          if (methodCount(m) == instantiatedTypes.size) {
            return
          }
        }
        methodCount.put(m, instantiatedTypes.size)
        instantiatedTypes.addAll(m.delegate.instantiatedTypes)
        def traverseBlock(b: ArrayBuffer[SWANStatement]): Unit = {
          if (blockCount.contains(b(0))) {
            if (blockCount(b(0)) == instantiatedTypes.size) {
              return
            }
          }
          blockCount.put(b(0), instantiatedTypes.size)
          b.foreach {
            case applyStmt: SWANStatement.ApplyFunctionRef => {
              m.delegate.symbolTable(applyStmt.inst.functionRef.name) match {
                case SymbolTableEntry.operator(_, operator) => {
                  operator match {
                    case Operator.functionRef(_, name) => {
                      val target = this.methods.get(name)
                      if (addCGEdge(m, target, applyStmt)) trivialEdges += 1
                      traverseMethod(target)
                    }
                    case Operator.dynamicRef(_, index) => {
                      module.ddgs.foreach(ddg => {
                        val functionNames = ddg._2.query(index, Some(instantiatedTypes))
                        functionNames.foreach(name => {
                          val target = this.methods.get(name)
                          if (addCGEdge(m, target, applyStmt)) virtualEdges += 1
                          traverseMethod(target)
                        })
                      })
                    }
                    case Operator.builtinRef(_, name) => {
                      if (this.methods.containsKey(name)) {
                        val target = this.methods.get(name)
                        if (addCGEdge(m, target, applyStmt)) trivialEdges += 1
                        traverseMethod(target)
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
            } case _ =>
          }
          m.getControlFlowGraph.getSuccsOf(b.last).forEach(nextBlock => {
            traverseBlock(m.getCFG.blocks(nextBlock.asInstanceOf[SWANStatement])._1)
          })
        }
        val startStatement = m.getControlFlowGraph.getStartPoints.iterator().next()
        traverseBlock(m.getCFG.blocks(startStatement.asInstanceOf[SWANStatement])._1)
      }
      traverseMethod(entryPoint)
    })
    Logging.printInfo("  Entry Points:  " + this.getEntryPoints.size().toString)
    Logging.printInfo("  Trivial Edges: " + trivialEdges.toString)
    Logging.printInfo("  Virtual Edges: " + virtualEdges.toString)
    Logging.printInfo("  Queried Edges: " + queriedEdges.toString)
    Logging.printTimeStampSimple(1, startTime, "constructing")
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
