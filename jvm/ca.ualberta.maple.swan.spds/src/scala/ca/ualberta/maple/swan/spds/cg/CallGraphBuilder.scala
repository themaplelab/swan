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

package ca.ualberta.maple.swan.spds.cg

import ca.ualberta.maple.swan.ir.ModuleGroup

object CallGraphBuilder {

  def createCallGraph(moduleGroup: ModuleGroup, style: CallGraphStyle.Style): CallGraphUtils.CallGraphData = {
    val cgBuilder = {
      style match {
        case CallGraphStyle.CHA => new CHA(moduleGroup, PointerAnalysisStyle.None)
        case CallGraphStyle.PRTA => new PRTA(moduleGroup, PointerAnalysisStyle.None)
      }
    }
    cgBuilder.buildCallGraph()
  }

  object CallGraphStyle extends Enumeration {
    type Style = Value

    val CHA: CallGraphStyle.Value = Value
    val PRTA: CallGraphStyle.Value = Value
  }

  object PointerAnalysisStyle extends Enumeration {
    type Style = Value

    val None: PointerAnalysisStyle.Value = Value
    val SWPA: PointerAnalysisStyle.Value = Value
    val SOD: PointerAnalysisStyle.Value = Value
    val SPDS: PointerAnalysisStyle.Value = Value
    val VTA: PointerAnalysisStyle.Value = Value
  }
}
