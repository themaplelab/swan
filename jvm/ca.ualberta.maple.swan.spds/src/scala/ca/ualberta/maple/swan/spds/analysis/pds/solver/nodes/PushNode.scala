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

import ca.ualberta.maple.swan.spds.analysis.pds.solver.SyncPDSSolver.PDSSystem

class PushNode[Stmt, Fact, Location](stmt: Stmt, variable: Fact,
                                     val location: Location,
                                     val system: PDSSystem) extends Node[Stmt, Fact](stmt, variable) {

  override def hashCode(): Int = Objects.hashCode(location)

  override def equals(obj: Any): Boolean = {
    obj match {
      case n: PushNode[Stmt, Fact, Location] => Objects.equals(location, n.location) && super.equals(n)
      case _ => false
    }
  }

  override def toString: String = s"${super.toString} Push $location"
}
