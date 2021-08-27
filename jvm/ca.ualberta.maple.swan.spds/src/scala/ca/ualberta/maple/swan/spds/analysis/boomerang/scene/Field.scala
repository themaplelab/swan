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

package ca.ualberta.maple.swan.spds.analysis.boomerang.scene

import java.util.Objects

import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.{Empty, Location}
import ca.ualberta.maple.swan.spds.analysis.wpds.wildcard.{ExclusionWildcard, Wildcard}

abstract class Field extends Location {

  override def accepts(other: Location): Boolean = this.equals(other)
}

object Field {

  def wildcard: Field = new WildcardField

  def exclusionWildcard(exclusion: Field): Field = new ExclusionWildcardField(exclusion)

  def empty: Field = new EmptyField("{}")

  def epsilon: Field = new EmptyField("eps_f")
}

class EmptyField(protected val rep: String) extends Field with Empty {

  override def toString: String = rep

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: EmptyField => Objects.equals(other.rep, rep)
      case _ => false
    }
  }
}

class WildcardField extends Field with Wildcard {

  override def toString: String = "*"

  override def equals(obj: Any): Boolean = {
    obj match {
      case _: WildcardField => true
      case _ => false
    }
  }
}

class ExclusionWildcardField(protected val excl: Field) extends Field with ExclusionWildcard[Field] {

  override def excludes: Field = excl

  override def toString: String = s"not $excl"

  override def hashCode(): Int = Objects.hashCode(excl)

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: ExclusionWildcardField => Objects.equals(other.excl, excl)
      case _ => false
    }
  }
}