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

import boomerang.scene.{DeclaredMethod, InvokeExpr, WrappedClass}

class SWANDeclaredMethod(inv: InvokeExpr) extends DeclaredMethod(inv) {

  override def isNative: Boolean = false

  override def getSubSignature: String = ???

  override def getName: String = ???

  override def isStatic: Boolean = ???

  override def isConstructor: Boolean = ???

  override def getSignature: String = ???

  override def getDeclaringClass: WrappedClass = ???
}
