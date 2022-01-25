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
import ca.ualberta.maple.swan.ir.DynamicDispatchGraph.Node
import ca.ualberta.maple.swan.ir.{ModuleGroup, Operator, SymbolTableEntry}
import ca.ualberta.maple.swan.spds.Stats.{CallGraphStats, SpecificCallGraphStats}
import ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle
import ca.ualberta.maple.swan.spds.cg.CallGraphUtils.addCGEdge
import ca.ualberta.maple.swan.spds.cg.UCG.UCGStats
import ca.ualberta.maple.swan.spds.structures.SWANControlFlowGraph.SWANBlock
import ca.ualberta.maple.swan.spds.structures.{SWANInvokeExpr, SWANMethod, SWANStatement, SWANVal}
import ujson.Value

import scala.collection.{immutable, mutable}

class UCG(mg: ModuleGroup, pas: PointerAnalysisStyle.Style) extends CallGraphConstructor(mg) {
  private val w: DFWorklist = new DFWorklist
  type DDGTypeSet = DDGBitSet //immutable.HashSet[String]
  private val inSets: mutable.HashMap[SWANBlock, DDGTypeSet] = new mutable.HashMap[SWANBlock, DDGTypeSet]
  private val outSets: mutable.HashMap[SWANBlock, DDGTypeSet] = new mutable.HashMap[SWANBlock, DDGTypeSet]
  private val interProcInSets: mutable.HashMap[SWANMethod, DDGTypeSet] = new mutable.HashMap[SWANMethod, DDGTypeSet]
  private val returnSites: mutable.HashMap[SWANMethod, mutable.HashSet[SWANBlock]] =
    new mutable.HashMap[SWANMethod, mutable.HashSet[SWANBlock]]
  private val interProcSuccessors: mutable.HashMap[SWANBlock, mutable.HashSet[SWANBlock]] =
    new mutable.HashMap[SWANBlock, mutable.HashSet[SWANBlock]]
  private implicit val ddgTypes: mutable.HashMap[String, Int] = mutable.HashMap.empty[String, Int]
  private implicit val ddgTypesInv: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty[String]

  override def buildSpecificCallGraph(cgs: CallGraphStats): Unit = {
    val startTimeMs = System.currentTimeMillis()

    // This type set creation
    moduleGroup.ddgs.foreach { case (_, ddg) =>
      ddg.nodes.keySet.foreach { typ =>
        if (ddg.nodes(typ).isInstanceOf[Node.Class] || ddg.nodes(typ).isInstanceOf[Node.Protocol]) {
          val n = ddgTypes.size
          ddgTypes.addOne(typ, n)
          ddgTypesInv.insert(n, typ)
        }
      }
    }

    val stats = new UCGStats
    val queryCache = new QueryCache(cgs.cg, stats)
    val callSites = mutable.HashSet.empty[SWANStatement.ApplyFunctionRef]

    val entryPoints = cgs.cg.getEntryPoints.asInstanceOf[java.util.Collection[SWANMethod]]
    entryPoints.forEach(m => {
      w.addMethod(m)
      processWorklist(m, cgs.cg.getEntryPoints.asInstanceOf[java.util.Collection[SWANMethod]],
        cgs, queryCache, stats, callSites)
    })

    cgs.totalCallSites = callSites.size
    stats.time = (System.currentTimeMillis() - startTimeMs).toInt
    cgs.specificData.addOne(stats)
    CallGraphUtils.pruneEntryPoints(cgs)
  }

