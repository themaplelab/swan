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

import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.{Location, State}

class NormalRule[N <: Location, D <: State, W <: Weight](s1: D, l1: N, s2: D, l2: N, w: W) extends Rule[N, D, W](s1, l1, s2, Some(l2), w) {

  override def toString: String = {
    s"<$s1;$l1>-><$s2;$l2>${if (w.isInstanceOf[Weight.NoWeight]) "" else s"($w)"}"
  }

  def canBeApplied(t: Transition[N, D], weight: W) = true
}
