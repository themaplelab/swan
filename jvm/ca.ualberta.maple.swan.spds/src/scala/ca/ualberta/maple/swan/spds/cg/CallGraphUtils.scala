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

import boomerang.scene.{AllocVal, CallGraph, ControlFlowGraph, DataFlowScope, DeclaredMethod, Method}
import boomerang.{BackwardQuery, Boomerang, DefaultBoomerangOptions}
import ca.ualberta.maple.swan.ir.{Instruction, ModuleGroup, Operator, SymbolTableEntry}
import ca.ualberta.maple.swan.spds.Stats.CallGraphStats
import ca.ualberta.maple.swan.spds.structures.{SWANCallGraph, SWANMethod, SWANStatement, SWANVal}

import java.io.{File, FileWriter}
import scala.collection.mutable

object CallGraphUtils {

  def getDataFlowScope(options: CallGraphConstructor.Options): DataFlowScope = new DataFlowScope {
    override def isExcluded(declaredMethod: DeclaredMethod): Boolean = isClosureRelated(declaredMethod.getName, options)

    override def isExcluded(method: Method): Boolean = isClosureRelated(method.getName, options)
  }

  /**
   * Add a Call Graph edge and update relevant information
   * @param from Method from which the edge is being drawn (caller).
   * @param to Method to which the edge is being drawn (callee).
   * @param stmt Call site
   * @param cfgEdge Relevant control-flow-graph edge. TODO: Does pred matter (e.g., call site is first stmt in block)?
   * @param cgs Call graph stats to update
   * @return true if the edge was added, false if not (because the edge already existed)
   */
  def addCGEdge(from: SWANMethod, to: SWANMethod, stmt: SWANStatement.ApplyFunctionRef,
                cfgEdge: ControlFlowGraph.Edge, cgs: CallGraphStats): Boolean = {
    val edge = new CallGraph.Edge(stmt, to)
    val b = cgs.cg.addEdge(edge)
    if (b) {
      cgs.cg.graph.addEdge(from, to)
      stmt.invokeExpr.updateDeclaredMethod(to.getName, cfgEdge)
      val op = stmt.delegate.instruction match {
        case Instruction.canOperator(op) => op
        case _ => null // never
      }
      if (cgs.options.addDebugInfo) {
        if (!cgs.debugInfo.edges.contains(op)) {
          cgs.debugInfo.edges.put(op, new mutable.HashSet[String]())
        }
        cgs.debugInfo.edges(op).add(to.getName)
      }
    }
    if (b) cgs.totalEdges += 1
    b
  }

  def calculateResolvedCallsites(cgs: CallGraphStats): (Int, Int, Int, Int) = {
    var callsites = 0
    var tUnresolved = 0
    var unresolved = 0
    var resolved = 0
    cgs.cg.methods.foreach(m => {
      m._2.getStatements.forEach {
        case apply: SWANStatement.ApplyFunctionRef => {
          callsites += 1
          if (cgs.cg.edgesOutOf(apply).isEmpty) {
            unresolved += 1
            var trulyUnresolved = false
            m._2.delegate.symbolTable(apply.inst.functionRef.name) match {
              case SymbolTableEntry.operator(symbol, operator) => {
                if (!apply.inst.functionType.get.name.contains("witness_method")) {
                  trulyUnresolved = true
                }
              }
              case SymbolTableEntry.argument(argument) => {
                if (!cgs.cg.getEntryPoints.contains(m._2) && !cgs.cg.edgesInto(m._2).isEmpty) trulyUnresolved = true
              }
              case SymbolTableEntry.multiple(symbol, operators) => trulyUnresolved = true
            }
            // Using isClosureRelated here might miss some, but that's okay
            if (trulyUnresolved && !isClosureRelated(m._1, cgs.options)) {
              // System.out.println(s"${apply.inst.result.ref.name} = apply ${apply.inst.functionRef.name}, ${if (apply.inst.functionType.nonEmpty) apply.inst.functionType.get.name else ""}")
              tUnresolved += 1
            }
          } else {
            resolved += 1
          }
        }
        case _ =>
      }
    })
    (tUnresolved, unresolved, resolved, callsites)
  }

  def isUninteresting(m: SWANMethod, options: CallGraphConstructor.Options): Boolean = {
    val name = m.getName
    name.endsWith(".deinit") ||
      name.endsWith(".modify") ||
      name.endsWith(".__deallocating_deinit") ||
      (!options.analyzeLibraries && m.delegate.isLibrary)
  }

  def isClosureRelated(name: String, options: CallGraphConstructor.Options): Boolean = {
    if (options.analyzeClosures) false else {
      name.startsWith("closure ") ||
        name.startsWith("reabstraction thunk")
    }
  }

