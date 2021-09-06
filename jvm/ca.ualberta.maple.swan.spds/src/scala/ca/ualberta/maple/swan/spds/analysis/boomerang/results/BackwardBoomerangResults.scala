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

package ca.ualberta.maple.swan.spds.analysis.boomerang.results

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.Type
import ca.ualberta.maple.swan.spds.analysis.boomerang.solver.{BackwardBoomerangSolver, ForwardBoomerangSolver}
import ca.ualberta.maple.swan.spds.analysis.boomerang.stats.IBoomerangStats
import ca.ualberta.maple.swan.spds.analysis.boomerang.{BackwardQuery, ForwardQuery, Query}
import ca.ualberta.maple.swan.spds.analysis.boomerang.util.DefaultValueMap
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.Weight
import com.google.common.base.Stopwatch

import scala.collection.mutable

class BackwardBoomerangResults[W <: Weight](query: BackwardQuery,
                                            queryToSolvers: DefaultValueMap[ForwardQuery, ForwardBoomerangSolver[W]],
                                            backwardSolver: BackwardBoomerangSolver[W],
                                            val stats: IBoomerangStats[W],
                                            val analysisWatch: Stopwatch) extends AbstractBoomerangResults[W](queryToSolvers) {

  stats.terminated(query, this)

  private var allocationSites: mutable.HashMap[ForwardQuery, Context] = null

  def getAllocationSites: mutable.HashMap[ForwardQuery, Context] = {
    if (allocationSites == null) {
      val results = mutable.HashSet.empty[ForwardQuery]
      queryToSolvers.keySet.foreach(q => {
        val fw = queryToSolvers(q)
        fw.fieldAutomaton.getInitialStates.foreach(node => {
          fw.fieldAutomaton.registerListener(new ExtractAllocationSiteStateListener[W](node, query, q) {

            override def allocationSiteFound(allocationSite: ForwardQuery, query: BackwardQuery): Unit = {
              results.add(allocationSite)
            }
          })
        })
      })
      allocationSites = mutable.HashMap.empty
      results.foreach(q => {
        val context = constructContextGraph(q, query.asNode)
        allocationSites.addOne(q, context)
      })
    }
    allocationSites
  }

  def aliases(el: Query): Boolean = {
    var res = false
    getAllocationSites.keySet.foreach(fw => {
      if (queryToSolvers.getOrCreate(fw).reachedStates.contains(el.asNode) &&
        queryToSolvers.getOrCreate(fw).reachesNodeWithEmptyField(el.asNode)) {
        res = true
      }
    })
    res
  }

  def isEmpty: Boolean = getAllocationSites.isEmpty

  def getPropagationType: mutable.HashSet[Type] = {
    backwardSolver.callAutomaton.transitions.map(x => x.getStart.fact.getType)
  }
}
