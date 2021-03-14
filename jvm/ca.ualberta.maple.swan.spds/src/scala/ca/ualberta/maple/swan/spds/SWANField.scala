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

import boomerang.scene.Field

class SWANField(val name: String) extends Field {
  override def toString: String = {
    name
  }
  override def hashCode: Int = {
    val prime = 31
    var result = 1
    result = prime * result + name.hashCode
    result
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case f: SWANField => f.name == this.name
      case _ => false
    }
  }
}
