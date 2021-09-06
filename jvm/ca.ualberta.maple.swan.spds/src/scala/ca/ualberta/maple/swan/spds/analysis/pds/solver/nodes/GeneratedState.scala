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

package ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes

import java.util.Objects

class GeneratedState[L, N](val node: INode[L], val location: N) extends INode[L] {

  override def fact: L = node.fact

  override def hashCode: Int = Objects.hashCode(location, node)

  override def equals(obj: Any): Boolean = {
    obj match {
      case s: GeneratedState[L, N] => {
        Objects.equals(location, s.location) && Objects.equals(node, s.node)
      }
      case _ => false
    }
  }

  override def toString = s"$node $location"
}
