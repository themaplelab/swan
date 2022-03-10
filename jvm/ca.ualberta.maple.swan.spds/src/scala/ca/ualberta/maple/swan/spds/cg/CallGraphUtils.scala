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
import ca.ualberta.maple.swan.ir.{Instruction, ModuleGroup}
import ca.ualberta.maple.swan.spds.Stats.CallGraphStats
import ca.ualberta.maple.swan.spds.structures.{SWANCallGraph, SWANMethod, SWANStatement}

import java.io.{File, FileWriter}
import scala.collection.mutable

object CallGraphUtils {

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
    if (isClosureRelated(from, cgs.options)) {
      // Closures are not supported
      //throw new RuntimeException("Adding CG edge from closure, this is not supported")
      return false
    }
    if (isClosureRelated(to, cgs.options)) {
      // Closures are not supported
      return false
    }
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

  def resolvedCallSites(cgs: CallGraphStats) : Int = {
    val cg : CallGraph = cgs.cg
    val dict : mutable.MultiDict[SWANStatement, SWANMethod] = mutable.MultiDict.empty
    cg.getEdges.forEach(e => dict.addOne(e.src().asInstanceOf[SWANStatement.ApplyFunctionRef], e.tgt().asInstanceOf[SWANMethod]))
    dict.keySet.count(k => dict.get(k).size == 1)
  }

  def totalCallSites(cgs: CallGraphStats) : Int = {
    val cg : CallGraph = cgs.cg
    val outOf : mutable.HashSet[SWANStatement] = mutable.HashSet.empty
    cg.getEdges.forEach(e => {
      val src = e.src().asInstanceOf[SWANStatement.ApplyFunctionRef]
      outOf.add(src)
    })
    outOf.size
  }

  def isUninteresting(m: SWANMethod, options: CallGraphConstructor.Options): Boolean = {
    val name = m.getName
    name.endsWith(".deinit") ||
      name.endsWith(".modify") ||
      name.endsWith(".__deallocating_deinit") ||
      (!options.analyzeLibraries && m.delegate.isLibrary)
  }

  def isClosureRelated(m: SWANMethod, options: CallGraphConstructor.Options): Boolean = {
    if (options.analyzeClosures) false else {
      val name = m.getName
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
      }
    })
  }

  def initializeCallGraph(moduleGroup: ModuleGroup, methods: mutable.HashMap[String, SWANMethod], cg: SWANCallGraph, cgs: CallGraphStats): Unit = {

    moduleGroup.functions.foreach(f => {
      val m = new SWANMethod(f, moduleGroup)
      methods.put(f.name, m)
      if (!isUninteresting(m, cgs.options) && !isClosureRelated(m, cgs.options)) {
        cg.addEntryPoint(m)
        cgs.entryPoints += 1
      }
    })

    methods.foreach{case (_,m) => cg.graph.addVertex(m)}
  }

  def writeToProbe(cg: SWANCallGraph, f: File, contextSenstive: Boolean = true): Unit = {
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
        if (contextSenstive) {
          fw.write(s"${edge.src().hashCode()}\n") // context
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
