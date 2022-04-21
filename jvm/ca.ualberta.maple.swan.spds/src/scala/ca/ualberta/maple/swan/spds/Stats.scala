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

package ca.ualberta.maple.swan.spds

import ca.ualberta.maple.swan.ir.{CanModule, CanOperator, Module, SWIRLPrinterOptions}
import ca.ualberta.maple.swan.spds.cg.{CallGraphConstructor, CallGraphUtils}
import ca.ualberta.maple.swan.spds.structures.SWANCallGraph

import java.io.{File, FileWriter}
import scala.collection.mutable

object Stats {

  trait SpecificCallGraphStats {
    def toJSON: ujson.Value
    def toString: String
  }

  class CallGraphStats(val cg: SWANCallGraph, val options: CallGraphConstructor.Options) {
    // TODO: Dynamic calculation
    var totalCGConstructionTimeMS: Int = 0
    var initializationTimeMS: Int = 0
    def cgOnlyConstructionTimeMS: Int = totalCGConstructionTimeMS - initializationTimeMS
    var entryPoints: Int = 0
    var totalEdges: Int = 0
    lazy val totalCallSites: Int = resolvedCallSiteStats._4
    var trivialCallSites: Int = 0
    lazy private val resolvedCallSiteStats: (Int, Int, Int, Int) = CallGraphUtils.calculateResolvedCallsites(this)
    lazy val trulyUnresolvedCallSites: Int = resolvedCallSiteStats._1
    lazy val unresolvedCallSites: Int = resolvedCallSiteStats._2
    lazy val resolvedCallSites: Int = resolvedCallSiteStats._3
    lazy val nonTrivialCallSites: Int = totalCallSites - trivialCallSites
    lazy private val methodStats: (Int, Int, Int, Int) = CallGraphUtils.calculateMethodStats(this)
    lazy val dynamicRefs = methodStats._1
    lazy val functionRefs = methodStats._2
    lazy val allocations = methodStats._3
    lazy val methodCount = methodStats._4
    lazy private val userMethodStats: (Int, Int, Int, Int) = CallGraphUtils.calculateMethodStats(this, skipLibraries = true)
    lazy val userDynamicRefs = userMethodStats._1
    lazy val userFunctionRefs = userMethodStats._2
    lazy val userAllocations = userMethodStats._3
    lazy val userMethodCount = userMethodStats._4
    var specificData: mutable.ArrayBuffer[SpecificCallGraphStats] = mutable.ArrayBuffer.empty
    val debugInfo = new SWIRLPrinterOptions.CallGraphDebugInfo()
    var finalModuleGroup: Object = cg.moduleGroup
    val dynamicModels: Option[(Module, CanModule)] = None

    final override def toString: String = {
      var indent = "  "
      val sb = new StringBuilder(indent + "Call Graph Stats:\n")
      indent = indent + indent
      sb.append(indent + s"Total CG construction time (ms): $totalCGConstructionTimeMS\n")
      sb.append(indent + s"Initialization time (ms): $initializationTimeMS\n")
      sb.append(indent + s"CG-only construction time: $cgOnlyConstructionTimeMS\n")
      sb.append(indent + s"Entry points: $entryPoints\n")
      sb.append(indent + s"Total edges: $totalEdges\n")
      sb.append(indent + s"Total call sites: $totalCallSites\n")
      sb.append(indent + s"Trivial call sites: $trivialCallSites\n")
      sb.append(indent + s"Resolved Call Sites: $resolvedCallSites\n")
      sb.append(indent + s"Unresolved Call Sites: $unresolvedCallSites\n")
      sb.append(indent + s"Possibly Unresolved Call Sites: $trulyUnresolvedCallSites\n")
      sb.append(indent + s"Non-trivial call sites: $nonTrivialCallSites\n")
      sb.append(indent + s"Method Count: $methodCount\n")
      sb.append(indent + s"Allocations: $allocations\n")
      sb.append(indent + s"Function Refs: $functionRefs\n")
      sb.append(indent + s"Dynamic Refs: $dynamicRefs\n")
      sb.append(indent + s"User Method Count: $userMethodCount\n")
      sb.append(indent + s"User Allocations: $userAllocations\n")
      sb.append(indent + s"User Function Refs: $userFunctionRefs\n")
      sb.append(indent + s"User Dynamic Refs: $userDynamicRefs\n")
      if (specificData.nonEmpty) {
        sb.append(indent + "Specific stats:\n")
        specificData.foreach(d => {
          d.toString.split('\n').foreach(s => {
            sb.append(indent)
            sb.append("  ")
            sb.append(s)
            sb.append('\n')
          })
        })
      }
      sb.toString()
    }

    def toJSON: ujson.Value = {
      val u = ujson.Obj()
      u("total_cg_time") = totalCGConstructionTimeMS
      u("initialization_time") = initializationTimeMS
      u("cg_only_time") = cgOnlyConstructionTimeMS
      u("entry_points") = entryPoints
      u("total_edges") = totalEdges
      u("total_call_sites") = totalCallSites
      u("trivial_call_sites") = trivialCallSites
      u("resolved_call_sites") = resolvedCallSites
      u("unresolved_call_sites") = unresolvedCallSites
      u("truly_unresolved_call_sites") = trulyUnresolvedCallSites
      u("non_trivial_call_sites") = nonTrivialCallSites
      u("method_count") = methodCount
      u("allocations") = allocations
      u("function_refs") = functionRefs
      u("dynamic_refs") = dynamicRefs
      u("user_method_count") = userMethodCount
      u("user_allocations") = userAllocations
      u("user_function_refs") = userFunctionRefs
      u("user_dynamic_refs") = userDynamicRefs
      specificData.foreach(s => s.toJSON.obj.foreach(o => u(o._1) = o._2))
      u
    }
  }

  class GeneralStats {
    var totalRunTimeMS: Int = 0
    var moduleGroupReadyTimeMS: Int = 0
    var modules: Int = 0
    var functions: Int = 0
    // var loc: Int = 0

    override def toString: String = {
      var indent = "  "
      val sb = new StringBuilder(indent + "General Stats:\n")
      indent = indent + indent
      sb.append(indent + s"Total runtime (ms): $totalRunTimeMS\n")
      sb.append(indent + s"Module group ready (ms): $moduleGroupReadyTimeMS\n")
      sb.append(indent + s"Modules: $modules\n")
      sb.append(indent + s"Functions: $functions\n")
      // sb.append(indent + s"LOC (SIL): $loc\n")
      sb.toString()
    }

    def toJSON: ujson.Value = {
      val u = ujson.Obj()
      u("total_runtime") = totalRunTimeMS
      u("module_group_ready") = moduleGroupReadyTimeMS
      u("modules") = modules
      u("functions") = functions
      // u("loc") = loc
      u
    }
  }

  class AllStats(val gs: GeneralStats) {
    var cgs: Option[CallGraphStats] = None

    override def toString: String = s"STATS:\n$gs${ if (cgs.nonEmpty) cgs.get else ""}"

    def toJSON: ujson.Value = {
      val u = ujson.Obj()
      gs.toJSON.obj.foreach(o => u(o._1) = o._2)
      if (cgs.nonEmpty) {
        cgs.get.toJSON.obj.foreach(o => u(o._1) = o._2)
      }
      u
    }

    def writeToFile(f: File): Unit = {
      val fw = new FileWriter(f)
      try {
        fw.write(toJSON.render(2))
        // Unix convention trailing newline
        fw.write("\n")
      } finally {
        fw.close()
      }
    }
  }
}
