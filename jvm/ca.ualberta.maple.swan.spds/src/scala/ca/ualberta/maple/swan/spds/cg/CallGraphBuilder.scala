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

object CallGraphBuilder {

  def createCallGraph(moduleGroup: ModuleGroup,
                      cgStyle: CallGraphStyle.Style,
                      options: CallGraphConstructor.Options): CallGraphStats = {
    val cgBuilder = {
      cgStyle match {
        case CallGraphStyle.CHA_FP => new CHA(moduleGroup, true, options)
        case CallGraphStyle.VTA_FP => new VTA(moduleGroup, options)
        case CallGraphStyle.UCG => new UCG(moduleGroup, UCG.Options.VTA_SPDS, true, options)
        case CallGraphStyle.UCG_NO_VTA => new UCG(moduleGroup, UCG.Options.SPDS, true, options)
//        case CallGraphStyle.CHA => new CHA(moduleGroup, false, options)
//        case CallGraphStyle.CHA_SIGMATCHING => new CHA(moduleGroup, true, options)
//        case CallGraphStyle.ORTA => new ORTA(moduleGroup, false, options)
//        case CallGraphStyle.ORTA_SIGMATCHING => new ORTA(moduleGroup, true, options)
//        case CallGraphStyle.PRTA => new PRTA(moduleGroup, false, options)
//        case CallGraphStyle.PRTA_SIGMATCHING => new PRTA(moduleGroup, true, options)
//        case CallGraphStyle.SPDS => new SPDS(moduleGroup, SPDS.Options.NO_FILTER, options)
//        case CallGraphStyle.SPDS_WP_FILTER => new SPDS(moduleGroup, SPDS.Options.WP_FILTER, options)
//        case CallGraphStyle.SPDS_QUERY_FILTER => new SPDS(moduleGroup, SPDS.Options.QUERY_FILTER, options)
//        case CallGraphStyle.VTA => new VTA(moduleGroup, options)
//        case CallGraphStyle.UCG => new UCG(moduleGroup, UCG.Options.NONE, true, options)
//        case CallGraphStyle.UCG_VTA => new UCG(moduleGroup, UCG.Options.VTA, true, options)
//        case CallGraphStyle.UCG_SPDS => new UCG(moduleGroup, UCG.Options.SPDS, true, options)
//        case CallGraphStyle.UCG_SPDS_DYNAMIC => new UCG(moduleGroup, UCG.Options.SPDS_DYNAMIC, true, options)
//        case CallGraphStyle.UCG_VTA_SPDS => new UCG(moduleGroup, UCG.Options.VTA_SPDS, true, options)
      }
    }
    cgBuilder.buildCallGraph(cgStyle)
  }

  def createCallGraph(moduleGroup: ModuleGroup, cgStyle: CallGraphStyle.Style): CallGraphStats = {
    createCallGraph(moduleGroup, cgStyle, CallGraphConstructor.defaultOptions)
  }

  object CallGraphStyle extends Enumeration {
    type Style = Value

    val CHA_FP: CallGraphStyle.Value = Value
    val VTA_FP: CallGraphStyle.Value = Value
    val UCG: CallGraphStyle.Value = Value
    val UCG_NO_VTA: CallGraphStyle.Value = Value

//    val CHA: CallGraphStyle.Value = Value
//    val VTA: CallGraphStyle.Value = Value
//    val UCG: CallGraphStyle.Value = Value
//    val UCG_VTA: CallGraphStyle.Value = Value
//    val UCG_SPDS: CallGraphStyle.Value = Value
//    val UCG_SPDS_DYNAMIC: CallGraphStyle.Value = Value
//    val UCG_VTA_SPDS: CallGraphStyle.Value = Value
  }
  
}
