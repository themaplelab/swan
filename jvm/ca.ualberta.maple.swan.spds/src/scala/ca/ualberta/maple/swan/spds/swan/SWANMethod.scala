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

package ca.ualberta.maple.swan.spds.swan

import java.util

import ca.ualberta.maple.swan.ir.{CanFunction, ModuleGroup, SymbolTableEntry}
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{ControlFlowGraph, Method, Statement, Val}
import com.google.common.collect.{Lists, Sets}

import scala.collection.mutable

class SWANMethod(val delegate: CanFunction, var moduleGroup: ModuleGroup) extends Method {

  // Use allValues instead of create new Vals, when possible
  // Only use for non allocation (simple value references).
  val allValues: mutable.HashMap[String, Val] = new mutable.HashMap[String, Val]()
  val newValues: mutable.HashMap[String, Val] = new mutable.HashMap[String, Val]()

  private val localParams: mutable.ArrayBuffer[Val] = mutable.ArrayBuffer.empty
  private val localValues: mutable.HashSet[Val] = mutable.HashSet.empty
  private val cfg: SWANControlFlowGraph = new SWANControlFlowGraph(this)

  def hasSwirlSource: Boolean = moduleGroup.swirlSourceMap.nonEmpty

  def swirlLineNum(o: Object): Int = moduleGroup.swirlSourceMap.get(o)._1

  def addVal[T<:Val](v: T): T = {
    localValues.add(v)
    v
  }

  delegate.symbolTable.foreach(sym => {
    sym._2 match {
      case SymbolTableEntry.operator(symbol, _) => {
        val v = SWANVal.Simple(symbol, this)
        localValues.add(v)
        allValues.put(symbol.ref.name, v)
        val n = SWANVal.NewExpr(symbol, this)
        localValues.add(n)
        newValues.put(symbol.ref.name, n)
      }
      case SymbolTableEntry.multiple(symbol, operators) => {
        val v = SWANVal.Simple(symbol, this)
        localValues.add(v)
        allValues.put(symbol.ref.name, v)
        val n = SWANVal.NewExpr(symbol, this)
        localValues.add(n)
        newValues.put(symbol.ref.name, n)
      }
      case _ =>
    }
  })

  delegate.arguments.foreach(argument => {
    val v = SWANVal.Simple(argument, this)
    localParams.addOne(v)
    localValues.add(v)
    allValues.put(argument.ref.name, v)
  })

  override def isParameterLocal(v: Val): Boolean = {
    localParams.contains(v)
  }

  override def getLocals: mutable.HashSet[Val] = localValues

  override def getParameterLocals: mutable.ArrayBuffer[Val] = localParams

  override def getStatements: mutable.ArrayBuffer[Statement] = cfg.getStatements

  override def getCFG: SWANControlFlowGraph = cfg

  override def getName: String = delegate.name

  override def toString: String = {
    val sb = new StringBuilder()
    sb.append("<method name=")
    sb.append(getName)
    sb.append(">\n")
    getStatements.foreach(s => {
      sb.append(s.toString)
      sb.append("\n")
    })
    sb.append("</method>\n")
    sb.toString()
  }

  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + delegate.hashCode
    result
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case m: SWANMethod => m.delegate == this.delegate
      case _ => false
    }
  }
}
