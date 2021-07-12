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

package ca.ualberta.maple.swan.spds.analysis.taint

import ca.ualberta.maple.swan.ir.{CanInstructionDef, Position}
import ca.ualberta.maple.swan.spds.analysis.taint.TaintResults.Path
import ca.ualberta.maple.swan.spds.analysis.taint.TaintSpecification.JSONMethod

import scala.collection.mutable.ArrayBuffer

class TaintResults(val paths: ArrayBuffer[Path],
                   val spec: TaintSpecification) {

  override def toString: String = {
    val sb = new StringBuilder()
    sb.append("Taint Analysis Results for specification:\n")
    sb.append(spec)
    sb.append("\nDetected:\n")
    paths.zipWithIndex.foreach(path => {
      sb.append("  (")
      sb.append(path._2.toString)
      sb.append(") from ")
      sb.append(path._1.source.name)
      sb.append(" to \n")
      sb.append("           ")
      sb.append(path._1.sink.name)
      sb.append("\n")
      if (path._1.nodes.nonEmpty) {
        sb.append("      path:\n")
        path._1.nodes.foreach(n => {
          if (n._2.nonEmpty) {
            sb.append("        ")
            sb.append(n._2.get.toString)
            sb.append("\n")
          }
        })
      }
    })
    sb.toString()
  }
}

object TaintResults {
  class Path(val nodes: ArrayBuffer[(CanInstructionDef, Option[Position])],
             val sourceName: String,
             val source: JSONMethod,
             val sinkName: String,
             val sink: JSONMethod)
}
