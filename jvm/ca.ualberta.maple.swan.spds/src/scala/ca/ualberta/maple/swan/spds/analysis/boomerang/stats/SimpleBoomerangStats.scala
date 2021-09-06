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

package ca.ualberta.maple.swan.spds.analysis.boomerang.stats

import ca.ualberta.maple.swan.spds.analysis.boomerang.{BackwardQuery, ForwardQuery, Query, WeightedBoomerang}
import ca.ualberta.maple.swan.spds.analysis.boomerang.results.{BackwardBoomerangResults, ForwardBoomerangResults}
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{ControlFlowGraph, Field, Method, Statement, Val}
import ca.ualberta.maple.swan.spds.analysis.boomerang.solver.AbstractBoomerangSolver
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes.{INode, Node}
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.{Transition, Weight, WeightedPAutomaton}

import scala.collection.mutable

class SimpleBoomerangStats[W <: Weight] extends IBoomerangStats[W] {

  val queries: mutable.HashMap[Query, AbstractBoomerangSolver[W]] = mutable.HashMap.empty
  val callVisitedMethods: mutable.HashSet[Method] = mutable.HashSet.empty
  val fieldVisitedMethods: mutable.HashSet[Method] = mutable.HashSet.empty

  override def registerSolver(key: Query, solver: AbstractBoomerangSolver[W]): Unit = {
    if (!queries.contains(key)) queries.put(key, solver)

    solver.callAutomaton.registerListener((t: Transition[Edge[Statement, Statement], INode[Val]],
                                           w: W, aut: WeightedPAutomaton[Edge[Statement, Statement], INode[Val], W]) => {
      callVisitedMethods.add(t.label.getMethod)
    })

    solver.fieldAutomaton.registerListener((t: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]],
                                            w: W, aut: WeightedPAutomaton[Field, INode[Node[Edge[Statement, Statement], Val]], W]) => {
      fieldVisitedMethods.add(t.start.fact.stmt.getMethod)
    })
  }

  override def registerFieldWritePOI(key: WeightedBoomerang[W]#FieldWritePOI): Unit = {}

  override def getCallVisitedMethods: mutable.HashSet[Method] = callVisitedMethods

  override def getForwardReachesNodes: mutable.HashSet[Node[ControlFlowGraph.Edge[Statement, Statement], Val]] = {
    val res = mutable.HashSet.empty[Node[Edge[Statement, Statement], Val]]
    queries.keySet.foreach(q => if (q.isInstanceOf[ForwardQuery]) res.addAll(queries(q).reachedStates))
    res
  }

  override def terminated(query: ForwardQuery, results: ForwardBoomerangResults[W]): Unit = {}

  override def terminated(query: BackwardQuery, results: BackwardBoomerangResults[W]): Unit = {}

  override def toString: String = {
    val sb = new StringBuilder("=========== Boomerang Stats =============\n")
    var forwardQuery = 0
    var backwardQuery = 0
    queries.keySet.foreach(q => {
      if (q.isInstanceOf[ForwardQuery]) {
        forwardQuery += 1
      } else backwardQuery += 1
    })
    sb.append(s"Queries (Forward/Backward/Total): $forwardQuery/$backwardQuery/${queries.keySet.size}\n")
    sb.append(s"Visited Methods (Field/Call): " +
      s"${fieldVisitedMethods.size}/${callVisitedMethods.size}" +
      s"(${fieldVisitedMethods.diff(callVisitedMethods).size}/${callVisitedMethods.diff(fieldVisitedMethods).size})\n\n")
    sb.toString()
  }
}
