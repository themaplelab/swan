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

import java.util

import boomerang.scene.{ControlFlowGraph, Statement}
import ca.ualberta.maple.swan.ir.{Constants, Operator, Terminator}
import com.google.common.collect.{HashMultimap, Lists, Maps, Multimap}

class SWANControlFlowGraph(val method: SWANMethod) extends ControlFlowGraph {

  // Map from OperatorDef or TerminatorDef to Statement
  private val mappedStatements: util.HashMap[Object, Statement] = Maps.newHashMap

  private val startPointCache: util.List[Statement] = Lists.newArrayList
  private val endPointCache: util.List[Statement] = Lists.newArrayList
  private val succsOfCache: Multimap[Statement, Statement] = HashMultimap.create
  private val predsOfCache: Multimap[Statement, Statement] = HashMultimap.create
  private val statements: java.util.List[Statement] = Lists.newArrayList

  method.delegate.blocks.foreach(b => {
    b.operators.foreach(op => {
      val statement: SWANStatement = {
        op.operator match {
          case operator: Operator.neww => SWANStatement.Allocation(op, operator, method)
          case operator: Operator.assign => SWANStatement.Assign(op, operator, method)
          case operator: Operator.literal => SWANStatement.Literal(op, operator, method)
          case operator: Operator.dynamicRef => SWANStatement.DynamicFunctionRef(op, operator, method)
          case operator: Operator.builtinRef => SWANStatement.BuiltinFunctionRef(op, operator, method)
          case operator: Operator.functionRef => SWANStatement.FunctionRef(op, operator, method)
          case operator: Operator.apply => SWANStatement.ApplyFunctionRef(op, operator, method)
          case operator: Operator.singletonRead => SWANStatement.StaticFieldLoad(op, operator, method)
          case operator: Operator.singletonWrite => SWANStatement.StaticFieldStore(op, operator, method)
          case operator: Operator.fieldRead => SWANStatement.FieldLoad(op, operator, method)
          case operator: Operator.fieldWrite => SWANStatement.FieldWrite(op, operator, method)
          case operator: Operator.unaryOp =>  SWANStatement.UnaryOperation(op, operator, method)
          case operator: Operator.binaryOp => SWANStatement.BinaryOperation(op, operator, method)
          case operator: Operator.condFail => SWANStatement.ConditionalFatalError(op, operator, method)
        }
      }
      mappedStatements.put(op, statement)
      statements.add(statement)
    })
    val termStatement: SWANStatement = {
      val term = b.terminator
      b.terminator.terminator match {
        case terminator: Terminator.br_can => SWANStatement.Branch(term, terminator, method)
        case terminator: Terminator.brIf_can => SWANStatement.ConditionalBranch(term, terminator, method)
        case terminator: Terminator.ret => SWANStatement.Return(term, terminator, method)
        case terminator: Terminator.thro => SWANStatement.Throw(term, terminator, method)
        case Terminator.unreachable => SWANStatement.Unreachable(term, method)
        case terminator: Terminator.yld => SWANStatement.Yield(term, terminator, method)
      }
    }
    mappedStatements.put(b.terminator, termStatement)
    statements.add(termStatement)
  })

  startPointCache.add(
    mappedStatements.get(method.delegate.blocks(0).operators(0)))

  method.delegate.blocks.foreach(b => {
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
      if (target.blockRef.label == Constants.exitBlock) {
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
