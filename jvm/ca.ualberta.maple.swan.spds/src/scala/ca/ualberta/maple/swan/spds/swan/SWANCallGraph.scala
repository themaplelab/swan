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

package ca.ualberta.maple.swan.spds.swan

import ca.ualberta.maple.swan.ir._
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.CallGraph

import scala.collection.mutable

class SWANCallGraph(var moduleGroup: ModuleGroup, val methods: mutable.HashMap[String, SWANMethod]) extends CallGraph {

  override def toString: String = {
    val sb = new StringBuilder
    sb.append("CG edges (")
    sb.append(edges.size)
    sb.append("): \n")
    edges.foreach(e => {
      val sp = new SWIRLPrinter
      sp.print(e.callSite.asInstanceOf[SWANStatement].getDelegate)
      sb.append(sp)
      sb.append(" -> ")
      sb.append(e.callee.getName)
      sb.append("\n")
    })
    sb.toString()
  }

  def toDot: String = {
    val sb = new StringBuilder()
    sb.append("\ndigraph G {\n")
    edges.foreach(edge => {
      sb.append("  \"")
      sb.append(edge.callSite.method.getName)
      sb.append("\" -> \"")
      sb.append(edge.callee.getName)
      sb.append("\"\n")
    })
    sb.append("}\n")
    sb.toString()
  }
}