  def pruneEntryPoints(cgs: CallGraphStats): Unit = {
    val cg = cgs.cg
    cg.methods.foreach(m => {
      if (!cg.edgesInto(m._2).isEmpty && cg.getEntryPoints.contains(m._2)) {
        cgs.cg.getEntryPoints.remove(m._2)
        cgs.entryPoints -= 1
        cgs.debugInfo.entries.remove(m._2.delegate)
      }
    })
  }

  def resolveFunctionPointersWithMatching(cgs: CallGraphStats): Unit = {
    // assumes trivial function pointers are already resolved
    // TODO: Use these stats somewhere
    var edgesMatchedUsingType = 0
    // var edgesMatchedUsingArgs = 0
    var edgesMatchedUsingArgsAndRetType = 0
    // Only additive
    cgs.cg.methods.values.foreach(m => {
      m.getStatements.forEach {
        case stmt@(apply: SWANStatement.ApplyFunctionRef) =>  {
          val isTrivial: Boolean = {
            m.delegate.symbolTable(stmt.inst.functionRef.name) match {
              case SymbolTableEntry.operator(_, operator) => {
                operator match {
                  case _: Operator.builtinRef => true
                  case _: Operator.functionRef => true
                  case _: Operator.dynamicRef => true
                  case _ => false
                }
              }
              case _ => false
            }
          }
          if (cgs.cg.edgesOutOf(stmt).isEmpty && !isTrivial) {
            val funcType = apply.inst.functionType
            def matchBasedOnArgs(): Unit = {
              var matched = false
              cgs.cg.methods.foreach(target => {
                if (target._2.getParameterLocals.size() == apply.getInvokeExpr.getArgs.size()) {
                  if (target._2.delegate.returnTpe == apply.inst.result.tpe) {
                    if (addCGEdge(m, target._2, stmt, new ControlFlowGraph.Edge(m.getCFG.getPredsOf(stmt).iterator().next(), stmt), cgs)) edgesMatchedUsingArgsAndRetType += 1
                    matched = true
                  }
                }
              })
              if (!matched) {
                // Insignificant amount, mostly closures
                // System.out.println(s"${apply.inst.result.ref.name} = apply ${apply.inst.functionRef.name}, ${ if (apply.inst.functionType.nonEmpty) apply.inst.functionType.get.name else ""}")
              }
            }
            if (funcType.nonEmpty) {
              val functions = new mutable.HashSet[SWANMethod]()
              cgs.cg.methods.foreach(m => {
                if (m._2.delegate.fullTpe == funcType.get) {
                  functions.add(m._2)
                }
              })
              if (functions.nonEmpty) {
                functions.foreach(f => {
                  if (addCGEdge(m, f, stmt, new ControlFlowGraph.Edge(m.getCFG.getPredsOf(stmt).iterator().next(), stmt), cgs)) edgesMatchedUsingType += 1
                })
              } else matchBasedOnArgs()
            } else matchBasedOnArgs()
          }
        }
        case _ =>
      }
    })
    //System.out.println("edges matched using type: " + edgesMatchedUsingType)
    //System.out.println("edges matched using args and ret type: " + edgesMatchedUsingArgsAndRetType)
  }

