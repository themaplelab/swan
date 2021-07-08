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

package ca.ualberta.maple.swan.ir

/**
 * Builds plain-text SWIRL for dynamic models.
 */
class SWIRLBuilder {

  private val sb = new StringBuilder
  sb.append("swirl_stage raw\n\n")

  def addLine(s: String, indent: Boolean = true): Unit = {
    if (indent) sb.append("  ")
    sb.append(s)
    sb.append("\n")
  }

  def openFunction(function: CanFunction, model: Boolean = false): Unit = {
    sb.append("func ")
    if (model) sb.append("[model] ")
    sb.append(" @`")
    sb.append(function.name)
    sb.append("` : $`")
    sb.append(function.tpe.name)
    sb.append("` {\n")
    sb.append("bb0")
    if (function.arguments.nonEmpty) {
      sb.append("(")
      function.arguments.foreach(arg => {
        sb.append(arg.ref.name)
        sb.append(" : $`")
        sb.append(arg.tpe.name)
        sb.append("`")
        if (function.arguments.last != arg) {
          sb.append(", ")
        }
      })
      sb.append(")")
    }
    sb.append(":\n")
  }

  def closeFunction(): Unit = {
    sb.append("}\n\n")
  }

  override def toString: String = sb.toString()

}
