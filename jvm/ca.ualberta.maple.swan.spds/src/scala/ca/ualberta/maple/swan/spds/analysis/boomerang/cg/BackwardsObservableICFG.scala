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

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{CallGraph, Method, Statement}

import scala.collection.mutable

class BackwardsObservableICFG(delegate: ObservableICFG[Statement, Method]) extends ObservableICFG[Statement, Method] {

  override def addCalleeListener(listener: CalleeListener[Statement, Method]): Unit = delegate.addCalleeListener(listener)

  override def addCallerListener(listener: CallerListener[Statement, Method]): Unit = delegate.addCallerListener(listener)

  override def isCallStmt(stmt: Statement): Boolean = delegate.isCallStmt(stmt)

  override def isExitStmt(stmt: Statement): Boolean = delegate.isExitStmt(stmt)

  override def isStartPoint(stmt: Statement): Boolean = delegate.isStartPoint(stmt)

  override def getNumberOfEdgesTakenFromPrecomputedGraph: Int = delegate.getNumberOfEdgesTakenFromPrecomputedGraph

  override def resetCallGraph(): Unit = delegate.resetCallGraph()

  override def getStartPointsOf(m: Method): mutable.HashSet[Statement] = delegate.getStartPointsOf(m)

  override def getEndPointsOf(m: Method): mutable.HashSet[Statement] = delegate.getEndPointsOf(m)

  override def computeFallback(): Unit = delegate.computeFallback()

  override def addEdges(e: CallGraph.Edge): Unit = delegate.addEdges(e)
}
