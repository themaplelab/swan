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

import boomerang.scene._
import ca.ualberta.maple.swan.ir.{CanFunction, ModuleGroup, SymbolRef, SymbolTableEntry}
import com.google.common.collect.{Lists, Sets}

import scala.collection.mutable

class SWANMethod(val delegate: CanFunction, var moduleGroup: ModuleGroup) extends Method {

  // Use allValues instead of create new Vals, when possible
  // Only use for non allocation (simple value references).
  val allValues: mutable.HashMap[String, Val] = new mutable.HashMap[String, Val]()
  val newValues: mutable.HashMap[String, Val] = new mutable.HashMap[String, Val]()

  private val localParams: util.List[Val] = Lists.newArrayList
  private val localValues: util.HashSet[Val] = Sets.newHashSet
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
    localParams.add(v)
    localValues.add(v)
    allValues.put(argument.ref.name, v)
  })

  override def isStaticInitializer: Boolean = false

  override def isParameterLocal(v: Val): Boolean = {
    localParams.contains(v)
  }

  override def isThisLocal(v: Val): Boolean = false

  override def getLocals: java.util.Set[Val] = localValues

  override def getThisLocal: Val = null

  override def getParameterLocals: util.List[Val] = localParams

  override def isStatic: Boolean = true

  override def isNative: Boolean = false

  override def getStatements: util.List[Statement] = cfg.getStatements

  override def getDeclaringClass: WrappedClass = new WrappedClass {
    override def getMethods: util.Set[Method] = ???
    override def hasSuperclass: Boolean = ???
    override def getSuperclass: WrappedClass = ???
    override def getType: Type = ???
    override def isApplicationClass: Boolean = true
    override def getFullyQualifiedName: String = ???
    override def getName: String = ???
    override def getDelegate: AnyRef = ???
  }

  override def getControlFlowGraph: ControlFlowGraph = cfg
  def getCFG: SWANControlFlowGraph = cfg

  override def getSubSignature: String = delegate.name

  override def getName: String = delegate.name

  override def isConstructor: Boolean = false

  override def isPublic: Boolean = true

  override def toString: String = {
    val sb = new StringBuilder()
    sb.append("<method name=")
    sb.append(getName)
    sb.append(">\n")
    getStatements.forEach(s => {
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
