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
                      paStyle: Option[PointerAnalysisStyle.Style],
                      options: CallGraphConstructor.Options): CallGraphStats = {
    val cgBuilder = {
      cgStyle match {
        case CallGraphStyle.CHA => {
          new CHA(moduleGroup, paStyle match {
            case Some(value) => value
            case None => PointerAnalysisStyle.None
          }, options)
        }
        case CallGraphStyle.PRTA => new PRTA(moduleGroup, paStyle match {
          case Some(value) => value
          case None => PointerAnalysisStyle.None
        }, options)
        case CallGraphStyle.UCG => new UCGSound(moduleGroup, paStyle match {
          case Some(value) => value
          case None => PointerAnalysisStyle.SPDS
        }, false, options)
        case CallGraphStyle.UCGSound => new UCGSound(moduleGroup, paStyle match {
          case Some(value) => value
          case None => PointerAnalysisStyle.SPDS
        }, true, options)
        case CallGraphStyle.SRTA => new SRTA(moduleGroup, paStyle match {
          case Some(value) => value
          case None => PointerAnalysisStyle.None
        }, options)
      }
    }
    cgBuilder.buildCallGraph()
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
    val SRTA: CallGraphStyle.Value = Value
    val UCG: CallGraphStyle.Value = Value
    val UCGSound: CallGraphStyle.Value = Value
  }

  object PointerAnalysisStyle extends Enumeration {
    type Style = Value

    val None: PointerAnalysisStyle.Value = Value
    val SPDS: PointerAnalysisStyle.Value = Value
    val UFF: PointerAnalysisStyle.Value = Value
  }
}
