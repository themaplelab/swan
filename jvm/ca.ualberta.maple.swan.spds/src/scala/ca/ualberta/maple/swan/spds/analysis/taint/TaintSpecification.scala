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

package ca.ualberta.maple.swan.spds.analysis.taint

import java.io.{File, FileWriter}

import ca.ualberta.maple.swan.spds.analysis.taint.TaintSpecification.JSONMethod

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.util.{Failure, Success, Try}

class TaintSpecification(val name: String,
                         val desc: String,
                         val advice: String,
                         // (<name>, <description>)
                         val sources: mutable.HashMap[String,JSONMethod],
                         val sinks: mutable.HashMap[String,JSONMethod],
                         val sanitizers: mutable.HashMap[String,JSONMethod]) {

  override def toString: String = {
    val sb = new StringBuilder()
    sb.append("  name: ")
    sb.append(name)
    sb.append("\n  description: ")
    sb.append(desc)
    sb.append("\n  advice: ")
    sb.append(advice)
    sb.append("\n  sources:\n")
    sources.foreach(src => {
      sb.append(s"    name: ${src._2.name}\n")
      sb.append(s"    description: ${src._2.description}\n")
      if (src._2.args.nonEmpty) sb.append(s"    args: ${src._2.args.get.toString()}\n")
    })
    sb.append("  sinks:\n")
    sinks.foreach(sink => {
      sb.append(s"    name: ${sink._2.name}\n")
      sb.append(s"    description: ${sink._2.description}\n")
      if (sink._2.args.nonEmpty) sb.append(s"    args: ${sink._2.args.get.toString()}\n")
    })
    if (sanitizers.nonEmpty) {
      sb.append("  sanitizers:\n")
      sanitizers.foreach(san => {
        sb.append(s"    name: ${san._2.name}\n")
        sb.append(s"    description: ${san._2.description}\n")
        if (san._2.args.nonEmpty) sb.append(s"    args: ${san._2.args.get.toString()}\n")
      })
    }
    sb.toString()
  }
}

object TaintSpecification {

  private def jsonVal(v: ujson.Value, s: String): ujson.Value = {
    Try(v(s)) match {
      case Failure(_) => throw new RuntimeException("JSON does not contain expected \"" + s + "\" field")
      case Success(value) => value
    }
  }

  class JSONMethod(val name: String, val description: String, val args: Option[ArrayBuffer[Int]], val regex: Boolean)

  def parse(file: File): ArrayBuffer[TaintSpecification] = {
    val buffer = Source.fromFile(file)
    val jsonString = buffer.getLines().mkString
    buffer.close()
    val data = ujson.read(jsonString)
    val specs = new ArrayBuffer[TaintSpecification]
    data.arr.foreach(v => {
      val name = jsonVal(v, "name")
      val desc = jsonVal(v, "description")
      val advice = jsonVal(v, "advice")
      def convert(v: ujson.Value): (String, JSONMethod) = { (jsonVal(v, "name").str,
        new JSONMethod(
          jsonVal(v, "name").str,
          jsonVal(v, "description").str,
          Try(Some(v("args").arr.map(v => v.num.toInt))).getOrElse(None),
          Try(jsonVal(v, "regex").bool).getOrElse(false))) }
      val sources = mutable.HashMap.from(jsonVal(v, "sources").arr.map(convert))
      val sinks = mutable.HashMap.from(jsonVal(v, "sinks").arr.map(convert))
      val sanitizers =
        Try(mutable.HashMap.from(v("sanitizers").arr.map(convert))).getOrElse(new mutable.HashMap[String,JSONMethod])
      val spec = new TaintSpecification(name.str, desc.str, advice.str, sources, sinks, sanitizers)
      specs.append(spec)
    })
    specs
  }

  def writeResults(f: File, allResults: ArrayBuffer[TaintResults]): Unit = {
    val fw = new FileWriter(f)
    try {
      val r = new ArrayBuffer[ujson.Obj]
      allResults.foreach(results => {
        val json = ujson.Obj("name" -> results.spec.name)
        json("description") = results.spec.desc
        json("advice") = results.spec.advice
        val paths = new ArrayBuffer[ujson.Value]
        results.paths.foreach(path => {
          val src = ujson.Obj()
          src("name") = path.sourceName
          src("description") = path.source.description
          val jsonPath = ujson.Obj("source" -> src)

          val sink = ujson.Obj()
          sink("name") = path.sinkName
          sink("description") = path.sink.description
          jsonPath("sink") = sink

          jsonPath("path") = path.nodes.filter(_._2.nonEmpty).map(_._2.get.toString)
          paths.append(jsonPath)
        })
        json("paths") = paths
        r.append(json)
      })
      val finalJson = ujson.Value(r)
      fw.write(finalJson.render(2))
    } finally {
      fw.close()
    }
  }
}