  def processWorklist(startMethod: SWANMethod, entryPoints: java.util.Collection[SWANMethod],
                      cgs: CallGraphStats, queryCache: QueryCache, stats: UCGStats,
                      callSites: mutable.HashSet[SWANStatement.ApplyFunctionRef]): Unit = {

    while (w.nonEmpty) {
      val currBlock = w.pop()
      var b: DDGTypeSet = new DDGTypeSet(immutable.BitSet.empty) //new immutable.HashSet[String]
      // process predecessors
      currBlock.preds.foreach(pred => outSets.get(pred) match {
        case Some(outSet) => b = b.union(outSet)
        case None =>
      })
      // process interproc predecessors and entry points
      val method = currBlock.method
      if (currBlock == method.getStartBlock) {
        interProcInSets.get(method) match {
          case Some(inSet) => b = b.union(inSet)
          case None =>
        }
      }
      // add inset for debugging
      inSets.put(currBlock, b)
      // process operators
      currBlock.stmts.foreach {
        case SWANStatement.Allocation(_, inst, _) => {
          val tpe = inst.allocType.name
          b = b + tpe
        }
        case applyStmt: SWANStatement.ApplyFunctionRef => {
          callSites.add(applyStmt)
          val m = currBlock.method
          val edge = new ControlFlowGraph.Edge(m.getCFG.getPredsOf(applyStmt).iterator().next(), applyStmt)

          def visitSimpleRef(name: String): Unit = {
            val target = cgs.cg.methods(name)
            if (addCGEdge(m, target, applyStmt, edge, cgs)) cgs.trivialCallSites += 1
            b = processTarget(target, currBlock, b)
          }

          def visitDynamicRef(index: String): Unit = {
            val currTypes = b
            val instantiatedTypes = b.toHashSet
            // TODO: Interproc Successors/Insets
            moduleGroup.ddgs.foreach(ddg => {
              val functionNames = ddg._2.query(index, Some(instantiatedTypes))
              functionNames.foreach(name => {
                val target = cgs.cg.methods(name)
                if (addCGEdge(m, target, applyStmt, edge, cgs)) stats.virtualEdges += 1
                b = b.union(processTarget(target, currBlock, currTypes))
              })
            })
          }

          def queryRef(stmt: SWANStatement.ApplyFunctionRef, method: SWANMethod): Unit = {
            val ref = stmt.getInvokeExpr.asInstanceOf[SWANInvokeExpr].getFunctionRef
            // not reusing the above because of different edge count increments and currTypes management
            val currTypes = b

            def visitSimpleRef(name: String): Unit = {
              val target = cgs.cg.methods(name)
              if (addCGEdge(m, target, applyStmt, edge, cgs)) stats.queriedEdges += 1
              b = b.union(processTarget(target, currBlock, currTypes))
            }

            def visitDynamicRef(index: String): Unit = {
              val instantiatedTypes = b.toHashSet
              moduleGroup.ddgs.foreach(ddg => {
                val functionNames = ddg._2.query(index, Some(instantiatedTypes))
                functionNames.foreach(name => {
                  val target = cgs.cg.methods(name)
                  if (addCGEdge(m, target, applyStmt, edge, cgs)) stats.queriedEdges += 1
                  b = b.union(processTarget(target, currBlock, currTypes))
                })
              })
            }

            m.getControlFlowGraph.getPredsOf(stmt).forEach(pred => {
              val allocSites = queryCache.get(pred, stmt, ref)
              allocSites.forEach((forwardQuery, _) => {
                forwardQuery.`var`().asInstanceOf[AllocVal].getAllocVal match {
                  case v: SWANVal.FunctionRef =>
                    visitSimpleRef(v.ref)
                  case v: SWANVal.BuiltinFunctionRef =>
                    visitSimpleRef(v.ref)
                  case v: SWANVal.DynamicFunctionRef =>
                    visitDynamicRef(v.index)
                  case _ => // likely result of partial_apply (ignore for now)
                }
              })
            })
          }

          m.delegate.symbolTable(applyStmt.inst.functionRef.name) match {
            case SymbolTableEntry.operator(_, operator) => {
              operator match {
                case Operator.functionRef(_, name) =>
                  visitSimpleRef(name)
                case Operator.builtinRef(_, name) =>
                  if (cgs.cg.methods.contains(name)) {
                    visitSimpleRef(name)
                  }
                case Operator.dynamicRef(_, _, index) =>
                  visitDynamicRef(index)
                case _ => queryRef(applyStmt, m)
              }
            }
            case _: SymbolTableEntry.argument => queryRef(applyStmt, m)
            case multiple: SymbolTableEntry.multiple => {
              multiple.operators.foreach {
                case Operator.functionRef(_, name) =>
                  visitSimpleRef(name)
                case Operator.builtinRef(_, name) =>
                  if (cgs.cg.methods.contains(name)) {
                    visitSimpleRef(name)
                  }
                case Operator.dynamicRef(_, _, index) =>
                  visitDynamicRef(index)
                case _ => queryRef(applyStmt, m)
              }
            }
          }
        }
        // other statements ignored
        case _ =>
      }
      // Process Outset
      val outSetChanged = outSets.get(currBlock) match {
        case Some(oldOutSet) => !b.subsetOf(oldOutSet)
        case None => b.nonEmpty
      }
      outSets.update(currBlock, b)
      if (outSetChanged) {
        currBlock.succs.foreach(blk => w.add(blk))
        interProcSuccessors.get(currBlock) match {
          case Some(succs) => succs.foreach(succ => w.add(succ))
          case None =>
        }
        if (currBlock.isExitBlock) {
          returnSites.get(currBlock.method) match {
            case Some(succs) => succs.foreach(succ => w.add(succ))
            case None =>
          }
        }
      }
    }
  }

  def processTarget(t: SWANMethod, currBlock: SWANBlock, b: DDGTypeSet): DDGTypeSet = {

    // Add currBlock to successors of t
    returnSites.get(t) match {
      case Some(succs) => succs.add(currBlock)
      case None =>
        val singleton = new mutable.HashSet[SWANBlock]()
        singleton.add(currBlock)
        returnSites.update(t, singleton)
    }

    // Add types to t's inter proc insets
    interProcInSets.get(t) match {
      case Some(inSet) =>
        if (!b.subsetOf(inSet)) {
          interProcInSets.update(t, inSet.union(b))
          w.addMethod(t)
        }
      case None =>
        interProcInSets.put(t, b)
        w.addMethod(t)
    }

    // Union t's outset to the set of DDGTypeSet
    val exitBlocks = t.getExitBlocks

    // TODO cache outsets per method
    exitBlocks.foldLeft(b)((acc, nxt) => outSets.get(nxt) match {
      case Some(outSet) => outSet.union(acc)
      case None => acc
    })
  }
}

object UCG {

  class UCGStats() extends SpecificCallGraphStats {
    var queriedEdges: Int = 0
    var virtualEdges: Int = 0
    var totalQueries: Int = 0
    var fruitlessQueries: Int = 0
    var time: Int = 0

    override def toJSON: Value = {
      val u = ujson.Obj()
      u("ucg_queried_edges") = queriedEdges
      u("ucg_virtual_edges") = virtualEdges
      u("ucg_total_queries") = totalQueries
      u("ucg_fruitless_queries") = fruitlessQueries
      u("ucg_time") = time
      u
    }

    override def toString: String = {
      val sb = new StringBuilder("UCG\n")
      sb.append(s"  Queried Edges: $queriedEdges\n")
      sb.append(s"  Virtual Edges: $virtualEdges\n")
      sb.append(s"  Total Queries: $totalQueries\n")
      sb.append(s"  Fruitless Queries: $fruitlessQueries\n")
      sb.append(s"  Time (ms): $time\n")
      sb.toString()
    }
  }
}
