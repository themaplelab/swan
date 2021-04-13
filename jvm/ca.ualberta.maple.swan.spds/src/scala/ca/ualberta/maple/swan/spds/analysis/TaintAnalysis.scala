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

import java.util

import boomerang.results.ForwardBoomerangResults
import boomerang.scene._
import boomerang.weights.{DataFlowPathWeight, PathTrackingBoomerang}
import boomerang.{BoomerangOptions, DefaultBoomerangOptions, ForwardQuery}
import ca.ualberta.maple.swan.ir.{CanInstructionDef, ModuleGroup, Position}
import ca.ualberta.maple.swan.spds.structures.{SWANCallGraph, SWANMethod, SWANStatement}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class TaintAnalysis(val group: ModuleGroup,
                    val spec: mutable.HashMap[String, mutable.HashSet[String]]) {

  class ForwardMethodTaintQuery(val edge: ControlFlowGraph.Edge,
                                val variable: Val, val source: Method,
                                val sinks: mutable.HashSet[SWANMethod]) extends ForwardQuery(edge, variable)

  class TaintAnalysisResults(val nodes: ArrayBuffer[(CanInstructionDef, Option[Position])],
                             val source: SWANMethod, val sink: SWANMethod)

  def run(): Unit = {
    val cg = new SWANCallGraph(group)
    cg.constructStaticCG()
    // System.out.println(cg)
    val options = getOptions
    val solver = new PathTrackingBoomerang(cg, DataFlowScope.INCLUDE_ALL, options) {}
    val seeds = generateSeeds(cg, spec)
    seeds.forEach(query => {
      System.out.println("Solving query: " + query)
      val queryResults = solver.solve(query.asInstanceOf[ForwardQuery])
      val taintAnalysisResults = processResults(query, queryResults)
      taintAnalysisResults.foreach(r => {
        System.out.println("Path detected from\n    `" + r.source.getName + "`\n--> `" + r.sink.getName + "`")
        r.nodes.foreach(n => {
          if (n._2.nonEmpty) {
            //val p = new SWIRLPrinter
            //p.print(n._1)
            //System.out.println(p)
            System.out.println(n._2.get)
          }
        })
      })
    })

  }


  private def generateSeeds(cg: SWANCallGraph,
                            spec: mutable.HashMap[String, mutable.HashSet[String]]): util.Collection[ForwardMethodTaintQuery] = {

    val seeds = new util.HashSet[ForwardMethodTaintQuery]()
    spec.foreach(s => {
      val source = cg.methods.get(s._1)
      val sinks = s._2.map(s => cg.methods.get(s))
      cg.edgesInto(source).forEach(edge => {
        val stmt = edge.src()
        seeds.add(new ForwardMethodTaintQuery(
          new ControlFlowGraph.Edge(stmt, stmt),
          new AllocVal(stmt.getLeftOp, stmt, stmt.getLeftOp),
          source, sinks))
      })
    })
    seeds
  }

  private def getOptions: BoomerangOptions = {
    new DefaultBoomerangOptions() {

    }
  }

  private def processResults(query: ForwardMethodTaintQuery, queryResults: ForwardBoomerangResults[DataFlowPathWeight]): ArrayBuffer[TaintAnalysisResults] = {
    val results = queryResults.asStatementValWeightTable
    val processed = new mutable.HashSet[Method]()
    val analysisResults = new ArrayBuffer[TaintAnalysisResults]()
    results.cellSet().forEach(s => {
      val m = s.getRowKey.getMethod.asInstanceOf[SWANMethod]
      if (!processed.contains(m) && query.sinks.contains(m)) {
        if (m.getParameterLocals.contains(s.getColumnKey)) {
          val nodes = new ArrayBuffer[(CanInstructionDef, Option[Position])]()
          System.out.println(query.asNode.fact.asInstanceOf[AllocVal].getAllocVal.toString
            + " reaches " + m.getName)
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
          })
          // Remove last node because it is not really part of the path
          nodes.remove(nodes.length - 1)
          analysisResults.append(new TaintAnalysisResults(nodes,
            query.edge.getMethod.asInstanceOf[SWANMethod],
            s.getRowKey.getMethod.asInstanceOf[SWANMethod]))
          processed.add(m)
        }
      }
    })
    analysisResults
  }
}
