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

package ca.ualberta.maple.swan.spds.structures

import boomerang.scene._
import ca.ualberta.maple.swan.ir._
import org.jgrapht.Graph
import org.jgrapht.graph.{DefaultDirectedGraph, DefaultEdge}

import scala.collection.mutable

class SWANCallGraph(var moduleGroup: ModuleGroup,
                    val methods: mutable.HashMap[String, SWANMethod]) extends CallGraph {

  val graph: Graph[SWANMethod, DefaultEdge] = new DefaultDirectedGraph(classOf[DefaultEdge])

  def outEdgeTargets(m: SWANMethod): Array[SWANMethod] = {
    graph.outgoingEdgesOf(m).toArray().map(e => graph.getEdgeTarget(e.asInstanceOf[DefaultEdge]))
  }

  override def toString: String = {
    val sb = new StringBuilder
    sb.append("CG edges (")
    sb.append(getEdges.size())
    sb.append("): \n")
    getEdges.forEach((e: CallGraph.Edge) => {
      val sp = new SWIRLPrinter
      sp.print(e.src.asInstanceOf[SWANStatement].delegate)
      sb.append(sp)
      sb.append(" -> ")
      sb.append(e.tgt.getName)
      sb.append("\n")
    })
    sb.toString()
  }

  def toDot: String = {
    val sb = new StringBuilder()
    sb.append("\ndigraph G {\n")
    this.getEdges.forEach(edge => {
      sb.append("  \"")
      sb.append(edge.src().getMethod.getName)
      sb.append("\" -> \"")
      sb.append(edge.tgt().getName)
      sb.append("\"\n")
    })
    sb.append("}\n")
    sb.toString()
  }
}
