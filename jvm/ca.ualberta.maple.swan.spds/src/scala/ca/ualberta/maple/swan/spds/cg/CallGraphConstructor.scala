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
import ca.ualberta.maple.swan.utils.Logging

abstract class CallGraphConstructor(val moduleGroup: ModuleGroup, val options: CallGraphConstructor.Options) {

  def buildCallGraph(): CallGraphStats = {
    Logging.printInfo("Constructing Call Graph")
    val startTimeNano = System.nanoTime()
    val startTimeMs = System.currentTimeMillis()
    // This initialization includes converting SWIRL to SPDS form
    val cgs = CallGraphUtils.initializeCallGraph(moduleGroup, options)
    cgs.initializationTimeMS = (System.currentTimeMillis() - startTimeMs).toInt
    buildSpecificCallGraph(cgs)
    CallGraphUtils.pruneEntryPoints(cgs)
    cgs.totalCGConstructionTimeMS = (System.currentTimeMillis() - startTimeMs).toInt
    cgs.entryPoints = cgs.cg.getEntryPoints.size()
    cgs.totalEdges = cgs.cg.getEdges.size()
    Logging.printTimeStampSimple(0, startTimeNano, "constructing")
    cgs
  }

  def buildSpecificCallGraph(cg: CallGraphStats): Unit
}

object CallGraphConstructor {
  class Options(val analyzeLibraries: Boolean = false)

  def defaultOptions: Options = new Options()
}