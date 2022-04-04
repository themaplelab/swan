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

package ca.ualberta.maple.swan.test

import ca.ualberta.maple.swan.drivers.Driver
import ca.ualberta.maple.swan.ir.{CanInstructionDef, Position}
import ca.ualberta.maple.swan.spds.analysis.crypto.CryptoAnalysis
import ca.ualberta.maple.swan.spds.analysis.taint.TaintResults.Path
import ca.ualberta.maple.swan.spds.analysis.taint.TaintSpecification.JSONMethod
import ca.ualberta.maple.swan.spds.analysis.taint.{TaintResults, TaintSpecification}
import ca.ualberta.maple.swan.spds.analysis.typestate.{TypeStateJSONProgrammaticSpecification, TypeStateResults}
import ca.ualberta.maple.swan.test.AnnotationChecker.Annotation
import org.apache.commons.io.FileExistsException
import picocli.CommandLine
import picocli.CommandLine.{ArgGroup, Command, Parameters}
import typestate.finiteautomata.State

import java.io.File
import java.nio.file.{Files, Paths}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.util.Try

object AnnotationChecker {

  def main(args: Array[String]): Unit = {
    val exitCode = new CommandLine(new AnnotationChecker).execute(args:_*)
    System.exit(exitCode)
  }

  private class Annotation(val name: String, val tpe: String, val status: Option[String], val line: Int)

  class ExtraSourceDir() {
    @picocli.CommandLine.Option(names = Array("--src-dir"),
      required = false,
      description = Array("Extra source directories to check for annotations."))
    var dir: String = null
  }
}

@Command(name = "SWAN Annotation Checker", mixinStandardHelpOptions = true)
class AnnotationChecker extends Runnable {

  @ArgGroup(exclusive = false, multiplicity="0..*")
  private val extraSourceDirs: Array[AnnotationChecker.ExtraSourceDir] = Array.empty[AnnotationChecker.ExtraSourceDir]

  @Parameters(arity = "1", paramLabel = "swan-dir", description = Array("swan-dir to process."))
  private val inputFile: File = null

  override def run(): Unit = {
    if (!inputFile.exists()) {
      throw new FileExistsException("swan-dir does not exist")
    }
    val taintResultsFile = new File(Paths.get(inputFile.getPath, Driver.taintAnalysisResultsFileName).toUri)
    if (taintResultsFile.exists()) {
      checkTaintAnalysisResults(taintResultsFile)
    }
    val typeStateResultsFile = new File(Paths.get(inputFile.getPath, Driver.typeStateAnalysisResultsFileName).toUri)
    if (typeStateResultsFile.exists()) {
      checkTypeStateAnalysisResults(typeStateResultsFile)
    }
    val cryptoResultsFile = new File(Paths.get(inputFile.getPath, Driver.cryptoAnalysisResultsFileName).toUri)
    if (cryptoResultsFile.exists()) {
      checkCryptoResults(cryptoResultsFile)
    }
  }

  private def getSourceDirs: ArrayBuffer[java.nio.file.Path] = {
    val sourceDir = new File(Paths.get(inputFile.getPath, "src").toUri)
    if (!sourceDir.exists()) {
      throw new FileExistsException("src directory does not exist in swan-dir")
    }
    val paths = ArrayBuffer.empty[java.nio.file.Path]
    Files.walk(sourceDir.toPath).filter(Files.isRegularFile(_)).forEach(p => paths.append(p))
    extraSourceDirs.foreach(s => if (s != null) {
      Files.walk(Paths.get(s.dir)).filter(Files.isRegularFile(_)).forEach(p => paths.append(p))
    })
    paths
  }

