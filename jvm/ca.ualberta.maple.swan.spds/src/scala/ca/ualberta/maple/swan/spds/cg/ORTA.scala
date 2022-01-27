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

import boomerang.scene.Method
import ca.ualberta.maple.swan.ir.ModuleGroup
import ca.ualberta.maple.swan.spds.Stats.{CallGraphStats, SpecificCallGraphStats}
import ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle
import ca.ualberta.maple.swan.spds.cg.pa.PointerAnalysis
import ca.ualberta.maple.swan.spds.structures.SWANMethod
import ujson.Value

import scala.collection.mutable

class ORTA(mg: ModuleGroup, pas: PointerAnalysisStyle.Style) extends CallGraphConstructor(mg) {

  val pa: Option[PointerAnalysis] = {
    pas match {
      case PointerAnalysisStyle.None => None
      case ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle.SWPA => None
      case ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle.SOD => None
      case ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle.SPDS => None
      case ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle.VTA => {
        throw new RuntimeException("VTA pointer analysis should only be used with VTA CG construction")
      }
    }
  }

  // TODO: Pointer analysis integration
  override def buildSpecificCallGraph(cgs: CallGraphStats): Unit = {

    // Run CHA
    new CHA(moduleGroup, pas).buildSpecificCallGraph(cgs)

    var ortaEdges: Int = 0
    val startTimeMs = System.currentTimeMillis()
    val methods = cgs.cg.methods

    val worklist = mutable.Queue.empty[Method]
    cgs.cg.getEntryPoints.forEach(e => worklist.enqueue(e))

    while (worklist.nonEmpty) {
      val m = worklist.dequeue().asInstanceOf[SWANMethod]
      // ... TODO
      // https://github.com/EnSoftCorp/call-graph-toolbox/blob/master/com.ensoftcorp.open.cg/src/com/ensoftcorp/open/cg/analysis/RapidTypeAnalysis.java
      // https://ben-holland.com/call-graph-construction-algorithms-explained/
    }

    val stats = new ORTA.ORTAStats(ortaEdges, (System.currentTimeMillis() - startTimeMs).toInt)
    cgs.specificData.addOne(stats)
  }
}

object ORTA {

  class ORTAStats(val edges: Int, time: Int) extends SpecificCallGraphStats {

    override def toJSON: Value = {
      val u = ujson.Obj()
      u("orta_edges") = edges
      u("orta_time") = time
      u
    }

    override def toString: String = {
      s"  oRTA\n    Edges: $edges\n    Time (ms): $time"
    }
  }
}


