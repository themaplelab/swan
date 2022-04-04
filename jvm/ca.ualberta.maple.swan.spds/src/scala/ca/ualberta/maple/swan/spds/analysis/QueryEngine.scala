/*
 * Copyright (c) 2022 the SWAN project authors. All rights reserved.
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

import boomerang.results.{BackwardBoomerangResults, ForwardBoomerangResults}
import boomerang.scene.{AllocVal, ControlFlowGraph, DataFlowScope}
import boomerang.{BackwardQuery, Boomerang, DefaultBoomerangOptions, ForwardQuery}
import ca.ualberta.maple.swan.ir.Position
import ca.ualberta.maple.swan.spds.analysis.taint.TaintSpecification
import ca.ualberta.maple.swan.spds.structures.{SWANCallGraph, SWANMethod, SWANStatement, SWANVal}
import wpds.impl.Weight

import java.io.File
import java.util.regex.Pattern
import scala.collection.mutable

// WIP experimental query engine - ignore
class QueryEngine(val cg: SWANCallGraph, val debugDir: File) {

  private val resolutions = mutable.HashMap.empty[SWANStatement.ApplyFunctionRef,
    mutable.HashMap[Dependency, (Boolean, Option[Position])]]

  private val boomerang = new Boomerang(cg, DataFlowScope.INCLUDE_ALL, new DefaultBoomerangOptions {
    override def allowMultipleQueries(): Boolean = true
  })

  def analyze(spec: TaintSpecification): Unit = {
    spec.sinks.foreach(sink => {
      val callSite = new CallSite(sink._2.name, sink._2.description, sink._2.regex, sink._2.args.nonEmpty, false)
      spec.sources.foreach(source => {
        val callSiteReturn = new CallSiteReturn(source._2.name, source._2.description, source._2.regex, false)
        callSite.dependencies.append(callSiteReturn)
        // TODO: Sanitizers
      })
      analyze(callSite)
    })
  }

  def analyze(root: CallSite): Unit = {
    val results = root.resolveAsRoot(this)
    System.out.println(s"===== Analyzing for ${root.name} =====")
    results.foreach(result => {
      if (result._2) {
        val applyStmt = result._1
        System.out.println("Found violation" + { if (applyStmt.getPosition.nonEmpty) s" at ${applyStmt.getPosition.get}" else "" })
        System.out.println("  Description: " + root.description)
        System.out.println("  Contributing factors:")
        resolutions(applyStmt).foreach(dep => {
          val pos = dep._2._2
          if (pos.nonEmpty) {
            System.out.println(s"    ${dep._1.description} | ${if (pos.nonEmpty) pos.get else "No location"}")
          }
        })
      }
    })
  }

  def runBackwardQuery(query: BackwardQuery):BackwardBoomerangResults[Weight.NoWeight] = {
    boomerang.unregisterAllListeners()
    boomerang.solve(query)
  }

  def runForwardQuery(query: ForwardQuery): ForwardBoomerangResults[Weight.NoWeight] = {
    boomerang.unregisterAllListeners()
    boomerang.solve(query)
  }

  def recordDependencyResolution(dependency: Dependency, status: Boolean, root: SWANStatement.ApplyFunctionRef, position: Option[Position]): Boolean = {
    if (!resolutions.contains(root)) resolutions.put(root, mutable.HashMap.empty)
    resolutions(root).put(dependency, (status, position))
    status
  }

  def findMethod(name: String, regex: Boolean): SWANMethod = {
    val r = cg.methods.iterator.find(m => if (regex) Pattern.matches(name, m._2.getName) else name.equals(m._2.getName))
    if (r.isEmpty) {
      null // throw new RuntimeException("could not find condition rhs matching: " + cas.name)
    } else r.get._2
  }

  def getCallSites(name: String, regex: Boolean): mutable.ArrayBuffer[SWANStatement.ApplyFunctionRef] = {
    val ret = mutable.ArrayBuffer.empty[SWANStatement.ApplyFunctionRef]
    val method = findMethod(name, regex)
    if (method != null) {
      cg.edgesInto(method).forEach(edge => {
        ret.append(edge.src().asInstanceOf[SWANStatement.ApplyFunctionRef])
      })
    }
    ret
  }

}

abstract class Dependency(val description: String) {
  val dependencies: mutable.ArrayBuffer[Dependency] = mutable.ArrayBuffer.empty
}

class CallSite(val name: String, description: String, val regex: Boolean, val argSpecific: Boolean, val negative: Boolean) extends Dependency(description) {

  def resolveAsRoot(engine: QueryEngine): mutable.HashMap[SWANStatement.ApplyFunctionRef, Boolean] = {
    val results = mutable.HashMap.empty[SWANStatement.ApplyFunctionRef, Boolean]
    val callSites = engine.getCallSites(name, regex)
    callSites.foreach(callSite => {
      results.put(callSite, resolve(callSite, engine, callSite))
    })
    results
  }

  def resolve(callSite: SWANStatement.ApplyFunctionRef, engine: QueryEngine, root: SWANStatement.ApplyFunctionRef): Boolean = {
    var result = true
    val cfgEdge = new ControlFlowGraph.Edge(callSite.m.getCFG.getPredsOf(callSite).iterator().next(), callSite)
    if (argSpecific) {
      dependencies.zipWithIndex.foreach(dep => {
        val v = callSite.getInvokeExpr.getArgs.get(dep._2)
        val queryVal = new AllocVal(v, callSite, v)
        val query = BackwardQuery.make(cfgEdge, queryVal)
        dep._1 match {
          case cs: CallSite => result = result && cs.resolve(query, engine, root)
          case td: TypeDependency => result = result && td.resolve(query, engine, root)
          case csr: CallSiteReturn => result = result && csr.resolve(query, engine, root)
        }
      })
    } else {
      callSite.getInvokeExpr.getArgs.forEach(arg => {
        val queryVal = new AllocVal(arg, callSite, arg)
        val query = BackwardQuery.make(cfgEdge, queryVal)
        dependencies.foreach {
          case cs: CallSite => result = result && cs.resolve(query, engine, root)
          case td: TypeDependency => result = result && td.resolve(query, engine, root)
          case csr: CallSiteReturn => result = result && csr.resolve(query, engine, root)
        }
      })
    }
    result
  }

  def resolve(query: BackwardQuery, engine: QueryEngine, root: SWANStatement.ApplyFunctionRef): Boolean = {
    var result = true
    var found = false
    var relevantPosition: Option[Position] = None
    engine.runBackwardQuery(query).getAllocationSites.forEach((allocSite, _) => {
      val query = new ForwardQuery(allocSite.cfgEdge(), allocSite.`var`())
      val results = engine.runForwardQuery(query)
      val m = engine.findMethod(name, regex)
      engine.cg.edgesInto(m).forEach(edge => {
        results.asStatementValWeightTable().cellSet().forEach(cell => {
          if (cell.getRowKey.getStart == edge.src() && edge.src().uses(cell.getColumnKey)) {
            found = true
            relevantPosition = edge.src().asInstanceOf[SWANStatement].getPosition
            val apply = edge.src().asInstanceOf[SWANStatement.ApplyFunctionRef]
            result = result && resolve(apply, engine, root)
          }
        })
      })
    })
    result = found && result
    engine.recordDependencyResolution(this, result, root, relevantPosition)
  }
}

// TODO
// class ConstantDependency(val description: String) extends Dependency

class TypeDependency(val types: mutable.HashSet[String], description: String, val regex: Boolean, val negative: Boolean) extends Dependency(description) {

  def resolve(query: BackwardQuery, engine: QueryEngine, root: SWANStatement.ApplyFunctionRef): Boolean = {
    val allocationSites = engine.runBackwardQuery(query).getAllocationSites
    var result = !allocationSites.isEmpty
    var relevantPosition: Option[Position] = None
    allocationSites.forEach((allocSite, _) => {
      val v = allocSite.`var`().asInstanceOf[AllocVal].getAllocVal
      v match {
        case ne: SWANVal.NewExpr => {
          val tpe = ne.tpe.tpe.name
          relevantPosition = allocSite.cfgEdge().getStart.asInstanceOf[SWANStatement].getPosition
          result = result && types.exists(s => if (regex) Pattern.matches(s, tpe) else s.equals(tpe))
          dependencies.foreach {
            case cs: CallSite => result = result && cs.resolve(query, engine, root)
            case _: TypeDependency => throw new RuntimeException("type dependency cannot have a type dependency")
            case _: CallSiteReturn => throw new RuntimeException("type dependency cannot have a call site return dependency")
          }
        }
        case _ =>
      }
    })
    engine.recordDependencyResolution(this, result, root, relevantPosition)
  }
}

class CallSiteReturn(val name: String, description: String, val regex: Boolean, val negative: Boolean) extends Dependency(description) {

  def resolve(compare: BackwardQuery, engine: QueryEngine, root: SWANStatement.ApplyFunctionRef): Boolean = {
    var result = true
    var found = false
    var relevantPosition: Option[Position] = None
    val m = engine.findMethod(name, regex)
    engine.cg.edgesInto(m).forEach(cgEdge => {
      val applyStmt = cgEdge.src().asInstanceOf[SWANStatement.ApplyFunctionRef]
      val cfgEdge = new ControlFlowGraph.Edge(applyStmt.m.getCFG.getPredsOf(applyStmt).iterator().next(), applyStmt)
      val allocVal = new AllocVal(applyStmt.getLeftOp, applyStmt, applyStmt.getLeftOp)
      val query = new ForwardQuery(cfgEdge, allocVal)
      val results = engine.runForwardQuery(query)
      results.asStatementValWeightTable().cellSet().forEach(cell => {
        if (cell.getRowKey.equals(compare.cfgEdge()) && cell.getColumnKey.equals(compare.`var`.asInstanceOf[AllocVal].getAllocVal)) {
          found = true
          relevantPosition = applyStmt.getPosition
          dependencies.foreach {
            case cs: CallSite => result = result && cs.resolve(compare, engine, root)
            case _: TypeDependency => throw new RuntimeException("call site return cannot have a type dependency (for now)")
            case _: CallSiteReturn => throw new RuntimeException("call site return cannot have a call site return dependency")
          }
        }
      })

    })
    result = found && result
    engine.recordDependencyResolution(this, result, root, relevantPosition)
  }
}

