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

package ca.ualberta.maple.swan.spds.analysis.typestate

import java.util.Objects

class Transition(val from: State, val to: State) {

  override def hashCode(): Int = Objects.hashCode(from, to)

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: Transition => Objects.equals(other.from, from) && Objects.equals(other.to, to)
      case _ => false
    }
  }

  override def toString: String = s"$from -> $to"
}

class StringTransition(val rep: String) extends Transition(null, null) {

  override def toString: String = rep

  override def hashCode(): Int = rep.hashCode

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: StringTransition => Objects.equals(other.rep, rep)
      case _ => false
    }
  }
}

object Transition {

  val identity: Transition = new StringTransition("ID -> ID")
}
