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

import ca.ualberta.maple.swan.spds.structures.SWANVal

import scala.collection.mutable
import scala.collection.Set

class UnionFindSets[E] {
  // Children should not contain singleton sets
  // If an E has no pro
  private val properChildren: mutable.MultiDict[E, E] = mutable.MultiDict.empty
  private val repsOf: mutable.HashMap[E, E] = mutable.HashMap.empty

  def find(v: E): E = {
    repsOf.get(v) match {
      case Some(v1) =>
        val vn = find(v1)
        repsOf.put(v,vn)
        vn
      case None => v
    }
  }

  def getSet(v: E): Set[E] = {
    val v1 = find(v)
    val values: Iterable[E] = Set.empty[E]
    properChildren.get(v1) + v1
  }

  def union(v1: E, v2: E): Boolean = {
    val v11 = find(v1)
    val v22 = find(v2)
    if (v11 == v22) {
      return false
    }
    getSet(v22).foreach(c => properChildren.addOne(v11,c))
    properChildren.removeKey(v22)
    repsOf.update(v22,v11)
    true
  }
}
