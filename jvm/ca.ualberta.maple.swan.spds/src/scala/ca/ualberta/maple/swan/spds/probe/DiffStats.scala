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

package ca.ualberta.maple.swan.spds.probe

import scala.collection.mutable
import scala.jdk.CollectionConverters._

class DiffStats(private val superGraph: CallGraph, private val subGraph: CallGraph) {

  private val superSiteMap: mutable.MultiDict[String, CallEdge] = mutable.MultiDict.empty[String, CallEdge]
  private val subSiteMap: mutable.MultiDict[String, CallEdge] = mutable.MultiDict.empty[String, CallEdge]

  {
    superGraph.edges().iterator().asScala.foreach{ e =>
      superSiteMap.addOne(e.context(), e)
    }
    subGraph.edges().iterator().asScala.foreach{ e =>
      subSiteMap.addOne(e.context(), e)
    }
  }

  private val superReach: mutable.Set[ProbeMethod] = superGraph.findReachables().asScala
  private val subReach: mutable.Set[ProbeMethod] = subGraph.findReachables().asScala

  private val superReachCtxs = mutable.HashSet.empty[String]
  private val subReachCtxs = mutable.HashSet.empty[String]

  {
    superGraph.edges().iterator().asScala.foreach{ e =>
      if (superReach.contains(e.src())) {
        superReachCtxs.add(e.context())
      }
    }
    subGraph.edges().iterator().asScala.foreach{ e =>
      if (subReach.contains(e.src())) {
        subReachCtxs.add(e.context())
      }
    }
  }

  val reachableToUnreachable: Int = superReach.count(m => !subReach.contains(m))

  private val (superMono, superPoly): (scala.collection.Set[String], scala.collection.Set[String]) =
  superSiteMap.keySet.partition(ctx => superSiteMap.get(ctx).size == 1)
  private val (superMonoReach, superMonoUnreach) = superMono.partition(ctx => superReachCtxs.contains(ctx))
  private val (superPolyReach, superPolyUnreach) = superPoly.partition(ctx => superReachCtxs.contains(ctx))
  private val (subMono, subPoly): (scala.collection.Set[String], scala.collection.Set[String]) =
    subSiteMap.keySet.partition(ctx => subSiteMap.get(ctx).size == 1)
  private val (subMonoReach, subMonoUnreach) = subMono.partition(ctx => subReachCtxs.contains(ctx))
  private val (subPolyReach, subPolyUnreach) = subPoly.partition(ctx => subReachCtxs.contains(ctx))

  private def toUnreachNoneMonoPoly(callSites: scala.collection.Set[String], callSiteName: String): (Int, Int,Int,Int) = {
    var toN: Int = 0
    var toU: Int = 0
    var toM: Int = 0
    var toP: Int = 0
    callSites.foreach(ctx =>
      if (subMonoReach.contains(ctx)) {
        toM += 1
      }
      else if (subPolyReach.contains(ctx)) {
        toP += 1
      }
      else if (subMonoUnreach.contains(ctx) || subPolyUnreach.contains(ctx)) {
        println(s"${callSiteName}-to-unreach ${ctx}")
        toU += 1
      }
      else {
        println(s"${callSiteName}-to-none ${ctx}")
        toN += 1
      }
    )

    (toU, toN, toM, toP)
  }

  private def countToUnrechable(callSites: scala.collection.Set[String]): Int = {
    callSites.count(ctx => !subReach.contains(superSiteMap.get(ctx).head.src()))
  }

  val monoToUnreachable: Int = {
    countToUnrechable(superMono)
  }

  val polyToUnreachable: Int = {
    countToUnrechable(superPoly)
  }

  val monoCount: Int = superMono.size
  val (monoToUnreach, monoToNone, monoToMono, monoToPoly): (Int,Int,Int,Int) = toUnreachNoneMonoPoly(superMono, "mono")

  val polyCount: Int = superPoly.size
  val (polyToUnreach, polyToNone, polyToMono, polyToPoly): (Int,Int,Int,Int) = toUnreachNoneMonoPoly(superPoly, "poly")

  val monoReachCount: Int = superMonoReach.size
  val (monoReachToUnreach, monoReachToNone, monoReachToMono, monoReachToPoly): (Int,Int,Int,Int) =
    toUnreachNoneMonoPoly(superMonoReach, "monoReach")
  val polyReachCount: Int = superPolyReach.size
  val (polyReachToUnreach, polyReachToNone, polyReachToMono, polyReachToPoly): (Int,Int,Int,Int) =
    toUnreachNoneMonoPoly(superPolyReach, "polyReach")

  // For sound call graphs
  // polyToPolyIncrease counts polymorphic sites where precision was reduced
  // polyToPolyDecrease counts polymorphic sites where precision was improved
  val (polyToPolyIncrease, polyToPolyDecrease, polyToPolySame): (Int,Int,Int) = {
    var inc = 0
    var dec = 0
    var same = 0
    superPolyReach.foreach{ ctx =>
      val superEdges = superSiteMap.get(ctx).toSet
      if (subPolyReach.contains(ctx)) {
        val subEdges = superSiteMap.get(ctx).toSet
        val superMore = (superEdges -- subEdges).size
        val subMore = (subEdges -- superEdges).size
        if (superMore > 0) inc += 1
        if (subMore > 0) dec += 1
        if (subMore == 0 && superMore == 0) same += 1
      }
    }
    (inc,dec,same)
  }
}
