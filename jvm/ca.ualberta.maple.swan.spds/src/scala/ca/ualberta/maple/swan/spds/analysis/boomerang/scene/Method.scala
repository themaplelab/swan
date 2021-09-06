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

import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.Location

import scala.collection.mutable

abstract class Method extends Location {

  def isParameterLocal(v: Val): Boolean

  def getLocals: mutable.HashSet[Val]

  def getParameterLocals: mutable.ArrayBuffer[Val]

  def getStatements: mutable.ArrayBuffer[Statement]

  def getCFG: ControlFlowGraph

  def getName: String

  def getParameterLocal(i: Int): Val = getParameterLocal(i)

  private val returnLocals = getStatements.filter(s => s.isInstanceOf[ReturnStatement])
    .map(r => r.asInstanceOf[ReturnStatement].getReturnOp)

  def getReturnLocals: mutable.HashSet[Val] = mutable.HashSet.from(returnLocals)

  override def accepts(other: Location): Boolean = this.equals(other)
}

object Method {
  val epsilon: Method = new Method {
    override def isParameterLocal(v: Val): Boolean = false
    override def getLocals: mutable.HashSet[Val] = mutable.HashSet.empty
    override def getParameterLocals: mutable.ArrayBuffer[Val] = mutable.ArrayBuffer.empty
    override def getStatements: mutable.ArrayBuffer[Statement] = mutable.ArrayBuffer.empty
    override def getCFG: ControlFlowGraph = null
    override def getName: String = null
    override def toString: String = "METHOD EPS"
  }
}


