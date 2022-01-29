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

// TODO: Check if node order matters, especially for path compression
// TODO: See if there is an optimization to get alloc nodes faster
//       instead of going over all nodes in the tree
class UnionFind {

  private val fields: mutable.HashMap[(SWANVal, String), SWANVal] = new mutable.HashMap()

  private val children = new mutable.HashMap[SWANVal, mutable.HashSet[SWANVal]]() {
    override def default(key: SWANVal): mutable.HashSet[SWANVal] = {
      get(key: SWANVal) match {
        case Some(value) => value
        case None => {
          val v = mutable.HashSet(key)
          put(key, v)
          v
        }
      }
    }
  }

  private val parents = new mutable.HashMap[SWANVal, SWANVal]() {
    override def default(key: SWANVal): SWANVal = {
      get(key: SWANVal) match {
        case Some(value) => value
        case None => {
          put(key, key)
          key
        }
      }
    }
  }

  private val rank = new mutable.HashMap[SWANVal, Int]() {
    override def default(key: SWANVal): Int = {
      get(key: SWANVal) match {
        case Some(value) => value
        case None => {
          put(key, 1)
          1
        }
      }
    }
  }

  def query(x: SWANVal): mutable.HashSet[SWANVal] = {
    val r = mutable.HashSet.empty[SWANVal]
    val xRoot = find(x)
    val worklist = new mutable.Queue[SWANVal]()
    worklist.append(xRoot)
    while (worklist.nonEmpty) {
      val c = worklist.dequeue()
      r.add(c)
      children(c).foreach(child => {
        if (c != child) {
          worklist.enqueue(child)
        }
      })
    }
    r
  }

  def handleStatement(cg: SWANCallGraph, stmt: SWANStatement): Unit = {
    stmt match {
      case s: SWANStatement.Allocation =>
        // Not really needed, but here for completeness
        union(s.getLeftOp.asInstanceOf[SWANVal], s.getRightOp.asInstanceOf[SWANVal])
      case s: SWANStatement.FunctionRef =>
        union(s.getLeftOp.asInstanceOf[SWANVal], s.getRightOp.asInstanceOf[SWANVal])
      case s: SWANStatement.BuiltinFunctionRef =>
        union(s.getLeftOp.asInstanceOf[SWANVal], s.getRightOp.asInstanceOf[SWANVal])
      case s: SWANStatement.DynamicFunctionRef =>
        union(s.getLeftOp.asInstanceOf[SWANVal], s.getRightOp.asInstanceOf[SWANVal])
      case s: SWANStatement.Assign => {
        union(s.getLeftOp.asInstanceOf[SWANVal], s.getRightOp.asInstanceOf[SWANVal])
      }
      case s: SWANStatement.FieldLoad => {
        // x = y.z
        val xRoot = find(s.getLeftOp.asInstanceOf[SWANVal])
        val yRoot = find(s.getFieldLoad.getX.asInstanceOf[SWANVal])
        val z = s.getLoadedField.toString
        fields.get((yRoot, z)) match {
          case Some(value) => union(value, xRoot)
          // Add a field, but really this is not necessary due to revisiting
          case None => fields.put((yRoot, z), xRoot)
        }
      }
      case s: SWANStatement.FieldWrite => {
        // x.y = z
        val xRoot = find(s.getFieldStore.getX.asInstanceOf[SWANVal])
        val y = s.getWrittenField.toString
        val zRoot = find(s.getRightOp.asInstanceOf[SWANVal])
        fields.get((xRoot, y)) match {
          case Some(value) => union(value, zRoot)
          case None => fields.put((xRoot, y), zRoot)
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

  private def union(x: SWANVal, y: SWANVal): Unit = {
    val xRoot = find(x)
    val yRoot = find(y)
    if (xRoot != yRoot) {
      val xRank = rank(x)
      val yRank = rank(y)
      if (xRank > yRank) {
        parents(yRoot) = xRoot
        children(xRoot).add(yRoot)
      } else if (xRank < yRank) {
        parents(xRoot) = yRoot
        children(yRoot).add(xRoot)
      } else {
        rank(xRoot) += 1
        parents(yRoot) = xRoot
        children(xRoot).add(yRoot)
      }
    }
  }

  private def find(x: SWANVal): SWANVal = {
    val xParent = parents(x)
    if (xParent == x) x else {
      val xRoot = find(xParent)
      parents(x) = xRoot
      children(xRoot).add(x)
      xRoot
    }
  }
}
