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

import boomerang.scene.{IfStatement, Statement, Val}

class SWANIfStatement(val stmt: SWANStatement.ConditionalBranch) extends IfStatement {

  override def getTarget: Statement = null
  override def evaluate(v: Val): IfStatement.Evaluation = null
  override def uses(v: Val): Boolean = stmt.getRightOp.equals(v)
  override def toString: String = "<if_stmt>" + stmt + "</if_stmt>"
}
