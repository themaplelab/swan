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

import boomerang.results.AbstractBoomerangResults
import boomerang.scene._
import boomerang.{BackwardQuery, Boomerang, DefaultBoomerangOptions, ForwardQuery}
import ca.ualberta.maple.swan.ir.{ModuleGroup, Operator, SymbolTableEntry}
import ca.ualberta.maple.swan.spds.Stats.{CallGraphStats, SpecificCallGraphStats}
import ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle
import ca.ualberta.maple.swan.spds.cg.CallGraphConstructor.Options
import ca.ualberta.maple.swan.spds.cg.CallGraphUtils.addCGEdge
import ca.ualberta.maple.swan.spds.cg.UCGSound.UCGSoundStats
import ca.ualberta.maple.swan.spds.cg.pa.{MatrixUnionFind, PointerAnalysis, UnionFind}
import ca.ualberta.maple.swan.spds.structures.SWANControlFlowGraph.SWANBlock
import ca.ualberta.maple.swan.spds.structures.SWANStatement.ApplyFunctionRef
import ca.ualberta.maple.swan.spds.structures.{SWANInvokeExpr, SWANMethod, SWANStatement, SWANVal}
import ujson.Value

import java.util
import scala.collection.mutable.ArrayBuffer
import scala.collection.{immutable, mutable}

/**
 * An algorithm that soundly and precisely handles dynamic
 * dispatch and function pointers simultaneously. The algorithm is tentatively
 * called UCG, or Unified Call Graph [Construction], because it is the first
 * algorithm to tackle the two aforementioned elements in a unified way.
 *
 * The algorithm ... distribute/monotonic/order-indendepent?
 *
 */
