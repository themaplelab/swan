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

package ca.ualberta.maple.swan.spds.analysis

import java.io.{File, FileWriter}
import java.util

import boomerang.results.ForwardBoomerangResults
import boomerang.scene._
import boomerang.weights.{DataFlowPathWeight, PathTrackingBoomerang}
import boomerang.{BackwardQuery, Boomerang, BoomerangOptions, DefaultBoomerangOptions, ForwardQuery}
import ca.ualberta.maple.swan.ir.{CanInstructionDef, ModuleGroup, Position}
import ca.ualberta.maple.swan.spds.analysis.TaintAnalysis.{Path, Specification, TaintAnalysisResults}
import ca.ualberta.maple.swan.spds.structures.{SWANCallGraph, SWANMethod, SWANStatement}
import wpds.impl.Weight

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.util.Try

class TaintAnalysis(val group: ModuleGroup,
                    val spec: Specification, val opts: TaintAnalysisOptions) {

  class ForwardMethodTaintQuery(val edge: ControlFlowGraph.Edge,
                                val variable: Val, val source: Method,
                                val sinks: mutable.HashSet[SWANMethod]) extends ForwardQuery(edge, variable)

  class BackwardMethodTaintQuery(val edge: ControlFlowGraph.Edge,
                                 val variable: Val, val sources: mutable.HashSet[SWANMethod],
                                 val sink: SWANMethod) extends BackwardQuery(edge, variable)

  def run(cg: SWANCallGraph): TaintAnalysisResults = {
    val options = getBoomerangOptions
    val allPaths = new ArrayBuffer[Path]
    opts.tpe match {
      case AnalysisType.Backward => {
        val seedsArray = generateBackwardSeeds(cg, spec)
        seedsArray.foreach(i => {
          val seeds = i._1
          val sink = i._2
          seeds.forEach(query => {
            val solver = new Boomerang(cg, DataFlowScope.INCLUDE_ALL, options)
            val queryResults = solver.solve(query)
            queryResults.getAllocationSites.forEach((forwardQuery, _) => {
              System.out.println(forwardQuery.`var`().asInstanceOf[AllocVal].getAllocVal.toString)
            })
          })
        })
      }
      case AnalysisType.Forward => {
        val seedsArray = generateForwardSeeds(cg, spec)
        seedsArray.foreach(i => {
          val seeds = i._1
          val source = i._2
          seeds.forEach(query => {
            val solver = new Boomerang(cg, DataFlowScope.INCLUDE_ALL, options)
            val queryResults = solver.solve(query)
            val paths = processResultsForward(query, queryResults, source)
            allPaths.appendAll(paths)
          })
        })
      }
      case AnalysisType.ForwardPathTracking => {
        val seedsArray = generateForwardSeeds(cg, spec)
        seedsArray.foreach(i => {
          val seeds = i._1
          val source = i._2
          seeds.forEach(query => {
            val solver = new PathTrackingBoomerang(cg, DataFlowScope.INCLUDE_ALL, options) {}
            val queryResults = solver.solve(query)
            val paths = processResultsForwardPathTracking(query, queryResults, source)
            allPaths.appendAll(paths)
          })
        })
      }
    }
    new TaintAnalysisResults(allPaths, spec)
  }

  private def generateForwardSeeds(cg: SWANCallGraph,
                            spec: Specification): ArrayBuffer[(util.Collection[ForwardMethodTaintQuery], String)] = {
    val seedArray = new ArrayBuffer[(util.Collection[ForwardMethodTaintQuery], String)]
    spec.sources.foreach(src => {
      val seeds = new util.HashSet[ForwardMethodTaintQuery]()
      val source = cg.methods.get(src)
      val sinks = spec.sinks.map(s => cg.methods.get(s))
      cg.edgesInto(source).forEach(edge => {
        val stmt = edge.src()
        seeds.add(new ForwardMethodTaintQuery(
          new ControlFlowGraph.Edge(stmt, stmt),
          new AllocVal(stmt.getLeftOp, stmt, stmt.getLeftOp),
          source, sinks))
      })
      seedArray.append((seeds, src))
    })
    seedArray
  }

  private def generateBackwardSeeds(cg: SWANCallGraph,
                                    spec: Specification): ArrayBuffer[(util.Collection[BackwardMethodTaintQuery], String)] = {
    val seedArray = new ArrayBuffer[(util.Collection[BackwardMethodTaintQuery], String)]
    spec.sinks.foreach(s => {
      val seeds = new util.HashSet[BackwardMethodTaintQuery]()
      val sink = cg.methods.get(s)
      val sources = spec.sources.map(s => cg.methods.get(s))
      sink.getParameterLocals.forEach(param => {
        sink.getControlFlowGraph.getStartPoints.forEach(start => {
          seeds.add(new BackwardMethodTaintQuery(
            new ControlFlowGraph.Edge(start, sink.getControlFlowGraph.getSuccsOf(start).iterator().next()),
            param, sources, sink))
        })
      })
      seedArray.append((seeds, s))
    })
    seedArray
  }

  private def getBoomerangOptions: BoomerangOptions = {
    new DefaultBoomerangOptions() {
      // For debugging
      override def analysisTimeoutMS(): Int = 100000000

      // Broken in 3.1.1
      /* override def getStaticFieldStrategy: BoomerangOptions.StaticFieldStrategy = {
        BoomerangOptions.StaticFieldStrategy.FLOW_SENSITIVE
      } */
    }
  }

  private def processResultsForwardPathTracking(query: ForwardMethodTaintQuery,
                             queryResults: ForwardBoomerangResults[DataFlowPathWeight],
                             source: String): ArrayBuffer[Path] = {
    val results = queryResults.asStatementValWeightTable
    val processed = new mutable.HashSet[Method]()
    val paths = new ArrayBuffer[Path]()
    results.cellSet().forEach(s => {
      val m = s.getRowKey.getMethod.asInstanceOf[SWANMethod]
      if (!processed.contains(m) && query.sinks.contains(m)) {
        if (m.getParameterLocals.contains(s.getColumnKey)) {
          val nodes = new ArrayBuffer[(CanInstructionDef, Option[Position])]()
          var prev: SWANStatement = null
          def filterPosition(stmt: SWANStatement): Option[Position] = {
            val pos = stmt.getPosition
            if (pos.nonEmpty && {
              val prevPos = if (prev != null) prev.getPosition else None
              if (prevPos.nonEmpty) {
                !pos.get.sameLine(prevPos.get)
              } else { true }
            }) { pos } else { None }
          }
          var sanitized = false
          s.getValue.getAllStatements.forEach(node => {
            val edge = node.stmt()
            val start = edge.getStart.asInstanceOf[SWANStatement]
            val target = edge.getTarget.asInstanceOf[SWANStatement]
            val startPos = filterPosition(start)
            nodes.append((start.delegate, startPos))
            prev = start
            val targetPos = filterPosition(target)
            nodes.append((target.delegate, targetPos))
            prev = target
            if (spec.sanitizers.contains(edge.getMethod.getName)) {
              sanitized = true
            }
          })
          // Remove last node because it is not really part of the path
          nodes.remove(nodes.length - 1)
          if (!sanitized) {
            paths.append(new Path(nodes, source, s.getRowKey.getMethod.asInstanceOf[SWANMethod].getName))
          }
          processed.add(m)
        }
      }
    })
    paths
  }

  private def processResultsForward(query: ForwardMethodTaintQuery,
                                    queryResults: ForwardBoomerangResults[Weight.NoWeight],
                                    source: String): ArrayBuffer[Path] = {
    val results = queryResults.asStatementValWeightTable
    val processed = new mutable.HashSet[Method]()
    val paths = new ArrayBuffer[Path]
    results.cellSet().forEach(s => {
      val m = s.getRowKey.getMethod.asInstanceOf[SWANMethod]
      if (!processed.contains(m) && query.sinks.contains(m)) {
        if (m.getParameterLocals.contains(s.getColumnKey)) {
          val nodes = new ArrayBuffer[(CanInstructionDef, Option[Position])]
          paths.append(new Path(nodes, source, m.getName))
          processed.add(m)
        }
      }
    })
    paths
  }
}

