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
import ca.ualberta.maple.swan.ir.Operator

class SWANInvokeExpr(val delegate: Operator.apply, val method: SWANMethod) extends InvokeExpr {

  val args: util.List[Val] = util.Collections.emptyList

  delegate.arguments.foreach(arg => {
    args.add(method.allValues(arg.name))
  })

  override def getArg(index: Int): Val = args.get(index)

  override def getArgs: util.List[Val] = args

  override def isInstanceInvokeExpr: Boolean = true

  override def getBase: Val = ???

  override def getMethod: DeclaredMethod = null

  override def isSpecialInvokeExpr: Boolean = false

  override def isStaticInvokeExpr: Boolean = false
}
