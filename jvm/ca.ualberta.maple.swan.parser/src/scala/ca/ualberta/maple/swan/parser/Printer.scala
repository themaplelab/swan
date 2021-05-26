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

package ca.ualberta.maple.swan.parser

import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.{break, breakable}

class Printer {

  var line: Int = 1
  var description: StringBuilder = new StringBuilder()
  private var indentation: String = ""
  private var indented: Boolean = false

  def indent(): Unit = {
    val count = indentation.length + 2
    indentation = new String(" " * count)
  }

  def unindent(): Unit = {
    val count = Math.max(indentation.length - 2, 0)
    indentation = new String(" " * count)
  }

  def printNewline(): Unit = {
    line += 1
    description.append("\n")
    indented = false
  }

  def print[T](i: T, when: Boolean = true): Unit = {
    if (!when) return
    val s = i.toString

    // "\n*"
    var allNewLines = s.nonEmpty
    s.foreach(char => {
      if (char != '\n') {
        allNewLines = false
      }
    })
    if (allNewLines) {
      description.append("\n" * s.length)
      this.line += s.length
      indented = false
      return
    }
    // "mystring\n"
    if (s.count(_ == '\n') == 1 && s.endsWith("\n")) {
      if (!indented) {
        description.append(indentation)
      }
      description.append(s)
      this.line += 1
      indented = false
      return
    }
    // "string0\nstring1\nstring2[\n]"
    this.line += s.count(_ == '\n')
    val lines = s.split('\n').iterator
    var lineIdx = 0
    while(lines.hasNext) {
      val line = lines.next()
      if (!indented && !line.isEmpty) {
        description.append(indentation)
        indented = true
      }
      description.append(line)
      if (lineIdx < lines.length - 1) {
        description.append("\n")
        indented = false
      }
      lineIdx += 1
    }
  }

  def print[T](x: Option[T], fn: T => Unit): Unit = {
    if (x.isEmpty) return
    fn(x.get)
  }

  def print[T](pre: String, x: Option[T], fn: T => Unit): Unit = {
    if (x.isEmpty) return
    print(pre)
    fn(x.get)
  }

  def print[T](x: Option[T], suf: String, fn: T => Unit): Unit = {
    if (x.isEmpty) return
    fn(x.get)
    print(suf)
  }

  def print[T](xs: ArrayBuffer[T], fn: T => Unit): Unit = {
    for (x <- xs) {
      fn(x)
    }
  }

  def print[T](xs: ArrayBuffer[T], sep: String, fn: T => Unit): Unit = {
    var needSep = false
    for (x <- xs) {
      if (needSep) {
        print(sep)
      }
      needSep = true
      fn(x)
    }
  }

  def print[T](whenEmpty: Boolean, pre: String, xs: ArrayBuffer[T], sep: String, suf: String, fn: T => Unit): Unit = {
    if (!(xs.nonEmpty || whenEmpty)) return
    print(pre)
    print(xs, sep, fn)
    print(suf)
  }

  def getCol: Int = {
    var count = indentation.length
    breakable {
      for (i <- description.length() - 1 to 0 by -1) {
        if (description(i) == '\n') {
          break()
        }
        count += 1
      }
    }
    count
  }

  def literal(b: Boolean): Unit = {
    print(b.toString)
  }

  def literal(f: Float): Unit = {
    print(f.toString)
  }

  def literal(n: BigInt): Unit = {
    print(n.toString)
  }

  def literal(n: Int): Unit = {
    print(n.toString)
  }

  def hex(n: Int): Unit = {
    print("0x" + String.format("%X", n))
  }

  def literal(s: String, naked: Boolean = false): Unit = {
    print("\"", when = !naked)
    print(s)
    print("\"", when = !naked)
  }

  override def toString: String = {
    description.toString()
  }

}
