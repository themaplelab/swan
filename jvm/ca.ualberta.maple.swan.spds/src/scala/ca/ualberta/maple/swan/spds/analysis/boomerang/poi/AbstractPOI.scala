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

package ca.ualberta.maple.swan.spds.analysis.boomerang.poi

import java.util.Objects

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge

abstract class AbstractPOI[S, V, F](val cfgEdge: Edge, val baseVar: V, val field: F, val storedVar: V) extends PointOfIndirection {

  override def getCfgEdge: ControlFlowGraph.Edge = cfgEdge

  override def hashCode(): Int = Objects.hashCode(field, baseVar, storedVar, cfgEdge)

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: AbstractPOI[S, V, F] => {
        Objects.equals(other.cfgEdge, cfgEdge) && Objects.equals(other.baseVar, baseVar) &&
          Objects.equals(other.field, field) && Objects.equals(other.storedVar, storedVar)
      }
      case _ => false
    }
  }

  override def toString: String = s"POI:$cfgEdge"
}
