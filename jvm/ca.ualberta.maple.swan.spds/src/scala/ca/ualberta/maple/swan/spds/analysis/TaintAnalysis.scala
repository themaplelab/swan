/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2021 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.spds.analysis

import java.io.File
import java.util

import boomerang.results.ForwardBoomerangResults
import boomerang.scene._
import boomerang.weights.{DataFlowPathWeight, PathTrackingBoomerang}
import boomerang.{Boomerang, BoomerangOptions, DefaultBoomerangOptions, ForwardQuery}
import ca.ualberta.maple.swan.ir.{CanInstructionDef, ModuleGroup, Position}
import ca.ualberta.maple.swan.spds.analysis.TaintAnalysis.{Path, Specification, TaintAnalysisResults}
import ca.ualberta.maple.swan.spds.structures.{SWANCallGraph, SWANMethod, SWANStatement}
import wpds.impl.Weight

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.util.Try

class TaintAnalysis(val group: ModuleGroup,
                    val spec: Specification) {

  class ForwardMethodTaintQuery(val edge: ControlFlowGraph.Edge,
                                val variable: Val, val source: Method,
                                val sinks: mutable.HashSet[SWANMethod]) extends ForwardQuery(edge, variable)

  def run(pathTracking: Boolean): TaintAnalysisResults = {
    val cg = new SWANCallGraph(group)
    cg.constructStaticCG()
    // System.out.println(cg)
    val options = getOptions
    val seedsArray = generateSeeds(cg, spec)
    val allPaths = new ArrayBuffer[Path]
    seedsArray.foreach(i => {
      val seeds = i._1
      val source = i._2
      seeds.forEach(query => {
        // System.out.println("Solving query: " + query)
        val solver = if (pathTracking) new PathTrackingBoomerang(cg, DataFlowScope.INCLUDE_ALL, options) {}
        else new Boomerang(cg, DataFlowScope.INCLUDE_ALL, options)
        val queryResults = solver.solve(query.asInstanceOf[ForwardQuery])
        val paths = if (pathTracking)
          processResults(query, queryResults.asInstanceOf[ForwardBoomerangResults[DataFlowPathWeight]], source)
        else processResultsVanilla(query, queryResults.asInstanceOf[ForwardBoomerangResults[Weight.NoWeight]], source)
        allPaths.appendAll(paths)
      })
    })
    new TaintAnalysisResults(allPaths, spec)
  }

  private def generateSeeds(cg: SWANCallGraph,
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

  private def getOptions: BoomerangOptions = {
    new DefaultBoomerangOptions() {
      // For debugging
      // override def analysisTimeoutMS(): Int = 100000000
    }
  }

  private def processResults(query: ForwardMethodTaintQuery,
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

  private def processResultsVanilla(query: ForwardMethodTaintQuery,
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

object TaintAnalysis {
  class Specification(val name: String,
                      val sources: mutable.HashSet[String],
                      val sinks: mutable.HashSet[String],
                      val sanitizers: mutable.HashSet[String])

  object Specification {
    def parse(file: File): ArrayBuffer[Specification] = {
      val buffer = Source.fromFile(file)
      val jsonString = buffer.getLines().mkString
      buffer.close()
      val data = ujson.read(jsonString)
      val specs = new ArrayBuffer[Specification]
      data("specs").arr.foreach(v => {
        val name = v("name")
        val sources = mutable.HashSet.from(v("sources").arr.map(_.str))
        val sinks = mutable.HashSet.from(v("sinks").arr.map(_.str))
        val sanitizers = if (Try(v("sanitizers")).isSuccess) mutable.HashSet.from(v("sanitizers").arr.map(_.str)) else new mutable.HashSet[String]
        val spec = new Specification(name.str, sources, sinks, sanitizers)
        specs.append(spec)
      })
      specs
    }
  }

  class TaintAnalysisResults(val paths: ArrayBuffer[Path],
                             val spec: Specification) {
    override def toString: String = {
      val sb = new StringBuilder()
      sb.append("Taint Analysis Results for specification:\n")
      sb.append("  name: ")
      sb.append(spec.name)
      sb.append("\n  sources:\n")
      spec.sources.foreach(src => {
        sb.append("    ")
        sb.append(src)
        sb.append("\n")
      })
      sb.append("  sinks:\n")
      spec.sinks.foreach(sink => {
        sb.append("    ")
        sb.append(sink)
        sb.append("\n")
      })
      if (spec.sanitizers.nonEmpty) {
        sb.append("  sanitizers:\n")
        spec.sanitizers.foreach(san => {
          sb.append("    ")
          sb.append(san)
          sb.append("\n")
        })
      }
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

  class Path(val nodes: ArrayBuffer[(CanInstructionDef, Option[Position])], val source: String, val sink: String)
}
