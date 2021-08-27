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

class NodeWithLocation[Stmt, Fact, Location](val stmt: Stmt, val variable: Fact, val location: Location) extends INode[Node[Stmt, Fact]] {

  private val f: Node[Stmt, Fact] = new Node(stmt, variable)

  override def fact(): Node[Stmt, Fact] = f

  override def hashCode: Int = Objects.hashCode(f, location)

  override def equals(obj: Any): Boolean = {
    obj match {
      case n: NodeWithLocation[Stmt, Fact, Location] => Objects.equals(location, n.location) && Objects.equals(f, n.f)
      case _ => false
    }
  }

  override def toString: String = s"$f loc: $location"
}
