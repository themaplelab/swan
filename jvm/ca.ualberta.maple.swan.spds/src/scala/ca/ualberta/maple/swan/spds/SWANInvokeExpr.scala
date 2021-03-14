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

import boomerang.scene.{DeclaredMethod, InvokeExpr, Val}
import ca.ualberta.maple.swan.spds.SWANStatement.ApplyFunctionRef

class SWANInvokeExpr(val stmt: ApplyFunctionRef, val method: SWANMethod) extends InvokeExpr {

  val args: util.List[Val] = new util.ArrayList[Val]()

  stmt.inst.arguments.foreach(arg => {
    args.add(method.allValues(arg.name))
  })

  def getFunctionRef: Val = stmt.getFunctionRef

  override def getArg(index: Int): Val = args.get(index)

  override def getArgs: util.List[Val] = args

  override def isInstanceInvokeExpr: Boolean = false

  override def getBase: Val = ???

  override def getMethod: DeclaredMethod = null

  override def isSpecialInvokeExpr: Boolean = false

  override def isStaticInvokeExpr: Boolean = false

  override def toString: String = {
    if (getMethod == null) {
      stmt.inst.functionRef.name + "(" + args + ")"
    } else {
      getMethod.toString + "(" + args + ")"
    }
  }
}
