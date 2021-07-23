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

import java.util
import java.util.regex.Pattern

import boomerang.results.ForwardBoomerangResults
import boomerang.scene._
import boomerang.weights.{DataFlowPathWeight, PathTrackingBoomerang}
import boomerang.{BackwardQuery, Boomerang, BoomerangOptions, DefaultBoomerangOptions, ForwardQuery}
import ca.ualberta.maple.swan.ir.{CanInstructionDef, Position}
import ca.ualberta.maple.swan.spds.analysis.taint.TaintResults.Path
import ca.ualberta.maple.swan.spds.analysis.taint.TaintSpecification.JSONMethod
import ca.ualberta.maple.swan.spds.structures.{SWANCallGraph, SWANInvokeExpr, SWANMethod, SWANStatement}
import wpds.impl.Weight

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

trait AnalysisType
object AnalysisType {
  case object Forward extends AnalysisType
  case object ForwardPathTracking extends AnalysisType
  case object Backward extends AnalysisType
}

class TaintAnalysisOptions(val tpe: AnalysisType)


class TaintAnalysis(val spec: TaintSpecification, val opts: TaintAnalysisOptions) {

  class ForwardMethodTaintQuery(val edge: ControlFlowGraph.Edge,
                                val variable: Val, val source: Method,
                                val sinks: mutable.HashSet[SWANMethod]) extends ForwardQuery(edge, variable)

  class BackwardMethodTaintQuery(val edge: ControlFlowGraph.Edge,
                                 val variable: Val, val sources: mutable.HashSet[SWANMethod],
                                 val sink: SWANMethod) extends BackwardQuery(edge, variable)

  val spdsTimeoutError = "SPDS timed out. Enable logging (remove log4j.properties) to see issue."
  val spdsError = "SPDS error. Enable logging (remove log4j.properties) to see issue or try increasing stack size with -Xss."

