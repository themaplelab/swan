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

import boomerang.scene.{DeclaredMethod, InvokeExpr, Val, WrappedClass}
import ca.ualberta.maple.swan.spds.SWANStatement.ApplyFunctionRef

class SWANInvokeExpr(val stmt: ApplyFunctionRef, val method: SWANMethod) extends InvokeExpr {

  val args: util.List[Val] = new util.ArrayList[Val]()

  stmt.inst.arguments.zipWithIndex.foreach(arg => {
    args.add(method.addVal(SWANVal.Argument(method.delegate.getSymbol(arg._1.name), arg._2, method)))
  })

  def getFunctionRef: Val = stmt.getFunctionRef

  override def getArg(index: Int): Val = args.get(index)

  override def getArgs: util.List[Val] = args

  override def isInstanceInvokeExpr: Boolean = false

  override def getBase: Val = ???

  override def getMethod: DeclaredMethod = new DeclaredMethod(this) {
    override def isNative: Boolean = ???
    override def getSubSignature: String = ???
    override def getName: String = "" // Boomerang checks this against Java method names -.-
    override def isStatic: Boolean = ???
    override def isConstructor: Boolean = ???
    override def getSignature: String = ???
    override def getDeclaringClass: WrappedClass = ???
  }

  override def isSpecialInvokeExpr: Boolean = false

  override def isStaticInvokeExpr: Boolean = true

  override def toString: String = {
    val sb = new StringBuilder()
    sb.append("<invoke_expr>")
    sb.append(getFunctionRef.toString)
    sb.append("<args>")
    args.forEach(a => {
      sb.append("<arg>")
      sb.append(a.toString)
      sb.append("</arg>")
    })
    sb.append("</args>")
    sb.append("</invoke_expr>")
    sb.toString()
  }
}
