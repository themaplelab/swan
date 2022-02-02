/*
 * Copyright (c) 2022 the SWAN project authors. All rights reserved.
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

package ca.ualberta.maple.swan.spds.cg.pa

import ca.ualberta.maple.swan.ir.FunctionAttribute
import ca.ualberta.maple.swan.spds.structures.{SWANCallGraph, SWANMethod, SWANStatement, SWANVal}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

// TODO: Track lookup length
class MatrixUnionFind(callGraph: SWANCallGraph) {

  class Entry(val index: Int,
              var rep: Int,
              var largest: Int,
              var size: Int,
              val set: mutable.BitSet)

  private val fields: mutable.HashMap[(SWANVal, String), SWANVal] = new mutable.HashMap()
  private var allocBoundary: Int = 0
  private var bitSetSize: Int = 0
  private val itv: mutable.HashMap[Int, SWANVal] = new mutable.HashMap()
  val m: mutable.HashMap[SWANVal, Entry] = {
    val m = new mutable.HashMap[SWANVal, Entry]()
    val vals = new ArrayBuffer[SWANVal]()
    callGraph.methods.foreach(m => {
      m._2.allValues.foreach(v => {
        v._2 match {
          case s: SWANVal.Simple => vals.append(s)
          case s: SWANVal.FunctionRef => vals.prepend(s)
          case s: SWANVal.BuiltinFunctionRef => vals.prepend(s)
          case s: SWANVal.DynamicFunctionRef => vals.prepend(s)
          case s: SWANVal.Argument => vals.append(s)
          case _ =>
        }
      })
    })
    bitSetSize = vals.length
    allocBoundary = vals.lastIndexWhere(_.isNewExpr)
    vals.zipWithIndex.foreach(v => {
      val b = new mutable.BitSet(bitSetSize)
      b.add(v._2)
      m.put(v._1, new Entry(v._2, v._2, v._2, 1, b))
      itv.put(v._2, v._1)
    })
    m
  }

  def query(x: SWANVal): mutable.HashSet[SWANVal] = {
    val r = mutable.HashSet.empty[SWANVal]
    val rep = find(x)
    if (rep.index <= allocBoundary) {
      rep.set.takeWhile(v => {
        r.add(itv(v))
        v <= allocBoundary
      })
    }
    r
  }

  // TODO: Static fields
  def handleStatement(cg: SWANCallGraph, stmt: SWANStatement): Unit = {
    stmt match {
      case s: SWANStatement.Assign => {
        union(s.getLeftOp.asInstanceOf[SWANVal], s.getRightOp.asInstanceOf[SWANVal])
      }
      case s: SWANStatement.FieldLoad => {
        // x = y.z
        val xVal = s.getLeftOp.asInstanceOf[SWANVal]
        val yVal = s.getFieldLoad.getX.asInstanceOf[SWANVal]
        if (m.contains(xVal) && m.contains(yVal)) {
          val xRoot = itv(find(xVal).index)
          val yRoot = itv(find(yVal).index)
          val z = s.getLoadedField.toString
          fields.get((yRoot, z)) match {
            case Some(value) => union(value, xRoot)
            // Add a field, but really this is not necessary due to revisiting
            case None => fields.put((yRoot, z), xRoot)
          }
        }
      }
      case s: SWANStatement.FieldWrite => {
        // x.y = z
        val xVal = s.getFieldStore.getX.asInstanceOf[SWANVal]
        val zVal = s.getRightOp.asInstanceOf[SWANVal]
        if (m.contains(xVal) && m.contains(zVal)) {
          val xRoot = itv(find(xVal).index)
          val y = s.getWrittenField.toString
          val zRoot = itv(find(zVal).index)
          fields.get((xRoot, y)) match {
            case Some(value) => union(value, zRoot)
            case None => fields.put((xRoot, y), zRoot)
          }
        }
      }
      case s: SWANStatement.Return => {
        cg.edgesInto(stmt.getMethod).forEach(edge => {
          union(edge.src().getLeftOp.asInstanceOf[SWANVal], s.getReturnOp.asInstanceOf[SWANVal])
        })
      }
      case s: SWANStatement.Yield => {
        cg.edgesInto(stmt.getMethod).forEach(edge => {
          union(edge.src().getLeftOp.asInstanceOf[SWANVal], s.getReturnOp.asInstanceOf[SWANVal])
        })
      }
      case _ =>
    }
  }

  def handleCGEdge(from: SWANStatement.ApplyFunctionRef, to: SWANMethod): Unit = {
    // stubs often don't have the correct # of args
    if (to.delegate.attribute.isEmpty || to.delegate.attribute.get != FunctionAttribute.stub) {
      var index = 0
      from.invokeExpr.args.forEach(arg => {
        union(arg.asInstanceOf[SWANVal], to.getParameterLocals.get(index).asInstanceOf[SWANVal])
        index += 1
      })
    }
  }

  private def find(x: SWANVal): Entry = {
    val entry = m(x)
    val largest = m(itv(entry.largest))
    var rep = m(itv(largest.rep))
    while (rep.index != rep.rep) {
      rep = m(itv(rep.rep))
    }
    rep
  }

  private def union(x: SWANVal, y: SWANVal): Unit = {
    if (m.contains(x) && m.contains(y)) {
      val xRep = find(x)
      val yRep = find(y)
      if (xRep.size >= yRep.size) {
        doUnion(xRep, y, yRep)
      } else {
        doUnion(yRep, x, xRep)
      }
    }
  }

  private def doUnion(xRep: Entry, y: SWANVal, yRep: Entry): Unit = {
    xRep.set |= yRep.set
    xRep.size += yRep.size
    if (yRep.rep < xRep.rep) {
      xRep.rep = yRep.rep
    }
    m(y).largest = xRep.index
    yRep.largest = xRep.index
  }
}
