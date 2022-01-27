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
import boomerang.scene.{ControlFlowGraph, DataFlowScope, Val}
import boomerang.{BackwardQuery, Boomerang, DefaultBoomerangOptions, ForwardQuery}
import ca.ualberta.maple.swan.ir.FunctionAttribute
import ca.ualberta.maple.swan.spds.cg.UCG.UCGStats
import ca.ualberta.maple.swan.spds.structures.SWANControlFlowGraph.SWANBlock
import ca.ualberta.maple.swan.spds.structures.SWANStatement.ApplyFunctionRef
import ca.ualberta.maple.swan.spds.structures.{SWANCallGraph, SWANMethod, SWANStatement}
import ca.ualberta.maple.swan.utils.Logging

import java.util
import scala.collection.{immutable, mutable}

// The Depth First Worklist is a stack, and not a queue
// The Depth First Worklist will ignore blocks already in the worklist
final class DFWorklist {
  private val hs: mutable.HashSet[SWANBlock] = new mutable.HashSet[SWANBlock]()
  private val stack: mutable.Stack[SWANBlock] = new mutable.Stack[SWANBlock]()

  def add(b: SWANBlock): Unit = {
    if (!hs.contains(b) && !CallGraphUtils.isUninteresting(b.method)) {
      hs.add(b)
      stack.push(b)
    }
  }

  def addMethod(m: SWANMethod): Unit = {
    // TODO move this to traverse method
    if (m.delegate.attribute.nonEmpty && m.delegate.attribute.get == FunctionAttribute.stub) {
      return
    }

    if (m.getName.startsWith("closure ") || m.getName.startsWith("reabstraction thunk")) {
      return
    }

    // TODO revert hack
    m.getCFG.blocks.foreach{ case (_,blk) => add(blk)}
    //add(m.getStartBlock)
  }

  def isEmpty: Boolean = {
    stack.isEmpty
  }

  def nonEmpty: Boolean = {
    stack.nonEmpty
  }

  def pop(): SWANBlock = {
    val b = stack.pop()
    hs.remove(b)
    b
  }

  def next(): Option[SWANBlock] = {
    if (stack.isEmpty) {
      None
    }
    else {
      Some (pop())
    }
  }

}

final class DDGBitSet(val bitset: immutable.BitSet)(
  private implicit val ddgTypes: mutable.HashMap[String,Int],
  private implicit val ddgTypesInv: mutable.ArrayBuffer[String]) {

  def +(elem: String): DDGBitSet = {
    ddgTypes.get(elem) match {
      case Some(n) => new DDGBitSet(bitset + n)
      case None => this
    }
  }

  def union(that: DDGBitSet): DDGBitSet = {
    new DDGBitSet(bitset.union(that.bitset))
  }

  def union(elem: String): DDGBitSet = {
    ddgTypes.get(elem) match {
      case Some(n) => new DDGBitSet(bitset + n)
      case None => this
    }
  }

  def contains(s: String): Boolean = {
    ddgTypes.get(s) match {
      case Some(n) => bitset.contains(n)
      case None => false
    }
  }

  def toHashSet: mutable.HashSet[String] = {
    mutable.HashSet.from(bitset.map(n => ddgTypesInv(n)))
  }

  def subsetOf(that: DDGBitSet): Boolean = {
    bitset.subsetOf(that.bitset)
  }

  def nonEmpty: Boolean = {
    bitset.nonEmpty
  }

}

final class QueryCache(cg: SWANCallGraph, cgs: UCGStats) {
  private val cache: mutable.HashMap[(boomerang.scene.Statement, ApplyFunctionRef, Val), util.Map[ForwardQuery, AbstractBoomerangResults.Context]] =
    mutable.HashMap.empty[(boomerang.scene.Statement, ApplyFunctionRef, Val), util.Map[ForwardQuery, AbstractBoomerangResults.Context]]

  def get(pred: boomerang.scene.Statement, stmt: ApplyFunctionRef, ref: Val): util.Map[ForwardQuery, AbstractBoomerangResults.Context] = {
    cache.get(pred, stmt, ref) match {
      case Some(allocSites) => allocSites
      case None =>
        val allocSites = query(pred, stmt, ref)
        cache.update((pred,stmt,ref), allocSites)
        allocSites
    }
  }

  private def query(pred: boomerang.scene.Statement, stmt: ApplyFunctionRef, ref: Val): util.Map[ForwardQuery, AbstractBoomerangResults.Context] = {
    val query = BackwardQuery.make(new ControlFlowGraph.Edge(pred, stmt), ref)
    val solver = new Boomerang(cg, DataFlowScope.INCLUDE_ALL, new DefaultBoomerangOptions)
    val backwardQueryResults = solver.solve(query)
    cgs.totalQueries += 1
    val allocSites = backwardQueryResults.getAllocationSites
    if (allocSites.isEmpty) {
      cgs.fruitlessQueries += 1
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