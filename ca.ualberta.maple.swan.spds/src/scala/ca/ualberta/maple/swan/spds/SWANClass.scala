/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.spds

import java.util

import boomerang.scene.{Method, Type, WrappedClass}

class SWANClass(val delegate: ca.ualberta.maple.swan.ir.Type) extends WrappedClass {

  override def getMethods: util.Set[Method] = {
    null
  }

  override def hasSuperclass: Boolean = {
    false
  }

  override def getSuperclass: WrappedClass = {
    null
  }

  override def getType: Type = {
    null
  }

  override def isApplicationClass: Boolean = {
    false // maybe true
  }

  override def getFullyQualifiedName: String = {
    delegate.name
  }

  override def getName: String = {
    delegate.name
  }

  override def getDelegate: AnyRef = {
    delegate
  }
}
