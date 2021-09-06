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

import ca.ualberta.maple.swan.spds.analysis.boomerang.ForwardQuery
import ca.ualberta.maple.swan.spds.analysis.boomerang.cfg.{ObservableCFG, PredecessorListener}
import ca.ualberta.maple.swan.spds.analysis.boomerang.cg.{CallerListener, ObservableICFG}
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene._
import ca.ualberta.maple.swan.spds.analysis.boomerang.solver.ForwardBoomerangSolver
import ca.ualberta.maple.swan.spds.analysis.boomerang.stats.IBoomerangStats
import ca.ualberta.maple.swan.spds.analysis.boomerang.util.DefaultValueMap
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.Weight
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.State
import com.google.common.base.Stopwatch
import com.google.common.collect.{HashBasedTable, Table}

import scala.collection.mutable

class ForwardBoomerangResults[W <: Weight](query: ForwardQuery,
                                           icfg: ObservableICFG[Statement, Method],
                                           cfg: ObservableCFG,
                                           queryToSolvers: DefaultValueMap[ForwardQuery, ForwardBoomerangSolver[W]],
                                           stats: IBoomerangStats[W],
                                           analysisWatch: Stopwatch,
                                           visitedMethods: mutable.HashSet[Method]) extends AbstractBoomerangResults[W](queryToSolvers) {

  stats.terminated(query, this)

  def getObjectDestructingStatements: Table[Edge[Statement, Statement], Val, W] = {
    val solver = queryToSolvers.get(query)
    if (solver.isEmpty) {
      HashBasedTable.create()
    } else {
      val res = asStatementValWeightTable
      val visitedMethods = mutable.HashSet.empty[Method]
      res.rowKeySet().forEach(s => visitedMethods.add(s.getMethod))
      val forwardSolver = queryToSolvers(query)
      val destructingStatement = HashBasedTable.create[Edge[Statement, Statement], Val, W]()
      visitedMethods.foreach(flowReaches => {
        icfg.getEndPointsOf(flowReaches).foreach(exitStmt => {
          exitStmt.method.getCFG.getPredsOf(exitStmt).foreach(predOfExit => {
            val exitEdge = new Edge[Statement, Statement](predOfExit, exitStmt)
            val escapes = mutable.HashSet.empty[State]
            icfg.addCallerListener(new CallerListener[Statement, Method] {

              override def getObservedCallee: Method = flowReaches

              override def onCallerAdded(callSite: Statement, callee: Method): Unit = {
                if (visitedMethods.contains(callSite.method)) {
                  res.row(exitEdge).entrySet().forEach(valAndW => {
                    escapes.addAll(forwardSolver.computeReturnFlow(flowReaches, exitStmt, valAndW.getKey))
                  })
                }
              }
            })
            if (escapes.isEmpty) {
              findLastUsage(exitEdge, res.row(exitEdge), destructingStatement, forwardSolver)
            }
          })
        })
      })
      destructingStatement
    }
  }

  def asStatementValWeightTable: Table[Edge[Statement, Statement], Val, W] = {
    queryToSolvers.getOrCreate(query).asStatementValWeightTable
  }

  def findLastUsage(exitStmt: Edge[Statement, Statement], row: java.util.Map[Val, W],
                    destructingStatement: Table[Edge[Statement, Statement], Val, W],
                    forwardSolver: ForwardBoomerangSolver[W]): Unit = {
    val worklist = new mutable.Queue[Edge[Statement, Statement]]()
    worklist.addOne(exitStmt)
    val visited = mutable.HashSet.empty[Edge[Statement, Statement]]
    while (worklist.nonEmpty) {
      val currEdge = worklist.dequeue()
      if (!visited.contains(currEdge)) {
        var valueUsedInStmt = false
        row.keySet().forEach(e => {
          if (currEdge.target.uses(e)) {
            destructingStatement.put(currEdge, e, row.get(e))
            valueUsedInStmt = true
          }
        })
        if (!valueUsedInStmt) {
          cfg.addPredsOfListener(new PredecessorListener(currEdge.start) {

            override def handlePredecessor(succ: Statement): Unit = {
              worklist.enqueue(new Edge[Statement, Statement](succ, currEdge.start))
            }
          })
        }
      }
    }
  }
}
