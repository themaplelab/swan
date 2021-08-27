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

package ca.ualberta.maple.swan.spds.analysis.wpds.impl

import java.util.Objects

import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.{Location, State}

abstract class Rule[N <: Location, D <: State, W <: Weight](var s1: D, val l1: N, val s2: D, val l2: Option[N], val w: W) {

  def getStartConfig: Configuration[N, D] = new Configuration[N, D](Some(l1), s1)

  def getTargetConfig: Configuration[N, D] = new Configuration[N, D](l2, s2)

  override def hashCode(): Int = Objects.hashCode(l1, l2, s1, s2, w)

  override def equals(obj: Any): Boolean = {
    obj match {
      case r: Rule[N, D, W] => {
        Objects.equals(l1, r.l1) && Objects.equals(l2, r.l2) && Objects.equals(s1, r.s1) &&
          Objects.equals(s2, r.s2) && Objects.equals(w, r.w)
      }
      case _ => false
    }
  }
}
