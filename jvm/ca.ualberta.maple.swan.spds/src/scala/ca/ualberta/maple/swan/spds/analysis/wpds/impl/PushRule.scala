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

class PushRule[N <: Location, D <: State, W <: Weight](s1: D, l1: N, s2: D, l2: N, val callSite: N, w: W) extends Rule[N, D, W](s1, l1, s2, Some(l2), w) {

  override def hashCode(): Int = Objects.hashCode(callSite) + super.hashCode()

  override def equals(obj: Any): Boolean = {
    obj match {
      case r: PushRule[N, D, W] => Objects.equals(callSite, r.callSite) && super.equals(r)
      case _ => false
    }
  }

  override def toString: String = {
    s"<$s1;$l1>-><$s2;$l2.$callSite>($w)"
  }
}