  def run(cg: SWANCallGraph): TaintResults = {
    val options = getBoomerangOptions
    val allPaths = new ArrayBuffer[Path]
    opts.tpe match {
      case AnalysisType.Backward => {
        val seedsArray = generateBackwardSeeds(cg, spec)
        seedsArray.foreach(i => {
          val seeds = i._1
          val sink = i._2
          seeds.forEach(query => {
            val solver = new PathTrackingBoomerang(cg, DataFlowScope.INCLUDE_ALL, options) {}
            val queryResults = solver.solve(query)
            if (queryResults.isTimedout) {
              throw new RuntimeException(spdsTimeoutError)
            }
            System.out.println("VARIABLE: " + query.variable.toString)
            queryResults.getAllocationSites.forEach((forwardQuery, _) => {
              queryResults.asStatementValWeightTable(forwardQuery).cellSet().forEach(s => {
                if (s.getRowKey == query.edge && s.getColumnKey == query.variable) {
                  System.out.println("PATH")
                  var print = false
                  s.getValue.getAllStatements.forEach(n => {
                    if (n.stmt().getStart.containsInvokeExpr()) {
                      if (query.sources.map(f => f.getName).contains(n.stmt().getStart.getInvokeExpr.getMethod.getName)) {
                        print = true
                      }
                    }
                    if (print) System.out.println("  " + n.stmt().getStart)
                  })
                }
              })
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
            if (queryResults.isTimedout) {
              throw new RuntimeException(spdsTimeoutError)
            }
            val paths = processResultsForward(cg, query, queryResults, query.source.getName, source)
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
            if (queryResults.isTimedout) {
              throw new RuntimeException(spdsTimeoutError)
            }
            val paths = processResultsForwardPathTracking(query, queryResults, query.source.getName, source)
            allPaths.appendAll(paths)
          })
        })
      }
    }
    new TaintResults(allPaths, spec)
  }

  private def generateForwardSeeds(cg: SWANCallGraph,
                            spec: TaintSpecification): ArrayBuffer[(util.Collection[ForwardMethodTaintQuery], JSONMethod)] = {
    val seedArray = new ArrayBuffer[(util.Collection[ForwardMethodTaintQuery], JSONMethod)]
    spec.sources.foreach(src => {
      val seeds = new util.HashSet[ForwardMethodTaintQuery]()
      val source = if (src._2.regex) {
        val m = cg.methods.values.toArray.find(x => Pattern.matches(src._1, x.getName))
        if (m.nonEmpty) m.get else null
      } else {
        cg.methods(src._1)
      }
      val sinks = mutable.HashSet.from[SWANMethod](spec.sinks.map(s => {
        if (s._2.regex) {
          val m = cg.methods.values.toArray.find(x => Pattern.matches(s._1, x.getName))
          if (m.nonEmpty) m.get else null
        } else {
          cg.methods(s._1)
        }
      }))
      cg.edgesInto(source).forEach(edge => {
        val stmt = edge.src()
        seeds.add(new ForwardMethodTaintQuery(
          new ControlFlowGraph.Edge(stmt, stmt),
          new AllocVal(stmt.getLeftOp, stmt, stmt.getLeftOp),
          source, sinks))
      })
      seedArray.append((seeds, src._2))
    })
    seedArray
  }

  private def generateBackwardSeeds(cg: SWANCallGraph,
                                    spec: TaintSpecification): ArrayBuffer[(util.Collection[BackwardMethodTaintQuery], String)] = {
    val seedArray = new ArrayBuffer[(util.Collection[BackwardMethodTaintQuery], String)]
    spec.sinks.foreach(s => {
      val seeds = new util.HashSet[BackwardMethodTaintQuery]()
      val sink = cg.methods(s._1)
      val sources = mutable.HashSet.from[SWANMethod](spec.sources.map(s => cg.methods(s._1)))
      cg.edgesInto(sink).forEach(cgEdge => {
        val cfEdge = new ControlFlowGraph.Edge(
          cgEdge.src(),
          cgEdge.src().getMethod.getControlFlowGraph.getSuccsOf(cgEdge.src()).iterator().next())
        val apply = cgEdge.src().asInstanceOf[SWANStatement.ApplyFunctionRef]
        apply.invokeExpr.args.forEach(v => {
          seeds.add(new BackwardMethodTaintQuery(cfEdge, v, sources, sink))
          System.out.println("Added seed: " + cfEdge + " | " + v.toString)
        })
      })
      sink.getParameterLocals.forEach(param => {
        sink.getControlFlowGraph.getStartPoints.forEach(start => {
          val edge = new ControlFlowGraph.Edge(
            start,
            sink.getControlFlowGraph.getSuccsOf(start).iterator().next())
          //seeds.add(new BackwardMethodTaintQuery(edge, param, sources, sink))
        })
      })
      seedArray.append((seeds, s._1))
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
                                                sourceName: String, source: JSONMethod): ArrayBuffer[Path] = {
    val results = queryResults.asStatementValWeightTable
    val processed = new mutable.HashSet[Method]()
    val paths = new ArrayBuffer[Path]()
    results.cellSet().forEach(s => {
      val m = s.getRowKey.getMethod.asInstanceOf[SWANMethod]
      if (!processed.contains(m) && query.sinks.contains(m)) {
        if (m.getParameterLocals.contains(s.getColumnKey)) {
          var continue = false
          var sinkJSONMethod: JSONMethod = null
          spec.sinks.foreach(sink => {
            if ((sink._2.regex && Pattern.matches(sink._2.name, m.getName)) || sink._2.name == m.getName)  {
              sinkJSONMethod = sink._2
              if (sink._2.args.nonEmpty) {
                if (!sink._2.args.get.contains(m.getParameterLocals.indexOf(s.getColumnKey))) {
                  continue = true
                }
              }
            }
          })
          if (!continue) {
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
              paths.append(new Path(nodes, sourceName, source, s.getRowKey.getMethod.asInstanceOf[SWANMethod].getName, sinkJSONMethod))
            }
            processed.add(m)
          }
        }
      }
    })
    paths
  }

  private def processResultsForward(cg: SWANCallGraph, query: ForwardMethodTaintQuery,
                                    queryResults: ForwardBoomerangResults[Weight.NoWeight],
                                    sourceName: String, source: JSONMethod): ArrayBuffer[Path] = {
    val results = queryResults.asStatementValWeightTable
    val paths = new ArrayBuffer[Path]

    spec.sinks.foreach(sink => {
      val sinkMethod = {
        if (sink._2.regex) {
          cg.methods.find(s => Pattern.matches(sink._1, s._1)).get._2
        } else {
          cg.methods(sink._1)
        }
      }
      cg.edgesInto(sinkMethod).forEach(edge => {
        results.cellSet().forEach(cell => {
          if (cell.getRowKey.getStart == edge.src() && edge.src().uses(cell.getColumnKey)) {
            var skip = false
            if (sink._2.args.nonEmpty) {
              val invokeIdx = edge.src().getInvokeExpr.asInstanceOf[SWANInvokeExpr].getIndexOf(cell.getColumnKey)
              if (invokeIdx.isEmpty) {
                throw new RuntimeException("Could not find val index in invoke expr")
              }
              skip = !sink._2.args.get.contains(invokeIdx.get)
            }
            if (!skip) {
              val srcStmt =  query.edge.getStart.asInstanceOf[SWANStatement]
              val sinkStmt = cell.getRowKey.getStart.asInstanceOf[SWANStatement]
              val nodes = new ArrayBuffer[(CanInstructionDef, Option[Position])]
              nodes.append((srcStmt.delegate, srcStmt.getPosition))
              nodes.append((sinkStmt.delegate, sinkStmt.getPosition))
              paths.append(new Path(nodes, sourceName, source, sinkMethod.getName, sink._2))
            }
          }
        })
      })
    })
    paths
  }
}
