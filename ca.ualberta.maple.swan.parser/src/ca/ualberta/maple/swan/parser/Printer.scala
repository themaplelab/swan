/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.parser

class Printer {

  var description: String = ""
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

  def print[T](when: Boolean = true, i: T): Unit = {
    print(when, i.toString)
  }

  def print(when: Boolean, s: String): Unit = {
    if (!when) return
    val lines = s.split('\n').iterator
    while(lines.hasNext) {
      val line = lines.next
      if (!indented && !line.isEmpty) {
        description += indentation
        indented = true
      }
      description += line

      if (!lines.hasNext) {
        description += "\n"
        indented = false
      }
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

  def print[T, S <: Iterable[T]](xs: S, fn: T => Unit): Unit = {
    for (x <- xs) {
      fn(x)
    }
  }

  def print[T, S <: Iterable[T]](xs: S, sep: String, fn: T => Unit): Unit = {
    var needSep = false
    for (x <- xs) {
      if (needSep) {
        print(sep)
      }
      needSep = true
      fn(x)
    }
  }

  def print[T, S <: Iterable[T]](whenEmpty: Boolean = true, pre: String, xs: S, sep: String, suf: String, fn: (T) => Unit): Unit = {
    if (!(xs.nonEmpty || whenEmpty)) return
    print(pre)
    print(xs, sep, fn)
    print(suf)
  }

  def print(s : String): Unit = {
    System.out.print(s) // replace?
  }

  def literal(b: Boolean): Unit = {
    print(b.toString) // TODO: Verify output format
  }

  def literal(f: Float): Unit = {
    print(f.toString) // TODO: Verify output format
  }

  def literal(n: Int): Unit = {
    print(n.toString)
  }

  def hex(n: Int): Unit = {
    print("0x" + String.format("%X", n))
  }

  def literal(s: String): Unit = {
    print("\"")
    print(s)
    print("\"")
  }

  override def toString: String = {
    description
  }

}
