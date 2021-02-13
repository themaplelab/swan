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

import com.google.common.collect.{Lists, Sets}
import boomerang.scene.{ControlFlowGraph, Method, Statement, Val, WrappedClass}
import ca.ualberta.maple.swan.ir.{CanFunction, SymbolTableEntry}

class SWANMethod(val delegate: CanFunction) extends Method {

  private val localParams: util.List[Val] = Lists.newArrayList
  private val localValues: util.Set[Val] = Sets.newHashSet
  private val cfg: SWANControlFlowGraph = new SWANControlFlowGraph(this)

  delegate.symbolTable.foreach(sym => {
    sym._2 match {
      case SymbolTableEntry.operator(symbol, _) => {
        localValues.add(new SWANVal(symbol))
      }
      case SymbolTableEntry.argument(argument) => {
        localParams.add(new SWANVal(argument))
      }
    }
  })

  override def isStaticInitializer: Boolean = false

  override def isParameterLocal(v: Val): Boolean = {
    delegate.symbolTable.contains(v.getVariableName)
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
}
