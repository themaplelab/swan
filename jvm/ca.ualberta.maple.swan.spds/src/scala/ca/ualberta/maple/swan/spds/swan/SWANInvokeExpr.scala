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

import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{InvokeExpr, Method, Val}
import ca.ualberta.maple.swan.spds.swan.SWANStatement.CallSite

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class SWANInvokeExpr(val stmt: CallSite, val method: SWANMethod) extends InvokeExpr {

  val args: mutable.ArrayBuffer[Val] = new ArrayBuffer[Val]()

  stmt.inst.arguments.zipWithIndex.foreach(arg => {
    args.addOne(method.addVal(SWANVal.Argument(method.delegate.getSymbol(arg._1.name), arg._2, method)))
  })

  protected var resolvedMethod: Option[Method] = None

  def getFunctionRef: Val = stmt.getFunctionRef

  def updateResolvedMethod(name: String, cg: SWANCallGraph): Unit = {
    this.resolvedMethod = Some(cg.methods(name))
  }

  def getIndexOf(v: Val): Option[Int] = {
    var idx = 0
    args.foreach(a => {
      if (a == v) {
        return Some(idx)
      }
      idx += 1
    })
    None
  }

  override def getArg(index: Int): Val = args(index)

  override def getArgs: mutable.ArrayBuffer[Val] = args

  override def getResolvedMethod: Option[Method] = resolvedMethod

  override def toString: String = {
    val sb = new StringBuilder()
    sb.append("<invoke_expr>")
    sb.append(getFunctionRef.toString)
    sb.append("<args>")
    args.foreach(a => {
      sb.append("<arg>")
      sb.append(a.toString)
      sb.append("</arg>")
    })
    sb.append("</args>")
    sb.append("</invoke_expr>")
    sb.toString()
  }
}
