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

import java.util.Objects

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.CallGraph.Edge

import scala.collection.mutable

class CallGraph {

  val edges: mutable.HashSet[Edge] = mutable.HashSet.empty
  val edgesOutOf: mutable.MultiDict[Statement, Edge] = mutable.MultiDict.empty
  val edgesInto: mutable.MultiDict[Method, Edge] = mutable.MultiDict.empty
  val entryPoints: mutable.HashSet[Method] = mutable.HashSet.empty
  val fieldLoadStatements: mutable.MultiDict[Field, Statement] = mutable.MultiDict.empty
  val fieldStoreStatements: mutable.MultiDict[Field, Statement] = mutable.MultiDict.empty

  def edgesOutOf(stmt: Statement): scala.collection.Set[Edge] = edgesOutOf.get(stmt)

  def edgesInto(m: Method): scala.collection.Set[Edge] = edgesInto.get(m)

  def addEdge(edge: Edge): Boolean = {
    edgesOutOf.addOne(edge.callSite, edge)
    edgesInto.addOne(edge.callee, edge)
    computeStaticFieldLoadAndStores(edge.callee)
    edges.add(edge)
  }

  def size: Int = edges.size

  def addEntryPoint(m: Method): Boolean = {
    computeStaticFieldLoadAndStores(m)
    entryPoints.add(m)
  }

  def getReachableMethods: mutable.HashSet[Method] = {
    val set = mutable.HashSet.empty[Method]
    set.addAll(entryPoints)
    set.addAll(edgesInto.keySet)
    set
  }

  def computeStaticFieldLoadAndStores(m: Method): Unit = {
    m.getStatements.foreach {
      case s: StaticFieldLoadStatement => {
        fieldLoadStatements.addOne(s.getStaticField.field, s)
      }
      case s: StaticFieldWriteStatement => {
        fieldStoreStatements.addOne(s.getStaticField.field, s)
      }
      case _ =>
    }
  }
}

object CallGraph {

  class Edge(val callSite: Statement, val callee: Method) {

    if (!callSite.isInstanceOf[CallSiteStatement]) throw new RuntimeException("Illegal CG edge")

    override def hashCode: Int = Objects.hashCode(callSite, callee)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: Edge => Objects.equals(other.callee, callee) && Objects.equals(other.callSite, callSite)
        case _ => false
      }
    }

    override def toString: String = s"CG Edge $callSite calls $callee"
  }
}
