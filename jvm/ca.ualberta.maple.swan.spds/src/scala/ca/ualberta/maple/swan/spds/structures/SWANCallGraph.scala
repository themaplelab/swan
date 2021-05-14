/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2021 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.spds.structures

import java.util
import java.util.Collections

import boomerang.scene._
import boomerang.{BackwardQuery, Boomerang, DefaultBoomerangOptions, Query}
import ca.ualberta.maple.swan.ir.{CanFunction, Constants, ModuleGroup, SWIRLPrinter}
import com.google.common.collect.Maps

import scala.collection.mutable

// TODO: iOS lifecycle
class SWANCallGraph(val module: ModuleGroup) extends CallGraph {

  val methods: util.HashMap[String, SWANMethod] = Maps.newHashMap[String, SWANMethod]
  final val methodEdges = new mutable.HashMap[SWANMethod, mutable.Set[SWANMethod]] with mutable.MultiMap[SWANMethod, SWANMethod]

  module.functions.foreach(f => {
    val m = makeMethod(f)
    if (f.name.startsWith(Constants.fakeMain)) {
      this.addEntryPoint(m)
    }
  })

  def makeMethod(f: CanFunction): SWANMethod = {
    val m = new SWANMethod(f, module)
    methods.put(f.name, m)
    m
  }

  def addSWANEdge(from: SWANMethod, to: SWANMethod, stmt: SWANStatement.ApplyFunctionRef): Unit = {
    val edge = new CallGraph.Edge(stmt, to)
    this.methodEdges.addBinding(from, to)
    this.addEdge(edge)
  }

  // TODO: using multiple queries, even with unregisterAllListeners doesn't work
  // Start at the entry points, find apply instructions, query the
  // function references, add edge to CG (if possible), repeat until no change.
  def constructStaticCG(): Unit = {

    val visited = new util.HashSet[SWANStatement]()
    val rtaTypes = new mutable.HashSet[String]

    val options = new DefaultBoomerangOptions {
      override def allowMultipleQueries(): Boolean = true
    }

    var changed = true
    while (changed) {
      changed = false
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
      val seeds = scope.computeSeeds
      if (!seeds.isEmpty) {
        seeds.forEach(query => {
          val solver = new Boomerang(this, DataFlowScope.INCLUDE_ALL, options)
          val backwardQueryResults = solver.solve(query.asInstanceOf[BackwardQuery])
          backwardQueryResults.getAllocationSites.forEach((forwardQuery, _) => {
            val applyStmt = query.asNode().stmt().getTarget.asInstanceOf[SWANStatement.ApplyFunctionRef]
            forwardQuery.`var`().asInstanceOf[AllocVal].getAllocVal match {
              case v: SWANVal.FunctionRef => {
                val target = this.methods.get(v.ref)
                addSWANEdge(v.method, target, applyStmt)
                visited.add(applyStmt)
                rtaTypes.addAll(v.method.delegate.instantiatedTypes)
                changed = true
              }
              case v: SWANVal.BuiltinFunctionRef => {
                if (this.methods.containsKey(v.ref)) {
                  val target = this.methods.get(v.ref)
                  addSWANEdge(v.method, target, applyStmt)
                  visited.add(applyStmt)
                  rtaTypes.addAll(v.method.delegate.instantiatedTypes)
                  changed = true
                }
              }
              case v: SWANVal.DynamicFunctionRef => {
                module.ddgs.foreach(ddg => {
                  val functionNames = ddg._2.query(v.index, Some(rtaTypes))
                  // System.out.println(ddg._2.printToDot())
                  functionNames.foreach(name => {
                    if (this.methods.containsKey(name)) {
                      val target = this.methods.get(name)
                      addSWANEdge(v.method, target, applyStmt)
                      visited.add(applyStmt)
                      rtaTypes.addAll(v.method.delegate.instantiatedTypes)
                      changed = true
                    }
                  })
                })
              }
              case _ => // never happens
            }
          })
        })
      }
    }
  }

  override def toString: String = {
    val sb = new StringBuilder
    sb.append("CG edges (")
    sb.append(getEdges.size())
    sb.append("): \n")
    getEdges.forEach((e: CallGraph.Edge) => {
      def foo(e: CallGraph.Edge) = {
        val sp = new SWIRLPrinter
        sp.print(e.src.asInstanceOf[SWANStatement].delegate)
        sb.append(sp)
        sb.append(" -> ")
        sb.append(e.tgt.getName)
        sb.append("\n")
      }
      foo(e)
    })
    sb.toString()
  }

}
