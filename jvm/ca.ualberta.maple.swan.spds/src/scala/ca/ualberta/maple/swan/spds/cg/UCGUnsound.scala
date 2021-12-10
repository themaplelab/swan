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

import boomerang.{BackwardQuery, Boomerang, DefaultBoomerangOptions, ForwardQuery}
import boomerang.results.AbstractBoomerangResults
import ca.ualberta.maple.swan.spds.Stats.{CallGraphStats, SpecificCallGraphStats}
import boomerang.scene.{AllocVal, CallGraph, ControlFlowGraph, DataFlowScope, Val}
import ca.ualberta.maple.swan.ir.{ModuleGroup, Operator, SymbolTableEntry}
import ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle
import ca.ualberta.maple.swan.spds.cg.CallGraphUtils.addCGEdge
import ca.ualberta.maple.swan.spds.cg.UCGUnsound.UCGUnsoundStats
import ca.ualberta.maple.swan.spds.structures.SWANControlFlowGraph.SWANBlock
import ca.ualberta.maple.swan.spds.structures.SWANStatement.ApplyFunctionRef
import ca.ualberta.maple.swan.spds.structures.{SWANInvokeExpr, SWANMethod, SWANStatement, SWANVal}
import ujson.Value

import java.util
import scala.collection.{immutable, mutable}

final class UQueryCache(cgs: CallGraphStats, stats: UCGUnsoundStats) {
  val cache: mutable.HashMap[(boomerang.scene.Statement, ApplyFunctionRef, Val), (util.Map[ForwardQuery, AbstractBoomerangResults.Context], Int)] =
    mutable.HashMap.empty

  def get(pred: boomerang.scene.Statement, stmt: ApplyFunctionRef, ref: Val): util.Map[ForwardQuery, AbstractBoomerangResults.Context] = {
    cache.get(pred, stmt, ref) match {
      case Some((allocSites,_)) => allocSites
      case None =>
        val allocSites = query(pred, stmt, ref)
        val edgeCount = cgs.cg.size()
        cache.update((pred,stmt,ref), (allocSites,edgeCount))
        allocSites
    }
  }

  def query(pred: boomerang.scene.Statement, stmt: ApplyFunctionRef, ref: Val): util.Map[ForwardQuery, AbstractBoomerangResults.Context] = {
    val query = BackwardQuery.make(new ControlFlowGraph.Edge(pred, stmt), ref)
    val solver = new Boomerang(cgs.cg, DataFlowScope.INCLUDE_ALL, new DefaultBoomerangOptions)
    val backwardQueryResults = solver.solve(query)
    stats.totalQueries += 1
    val allocSites = backwardQueryResults.getAllocationSites
    if (allocSites.isEmpty) {
      stats.fruitlessQueries += 1
    }
    if (query.asNode().stmt().getTarget.asInstanceOf[SWANStatement.ApplyFunctionRef] != stmt) {
      throw new AssertionError()
    }
    allocSites
  }

  def invalidate(pred: boomerang.scene.Statement, stmt: ApplyFunctionRef, ref: Val): Unit = {
    cache.remove(pred, stmt, ref)
  }

}

class UCGUnsound(mg: ModuleGroup, pas: PointerAnalysisStyle.Style) extends CallGraphConstructor(mg) {
  private val w: DFWorklist = new DFWorklist
  type DDGTypeSet = DDGBitSet//immutable.HashSet[String]
  private val inSets: mutable.HashMap[SWANBlock, DDGTypeSet] = new mutable.HashMap[SWANBlock, DDGTypeSet]
  private val outSets: mutable.HashMap[SWANBlock, DDGTypeSet] = new mutable.HashMap[SWANBlock, DDGTypeSet]
  private val interProcInSets: mutable.HashMap[SWANMethod, DDGTypeSet] = new mutable.HashMap[SWANMethod, DDGTypeSet]
  private val returnSites: mutable.HashMap[SWANMethod, mutable.HashSet[SWANBlock]] =
    new mutable.HashMap[SWANMethod, mutable.HashSet[SWANBlock]]
  private val interProcSuccessors: mutable.HashMap[SWANBlock, mutable.HashSet[SWANBlock]] =
    new mutable.HashMap[SWANBlock, mutable.HashSet[SWANBlock]]
  private val edgeMap: mutable.HashMap[(SWANMethod,SWANMethod), Int] = new mutable.HashMap[(SWANMethod,SWANMethod), Int]
  private implicit val ddgTypes = mutable.HashMap.empty[String, Int]
  private implicit val ddgTypesInv = mutable.ArrayBuffer.empty[String]
  val stats = new UCGUnsoundStats

  private var queryCache: UQueryCache = null

  override def buildSpecificCallGraph(cgs: CallGraphStats): Unit = {
    // This type set creation
    moduleGroup.ddgs.foreach { case (_,ddg) =>
      ddg.nodes.keySet.foreach{typ =>
        val n = ddgTypes.size
        ddgTypes.addOne(typ,n)
        ddgTypesInv.insert(n,typ)
      }
    }

    // query cache
    queryCache = new UQueryCache(cgs, stats)

    val entryPoints = cgs.cg.getEntryPoints.asInstanceOf[java.util.Collection[SWANMethod]]
    entryPoints.forEach{m =>
      w.addMethod(m)
      processWorklist(m, cgs.cg.getEntryPoints.asInstanceOf[java.util.Collection[SWANMethod]], cgs)
      val predMap = newestNewPredecessor(cgs)
      val cacheClone = queryCache.cache.clone()
      cacheClone.foreach{ case ((pred,stmt,ref),(allocSites,edgeCount)) =>
        val m = stmt.getSWANMethod
        if (predMap.contains(m) && predMap(m) >= edgeCount) {
          val updatedAllocs = queryCache.query(pred,stmt,ref)
          if (updatedAllocs.size() != allocSites.size()) {
            queryCache.cache.update((pred,stmt,ref),(updatedAllocs,cgs.cg.size()))
            // TODO: just stmt's block not entire method
            stmt.getSWANMethod.getCFG.blocks.foreach{ case (_,block) => w.add(block)}
          }
        }
      }
    }
  }

