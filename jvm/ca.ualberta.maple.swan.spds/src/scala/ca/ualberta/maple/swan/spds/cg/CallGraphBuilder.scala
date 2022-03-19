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
import ca.ualberta.maple.swan.spds.Stats.CallGraphStats
import ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle

object CallGraphBuilder {

  def createCallGraph(moduleGroup: ModuleGroup,
                      cgStyle: CallGraphStyle.Style,
                      paStyleOpt: Option[PointerAnalysisStyle.Style],
                      options: CallGraphConstructor.Options): CallGraphStats = {
    val paStyle = paStyleOpt.getOrElse(defaultPAStyle(cgStyle))
    val cgBuilder = {
      cgStyle match {
        case CallGraphStyle.CHA => new CHA(moduleGroup, paStyle, options)
        case CallGraphStyle.PRTA => new PRTA(moduleGroup, paStyle, options)
        case CallGraphStyle.VTA => new VTA(moduleGroup, paStyle, options)
        case CallGraphStyle.UCG => new UCG(moduleGroup, paStyle, true, options)
        case CallGraphStyle.ORTA => new ORTA(moduleGroup, paStyle, options)
      }
    }
    cgBuilder.buildCallGraph(cgStyle)
  }

  def defaultPAStyle(callGraphStyle: CallGraphStyle.Style): PointerAnalysisStyle.Value = callGraphStyle match {
    case CallGraphStyle.CHA => PointerAnalysisStyle.None
    case CallGraphStyle.PRTA => PointerAnalysisStyle.None
    case CallGraphStyle.ORTA => PointerAnalysisStyle.None
    case CallGraphStyle.VTA => PointerAnalysisStyle.None
    case CallGraphStyle.UCG => PointerAnalysisStyle.SPDS
  }

  def createCallGraph(moduleGroup: ModuleGroup, cgStyle: CallGraphStyle.Style, options: CallGraphConstructor.Options): CallGraphStats = {
    createCallGraph(moduleGroup, cgStyle, None, options)
  }

  def createCallGraph(moduleGroup: ModuleGroup, cgStyle: CallGraphStyle.Style): CallGraphStats = {
    createCallGraph(moduleGroup, cgStyle, None, CallGraphConstructor.defaultOptions)
  }

  object CallGraphStyle extends Enumeration {
    type Style = Value

    val CHA: CallGraphStyle.Value = Value
    val PRTA: CallGraphStyle.Value = Value
    val ORTA: CallGraphStyle.Value = Value
    val VTA: CallGraphStyle.Value = Value
    val UCG: CallGraphStyle.Value = Value
  }

  object PointerAnalysisStyle extends Enumeration {
    type Style = Value

    val None: PointerAnalysisStyle.Value = Value
    val SPDS: PointerAnalysisStyle.Value = Value
    val SPDSVTA: PointerAnalysisStyle.Value = Value
    val UFF: PointerAnalysisStyle.Value = Value
    val NameBased: PointerAnalysisStyle.Value = Value
  }
}
