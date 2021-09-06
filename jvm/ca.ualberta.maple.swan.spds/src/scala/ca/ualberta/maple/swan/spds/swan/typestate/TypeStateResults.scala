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

package ca.ualberta.maple.swan.spds.swan.typestate

import java.io.{File, FileWriter}
import ca.ualberta.maple.swan.ir.Position
import ca.ualberta.maple.swan.spds.analysis.typestate.State

import scala.collection.mutable.ArrayBuffer

class TypeStateResults(val errors: ArrayBuffer[(Position, State)], val spec: TypeStateSpecification) {

  override def toString: String = {
    val sb = new StringBuilder()
    sb.append("TypeState Analysis Results for specification:\n")
    sb.append(spec)
    sb.append("\nDetected error states on line(s):\n")
    errors.foreach(e => {
      sb.append(e)
      sb.append("\n")
    })
    sb.toString()
  }
}

object TypeStateResults {
  def writeResults(f: File, allResults: ArrayBuffer[TypeStateResults]): Unit = {
    val fw = new FileWriter(f)
    try {
      val r = new ArrayBuffer[ujson.Obj]
      allResults.foreach(results => {
        val json = ujson.Obj()
        results.spec.writeResults(json, results.errors)
        r.append(json)
      })
      val finalJson = ujson.Value(r)
      fw.write(finalJson.render(2))
    } finally {
      fw.close()
    }
  }
}