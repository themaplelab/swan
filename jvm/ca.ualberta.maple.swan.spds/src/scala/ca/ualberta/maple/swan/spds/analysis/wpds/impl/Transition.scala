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

import ca.ualberta.maple.swan.spds.analysis.pathexpression.Edge
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces._
import ca.ualberta.maple.swan.spds.analysis.wpds.wildcard.Wildcard

class Transition[N <: Location, D <: State](val start: D, val label: N, val target: D) extends Edge[D, N] {

  if (start == null || label == null || target == null) {
    throw new RuntimeException("Illegal transition")
  }

  if (label.isInstanceOf[Wildcard]) throw new RuntimeException("No wildcards allowed")

  override def getStart: D = start

  override def getTarget: D = target

  override def getLabel: N = label

  def getStartConfig: Configuration[N, D] = new Configuration(Some(label), start)

  override def hashCode: Int = Objects.hashCode(start, label, target)

  override def equals(obj: Any): Boolean = {
    obj match {
      case t: Transition[N, D] => {
        Objects.equals(start, t.start) && Objects.equals(label, t.label) && Objects.equals(target, t.target)
      }
      case _ => false
    }
  }

  override def toString: String = s"$start~$label~$target"
}