class UCGSound(mg: ModuleGroup, pas: PointerAnalysisStyle.Style,
               val invalidations: Boolean,options: Options) extends CallGraphConstructor(mg, options) {

  type DDGTypeSet = DDGBitSet
  type SPDSResults = util.Map[ForwardQuery, AbstractBoomerangResults.Context]
  type UFFResults = mutable.HashSet[SWANVal]

  /** Worklist of blocks to be visited and processed (unique stack) */
  private val w: DFWorklist = new DFWorklist
  /** Seen/visited blocks (for more efficient condition checking) */
  private val seen: mutable.HashMap[SWANBlock, DDGTypeSet] = new mutable.HashMap[SWANBlock, DDGTypeSet]
  /** The cached outsets of blocks (for later comparison) */
  private val outSets: mutable.HashMap[SWANBlock, DDGTypeSet] = new mutable.HashMap[SWANBlock, DDGTypeSet]
  /** Mapping of methods to bitsets, which are aggregates of all callsites that call the method */
  private val interProcInSets: mutable.HashMap[SWANMethod, DDGTypeSet] = new mutable.HashMap[SWANMethod, DDGTypeSet]
  /** Mapping of methods to their return sites (set of blocks) */
  private val returnSites: mutable.HashMap[SWANMethod, mutable.HashSet[SWANBlock]] =
    new mutable.HashMap[SWANMethod, mutable.HashSet[SWANBlock]]
  /** Mapping of dynamic dispatch reference blocks to the blocks where the reference is used (call sites) */
  private val interProcSuccessors: mutable.HashMap[SWANBlock, mutable.HashSet[SWANBlock]] =
    new mutable.HashMap[SWANBlock, mutable.HashSet[SWANBlock]]

  private var uf: MatrixUnionFind = null

  private implicit val ddgTypes: mutable.HashMap[String, Int] = mutable.HashMap.empty[String, Int]
  private implicit val ddgTypesInv: ArrayBuffer[String] = mutable.ArrayBuffer.empty[String]
  val stats = new UCGSoundStats

  private var queryCache: SQueryCache[_] = null

  override def buildSpecificCallGraph(): Unit = {

    pas match {
      case ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle.UFF => {
        uf = new MatrixUnionFind(cgs.cg)
      }
      case _ =>
    }

    // This type set creation (map types to bits)
    moduleGroup.ddgs.foreach { case (_, ddg) =>
      ddg.nodes.keySet.foreach{typ =>
        val n = ddgTypes.size
        ddgTypes.addOne(typ, n)
        ddgTypesInv.insert(n, typ)
      }
    }

    // Add specific data to call graph stats
    cgs.specificData.append(stats)

    // Call the algorithm
    mainLoop(cgs)

    //System.out.println(uf.printToDot())
  }

  /**
   * Keeps a worklist of blocks, using a stack with _unique_ elements, and
   * the blocks are those we want to process/visit. Initially, all methods are
   * considered to be entry points. The worklist initially contains the first
   * block of an entry point, and once it has completely exhausted the
   * (transitive) successors of the entry point, it will add the first block of
   * the next entry point and continue. Entry points will be removed from the
   * list if we discover an edge to that method, and therefore that method will
   * not be added to the worklist.
   */
  def mainLoop(cgs: CallGraphStats): Unit = {
    // Init cache of call sites to their ref alloc sites
    queryCache = {
      pas match {
        case PointerAnalysisStyle.None => null
        case PointerAnalysisStyle.SPDS => {
          new SQueryCache[SPDSResults](cgs, stats) {
            def query(pred: boomerang.scene.Statement, stmt: ApplyFunctionRef, ref: Val): SPDSResults = {
              val query = BackwardQuery.make(new ControlFlowGraph.Edge(pred, stmt), ref)
              val solver = new Boomerang(cgs.cg, DataFlowScope.INCLUDE_ALL, new DefaultBoomerangOptions)
              val backwardQueryResults = solver.solve(query)
              val prevAllocSites = this.cacheGet(pred, stmt, ref)
              val allocSites = backwardQueryResults.getAllocationSites
              if (prevAllocSites.nonEmpty && !prevAllocSites.get.equals(allocSites)) {
                stats.updatedCallSites += 1
              }
              if (allocSites.isEmpty) {
                stats.fruitlessQueries += 1
              } else {
                var fruitless = true
                allocSites.forEach((forwardQuery, _) => {
                  forwardQuery.`var`().asInstanceOf[AllocVal].getAllocVal match {
                    case _: SWANVal.FunctionRef => fruitless = false
                    case _: SWANVal.BuiltinFunctionRef => fruitless = false
                    case _: SWANVal.DynamicFunctionRef => fruitless = false
                    case _ =>
                  }
                })
                if (fruitless) stats.fruitlessQueries += 1
              }
              if (query.asNode().stmt().getTarget.asInstanceOf[SWANStatement.ApplyFunctionRef] != stmt) {
                throw new RuntimeException()
              }
              allocSites
            }
          }
        }
        case PointerAnalysisStyle.UFF => {
          new SQueryCache[UFFResults](cgs, stats) {
            override def query(pred: Statement, stmt: ApplyFunctionRef, ref: Val): UFFResults = {
              val results = uf.query(stmt.getFunctionRef.asInstanceOf[SWANVal])
              val prevAllocSites = this.cacheGet(pred, stmt, ref)
              val allocSites = results.filter(v => v.isNewExpr)
              if (prevAllocSites.nonEmpty && !prevAllocSites.get.equals(allocSites)) {
                stats.updatedCallSites += 1
              }
              if (allocSites.isEmpty) {
                stats.fruitlessQueries += 1
              } else {
                var fruitless = true
                allocSites.foreach {
                    case _: SWANVal.FunctionRef => fruitless = false
                    case _: SWANVal.BuiltinFunctionRef => fruitless = false
                    case _: SWANVal.DynamicFunctionRef => fruitless = false
                    case _ =>
                }
                if (fruitless) stats.fruitlessQueries += 1
              }
              allocSites
            }
          }
        }
      }
    }

    // Iterate over entry points and add them to work list
    val entryPoints = cgs.cg.getEntryPoints.asInstanceOf[java.util.Collection[SWANMethod]]
    entryPoints.forEach{ m =>
      w.addMethod(m)
      processWorklist(cgs)
    }
  }

  /**
   * Process the worklist by exhausting it and its successors... TODO
   */
  def processWorklist(cgs: CallGraphStats): Unit = {
    // Process the blocks in the worklist until it is empty.
    // When it is empty, that means we have exhausted the current entry point
    while (w.nonEmpty) {

      // c is the current block being processed
      val c = w.pop()

      // b is the current working bitset
      var b: DDGTypeSet = new DDGTypeSet(immutable.BitSet.empty)

      // Add the outsets of c's block predecessors to b.
      // We need the aggregate bitset from them.
      c.preds.foreach(pred => outSets.get(pred) match {
        case Some(outSet) => b = b.union(outSet)
        case None =>
      })

      // Process inter-prococedural predecessors.
      // We want to have the aggregate bitsets from any known callers.
      // We may not have all the callers at the moment, though.
      val method = c.method
      if (c == method.getStartBlock) {
        interProcInSets.get(method) match {
          case Some(inSet) => b = b.union(inSet)
          case None =>
        }
      }

      // Add current block to seen blocks for avoiding needless execution later.
      seen.put(c, b)

      // Process operators in the block.
      c.stmts.foreach(stmt => {
        pas match {
          case PointerAnalysisStyle.UFF =>
            uf.handleStatement(cgs.cg, stmt)
          case _ =>
        }
        stmt match {
          // If operator is an allocation, add alloc type to b.
          case SWANStatement.Allocation(_, inst, _) =>
            b = b.union(inst.result.tpe.name)

          // If operator is a call site...
          case applyStmt: SWANStatement.ApplyFunctionRef => {

            val m = c.method
            // TODO: Verify only ever one pred? Do multiple preds create problems?
            val edge = new ControlFlowGraph.Edge(m.getCFG.getPredsOf(applyStmt).iterator().next(), applyStmt)

            // Look up the function ref in the symbol table...
            m.delegate.symbolTable(applyStmt.inst.functionRef.name) match {
              // We find an operator
              case SymbolTableEntry.operator(_, operator) => {
                operator match {
                  // Trivial cases (intra-procedural)
                  case Operator.functionRef(_, name) =>
                    visitSimpleRef(name, trivial = true)
                  case Operator.builtinRef(_, name) =>
                    if (cgs.cg.methods.contains(name)) {
                      visitSimpleRef(name, trivial = true)
                    }
                  case Operator.dynamicRef(_, _, index) => {
                    val block = m.getCFG.stmtToBlock(m.getCFG.mappedStatements.get(operator).asInstanceOf[SWANStatement])
                    visitDynamicRef(index, block, queried = false)
                  }

                  // The function ref must be being used in a more interesting
                  // way (e.g., assignment). TODO: Count these cases.
                  case _ => queryRef(applyStmt)
                }
              }
              // Function ref is an argument, which means it is inter-procedural,
              // so we need to query.
              case _: SymbolTableEntry.argument => queryRef(applyStmt)
              // Function ref has multiple symbol table entries (certainly from
              // non-SSA compliant basic block argument manipulation
              // from SWIRLPass). The function ref is a basic block argument.
              case multiple: SymbolTableEntry.multiple => {
                multiple.operators.foreach {
                  // Trivial cases (intra-procedural)
                  case Operator.functionRef(_, name) =>
                    visitSimpleRef(name, trivial = true)
                  case Operator.builtinRef(_, name) =>
                    if (cgs.cg.methods.contains(name)) {
                      visitSimpleRef(name, trivial = true)
                    }
                  case operator@Operator.dynamicRef(_, _, index) => {
                    val block = m.getCFG.stmtToBlock(m.getCFG.mappedStatements.get(operator).asInstanceOf[SWANStatement])
                    visitDynamicRef(index, block, queried = false)
                  }
                  // The function ref must be being used in a more interesting
                  // way (e.g., assignment). TODO: Count these cases.
                  case _ => queryRef(applyStmt)
                }
              }
            }

            // Below are handlers for two cases: static and dynamic dispatch.
            // In both cases, once we have the target method, we add an edge
            // to the target and process it. If we have seen the target before,
            // we need to invalidate and revisit its successors because these
            // new edges may introduce new query results for function pointers.

            // Handler for simple (trivial) function references
            def visitSimpleRef(name: String, trivial: Boolean): Unit = {
              val target = cgs.cg.methods(name)
              val added = addCGEdge(m, target, applyStmt, edge, cgs)
              pas match {
                case PointerAnalysisStyle.UFF => uf.handleCGEdge(applyStmt, target)
                case _ =>
              }
              b = b.union(processTarget(target, c, b))
              if (added) {
                if (trivial) {
                  cgs.trivialCallSites += 1
                } else {
                  stats.queriedEdges += 1
                }
                if (seen.contains(target.getStartBlock)) {
                  invalidateAndRevisitSuccessors(target, cgs)
                }
              }
            }

            // Handler for dynamic (virtual) function references
            def visitDynamicRef(index: String, block: SWANBlock, queried: Boolean): Unit = {
              val instantiatedTypes = b.toHashSet
              interProcSuccessors.get(block) match {
                case Some(_) => interProcSuccessors(block).add(c)
                case None => interProcSuccessors.addOne(block, mutable.HashSet(c))
              }
              moduleGroup.ddgs.foreach(ddg => {
                val functionNames = ddg._2.query(index, Some(instantiatedTypes))
                functionNames.foreach(name => {
                  val target = cgs.cg.methods(name)
                  val added = addCGEdge(m, target, applyStmt, edge, cgs)
                  pas match {
                    case PointerAnalysisStyle.UFF => uf.handleCGEdge(applyStmt, target)
                    case _ =>
                  }
                  b = b.union(processTarget(target, c, b))
                  if (added) {
                    if (queried) {
                      stats.queriedEdges += 1
                    } else {
                      stats.virtualEdges += 1
                    }
                    if (seen.contains(target.getStartBlock)) {
                      invalidateAndRevisitSuccessors(target, cgs)
                    }
                  }
                })
              })
            }

            // Query a call-site for its function reference allocation site
            def queryRef(stmt: SWANStatement.ApplyFunctionRef): Unit = {
              val ref = stmt.getInvokeExpr.asInstanceOf[SWANInvokeExpr].getFunctionRef

              // TODO: Do multiple preds create problems?
              m.getControlFlowGraph.getPredsOf(stmt).forEach(pred => {
                val allocSites = new ArrayBuffer[SWANVal]()
                pas match {
                  case PointerAnalysisStyle.None =>
                  case ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle.SPDS => {
                    queryCache.asInstanceOf[SQueryCache[SPDSResults]].get(pred, stmt, ref).forEach((forwardQuery, _) => {
                      allocSites.addOne(forwardQuery.`var`().asInstanceOf[AllocVal].getAllocVal.asInstanceOf[SWANVal])
                    })
                  }
                  case ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle.UFF => {
                    queryCache.asInstanceOf[SQueryCache[UFFResults]].get(pred, stmt, ref).foreach(allocSites.addOne)
                  }
                }
                allocSites.foreach {
                  case v: SWANVal.FunctionRef =>
                    visitSimpleRef(v.ref, trivial = false)
                  case v: SWANVal.BuiltinFunctionRef =>
                    visitSimpleRef(v.ref, trivial = false)
                  case v: SWANVal.DynamicFunctionRef => {
                    val block = {
                      v.method.delegate.symbolTable(v.getVariableName) match {
                        case SymbolTableEntry.operator(_, operator) => {
                          v.method.getCFG.stmtToBlock(v.method.getCFG.mappedStatements.get(operator).asInstanceOf[SWANStatement])
                        }
                        case _ => throw new RuntimeException("unexpected")
                      }
                    }
                    visitDynamicRef(v.index, block, queried = true)
                  }
                  case _ => // likely partial_apply results
                  }
              })
            }
          }
          // All other statement types are ignored.
          case _ =>
        }
      })
      // Check if the outset has changed since we last visited the block
      // (if we have visited it).
      val outSetChanged = outSets.get(c) match {
        case Some(oldOutSet) => !b.subsetOf(oldOutSet)
        case None => b.nonEmpty
      }
      // Update the outset with the new one
      outSets.update(c, b)
      // If the outset has indeed changed, add the relevant successors of the
      // block to the worklist because they now have new information to work off.
      if (outSetChanged) {
        c.succs.foreach(blk => w.add(blk))
        interProcSuccessors.get(c) match {
          case Some(succs) => succs.foreach(succ => w.add(succ))
          case None =>
        }
        // If the block is an exit block, then we also need to add any return
        // site blocks as successors.
        if (c.isExitBlock) {
          returnSites.get(c.method) match {
            case Some(succs) => succs.foreach(succ => w.add(succ))
            case None =>
          }
        }
      }
    }
  }

  /**
   * Find all (transitive) successors of the method and invalidate all the
   * relevant cached information to facilitate revisting.
   */
  def invalidateAndRevisitSuccessors(startMethod: SWANMethod, cgs: CallGraphStats): Unit = {
    if (!invalidations) {
      return
    }
    val processed = mutable.HashSet.empty[SWANMethod]
    val next = mutable.HashSet.empty[SWANMethod]
    next.add(startMethod)
    while (next.nonEmpty) {
      processed.addAll(next)
      val successors = next.flatMap(m => cgs.cg.outEdgeTargets(m).diff(processed.toSeq))
      next.clear()
      next.addAll(successors)
    }

    var shortExit = true
    processed.foreach{ m =>
      m.getCFG.blocks.foreach{ case (_,blk) =>
        blk.stmts.foreach {
          case applyStmt: SWANStatement.ApplyFunctionRef => {
            if (queryCache != null && queryCache.cache.contains(applyStmt)) {
              shortExit = false
            }
          }
          case _ =>
        }
      }
    }

    if (shortExit) return

    if (processed.nonEmpty) {
      stats.recursiveInvalidations += 1
    }
    processed.foreach{ m =>
      m.getCFG.blocks.foreach{ case (_,blk) =>
        seen.remove(blk)
        outSets.remove(blk)
        w.add(blk)
        blk.stmts.foreach {
          case applyStmt: SWANStatement.ApplyFunctionRef =>
            if (queryCache != null) queryCache.invalidate(applyStmt)
          case _ =>
        }
      }
    }
  }

  /**
   * Update relevant bitsets to prepare the target for being processed
   * and add the target to the worklist.
   */
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
    // TODO: cache outsets per method
    exitBlocks.foldLeft(b)((acc,nxt) => outSets.get(nxt) match {
      case Some(outSet) => outSet.union(acc)
      case None => acc
    })
  }

}

