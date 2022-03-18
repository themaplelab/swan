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

import boomerang.scene.{ControlFlowGraph, Method}
import ca.ualberta.maple.swan.ir.{Constants, ModuleGroup, Operator, SymbolTableEntry}
import ca.ualberta.maple.swan.spds.Stats.{CallGraphStats, SpecificCallGraphStats}
import ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle
import ca.ualberta.maple.swan.spds.cg.CallGraphConstructor.Options
import ca.ualberta.maple.swan.spds.cg.pa.PointerAnalysis
import ca.ualberta.maple.swan.spds.structures.{SWANMethod, SWANStatement}
import ca.ualberta.maple.swan.spds.structures.SWANStatement.ApplyFunctionRef
import ujson.Value

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class ORTA(mg: ModuleGroup, pas: PointerAnalysisStyle.Style, options: Options) extends CallGraphConstructor(mg, options) {

  pas match {
    case PointerAnalysisStyle.None =>
    case PointerAnalysisStyle.SPDS =>
    case PointerAnalysisStyle.UFF =>
      throw new RuntimeException("UFF pointer analysis is currently not supported with ORTA")
    case PointerAnalysisStyle.NameBased =>
  }

  val unreachableTypeTargets: mutable.MultiDict[String, (ApplyFunctionRef, ControlFlowGraph.Edge, Set[String])] = mutable.MultiDict.empty[String, (ApplyFunctionRef, ControlFlowGraph.Edge, Set[String])]
  val reachableTypes = mutable.HashSet.empty[String]
  val reachableMethods = mutable.HashSet.empty[SWANMethod]
  val blockWorklist = new DFWorklist(options)
  val newTypesWorklist = mutable.HashSet.empty[String]
  val newMethodsWorklist = mutable.HashSet.empty[SWANMethod]

  def addMethod(m : SWANMethod): Unit = {
    if (!reachableMethods.contains(m)) {
      reachableMethods.add(m)
      blockWorklist.addMethod(m)
      m.delegate.blocks.foreach(_.operators.foreach{op => op.operator match {
        case Operator.neww(_, allocType) => {
          if (!reachableTypes.contains(allocType.name)) {
            newTypesWorklist.addOne(allocType.name)
          }
        }
        // ignore non allocation operators
        case _ =>
      }})
    }
  }

  var rtaEdges: Int = 0
  def addCallSiteEdges(cgs: CallGraphStats, stmt: ApplyFunctionRef, predEdge: ControlFlowGraph.Edge): Unit = {
    val method = stmt.m
    val methods = cgs.cg.methods
    method.delegate.symbolTable(stmt.inst.functionRef.name) match {
      case SymbolTableEntry.operator(_, operator) => {
        operator match {
          case Operator.builtinRef(_, name) => {
            if (methods.contains(name)) {
              val targetMethod = methods(name)
              if (CallGraphUtils.addCGEdge(method, targetMethod, stmt, predEdge, cgs)) {
                cgs.trivialCallSites += 1
                addMethod(targetMethod)
              }
            }
          }
          case Operator.functionRef(_, name) => {
            val targetMethod = methods(name)
            if (CallGraphUtils.addCGEdge(method, targetMethod, stmt, predEdge, cgs)) {
              cgs.trivialCallSites += 1
              addMethod(targetMethod)
            }
          }
          case Operator.dynamicRef(_, _, index) => {
            moduleGroup.ddgs.foreach{ case (_,ddg) => {
              ddg.queryTypeTargets(index).sets.foreach{ case (typ,targets) => {
                if (reachableTypes.contains(typ)) {
                  targets.foreach{target =>
                    val targetMethod = methods(target)
                    if (CallGraphUtils.addCGEdge(method, targetMethod, stmt, predEdge, cgs)) {
                      rtaEdges += 1
                      addMethod(targetMethod)
                    }
                  }
                }
                else {
                  unreachableTypeTargets.addOne(typ,(stmt,predEdge,targets))
                }
              }}
            }}
          }
          // ignore other operators
          case _ =>
        }
      }
      // ignore non-operator symbol table entries
      case _ =>
    }
  }

  override def buildSpecificCallGraph(): Unit = {
    val startTimeMs = System.currentTimeMillis()

    // ORTA isn't meaningful without a main function
    // ORTA is equivalent to PRTA if everything is reachable
    // TODO figure out better entry points
    val entryPointsIterator = cgs.cg.getEntryPoints.asInstanceOf[java.util.Collection[SWANMethod]].iterator()
    while (entryPointsIterator.hasNext) {
      val m = entryPointsIterator.next()
      addMethod(m)
    }

    while (blockWorklist.nonEmpty || newTypesWorklist.nonEmpty) {
      while (blockWorklist.nonEmpty) {
        val blk = blockWorklist.pop()
        blk.stmts.foreach {
          // call site
          case applyStmt: SWANStatement.ApplyFunctionRef =>
            val predEdge = new ControlFlowGraph.Edge(blk.method.getCFG.getPredsOf(applyStmt).iterator().next(), applyStmt)
            addCallSiteEdges(cgs,applyStmt,predEdge)
          // ignore non-call-sites
          case _ =>
        }
      }
      while (newTypesWorklist.nonEmpty) {
        val typ = newTypesWorklist.iterator.next()
        newTypesWorklist.remove(typ)
        reachableTypes.add(typ)
        val callSites = unreachableTypeTargets.get(typ)
        callSites.foreach{case (stmt,predEdge,targets) =>
          val method = stmt.getSWANMethod
          val methods = cgs.cg.methods
          targets.foreach{target =>
            val targetMethod = methods(target)
            if (CallGraphUtils.addCGEdge(method, targetMethod, stmt, predEdge, cgs)) {
              rtaEdges += 1
              addMethod(targetMethod)
            }
          }
        }
        unreachableTypeTargets.removeKey(typ)
      }
    }

    pas match {
      case PointerAnalysisStyle.SPDS =>
        CallGraphUtils.resolveFunctionPointersWithSPDS(cgs, additive = false)
      case PointerAnalysisStyle.NameBased =>
        CallGraphUtils.resolveFunctionPointersWithMatching(cgs)
      case _ =>
    }

    val stats = new ORTA.ORTAStats(rtaEdges, (System.currentTimeMillis() - startTimeMs).toInt)
    cgs.specificData.addOne(stats)
  }
}


object ORTA {

  class ORTAStats(val edges: Int, time: Int) extends SpecificCallGraphStats {

    override def toJSON: Value = {
      val u = ujson.Obj()
      u("orta_edges") = edges
      u("orta_time") = time
      u
    }

    override def toString: String = {
      s"ORTA\n  Edges: $edges\n  Time (ms): $time"
    }
  }
}




