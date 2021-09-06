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

import java.util.Objects

import ca.ualberta.maple.swan.ir.{Literal, Symbol}
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{Statement, Type, Val}

abstract class SWANVal(m: SWANMethod, val delegate: Symbol, unbalanced: Edge[Statement, Statement]) extends Val(m, unbalanced) {

  protected val tpe: Type = SWANType.create(delegate.tpe)

  override def getName: String = delegate.ref.name

  override def getType: Type = tpe

  override def hashCode(): Int = super.hashCode() + Objects.hashCode(delegate)

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: SWANVal => super.equals(other) && Objects.equals(other.delegate, delegate)
      case _ => false
    }
  }
}

object SWANVal {

  case class Simple(d: Symbol,
                    m: SWANMethod,
                    unbalanced: Edge[Statement, Statement] = null) extends SWANVal(m, d, unbalanced) {

    override def asUnbalanced(edge: Edge[Statement, Statement]): Val = m.addVal(Simple(d, m, edge))

    override def toString: String = {
      if (m.hasSwirlSource) getName + " v" + m.swirlLineNum(delegate) else
      "<v " + getName + " " + getType.toString + " />"
    }
  }

  case class Argument(d: Symbol,
                      index: Int,
                      m: SWANMethod,
                      unbalanced: Edge[Statement, Statement] = null) extends SWANVal(m, d, unbalanced) {

    override def asUnbalanced(edge: Edge[Statement, Statement]): Val = m.addVal(Argument(delegate, index, m, edge))

    override def hashCode(): Int = super.hashCode() + Objects.hashCode(index)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: Argument => super.equals(other) && Objects.equals(other.index, index)
      }
    }

    override def toString: String = {
      if (m.hasSwirlSource) getName + " v" + m.swirlLineNum(delegate) else
      "<a " + getName + " " + this.getType.toString + " />"
    }
  }

  case class NewExpr(d: Symbol, m: SWANMethod, unbalanced: Edge[Statement, Statement] = null) extends SWANVal(m, d, unbalanced) with Val.NewExpr {

    override def asUnbalanced(edge: Edge[Statement, Statement]): Val = m.addVal(NewExpr(delegate, m, edge))

    override def toString: String = {
      if (m.hasSwirlSource) getName + " v" + m.swirlLineNum(delegate) else
      "<nv " + getName + " " + this.getType.toString + " />"
    }
  }

  case class Constant(d: Symbol,
                      literal: Literal,
                      m: SWANMethod,
                      unbalanced: Edge[Statement, Statement] = null) extends SWANVal(m, d, unbalanced) with Val.NewExpr {

    override def asUnbalanced(edge: Edge[Statement, Statement]): Val = m.addVal(Constant(delegate, literal, m, edge))

    override def hashCode(): Int = super.hashCode() + Objects.hashCode(literal)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: Constant => super.eq(other) && Objects.equals(other.literal, literal)
        case _ => false
      }
    }

    override def toString: String = {
      if (m.hasSwirlSource) getName + " v" + m.swirlLineNum(delegate) else {
        "<nlv " + getName + " " + {
          literal match {
            case Literal.string(value) => value
            case Literal.int(value) => value
            case Literal.float(value) => value
          }
        } + " " + this.getType.toString + " />"
      }
    }
  }

  case class FunctionRef(d: Symbol,
                         ref: String,
                         m: SWANMethod,
                         unbalanced: Edge[Statement, Statement] = null) extends SWANVal(m, d, unbalanced) with Val.NewExpr {

    override def asUnbalanced(edge: Edge[Statement, Statement]): Val = m.addVal(FunctionRef(delegate, ref, m, edge))

    override def hashCode(): Int = super.hashCode() + Objects.hashCode(ref)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: FunctionRef => super.equals(other) && Objects.equals(other.ref, ref)
        case _ => false
      }
    }

    override def toString: String = {
      if (m.hasSwirlSource) getName + " v" + m.swirlLineNum(delegate) else
      "<frv " + getName + " " + getType.toString + " f=" + ref + " />"
    }
  }

  case class BuiltinFunctionRef(d: Symbol,
                                ref: String,
                                m: SWANMethod,
                                unbalanced: Edge[Statement, Statement] = null) extends SWANVal(m, d, unbalanced) with Val.NewExpr {

    override def asUnbalanced(edge: Edge[Statement, Statement]): Val = m.addVal(BuiltinFunctionRef(delegate, ref, m, edge))

    override def hashCode(): Int = super.hashCode() + Objects.hashCode(ref)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: BuiltinFunctionRef => super.equals(other) && Objects.equals(other.ref, ref)
        case _ => false
      }
    }

    override def toString: String = {
      if (m.hasSwirlSource) getName + " v" + m.swirlLineNum(delegate) else
        "<bfrv " + getName + " " + getType.toString + " f=" + ref + " />"
    }
  }

  case class DynamicFunctionRef(d: Symbol,
                                index: String,
                                m: SWANMethod,
                                unbalanced: Edge[Statement, Statement] = null) extends SWANVal(m, d, unbalanced) with Val.NewExpr {

    override def asUnbalanced(edge: Edge[Statement, Statement]): Val = m.addVal(DynamicFunctionRef(delegate, index, m, edge))

    override def hashCode(): Int = super.hashCode() + Objects.hashCode(index)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: DynamicFunctionRef => super.equals(other) && Objects.equals(other.index, index)
        case _ => false
      }
    }

    override def toString: String = {
      if (m.hasSwirlSource) getName + " v" + m.swirlLineNum(delegate) else
        "<dfrv " + getName + " " + getType.toString + " i=" + index + " />"
    }
  }
}