  private def checkTaintAnalysisResults(resultsFile: File): Unit = {
    def printErr(s: String, exit: Boolean = false): Unit = {
      System.err.println("[Taint] " + s)
      if (exit) System.exit(1)
    }
    val results = getTaintResults(resultsFile)
    var failure = false
    getSourceDirs.foreach(p => {
      val f = new File(p.toUri)
      val buffer = Source.fromFile(f)
      val annotations = new mutable.HashMap[Int, ArrayBuffer[Annotation]]
      buffer.getLines().zipWithIndex.foreach(l => {
        val line = l._1
        val idx = l._2 + 1
        // Ignore lines that are completely commented out (for disabled tests)
        if (line.contains("//") && !line.startsWith("//")) {
          line.split("//").foreach(c => {
            if (c.startsWith("!")) {
              val components = c.trim.split(" ")(0).split("!")
              val name = components(1)
              val tpe = components(2)
              if (tpe != "sink" && tpe != "source") {
                printErr("Invalid annotation type: " + tpe + " at line " + idx + " in\n  " + f.getName, exit = true)
              }
              if (!annotations.contains(idx)) { annotations.put(idx, new ArrayBuffer[Annotation]())}
              var status: Option[String] = None
              if (components.length > 3) {
                status = Some(components(3))
                if (status.get != "fn" && status.get != "fp") {
                  printErr("invalid status type: " + status + " at line " + idx + " in\n  " + f.getName, exit = true)
                }
              }
              annotations(idx).append(new Annotation(name, tpe, status, idx))
            }
          })
        }
      })
      buffer.close()
      results.foreach(r => {
        r.paths.foreach(p => {
          def handle(pos: Position, tpe: String): Unit = {
            if (pos.path.endsWith(f.getName)) { // copied file has different path than original
              if (annotations.contains(pos.line)) {
                var idx = -1
                val annot = annotations(pos.line)
                annot.zipWithIndex.foreach(a => {
                  if (a._1.name == r.spec.name && a._1.tpe == tpe) {
                    idx = a._2
                  }
                })
                if (idx > -1) {
                  val a = annot(idx)
                  if (a.status.isEmpty || a.status.get == "fp") {
                    annot.remove(idx)
                  } else if (a.status.get == "fn") {
                    failure = true
                    printErr("Annotation is not an FN: //!" + a.name + "!" + a.tpe + "!fn" + " on line " + a.line)
                  }
                  if (annot.isEmpty) {
                    annotations.remove(pos.line)
                  }
                }
              } else {
                failure = true
                printErr("Missing " + tpe + " annotation for " + pos.toString)
              }
            }
          }
          handle(p.nodes(0)._2.get, "source")
          handle(p.nodes.last._2.get, "sink")
        })
      })
      annotations.foreach(v => {
        v._2.foreach(a => {
          if (a.status.isEmpty || a.status.get != "fn") {
            failure = true
            System.err.println("No matching path node for annotation: //!" + a.name + "!" + a.tpe +
              { if (a.status.nonEmpty) "!" + a.status.get else ""} + " on line " + a.line)
          }
        })
      })
    })
    if (failure) System.exit(1)
  }

