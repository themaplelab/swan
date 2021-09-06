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

package ca.ualberta.maple.swan.spds.analysis.boomerang.cg

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene._

import scala.collection.mutable

class ObservableStaticICFG(cg: CallGraph) extends ObservableICFG[Statement, Method] {

  override def addCalleeListener(listener: CalleeListener[Statement, Method]): Unit = {
    val edges = cg.edgesOutOf(listener.getObservedCaller)
    edges.foreach(e => {
      listener.onCalleeAdded(listener.getObservedCaller, e.callee)
    })
    if (edges.isEmpty) listener.onNoCalleeFound()
  }

  override def addCallerListener(listener: CallerListener[Statement, Method]): Unit = {
    cg.edgesInto(listener.getObservedCallee).foreach(e => {
      listener.onCallerAdded(e.callSite, listener.getObservedCallee)
    })
  }

  override def isCallStmt(stmt: Statement): Boolean = {
    stmt match {
      case _: CallSiteStatement => true
      case _ => false
    }
  }

  override def isExitStmt(stmt: Statement): Boolean = {
    stmt.method.getCFG.getEndPoints.contains(stmt)
  }

  override def isStartPoint(stmt: Statement): Boolean = {
    stmt.method.getCFG.getStartPoints.contains(stmt)
  }

  override def getNumberOfEdgesTakenFromPrecomputedGraph: Int = -1

  override def resetCallGraph(): Unit = {}

  override def getStartPointsOf(m: Method): mutable.HashSet[Statement] = m.getCFG.getStartPoints

  override def getEndPointsOf(m: Method): mutable.HashSet[Statement] = m.getCFG.getEndPoints

  override def computeFallback(): Unit = {}

  override def addEdges(e: CallGraph.Edge): Unit = ???
}