  def newestNewPredecessor(cgs: CallGraphStats): mutable.HashMap[SWANMethod, Int] = {
    val predMap: mutable.HashMap[SWANMethod, Int] = mutable.HashMap.empty
    val processed: mutable.HashSet[SWANMethod] = mutable.HashSet.empty
    while (edgeMap.nonEmpty) {
      val ((_, to), maxEdge) = edgeMap.maxBy(_._2)
      val next: mutable.HashSet[SWANMethod] = mutable.HashSet.empty
      next.add(to)
      while (next.nonEmpty) {
        edgeMap.filterInPlace{ case ((_,to1),_) => !next.contains(to1)}
        next.foreach(m => predMap.update(m,maxEdge))
        processed.addAll(next)
        val succs = new mutable.HashSet[SWANMethod]
        next.foreach{m =>
          val statements = m.getStatements.toArray()
          statements.map{s =>
            cgs.cg.edgesOutOf(s.asInstanceOf[SWANStatement]).toArray().foreach{f =>
              val tgt = f.asInstanceOf[CallGraph.Edge].tgt().asInstanceOf[SWANMethod]
              succs.add(tgt)
            }
          }
        }
        succs.filterInPlace(m => !processed.contains(m))
        next.clear()
        next.addAll(succs)
      }
    }
    predMap
  }

  def processWorklist(startMethod: SWANMethod, entryPoints: util.Collection[SWANMethod], cgs: CallGraphStats) = {
    while (w.nonEmpty) {
      val currBlock = w.pop()
      var b: DDGTypeSet = new DDGTypeSet(immutable.BitSet.empty)//new immutable.HashSet[String]
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
      currBlock.stmts.foreach{
        case SWANStatement.Allocation(_, inst, _) =>
          val tpe = inst.result.tpe.name
          b = b + tpe
        case applyStmt: SWANStatement.ApplyFunctionRef => {
          val m = currBlock.method
          val edge = new ControlFlowGraph.Edge(m.getCFG.getPredsOf(applyStmt).iterator().next(), applyStmt)
          def visitSimpleRef(name: String) = {
            val target = cgs.cg.methods(name)
            val cgSize = cgs.cg.size()
            if (addCGEdge(m, target, applyStmt, edge, cgs)) {
              cgs.trivialCallSites += 1
              edgeMap.update((m,target),cgSize)
            }
            b = processTarget(target, currBlock, b)
          }
          def visitDynamicRef(index: String) = {
            val currTypes = b
            val instantiatedTypes = b.toHashSet
            // TODO: Interproc Successors/Insets
            moduleGroup.ddgs.foreach(ddg => {
              val functionNames = ddg._2.query(index, Some(instantiatedTypes))
              functionNames.foreach(name => {
                val target = cgs.cg.methods(name)
                val cgSize = cgs.cg.size()
                if (addCGEdge(m, target, applyStmt, edge, cgs)) {
                  stats.virtualEdges += 1
                  edgeMap.update((m,target),cgSize)
                }
                b = b.union(processTarget(target, currBlock, currTypes))
              })
            })
          }
          def queryRef(stmt: SWANStatement.ApplyFunctionRef, method: SWANMethod) = {
            val ref = stmt.getInvokeExpr.asInstanceOf[SWANInvokeExpr].getFunctionRef
            // not reusing the above because of different edge count increments and currTypes management
            val currTypes = b
            def visitSimpleRef(name: String) = {
              val target = cgs.cg.methods(name)
              val cgSize = cgs.cg.size()
              if (addCGEdge(m, target, applyStmt, edge, cgs)) {
                stats.queriedEdges += 1
                edgeMap.update((m,target),cgSize)
              }
              b = b.union(processTarget(target, currBlock, currTypes))
            }
            def visitDynamicRef(index: String) = {
              val instantiatedTypes = b.toHashSet
              moduleGroup.ddgs.foreach(ddg => {
                val functionNames = ddg._2.query(index, Some(instantiatedTypes))
                functionNames.foreach(name => {
                  val target = cgs.cg.methods(name)
                  val cgSize = cgs.cg.size()
                  if (addCGEdge(m, target, applyStmt, edge, cgs)) {
                    stats.queriedEdges += 1
                    edgeMap.update((m,target),cgSize)
                  }
                  b = b.union(processTarget(target, currBlock, currTypes))
                })
              })
            }
            m.getControlFlowGraph.getPredsOf(stmt).forEach(pred => {
              val allocSites = queryCache.get(pred, stmt, ref)
              allocSites.forEach((forwardQuery, _) => {
                forwardQuery.`var`().asInstanceOf[AllocVal].getAllocVal match {
                  case v : SWANVal.FunctionRef =>
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
      outSets.update(currBlock,b)
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
          interProcInSets.update(t,inSet.union(b))
          w.addMethod(t)
        }
      case None =>
        interProcInSets.put(t,b)
        w.addMethod(t)
    }
    // Union t's outset to the set of DDGTypeSet
    val exitBlocks = t.getExitBlocks
    // TODO cache outsets per method
    exitBlocks.foldLeft(b)((acc,nxt) => outSets.get(nxt) match {
      case Some(outSet) => outSet.union(acc)
      case None => acc
    })
  }

}

object UCGUnsound {

  class UCGUnsoundStats() extends SpecificCallGraphStats {
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
  }
}