  def resolveFunctionPointersWithSPDS(cgs: CallGraphStats, additive: Boolean): Unit = {
    val scope = CallGraphUtils.getDataFlowScope(cgs.options)
    // Using fixed-point Kleene's
    // TODO: stats
    var edges = 0
    while (edges != cgs.cg.getEdges.size()) {
      edges = cgs.cg.getEdges.size()
      if (!additive) { // pruning (e.g., VTA)
        val toReplace = new mutable.HashMap[SWANStatement.ApplyFunctionRef, mutable.HashSet[String]]
        cgs.cg.getEdges.forEach(cgEdge => {
          if (!toReplace.contains(cgEdge.src().asInstanceOf[SWANStatement.ApplyFunctionRef])) {
            cgEdge.src() match {
              case apply: SWANStatement.ApplyFunctionRef => {
                val edge = new ControlFlowGraph.Edge(cgEdge.src().getMethod.getControlFlowGraph.getPredsOf(apply).iterator().next(), apply)
                val query = BackwardQuery.make(edge, apply.getFunctionRef)
                val solver = new Boomerang(cgs.cg, scope, new DefaultBoomerangOptions)
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
                  solver.unregisterAllListeners()
                  val query = BackwardQuery.make(edge, apply.getInvokeExpr.getArgs.get(apply.getInvokeExpr.getArgs.size() - 1))
                  val allocSites = solver.solve(query).getAllocationSites
                  val types = new mutable.HashSet[String]()
                  allocSites.keySet().forEach(allocSite => {
                    allocSite.`var`().asInstanceOf[AllocVal].getAllocVal match {
                      case alloc: SWANVal.NewExpr => types.add(alloc.tpe.tpe.name)
                      case _ =>
                    }
                  })
                  val dynamicTargets = new mutable.HashSet[String]()
                  indices.foreach(i => {
                    cgs.cg.moduleGroup.ddgs.foreach(ddg => {
                      dynamicTargets.addAll(ddg._2.query(i, Some(types)))
                    })
                  })
                  if (dynamicTargets.nonEmpty) {
                    toReplace.put(apply, dynamicTargets)
                  }
                } else {
                  toReplace.put(apply, functions)
                }
              }
              case _ =>
            }
          }
        })
        toReplace.foreach(r => {
          val edges = cgs.cg.edgesOutOf(r._1)
          edges.forEach(cgs.cg.removeEdge)
          val stmt = r._1
          val src = stmt.getMethod.asInstanceOf[SWANMethod]
          r._2.foreach(f => {
            addCGEdge(src, cgs.cg.methods(f), stmt, new ControlFlowGraph.Edge(src.getCFG.getPredsOf(stmt).iterator().next(), stmt), cgs)
          })
        })
      } else { // adding (e.g., RTA)
        cgs.cg.methods.values.foreach(m => {
          m.getStatements.forEach {
            case stmt@(apply: SWANStatement.ApplyFunctionRef) => {
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
                solver.unregisterAllListeners()
                val query = BackwardQuery.make(edge, apply.getInvokeExpr.getArgs.get(apply.getInvokeExpr.getArgs.size() - 1))
                val allocSites = solver.solve(query).getAllocationSites
                val types = new mutable.HashSet[String]()
                allocSites.keySet().forEach(allocSite => {
                  allocSite.`var`().asInstanceOf[AllocVal].getAllocVal match {
                    case alloc: SWANVal.NewExpr => types.add(alloc.tpe.tpe.name)
                    case _ =>
                  }
                })
                indices.foreach(i => {
                  cgs.cg.moduleGroup.ddgs.foreach(ddg => {
                    val targets = ddg._2.query(i, Some(types))
                    targets.foreach(t => cgs.cg.addEdge(new CallGraph.Edge(stmt, cgs.cg.methods(t))))
                  })
                })
              } else {
                functions.foreach(f => cgs.cg.addEdge(new CallGraph.Edge(stmt, cgs.cg.methods(f))))
              }
            }
            case _ =>
          }
        })
      }
    }
  }

  def initializeCallGraph(moduleGroup: ModuleGroup, methods: mutable.HashMap[String, SWANMethod], cg: SWANCallGraph, cgs: CallGraphStats): Unit = {

    moduleGroup.functions.foreach(f => {
      val m = new SWANMethod(f, moduleGroup)
      methods.put(f.name, m)
      if (!isUninteresting(m, cgs.options)) {
        cg.addEntryPoint(m)
        cgs.entryPoints += 1
      }
    })

    methods.foreach{case (_,m) => cg.graph.addVertex(m)}
  }

  def writeToProbe(cg: SWANCallGraph, f: File, contextSensitive: Boolean = true): Unit = {
    val fw = new FileWriter(f)
    try {
      // Generate a fake class
      fw.write("CLASS\n")
      fw.write("id\n")
      fw.write("package\n")
      fw.write("name\n")
      cg.methods.foreach(m => {
        fw.write("METHOD\n")
        fw.write(s"${m._2.getName.hashCode()}\n") // id
        fw.write(s"${m._2.getName}\n") // name
        fw.write(s"${m._2.getName}\n") // signature
        fw.write(s"id\n") // class
      })
      cg.getEntryPoints.forEach(m => {
        fw.write("ENTRYPOINT\n")
        fw.write(s"${m.getName.hashCode()}\n") // id
      })
      cg.getEdges.forEach(edge => {
        fw.write("CALLEDGE\n")
        fw.write(s"${edge.src().getMethod.getName.hashCode}\n") // src id
        fw.write(s"${edge.tgt().getName.hashCode}\n") // dst id
        fw.write(s"1.0\n") // weight
        // context
        if (contextSensitive) {
          fw.write(s"${edge.src.getStartLineNumber}:${edge.src.getStartColumnNumber}\n") // context
          //fw.write(s"${edge.src.getStartLineNumber}:${edge.src().hashCode()}\n") // context
        }
        else {
          fw.write(s"\n")
        }
      })
    } finally {
      fw.close()
    }
  }

}