  private def checkTypeStateAnalysisResults(resultsFile: File): Unit = {
    def printErr(s: String, exit: Boolean = false): Unit = {
      System.err.println("[TypeState] " + s)
      if (exit) System.exit(1)
    }
    val results = getTypeStateResults(resultsFile)
    var failure = false
    getSourceDirs.foreach(p => {      val f = new File(p.toUri)
      val buffer = Source.fromFile(f)
      val annotations = new mutable.HashMap[Int, ArrayBuffer[Annotation]]
      buffer.getLines().zipWithIndex.foreach(l => {
        val line = l._1
        val idx = l._2 + 1
        if (line.contains("//")) {
          line.split("//").foreach(c => {
            if (c.startsWith("?")) {
              val components = c.trim.split(" ")(0).split("\\?")
              val name = components(1)
              val tpe = components(2)
              if (tpe != "error") {
                printErr("Invalid annotation type: " + tpe + " at line " + idx + " in\n  " + f.getName, exit = true)
              }
              if (!annotations.contains(idx)) { annotations.put(idx, new ArrayBuffer[Annotation]())}
              var status: Option[String] = None
              if (components.length > 3) {
                status = Some(components(3))
                if (status.get != "fn" && status.get != "fp") {
                  printErr("Invalid status type: " + status + " at line " + idx + " in\n  " + f.getName, exit = true)
                }
              }
              annotations(idx).append(new Annotation(name, tpe, status, idx))
            }
          })
        }
      })
      buffer.close()
      results.foreach(r => {
        r.errors.foreach(e => {
          val pos = e._1
          if (pos.path.endsWith(f.getName)) { // copied file has different path than original
            if (annotations.contains(pos.line)) {
              var idx = -1
              val annot = annotations(pos.line)
              annot.zipWithIndex.foreach(a => {
                if (a._1.name == r.spec.getName) {
                  idx = a._2
                }
              })
              if (idx > -1) {
                val a = annot(idx)
                if (a.status.isEmpty || a.status.get == "fp") {
                  annot.remove(idx)
                } else if (a.status.get == "fn") {
                  failure = true
                  printErr("Annotation is not an FN: //?" + a.name + "?" + a.tpe + "?fn" + " on line " + a.line)
                }
                if (annot.isEmpty) {
                  annotations.remove(pos.line)
                }
              }
            } else {
              failure = true
              printErr("Missing annotation for " + pos.toString)
            }
          }
        })
      })
      annotations.foreach(v => {
        v._2.foreach(a => {
          if (a.status.isEmpty || a.status.get != "fn") {
            failure = true
            printErr("No matching path node for annotation: //?" + a.name + "?" + a.tpe +
              { if (a.status.nonEmpty) "?" + a.status.get else "" } + " on line " + a.line)
          }
        })
      })
    })
    if (failure) System.exit(1)
  }

  private def checkCryptoResults(resultsFile: File): Unit = {
    def printErr(s: String, exit: Boolean = false): Unit = {
      System.err.println("[Crypto] " + s)
      if (exit) System.exit(1)
    }
    val results = getCryptoResults(resultsFile)
    var failure = false
    getSourceDirs.foreach(p => {
      val f = new File(p.toUri)
      val buffer = Source.fromFile(f)
      val annotations = new mutable.HashMap[Int, ArrayBuffer[Annotation]]
      buffer.getLines().zipWithIndex.foreach(l => {
        val line = l._1
        val idx = l._2 + 1
        if (line.contains("//")) {
          line.split("//").foreach(c => {
            if (c.startsWith("$")) {
              val components = c.trim.split(" ")(0).split("\\$")
              val name = components(1)
              val tpe = components(2)
              if (tpe != "error") {
                printErr("Invalid annotation type: " + tpe + " at line " + idx + " in\n  " + f.getName, exit = true)
              }
              if (!annotations.contains(idx)) { annotations.put(idx, new ArrayBuffer[Annotation]())}
              var status: Option[String] = None
              if (components.length > 3) {
                status = Some(components(3))
                if (status.get != "fn" && status.get != "fp") {
                  printErr("Invalid status type: " + status + " at line " + idx + " in\n  " + f.getName, exit = true)
                }
              }
              annotations(idx).append(new Annotation(name, tpe, status, idx))
            }
          })
        }
      })
      buffer.close()
      results.values.foreach(e => {
        if (e.pos.nonEmpty) {
          val pos = e.pos.get
          if (pos.path.endsWith(f.getName)) { // copied file has different path than original
            if (annotations.contains(pos.line)) {
              var idx = -1
              val annot = annotations(pos.line)
              annot.zipWithIndex.foreach(a => {
                if (a._1.name == CryptoAnalysis.ResultType.toDisplayString(e.tpe)) {
                  idx = a._2
                }
              })
              if (idx > -1) {
                val a = annot(idx)
                if (a.status.isEmpty || a.status.get == "fp") {
                  annot.remove(idx)
                } else if (a.status.get == "fn") {
                  failure = true
                  printErr("Annotation is not an FN: //?" + a.name + "?" + a.tpe + "?fn" + " on line " + a.line)
                }
                if (annot.isEmpty) {
                  annotations.remove(pos.line)
                }
              }
            } else {
              failure = true
              printErr("Missing annotation for " + pos.toString)
            }
          }
        }
      })
      annotations.foreach(v => {
        v._2.foreach(a => {
          if (a.status.isEmpty || a.status.get != "fn") {
            failure = true
            printErr("No matching path node for annotation: //?" + a.name + "?" + a.tpe +
              { if (a.status.nonEmpty) "?" + a.status.get else ""} + " on line " + a.line)
          }
        })
      })
    })
    if (failure) System.exit(1)
  }