trait AnalysisType
object AnalysisType {
  case object Forward extends AnalysisType
  case object ForwardPathTracking extends AnalysisType
  case object Backward extends AnalysisType
}

class TaintAnalysisOptions(val tpe: AnalysisType)

object TaintAnalysis {
  class Specification(val name: String,
                      val sources: mutable.HashSet[String],
                      val sinks: mutable.HashSet[String],
                      val sanitizers: mutable.HashSet[String]) {
    override def toString: String = {
      val sb = new StringBuilder()
      sb.append("  name: ")
      sb.append(name)
      sb.append("\n  sources:\n")
      sources.foreach(src => {
        sb.append("    ")
        sb.append(src)
        sb.append("\n")
      })
      sb.append("  sinks:\n")
      sinks.foreach(sink => {
        sb.append("    ")
        sb.append(sink)
        sb.append("\n")
      })
      if (sanitizers.nonEmpty) {
        sb.append("  sanitizers:\n")
        sanitizers.foreach(san => {
          sb.append("    ")
          sb.append(san)
          sb.append("\n")
        })
      }
      sb.toString()
    }
  }

  class Path(val nodes: ArrayBuffer[(CanInstructionDef, Option[Position])], val source: String, val sink: String)

  object Specification {
    def parse(file: File): ArrayBuffer[Specification] = {
      val buffer = Source.fromFile(file)
      val jsonString = buffer.getLines().mkString
      buffer.close()
      val data = ujson.read(jsonString)
      val specs = new ArrayBuffer[Specification]
      data.arr.foreach(v => {
        val name = v("name")
        val sources = mutable.HashSet.from(v("sources").arr.map(_.str))
        val sinks = mutable.HashSet.from(v("sinks").arr.map(_.str))
        val sanitizers = if (Try(v("sanitizers")).isSuccess) mutable.HashSet.from(v("sanitizers").arr.map(_.str)) else new mutable.HashSet[String]
        val spec = new Specification(name.str, sources, sinks, sanitizers)
        specs.append(spec)
      })
      specs
    }
    def writeResults(f: File, allResults: ArrayBuffer[TaintAnalysisResults]): Unit = {
      val fw = new FileWriter(f)
      try {
        val r = new ArrayBuffer[ujson.Obj]
        allResults.foreach(results => {
          val json = ujson.Obj("name" -> results.spec.name)
          val paths = new ArrayBuffer[ujson.Value]
          results.paths.foreach(path => {
            val jsonPath = ujson.Obj("source" -> path.source)
            jsonPath("sink") = path.sink
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

  class TaintAnalysisResults(val paths: ArrayBuffer[Path],
                             val spec: Specification) {
    override def toString: String = {
      val sb = new StringBuilder()
      sb.append("Taint Analysis Results for specification:\n")
      sb.append(spec)
      sb.append("\nDetected:\n")
      paths.zipWithIndex.foreach(path => {
        sb.append("  (")
        sb.append(path._2.toString)
        sb.append(") from ")
        sb.append(path._1.source)
        sb.append(" to \n")
        sb.append("           ")
        sb.append(path._1.sink)
        sb.append("\n")
        if (path._1.nodes.nonEmpty) {
          sb.append("      path:\n")
          path._1.nodes.foreach(n => {
            if (n._2.nonEmpty) {
              sb.append("        ")
              sb.append(n._2.get.toString)
              sb.append("\n")
            }
          })
        }
      })
      sb.toString()
    }
  }
}
