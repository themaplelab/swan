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

import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.{Empty, Location}

abstract class Statement(val method: Method) extends Location {

  override def accepts(other: Location): Boolean = this.equals(other)

  override def hashCode(): Int = Objects.hashCode(method)

  override def equals(obj: Any): Boolean = {
    obj match {
      case s: Statement => Objects.equals(s.method, this.method)
      case _ => false
    }
  }

  def uses(value: Val): Boolean
}

object Statement {

  val epsilon: Statement = new EpsStatement
}

trait Assignment {

  def lhs: Val

  def rhs: Val
}

class EpsStatement extends Statement(null) with Empty {

  override def uses(value: Val): Boolean = false
}

abstract class FieldStoreStatement(m: Method) extends Statement(m) with Assignment {

  def getWrittenField: Field

  def isFieldWriteWithBase(base: Val): Boolean

  def getFieldStore: Pair[Val, Field]
}

abstract class FieldLoadStatement(m: Method) extends Statement(m) with Assignment {

  def getLoadedField: Field

  def isFieldLoadWithBase(base: Val): Boolean

  def getFieldLoad: Pair[Val, Field]
}

abstract class StaticFieldStoreStatement(m: Method) extends Statement(m) with Assignment {

  def getStaticField: StaticFieldVal
}

abstract class StaticFieldLoadStatement(m: Method) extends Statement(m) with Assignment {

  def getStaticField: StaticFieldVal
}

abstract class NewStatement(m: Method) extends Statement(m) with Assignment

abstract class AssignStatement(m: Method) extends Statement(m) with Assignment

abstract class LiteralStatement(m: Method) extends NewStatement(m) with Assignment

abstract class CallSiteStatement(m: Method) extends Statement(m) with Assignment {

  def getInvokeExpr: InvokeExpr

  def isParameter(value: Val): Boolean = getInvokeExpr.getArgs.exists(_.equals(value))
}

abstract class ReturnStatement(m: Method) extends Statement(m) {

  def getReturnOp: Val
}

abstract class ThrowStatement(m: Method) extends Statement(m)
