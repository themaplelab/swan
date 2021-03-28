/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2021 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.spds

import java.util
import java.util.Collections

import boomerang.scene.{AnalysisScope, CallGraph, ControlFlowGraph, DataFlowScope}
import boomerang.{BackwardQuery, Boomerang, DefaultBoomerangOptions, Query}
import ca.ualberta.maple.swan.ir.{CanFunction, CanModule, Constants}
import com.google.common.collect.Maps

// TODO: iOS lifecycle
class SWANCallGraph(val module: CanModule) extends CallGraph {

  private val methods = Maps.newHashMap[String, SWANMethod]

  module.functions.foreach(f => {
    //val m = makeMethod(f)
    if (f.name == Constants.fakeMain) {
      val m = makeMethod(f) // ++
      this.getEntryPoints.add(m)
    }
  })

  def makeMethod(f: CanFunction): SWANMethod = {
    val m = new SWANMethod(f)
    methods.put(f.name, m)
    m
  }

  def constructStaticCG(): Unit = {

    val visited = new util.HashSet[SWANStatement]()

    val scope = new AnalysisScope(this) {
      override protected def generate(edge: ControlFlowGraph.Edge): util.Collection[_ <: Query] = {
        val statement = edge.getTarget
        if (statement.containsInvokeExpr && !visited.contains(statement)) {
          val ref = statement.getInvokeExpr.asInstanceOf[SWANInvokeExpr].getFunctionRef
          return Collections.singleton(BackwardQuery.make(edge, ref))
        }
        Collections.emptySet
      }
    }

    val options = new DefaultBoomerangOptions {
      override def allowMultipleQueries(): Boolean = true
    }

    val solver = new Boomerang(this, DataFlowScope.INCLUDE_ALL, options)

    var changed = true

    while (changed) {
      changed = false
      solver.unregisterAllListeners()
      val seeds = scope.computeSeeds

      seeds.forEach(query => {
        val backwardQueryResults = solver.solve(query.asInstanceOf[BackwardQuery])
        backwardQueryResults.getAllocationSites.forEach((forwardQuery, _) => {
          // Probably wrong
          val applyStmt = query.asNode().stmt().getStart.asInstanceOf[SWANStatement.ApplyFunctionRef]
          forwardQuery.`var`() match {
            case v: SWANVal.FunctionRef => {
              val target = this.methods.get(v.ref)
              val edge = new CallGraph.Edge(applyStmt, target)
              this.addEdge(edge)
              visited.add(applyStmt)
              changed = true
            }
            case v: SWANVal.BuiltinFunctionRef => // TODO
            case v: SWANVal.DynamicFunctionRef => // TODO, RTA
            case _ => // never happens
          }
        })
      })
    }
  }

}
