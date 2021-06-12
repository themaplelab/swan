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

import java.util

import boomerang.scene.{DeclaredMethod, InvokeExpr, Val, WrappedClass}
import ca.ualberta.maple.swan.spds.structures.SWANStatement.ApplyFunctionRef

class SWANInvokeExpr(val stmt: ApplyFunctionRef, val method: SWANMethod) extends InvokeExpr {

  val args: util.List[Val] = new util.ArrayList[Val]()

  stmt.inst.arguments.zipWithIndex.foreach(arg => {
    args.add(method.addVal(SWANVal.Argument(method.delegate.getSymbol(arg._1.name), arg._2, method)))
  })

  var declaredMethod: DeclaredMethod = new DeclaredMethod(this) {
    override def isNative: Boolean = ???
    override def getSubSignature: String = ""
    override def getName: String = ""
    override def isStatic: Boolean = ???
    override def isConstructor: Boolean = ???
    override def getSignature: String = ???
    override def getDeclaringClass: WrappedClass = ???
  }

  def getFunctionRef: Val = stmt.getFunctionRef

  def updateDeclaredMethod(name: String): Unit = {
    declaredMethod = new DeclaredMethod(this) {
      override def isNative: Boolean = ???
      override def getSubSignature: String = name
      override def getName: String = name
      override def isStatic: Boolean = ???
      override def isConstructor: Boolean = ???
      override def getSignature: String = ???
      override def getDeclaringClass: WrappedClass = ???
    }
  }

  override def getArg(index: Int): Val = args.get(index)

  override def getArgs: util.List[Val] = args

  override def isInstanceInvokeExpr: Boolean = false

  override def getBase: Val = ???

  override def getMethod: DeclaredMethod = declaredMethod

  override def isSpecialInvokeExpr: Boolean = false

  override def isStaticInvokeExpr: Boolean = true

  override def toString: String = {
    val sb = new StringBuilder()
    sb.append("<invoke_expr>")
    sb.append(getFunctionRef.toString)
    sb.append("<args>")
    args.forEach(a => {
      sb.append("<arg>")
      sb.append(a.toString)
      sb.append("</arg>")
    })
    sb.append("</args>")
    sb.append("</invoke_expr>")
    sb.toString()
  }
}
