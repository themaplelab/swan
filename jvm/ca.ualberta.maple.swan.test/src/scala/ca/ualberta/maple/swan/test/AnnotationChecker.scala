/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2021 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.test

import java.io.File
import java.nio.file.{Files, Paths}

import ca.ualberta.maple.swan.ir.{CanInstructionDef, Position}
import ca.ualberta.maple.swan.spds.analysis.TaintAnalysis.{Path, Specification, TaintAnalysisResults}
import ca.ualberta.maple.swan.test.AnnotationChecker.Annotation
import org.apache.commons.io.FileExistsException
import picocli.CommandLine
import picocli.CommandLine.{Command, Parameters}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.util.Try

object AnnotationChecker {

  def main(args: Array[String]): Unit = {
    val exitCode = new CommandLine(new AnnotationChecker).execute(args:_*)
    System.exit(exitCode);
  }

  private class Annotation(val name: String, val tpe: String)
}

@Command(name = "SWAN Annotation Checker", mixinStandardHelpOptions = true)
class AnnotationChecker extends Runnable {

  @Parameters(arity = "1", paramLabel = "swan-dir", description = Array("swan-dir to process."))
  private val inputFile: File = null

  override def run(): Unit = {
    if (!inputFile.exists()) {
      throw new FileExistsException("swan-dir does not exist")
    }
    val resultsFile = new File(Paths.get(inputFile.getPath, "results.json").toUri)
    if (!resultsFile.exists()) {
      throw new FileExistsException("results.json file does not exist in the swan-dir")
    }
    val sourceDir = new File(Paths.get(inputFile.getPath, "src").toUri)
    if (!sourceDir.exists()) {
      throw new FileExistsException("src directory does not exist in swan-dir")
    }
    val results = getResults(resultsFile)
    var failure = false
    Files.walk(sourceDir.toPath).filter(Files.isRegularFile(_)).forEach(p => {
      val f = new File(p.toUri)
      val buffer = Source.fromFile(f)
      val annotations = new mutable.HashMap[Int, ArrayBuffer[Annotation]]
      buffer.getLines().zipWithIndex.foreach(l => {
        val line = l._1
        val idx = l._2 + 1
        if (line.contains("//")) {
            line.split("//").foreach(c => {
            if (c.startsWith("!")) {
              val components = c.split("!")
              val name = components(1)
              val tpe = components(2).split(" ")(0)
              if (tpe != "sink" && tpe != "source") {
                System.err.println("invalid annotation type: " + tpe + " at line " + idx + " in\n  " + f.getName)
                System.exit(1)
              }
              if (!annotations.contains(idx)) { annotations.put(idx, new ArrayBuffer[Annotation]())}
              annotations(idx).append(new Annotation(name, tpe))
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
                annotations(pos.line).zipWithIndex.foreach(a => {
                  if (a._1.name == r.spec.name && a._1.tpe == tpe) {
                    idx = a._2
                  }
                })
                if (idx > -1) {
                  annotations(pos.line).remove(idx)
                  if (annotations(pos.line).isEmpty) { annotations.remove(pos.line) }
                }
              } else {
                failure = true
                System.err.println("Missing " + tpe + " annotation for " + pos.toString)
              }
            }
          }
          handle(p.nodes(0)._2.get, "source")
          handle(p.nodes.last._2.get, "sink")
        })
      })
      if (annotations.nonEmpty) {
        failure = true
        System.err.println("Missing annotations in " + f.getName)
      }
      annotations.foreach(v => {
        v._2.foreach(a => {
          failure = true
          System.err.println("No matching path node for annotation: //!" + a.name + "!" + a.tpe)
        })
      })
    })
    if (failure) System.exit(1)
  }

  private def getResults(resultsFile: File): ArrayBuffer[TaintAnalysisResults] = {
    val buffer = Source.fromFile(resultsFile)
    val jsonString = buffer.getLines().mkString
    buffer.close()
    val ret = new ArrayBuffer[TaintAnalysisResults]
    val data = ujson.read(jsonString)
    data.arr.foreach(v => {
      val spec = new Specification(v("name").str, new mutable.HashSet[String](), new mutable.HashSet[String](), new mutable.HashSet[String]())
      val paths = new ArrayBuffer[Path]
      v("paths").arr.foreach(p => {
        val source = p("source")
        val sink = p("sink")
        if (Try(p("path")).isSuccess) {
          val nodes = new ArrayBuffer[(CanInstructionDef, Option[Position])]
          p("path").arr.foreach(x => {
            val components = x.str.split(":")
            val p = new Position(components(0), components(1).toInt, components(2).toInt)
            nodes.append((null, Some(p)))
          })
          paths.append(new Path(nodes, source.str, sink.str))
        }
      })
      ret.append(new TaintAnalysisResults(paths, spec))
    })
    ret
  }
}