/*
 * Copyright (c) 2022 the SWAN project authors. All rights reserved.
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

import boomerang.scene.{ControlFlowGraph, Val}
import ca.ualberta.maple.swan.ir.{ModuleGroup, Operator, SymbolTableEntry}
import ca.ualberta.maple.swan.spds.cg.CallGraphConstructor.Options
import ca.ualberta.maple.swan.spds.cg.CallGraphUtils.addCGEdge
import ca.ualberta.maple.swan.spds.structures.{SWANInvokeExpr, SWANStatement, SWANVal}

import scala.collection.mutable
import scala.jdk.CollectionConverters.ListHasAsScala

abstract class TypeFlowCG(mg: ModuleGroup, options: Options) extends TrivialEdges(mg, options: Options) {
  private var typeFlow: pa.TypeFlow = _
  private var stats: TypeFlowCGStats = _

  def initializeTypeFlow(tf: pa.TypeFlow): Unit = {
    typeFlow = tf
  }
  def initializeTypeFlowStats(s: TypeFlowCGStats): Unit = {
    stats = s
  }

  def addTypeFlowEdges(): Unit = {
    methods.foreach { case (_, m) =>
      m.applyFunctionRefs.foreach {
        case stmt@SWANStatement.ApplyFunctionRef(opDef, inst, _m) =>
          val predEdge = new ControlFlowGraph.Edge(m.getCFG.getPredsOf(stmt).iterator().next(), stmt)

          m.delegate.symbolTable(inst.functionRef.name) match {
            // regular entry
            case SymbolTableEntry.operator(_, operator) =>
              handleOperator(operator,stmt,predEdge)
            // Function ref has multiple symbol table entries (certainly from
            // non-SSA compliant basic block argument manipulation
            // from SWIRLPass). The function ref is a basic block argument.
            case SymbolTableEntry.multiple(_, operators) => operators.foreach{ operator =>
              handleOperator(operator,stmt,predEdge)
            }
            // Function ref is an argument, which means it is inter-procedural,
            // so we need to use pointer analysis.
            case _: SymbolTableEntry.argument => typeFlowRef(stmt, predEdge)
          }
      }
    }
  }

  private def addDDGEdges(stmt: SWANStatement.ApplyFunctionRef, predEdge: ControlFlowGraph.Edge, index: String, instanTypes: mutable.HashSet[String]): Unit = {
    moduleGroup.ddgs.foreach{ case (_,ddg) =>
      ddg.query(index,Some(instanTypes)).foreach{ fn =>
        val tgt = methods(fn)
        if (addCGEdge(stmt.m,tgt,stmt,predEdge,cgs)) stats.ddgEdges += 1
      }
    }
  }

  private def typeFlowRef(stmt: SWANStatement.ApplyFunctionRef, predEdge: ControlFlowGraph.Edge): Unit = {
    stats.typeFlowRefs += 1

    val ref: Val = stmt.getInvokeExpr.asInstanceOf[SWANInvokeExpr].getFunctionRef
    val swanRef: SWANVal = ref.asInstanceOf[SWANVal]
    val types = typeFlow.getValTypes(swanRef)

    if (types.isEmpty) stats.emptyTypeFlowTypes += 1

    types.foreach{
      case SWANVal.FunctionRef(delegate, ref, method, unbalanced) =>
        val target = methods(ref)
        if (addCGEdge(from = stmt.m, to = target, stmt, predEdge, cgs)) stats.ptEdges += 1
      case SWANVal.BuiltinFunctionRef(delegate, ref, method, unbalanced) =>
        val target = methods(ref)
        if (addCGEdge(from = stmt.m, to = target, stmt, predEdge, cgs)) stats.ptEdges += 1
      case SWANVal.DynamicFunctionRef(delegate, index, method, unbalanced) =>
        addDDGEdges(stmt, predEdge, index, getRecieverTypes(stmt))
      case _: SWANVal.NewExpr => // dealt with via instantiated types
      case _: SWANVal.Simple | _: SWANVal.Constant => // ignore simple or constant
    }
  }

  private def getRecieverTypes(stmt: SWANStatement.ApplyFunctionRef): mutable.HashSet[String] = {
    val ie = stmt.getInvokeExpr
    if (!ie.getArgs.isEmpty) {
      val reciever = ie.getArgs.asScala.last.asInstanceOf[SWANVal]
      val types = typeFlow.getValTypes(reciever)
      mutable.HashSet.from(types.collect { case neww: SWANVal.NewExpr => neww.delegate.tpe.name })
    }
    else {
      mutable.HashSet.empty[String]
    }
  }

  private def handleOperator(operator: Operator, stmt: SWANStatement.ApplyFunctionRef, predEdge: ControlFlowGraph.Edge): Unit = {
    operator match {
      case Operator.dynamicRef(_, _, index) =>
        addDDGEdges(stmt, predEdge, index, getRecieverTypes(stmt))
      case _: Operator.builtinRef | _: Operator.functionRef => // already done via trivial edges pass
      // The function ref must be being used in a more interesting
      // way (e.g., assignment).
      case _ => typeFlowRef(stmt, predEdge)
    }
  }

}

trait TypeFlowCGStats {
  var ptEdges: Int = 0
  var ddgEdges: Int = 0
  var typeFlowRefs: Int = 0
  var emptyTypeFlowTypes: Int = 0
}