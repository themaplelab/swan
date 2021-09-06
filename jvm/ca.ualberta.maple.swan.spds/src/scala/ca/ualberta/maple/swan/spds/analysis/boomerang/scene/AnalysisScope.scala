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

package ca.ualberta.maple.swan.spds.analysis.boomerang.scene

import ca.ualberta.maple.swan.spds.analysis.boomerang.Query
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge

import scala.collection.mutable

abstract class AnalysisScope(val cg: CallGraph) {

  protected val seeds: mutable.HashSet[Query] = mutable.HashSet.empty
  protected val processed: mutable.HashSet[Method] = mutable.HashSet.empty
  protected var statementCount = 0

  def computeSeeds: mutable.HashSet[Query] = {
    val worklist = mutable.Queue.empty[Method]
    worklist.addAll(cg.entryPoints)
    while (worklist.nonEmpty) {
      val m = worklist.dequeue()
      if (processed.add(m)) {
        m.getStatements.foreach(u => {
          statementCount += 1
          u match {
            case callSiteStatement: CallSiteStatement => {
              val edgesOutOf = cg.edgesOutOf(u)
              edgesOutOf.foreach(e => {
                if (!processed.contains(e.callee)) {
                  worklist.enqueue(e.callee)
                }
              })
            }
            case _ =>
          }
          u.method.getCFG.getSuccsOf(u).foreach(succ => seeds.addAll(generate(new Edge(u, succ))))
        })
      }
    }
    seeds
  }

  def generate(seed: Edge[Statement, Statement]): mutable.HashSet[_ <: Query]
}
