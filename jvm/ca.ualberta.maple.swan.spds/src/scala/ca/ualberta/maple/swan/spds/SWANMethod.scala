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

import boomerang.scene._
import ca.ualberta.maple.swan.ir.{CanFunction, SymbolTableEntry}
import com.google.common.collect.{Lists, Sets}

import scala.collection.mutable

class SWANMethod(val delegate: CanFunction) extends Method {

  // Use allValues instead of create new Vals, when possible
  // Only use for non allocation (simple value references).
  val allValues: mutable.HashMap[String, Val] = new mutable.HashMap[String, Val]()
  val newValues: mutable.HashMap[String, Val] = new mutable.HashMap[String, Val]()

  private val localParams: util.List[Val] = Lists.newArrayList
  private val localValues: util.Set[Val] = Sets.newHashSet
  private val cfg: SWANControlFlowGraph = new SWANControlFlowGraph(this)

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
      case SymbolTableEntry.argument(argument) => {
        val v = SWANVal.Argument(argument, localParams.size(), this)
        localParams.add(v)
        allValues.put(argument.ref.name, v)
      }
    }
  })

  override def isStaticInitializer: Boolean = false

  override def isParameterLocal(v: Val): Boolean = {
    localParams.contains(v)
  }

  override def isThisLocal(v: Val): Boolean = false

  override def getLocals: java.util.Set[Val] = localValues

  override def getThisLocal: Val = null

  override def getParameterLocals: util.List[Val] = localParams

  override def isStatic: Boolean = false

  override def isNative: Boolean = false

  override def getStatements: util.List[Statement] = cfg.getStatements

  override def getDeclaringClass: WrappedClass = null

  override def getControlFlowGraph: ControlFlowGraph = cfg

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