object UCGSound {

  class UCGSoundStats() extends SpecificCallGraphStats {
    var queriedEdges: Int = 0
    var virtualEdges: Int = 0
    var totalQueries: Int = 0
    var fruitlessQueries: Int = 0
    var recursiveInvalidations: Int = 0
    var callSitesInvalidated: Int = 0
    var updatedCallSites: Int = 0
    var averageQueryTimeMs: Long = 0
    private var totalQueryTime: Long = 0

    def addQueryStat(time: Long): Unit = {
      totalQueryTime += time
      totalQueries += 1
      averageQueryTimeMs = totalQueryTime / totalQueries
    }

    override def toString: String = {
      val sb = new StringBuilder()
      sb.append("UCGSound\n")
      sb.append(s"  Queried Edges: $queriedEdges\n")
      sb.append(s"  Virtual Edges: $virtualEdges\n")
      sb.append(s"  Total Queries: $totalQueries\n")
      sb.append(s"  Fruitless Queries: $fruitlessQueries\n")
      sb.append(s"  Recursive Invalidations: $recursiveInvalidations\n")
      sb.append(s"  Call Sites Invalidated: $callSitesInvalidated\n")
      sb.append(s"  Updated Call Sites: $updatedCallSites\n")
      sb.append(s"  Average Query Time (ms): $averageQueryTimeMs\n")
      sb.toString()
    }