  private def getCryptoResults(resultsFile: File): CryptoAnalysis.Results = {
    val results = new CryptoAnalysis.Results
    val buffer = Source.fromFile(resultsFile)
    val jsonString = buffer.getLines().mkString
    buffer.close()
    val data = ujson.read(jsonString)
    data.arr.foreach(v => {
      val tpe: CryptoAnalysis.ResultType.Value = v("name").str match {
        case "ECB" => CryptoAnalysis.ResultType.RULE_1
        case "IV" => CryptoAnalysis.ResultType.RULE_2
        case "KEY" => CryptoAnalysis.ResultType.RULE_3
        case "SALT" => CryptoAnalysis.ResultType.RULE_4
        case "ITERATION" => CryptoAnalysis.ResultType.RULE_5
        case "PASSWORD" => CryptoAnalysis.ResultType.RULE_7
        case _ => throw new RuntimeException("Invalid crypto type")
      }
      val message = v("message").str
      val position: Option[Position] = {
        val p = v("location").str
        if (p.equals("none")) None else {
          val components = p.split(":")
          Some(new Position(components(0), components(1).toInt, components(2).toInt))
        }
      }
      results.add(new CryptoAnalysis.Result(tpe, position, message))
    })
    results
  }

  private def getTaintResults(resultsFile: File): ArrayBuffer[TaintResults] = {
    val buffer = Source.fromFile(resultsFile)
    val jsonString = buffer.getLines().mkString
    buffer.close()
    val ret = new ArrayBuffer[TaintResults]
    val data = ujson.read(jsonString)
    data.arr.foreach(v => {
      val spec = new TaintSpecification(v("name").str, "", "",
        new mutable.HashMap[String,JSONMethod], new mutable.HashMap[String,JSONMethod], new mutable.HashMap[String,JSONMethod])
      val paths = new ArrayBuffer[Path]
      v("paths").arr.foreach(p => {
        val source = p("source")("name")
        val sink = p("sink")("name")
        if (Try(p("path")).isSuccess) {
          val nodes = new ArrayBuffer[(CanInstructionDef, Option[Position])]
          p("path").arr.foreach(x => {
            val components = x.str.split(":")
            val p = new Position(components(0), components(1).toInt, components(2).toInt)
            nodes.append((null, Some(p)))
          })
          paths.append(new Path(nodes, source.str, null, sink.str, null))
        }
      })
      ret.append(new TaintResults(paths, spec))
    })
    ret
  }

  private def getTypeStateResults(resultsFile: File): ArrayBuffer[TypeStateResults] = {
    val buffer = Source.fromFile(resultsFile)
    val jsonString = buffer.getLines().mkString
    buffer.close()
    val ret = new ArrayBuffer[TypeStateResults]
    val data = ujson.read(jsonString)
    data.arr.foreach(v => {
      val spec = new TypeStateJSONProgrammaticSpecification(v("name").str, "", "", mutable.HashMap.empty)
      val errors = new ArrayBuffer[(Position,State)]()
      v("errors").arr.foreach(e => {
        val components = e("pos").str.split(":")
        val p = new Position(components(0), components(1).toInt, components(2).toInt)
        errors.append((p, null))
      })
      ret.append(new TypeStateResults(errors, spec))
    })
    ret
  }
}