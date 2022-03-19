/*
 * Copyright (c) 2022 the SWAN project authors. All rights reserved.
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
import ca.ualberta.maple.swan.spds.Stats.SpecificCallGraphStats
import ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle
import ca.ualberta.maple.swan.spds.cg.CallGraphConstructor.Options
import ca.ualberta.maple.swan.spds.cg.VTA.VTAStats
import ca.ualberta.maple.swan.utils.Logging
import ujson.Value

class VTA(mg: ModuleGroup, pas: PointerAnalysisStyle.Style, options: Options) extends TypeFlowCG(mg, options: Options) {

  pas match {
    case PointerAnalysisStyle.None =>
    case PointerAnalysisStyle.NameBased =>
    case _ =>
      throw new RuntimeException("Pointer Analysis must not be set for VTA call graph construction")
  }


  override def buildSpecificCallGraph(): Unit = {
    val stats = new VTAStats()
    initializeTypeFlowStats(stats)

    val startTimeMs = System.currentTimeMillis()

    addTrivialEdges()

    Logging.printInfo("Building conservative graph (CHA) for VTA")
    val conservativeGraph = new CHA(mg,pas, options: Options)
    // val conservativeGraph = new UCGSound(mg,PointerAnalysisStyle.SPDS,true, options)
    conservativeGraph.buildCallGraph(CallGraphBuilder.CallGraphStyle.CHA)
    cgs.specificData.addOne(conservativeGraph.cgs.specificData.last)

    Logging.printInfo("Building type propagation graph for VTA")
    val paStartTimeMs = System.currentTimeMillis()
    val vta = new pa.VTA(stats)
    val typeFlow = vta.getTypeFlow(methods, conservativeGraph.cg)
    stats.paTime = (System.currentTimeMillis() - paStartTimeMs).toInt

    initializeTypeFlow(typeFlow)
    addTypeFlowEdges()

    stats.time = (System.currentTimeMillis() - startTimeMs).toInt
    cgs.specificData.addOne(stats)
  }
}

object VTA {

  class VTAStats() extends SpecificCallGraphStats with pa.VTAPAStats with TypeFlowCGStats {
    var time: Int = 0
    var paTime: Int = 0
    override def toJSON: Value = {
      val u = ujson.Obj()
      u("vta_pa_assign_edges") = assignEdges
      u("vta_pa_allocations") = allocations
      u("vta_fp_edges") = ptEdges
      u("vta_ddg_edges") = ddgEdges
      u("vta_type_flow_refs") = typeFlowRefs
      u("vta_empty_type_flows") = emptyTypeFlowTypes
      u("vta_pa_time") = paTime
      u("vta_time") = time
      u
    }

    override def toString: String = {
      val sb = new StringBuilder()
      sb.append(s"VTA\n")
      sb.append(s"  PA Assign Edges: $assignEdges\n")
      sb.append(s"  PA Allocations: $allocations\n")
      sb.append(s"  FPEdges: $ptEdges\n")
      sb.append(s"  DDGEdges: $ddgEdges\n")
      sb.append(s"  TypeFlowRefs: $typeFlowRefs\n")
      sb.append(s"  emptyTypeFlows: $emptyTypeFlowTypes\n")
      sb.append(s"  PA Time (ms): $time\n")
      sb.append(s"  Time (ms): $time\n")
      sb.toString()
    }
  }
}
