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

import boomerang.scene.{ControlFlowGraph, Statement}
import ca.ualberta.maple.swan.ir
import ca.ualberta.maple.swan.ir.{CanOperatorDef, Constants, Operator, SymbolRef, Terminator, Type}
import com.google.common.collect.{HashMultimap, Lists, Maps, Multimap}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class SWANControlFlowGraph(val method: SWANMethod) extends ControlFlowGraph {

  // Map from OperatorDef or TerminatorDef to Statement
  private val mappedStatements: util.HashMap[Object, Statement] = Maps.newHashMap

  private val startPointCache: util.List[Statement] = Lists.newArrayList
  private val endPointCache: util.List[Statement] = Lists.newArrayList
  private val succsOfCache: Multimap[Statement, Statement] = HashMultimap.create
  private val predsOfCache: Multimap[Statement, Statement] = HashMultimap.create
  private val statements: java.util.List[Statement] = Lists.newArrayList

  val blocks = new mutable.HashMap[SWANStatement, (ArrayBuffer[SWANStatement], String)]

  {
    // TOD0: dedicated NOP instruction?
    val nopSymbol = new ir.Symbol(new SymbolRef("nop"), new Type("Any"))
    val opDef = new CanOperatorDef(Operator.neww(nopSymbol), None)
    val firstBlock = method.delegate.blocks(0)
    val startInst = if (firstBlock.operators.isEmpty) firstBlock.terminator else firstBlock.operators(0)
    val srcMap = method.moduleGroup.swirlSourceMap
    if (srcMap.nonEmpty) {
      srcMap.get.put(opDef, srcMap.get(startInst))
    }
    firstBlock.operators.insert(0, opDef)
    method.allValues.put("nop", SWANVal.Simple(nopSymbol, method))
    method.newValues.put("nop", SWANVal.NewExpr(nopSymbol, method))
  }

  method.delegate.blocks.foreach(b => {
    var startStatement: SWANStatement = null
    val blockStatements = new ArrayBuffer[SWANStatement]()
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
          case operator: Operator.condFail => SWANStatement.ConditionalFatalError(op, operator, method)
        }
      }
      if (startStatement == null) startStatement = statement
      blockStatements.append(statement)
      mappedStatements.put(op, statement)
      statements.add(statement)
    })
    val termStatement: SWANStatement = {
      val term = b.terminator
      val statement = b.terminator.terminator match {
        case terminator: Terminator.br_can => SWANStatement.Branch(term, terminator, method)
        case terminator: Terminator.brIf_can => SWANStatement.ConditionalBranch(term, terminator, method)
        case terminator: Terminator.ret => SWANStatement.Return(term, terminator, method)
        case terminator: Terminator.thro => SWANStatement.Throw(term, terminator, method)
        case Terminator.unreachable => SWANStatement.Unreachable(term, method)
        case terminator: Terminator.yld => SWANStatement.Yield(term, terminator, method)
      }
      if (startStatement == null) startStatement = statement
      blockStatements.append(statement)
      statement
    }
    blocks.put(startStatement, (blockStatements, b.blockRef.label))
    mappedStatements.put(b.terminator, termStatement)
    statements.add(termStatement)
  })

  if (method.delegate.blocks(0).operators.nonEmpty){
    startPointCache.add(
      mappedStatements.get(method.delegate.blocks(0).operators(0)))
  } else {
    startPointCache.add(
      mappedStatements.get(method.delegate.blocks(0).terminator))
  }

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
