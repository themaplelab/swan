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

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge

abstract class Val(val method: Method, val unbalancedStmt: Edge[Statement, Statement] = null) {

  def getType: Type

  def getName: String

  def asUnbalanced(edge: Edge[Statement, Statement]): Val

  def isReturnLocal: Boolean = method.getReturnLocals.contains(this)

  def isParameterLocal(i: Int): Boolean = {
    i < method.getParameterLocals.size && method.getParameterLocal(i).equals(this)
  }

  def isUnbalanced: Boolean = unbalancedStmt != null

  override def hashCode(): Int = Objects.hashCode(method, unbalancedStmt)

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: Val => Objects.equals(other.method, method) && Objects.equals(other.unbalancedStmt, unbalancedStmt)
      case _ => false
    }
  }
}

object Val {

  val zero: Val = {
    new Val(null) {
      override def getType: Type = null
      override def getName: String = toString
      override def asUnbalanced(edge: Edge[Statement, Statement]): Val = null
    }
  }

  case class AllocVal(delegate: Val, stmt: Statement, allocVal: Val) extends Val(stmt.method) {

    override def getType: Type = delegate.getType

    override def getName: String = delegate.getName

    override def asUnbalanced(edge: Edge[Statement, Statement]): Val = delegate.asUnbalanced(edge)
  }

  trait NewExpr
}
