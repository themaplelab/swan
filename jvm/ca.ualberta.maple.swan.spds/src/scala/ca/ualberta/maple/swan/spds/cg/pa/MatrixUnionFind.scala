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

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

// TODO: Track lookup length
// TODO: itv lookup takes long, just use Int instead of SWANVal where possible
class MatrixUnionFind(callGraph: SWANCallGraph) {

  class Entry(val index: Int,
              var rep: Int,
              var largest: Int,
              var size: Int,
              var set: mutable.BitSet /* can be null */)

  private val fields = new mutable.HashMap[SWANVal, mutable.HashMap[String, SWANVal]] {
    override def default(key: SWANVal): mutable.HashMap[String, SWANVal] = {
      get(key: SWANVal) match {
        case Some(value) => value
        case None => {
          val v = new mutable.HashMap[String, SWANVal]
          put(key,v)
          v
        }
      }
    }
  }
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
          case _ =>
        }
      })
    })
    bitSetSize = vals.length
    allocBoundary = vals.lastIndexWhere(_.isNewExpr)
    vals.zipWithIndex.foreach(v => {
      val b = new mutable.BitSet()
      //b.add(v._2)
      m.put(v._1, new Entry(v._2, v._2, v._2, 1, b))
      itv.put(v._2, v._1)
    })
    m
  }

  def query(x: SWANVal): mutable.HashSet[SWANVal] = {
    val r = mutable.HashSet.empty[SWANVal]
    if (m.contains(x)) {
      val rep = m(itv(find(x).largest))
      if (rep.rep <= allocBoundary) {
        rep.set.takeWhile(v => {
          r.add(itv(v))
          v <= allocBoundary
        })
      }
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
          val yRoot = itv(find(yVal).largest)
          val z = s.getLoadedField.toString
          val f = fields(yRoot)
          f.get(z) match {
            case Some(value) => union(value, xRoot)
            // Add a field, but really this is not necessary due to revisiting
            case None => //f.put(z, xRoot)
          }
        }
      }
      case s: SWANStatement.FieldWrite => {
        // x.y = z
        val xVal = s.getFieldStore.getX.asInstanceOf[SWANVal]
        val zVal = s.getRightOp.asInstanceOf[SWANVal]
        if (m.contains(zVal)) {
          if (!m.contains(xVal)) {
            m.put(xVal, new Entry(bitSetSize, bitSetSize, bitSetSize, 1, new mutable.BitSet()))
            itv.put(bitSetSize, xVal)
            bitSetSize += 1
          }
          val xRoot = itv(find(xVal).index)
          val y = s.getWrittenField.toString
          val zRoot = itv(find(zVal).index)
          val f = fields(xRoot)
          f.get(y) match {
            case Some(value) => union(value, zRoot)
            case None => f.put(y, zRoot)
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
      // TODO: Why does this happen?
      if (from.invokeExpr.args.size() <= to.getParameterLocals.size()) {
        from.invokeExpr.args.forEach(arg => {
          union(arg.asInstanceOf[SWANVal], to.getParameterLocals.get(index).asInstanceOf[SWANVal])
          index += 1
        })
      }
    }
  }

  private def find(x: SWANVal): Entry = {
    val entry = m(x)
    // isEmpty is slow, adding each time should be fine
    if (entry.set != null /*&& entry.set.isEmpty*/ ) {
      entry.set.add(entry.index)
    }
    var rep = m(itv(entry.rep))
    var depth = 0
    while (m(itv(rep.largest)).rep != rep.index) {
      val largest = m(itv(rep.largest))
      rep = m(itv(largest.rep))
      depth += 1
    }
    rep
  }

  private def union(x: SWANVal, y: SWANVal): Unit = {
    if (m.contains(x) && m.contains(y)) {
      val xRep = find(x)
      val yRep = find(y)
      if (xRep.index != yRep.index) {
        if (xRep.size >= yRep.size) {
          doUnion(xRep, y, yRep)
        } else {
          doUnion(yRep, x, xRep)
        }
      }
    }
  }

  private def doUnion(xRep: Entry, y: SWANVal, yRep: Entry): Unit = {
    val xLargest = m(itv(xRep.largest))
    val yLargest = m(itv(yRep.largest))
    xLargest.size += yLargest.size
    xLargest.set |= yLargest.set
    yRep.set = null
    yRep.size = 1
    if (yLargest.rep < xLargest.rep) {
      xLargest.rep = yLargest.rep
    }
    m(y).largest = xLargest.index
    yRep.largest = xLargest.index
    val yVal = itv(yRep.index) // TODO: Should be largest?
    val xVal = itv(xRep.index) // TODO: Should be largest?
    if (fields.contains(yVal)) {
      fields(xVal).addAll(fields(yVal))
      fields.remove(yVal)
    }
  }

  // Not for big programs!
  def printToDot(): String = {
    val dot: StringBuilder = new StringBuilder("digraph G {\n")
    val reps = new mutable.HashSet[Int]()
    this.m.foreach(x => {
      reps.add(x._2.rep)
    })
    def printEntry(x: (SWANVal, Entry)): String = {
      val s = "\"" + { if (x._1.getVariableName.startsWith("%")) "\\" else "" } + s"${x._1.getVariableName}, " +
        s"${ { x._1 match {
        case f: SWANVal.FunctionRef => s"f=${f.ref}"
        case f: SWANVal.BuiltinFunctionRef => s"bf=${f.ref}"
        case f: SWANVal.DynamicFunctionRef => s"df=${f.index}"
        case _ => x._1.getType.toString
      } }}, |${x._2.size}|" + "\""
      if (reps.contains(x._2.index)) {
        s"{$s [color=red]}"
      } else s
    }
    this.m.foreach(x => {
      if (x._2.largest != x._2.index) {
        val largest = itv(x._2.largest)
        dot.append(s"  ${printEntry(x)} -> ${printEntry((largest, m(largest)))} [color=blue, arrowhead=box]\n")
      }
      if (x._2.rep != x._2.index) {
        val rep = itv(x._2.rep)
        dot.append(s"  ${printEntry(x)} -> ${printEntry((rep, m(rep)))} [color=red]\n")
      }
      if (x._2.set != null) {
        x._2.set.foreach(i => {
          val to = itv(i)
          if (x._2.index != m(to).index) {
            dot.append(s"  ${printEntry(x)} -> ${printEntry((to, m(to)))}\n")
          }
        })
      }
    })
    this.fields.foreach(f => {
      f._2.foreach(t => {
        dot.append(s"  ${printEntry(f._1, m(f._1))} -> ${printEntry((t._2, m(t._2)))} [label=${t._1}, style=dotted]\n")
      })
    })
    dot.append("  subgraph cluster_legend {\n")
    dot.append("    label=\"Legend\"\n")
    dot.append("    graph[style=solid]\n")
    dot.append("    a -> b [label=largest, color=blue, arrowhead=box]\n")
    dot.append("    c -> {rep [color=red]} [label=rep, color=red]\n")
    dot.append("    e -> f [label=child]\n")
    dot.append("    g -> h [label=field, style=dotted]\n")
    dot.append("  }\n")
    dot.append("label=\"Self-relationships are ommited.\"\n")
    dot.append("}")
    val selection = new StringSelection(dot.toString())
    Toolkit.getDefaultToolkit.getSystemClipboard.setContents(selection, selection)
    dot.toString()
  }
}