    override def toJSON: Value = {
      val u = ujson.Obj()
      u("ucg_queried_edges") = queriedEdges
      u("ucg_virtual_edges") = virtualEdges
      u("ucg_total_queries") = totalQueries
      u("ucg_fruitless_queries") = fruitlessQueries
      u("recursive_invalidations") = recursiveInvalidations
      u("call_sites_invalidated") = callSitesInvalidated
      u("average_query_time_ms") = averageQueryTimeMs.toInt
      u
    }
  }
}

/**
 * This cache structure is for caching queries to their
 * function reference allocation sites.
 */
abstract class SQueryCache[T](cgs: CallGraphStats, stats: UCGSoundStats) {
  val cache: mutable.HashMap[ApplyFunctionRef, mutable.HashMap[(boomerang.scene.Statement, Val), T]] =
    mutable.HashMap.empty

  protected def cacheUpdate(pred: boomerang.scene.Statement, stmt: ApplyFunctionRef, ref: Val, allocSites: T): Unit = {
    cache.get(stmt) match {
      case Some(hashMap) => {
        hashMap.update((pred, ref), allocSites)
      }
      case None =>
        val hashMap: mutable.HashMap[(boomerang.scene.Statement, Val), T] =
          mutable.HashMap.empty
        hashMap.update((pred, ref), allocSites)
        cache.update(stmt, hashMap)
    }
  }

  protected def cacheGet(pred: boomerang.scene.Statement, stmt: ApplyFunctionRef, ref: Val): Option[T] = {
    cache.get(stmt) match {
      case Some(hashMap) => {
        hashMap.get((pred, ref))
      }
      case None => None
    }
  }

  def get(pred: boomerang.scene.Statement, stmt: ApplyFunctionRef, ref: Val): T = {
    cacheGet(pred, stmt, ref) match {
      case Some(allocSites) => allocSites
      case None =>
        val startTime = System.currentTimeMillis()
        val allocSites = query(pred, stmt, ref)
        stats.addQueryStat(System.currentTimeMillis() - startTime)
        cacheUpdate(pred,stmt,ref, allocSites)
        allocSites
    }
  }

  def query(pred: boomerang.scene.Statement, stmt: ApplyFunctionRef, ref: Val): T

  def invalidate(stmt: ApplyFunctionRef): Unit = {
    if (cache.contains(stmt)) {
      stats.callSitesInvalidated += 1
      cache.remove(stmt)
    }
  }

}
