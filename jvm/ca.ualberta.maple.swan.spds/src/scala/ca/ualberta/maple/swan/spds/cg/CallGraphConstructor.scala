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
import ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.CallGraphStyle
import ca.ualberta.maple.swan.spds.structures.{SWANCallGraph, SWANMethod}
import ca.ualberta.maple.swan.utils.Logging

import scala.collection.mutable

abstract class CallGraphConstructor(val moduleGroup: ModuleGroup, val options: CallGraphConstructor.Options) {

  // These must not be used without calling CallGraphUtils.initializeCallGraph(moduleGroup, methods, cg, cgs)
  val methods = new mutable.HashMap[String, SWANMethod]()
  val cg = new SWANCallGraph(moduleGroup, methods)
  val cgs = new CallGraphStats(cg, options)

  final def buildCallGraph(style: CallGraphStyle.Style): CallGraphStats = {
    Logging.printInfo("Constructing Call Graph: " + style)
    val startTimeNano = System.nanoTime()
    val startTimeMs = System.currentTimeMillis()
    // This initialization includes converting SWIRL to SPDS form
    CallGraphUtils.initializeCallGraph(moduleGroup, methods, cg, cgs)
    cgs.initializationTimeMS = (System.currentTimeMillis() - startTimeMs).toInt
    buildSpecificCallGraph()
    CallGraphUtils.pruneEntryPoints(cgs)
    // TODO: Add option to "plug holes" with over-approximation
    if (options.addDebugInfo) {
      cg.methods.foreach(m => {
        if (cg.edgesInto(m._2).isEmpty) {
          if (cg.getEntryPoints.contains(m._2)) {
            cgs.debugInfo.entries.add(m._2.delegate)
          } else {
            cgs.debugInfo.dead.add(m._2.delegate)
          }
        }
      })
    }
    cgs.totalCGConstructionTimeMS = (System.currentTimeMillis() - startTimeMs).toInt
    cgs.entryPoints = cgs.cg.getEntryPoints.size()
    cgs.totalEdges = cgs.cg.getEdges.size()
    Logging.printTimeStampSimple(0, startTimeNano, "constructing")
    if (cgs.totalEdges == 0) {
      Logging.printInfo("No call graph edges! Try analyzing libraries with `-l`.")
    }
    cgs
  }

  def buildSpecificCallGraph(): Unit
}

object CallGraphConstructor {
  class Options(var analyzeLibraries: Boolean,
                var analyzeClosures: Boolean,
                var addDebugInfo: Boolean)

  def defaultOptions: Options = new Options(false, false, false)
}