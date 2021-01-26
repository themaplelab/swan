/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2021 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.spds

import com.google.common.collect.HashMultimap
import com.google.common.collect.Lists
import com.google.common.collect.Multimap
import com.google.common.collect.Maps
import java.util

import boomerang.scene.{ControlFlowGraph, Statement}
import ca.ualberta.maple.swan.ir.InstructionDef

class SWANControlFlowGraph(val method: SWANMethod) extends ControlFlowGraph {

  // Map from OperatorDef or TerminatorDef to Statement
  private val mappedStatements: util.HashMap[Object, Statement] = Maps.newHashMap

  private val startPointCache: util.List[Statement] = Lists.newArrayList
  private val endPointCache: util.List[Statement] = Lists.newArrayList
  private val succsOfCache: Multimap[Statement, Statement] = HashMultimap.create
  private val predsOfCache: Multimap[Statement, Statement] = HashMultimap.create
  private val statements: java.util.List[Statement] = Lists.newArrayList

  method.delegate.f.blocks.foreach(b => {
    b.operators.foreach(op => {
      val statement = new SWANStatement(InstructionDef.operator(op), method)
      mappedStatements.put(op, statement)
      statements.add(statement)
    })
    val termStatement = new SWANStatement(InstructionDef.terminator(b.terminator), method)
    mappedStatements.put(b.terminator, termStatement)
    statements.add(termStatement)
  })

  startPointCache.add(
    mappedStatements.get(method.delegate.f.blocks(0).operators(0)))

  method.delegate.f.blocks.foreach(b => {
    var prev: Statement = null
    b.operators.foreach(op => {
      val curr = mappedStatements.get(op)
      if (prev != null) {
        succsOfCache.put(prev, curr)
        predsOfCache.put(curr, prev)
      }
      prev = curr
    })
    val term = mappedStatements.get(b.terminator)
    if (prev != null) {
      succsOfCache.put(prev, term)
      predsOfCache.put(term, prev)
    }
    method.delegate.cfg.outgoingEdgesOf(b).forEach(e => {
      val target = method.delegate.cfg.getEdgeTarget(e)
      if (target.blockRef.label == "EXIT") {
        endPointCache.add(term)
      } else {
        val targetStatement = mappedStatements.get({
          if (target.operators.nonEmpty) {
            target.operators(0)
          } else {
            target.terminator
          }
        })
        succsOfCache.put(term, targetStatement)
        predsOfCache.put(targetStatement, term)
      }
    })
  })

  override def getStartPoints: util.Collection[Statement] = startPointCache

  override def getEndPoints: util.Collection[Statement] = endPointCache

  override def getSuccsOf(statement: Statement): util.Collection[Statement] = succsOfCache.get(statement)

  override def getPredsOf(statement: Statement): util.Collection[Statement] = predsOfCache.get(statement)

  override def getStatements: java.util.List[Statement] = statements
}
