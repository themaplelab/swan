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

package ca.ualberta.maple.swan.spds.analysis.boomerang

import java.util.Objects
import java.util.concurrent.TimeUnit

import ca.ualberta.maple.swan.spds.analysis.boomerang.cfg.{ObservableCFG, StaticCFG}
import ca.ualberta.maple.swan.spds.analysis.boomerang.cg._
import ca.ualberta.maple.swan.spds.analysis.boomerang.poi.{AbstractPOI, CopyAccessPathChain, ExecuteImportFieldStmtPOI}
import ca.ualberta.maple.swan.spds.analysis.boomerang.results.{BackwardBoomerangResults, ForwardBoomerangResults}
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.Val.AllocVal
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene._
import ca.ualberta.maple.swan.spds.analysis.boomerang.solver.{AbstractBoomerangSolver, BackwardBoomerangSolver, ControlFlowEdgeBasedFieldTransitionListener, ForwardBoomerangSolver}
import ca.ualberta.maple.swan.spds.analysis.boomerang.stats.IBoomerangStats
import ca.ualberta.maple.swan.spds.analysis.boomerang.util.DefaultValueMap
import ca.ualberta.maple.swan.spds.analysis.pds.solver.WeightFunctions
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes.{GeneratedState, INode, Node, SingleNode}
import ca.ualberta.maple.swan.spds.analysis.wpds.impl._
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.WPAStateListener
import com.google.common.base.Stopwatch
import com.google.common.collect.{HashBasedTable, Table}

import scala.collection.mutable

abstract class WeightedBoomerang[W <: Weight](val cg: CallGraph, val scope: DataFlowScope, val options: BoomerangOptions) {

  val icfg: ObservableICFG[Statement, Method] = new ObservableStaticICFG(cg)
  protected val cfg: ObservableCFG = new StaticCFG
  protected val stats: IBoomerangStats[W] = options.statsFactory
  protected val queryGraph = new QueryGraph[W](this)
  protected val genField: mutable.HashMap[(INode[Node[Edge[Statement, Statement], Val]], Field), INode[Node[Edge[Statement, Statement], Val]]] = mutable.HashMap.empty

  protected val visitedMethods: mutable.HashSet[Method] = mutable.HashSet.empty

  protected val forwardCallSummaries: NestedWeightedPAutomatons[Edge[Statement, Statement], INode[Val], W] = new SummaryNestedWeightedPAutomatons
  protected val forwardFieldSummaries: NestedWeightedPAutomatons[Field, INode[Node[Edge[Statement, Statement], Val]], W] = new SummaryNestedWeightedPAutomatons

  protected val solverCreationListeners: mutable.HashSet[SolverCreationListener[W]] = mutable.HashSet.empty

  protected var backwardSolverIns: BackwardBoomerangSolver[W] = _
  protected val bwicfg: ObservableICFG[Statement, Method] = new BackwardsObservableICFG(icfg)
  protected val backwardCallSummaries: NestedWeightedPAutomatons[Edge[Statement, Statement], INode[Val], W] = new SummaryNestedWeightedPAutomatons
  protected val backwardFieldSummaries: NestedWeightedPAutomatons[Field, INode[Node[Edge[Statement, Statement], Val]], W] = new SummaryNestedWeightedPAutomatons

  protected val activatedPoi: mutable.MultiDict[SolverPair, INode[Node[Edge[Statement, Statement], Val]]] = mutable.MultiDict.empty
  protected val poiListeners: mutable.MultiDict[SolverPair, ExecuteImportFieldStmtPOI[W]] = mutable.MultiDict.empty

  protected var rootQuery: INode[Val] = _

  protected val fieldWrites: DefaultValueMap[FieldWritePOI, FieldWritePOI] = new DefaultValueMap[FieldWritePOI, FieldWritePOI] {
    override def createItem(key: FieldWritePOI): FieldWritePOI = {
      stats.registerFieldWritePOI(key)
      key
    }
  }

  protected var forwardQueries = 0
  protected var backwardQueries = 0

  protected var solving: Boolean = false
  protected val analysisWatch: Stopwatch = Stopwatch.createUnstarted()
  protected var lastTick: Long = 0

  val queryToSolvers: DefaultValueMap[ForwardQuery, ForwardBoomerangSolver[W]] = {
    new DefaultValueMap[ForwardQuery, ForwardBoomerangSolver[W]] {

      override def createItem(key: ForwardQuery): ForwardBoomerangSolver[W] = {
        forwardQueries += 1
        val solver = createForwardSolver(key)
        stats.registerSolver(key, solver)
        solver.callAutomaton.registerListener(
          (t: Transition[Edge[Statement, Statement], INode[Val]], w: W, aut: WeightedPAutomaton[Edge[Statement, Statement], INode[Val], W]) => checkTimeout())
        solver.fieldAutomaton.registerListener(
          (t: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]], w: W, aut: WeightedPAutomaton[Field, INode[Node[Edge[Statement, Statement], Val]], W]) => checkTimeout())
        solverCreationListeners.foreach(l => l.onCreatedSolver(key, solver))
        solver
      }
    }
  }

  val queryToBackwardSolvers: DefaultValueMap[BackwardQuery, BackwardBoomerangSolver[W]] = {
    new DefaultValueMap[BackwardQuery, BackwardBoomerangSolver[W]] {

      override def createItem(key: BackwardQuery): BackwardBoomerangSolver[W] = {
        if (backwardSolverIns != null) backwardSolverIns
        else {
          val backwardSolver = new BackwardBoomerangSolver[W](bwicfg, cfg, genField, key, WeightedBoomerang.this.options,
            createCallSummaries(null, backwardCallSummaries),
            createFieldSummaries(null, backwardFieldSummaries),
            WeightedBoomerang.this.scope, options.getBackwardFlowFunctions, cg.fieldLoadStatements,
            cg.fieldStoreStatements, null) {

            override def getFieldWeights: WeightFunctions[Edge[Statement, Statement], Val, Field, W] = {
              WeightedBoomerang.this.getBackwardFieldWeights
            }

            override def getCallWeights: WeightFunctions[Edge[Statement, Statement], Val, Edge[Statement, Statement], W] = {
              WeightedBoomerang.this.getBackwardCallWeights
            }

            override def forceUnbalanced(node: INode[Val], sources: collection.Set[INode[Val]]): Boolean = {
              sources.contains(rootQuery) && callAutomaton.isUnbalancedState(node)
            }

            override def preventCallTransitionAdd(trans: Transition[Edge[Statement, Statement], INode[Val]], weight: W): Boolean = {
              checkTimeout()
              super.preventCallTransitionAdd(trans, weight)
            }

            override def preventFieldTransitionAdd(trans: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]], weight: W): Boolean = {
              checkTimeout()
              super.preventFieldTransitionAdd(trans, weight)
            }
          }

          backwardSolver.registerListener((node: Node[Edge[Statement, Statement], Val]) => {
            val allocNode = isAllocationNode(node.stmt, node.fact)
            if (allocNode.nonEmpty || node.stmt.target.isInstanceOf[FieldLoadStatement]) {
              backwardSolver.fieldAutomaton.registerListener(new EmptyFieldListener(key, node))
            }
            addVisitedMethod(node.stmt.start.method)
          })

          backwardSolverIns = backwardSolver
          backwardSolver
        }
      }
    }
  }

  protected def getForwardCallWeights(sourceQuery: ForwardQuery): WeightFunctions[Edge[Statement, Statement], Val, Edge[Statement, Statement], W]

  protected def getForwardFieldWeights: WeightFunctions[Edge[Statement, Statement], Val, Field, W]

  protected def getBackwardCallWeights: WeightFunctions[Edge[Statement, Statement], Val, Edge[Statement, Statement], W]

  protected def getBackwardFieldWeights: WeightFunctions[Edge[Statement, Statement], Val, Field, W]

  def isAllocationNode(s: Edge[Statement, Statement], fact: Val): Option[AllocVal] = {
    options.getAllocationVal(s.start.method, s.start, fact)
  }

  def isAllocationNode(fact: Val, sourceQuery: ForwardQuery): Boolean = {
    fact.equals(sourceQuery.variable.asUnbalanced(sourceQuery.cfgEdge))
  }

  def addVisitedMethod(method: Method): Unit = {
    if (!scope.isExcluded(method)) visitedMethods.add(method)
  }

  def unregisterAllListeners(): Unit = {
    queryToSolvers.values.foreach(_.unregisterAllListeners())
    queryToBackwardSolvers.values.foreach(_.unregisterAllListeners())
    cfg.unregisterAllListeners()
    queryGraph.unregisterAllListeners()
    poiListeners.clear()
    activatedPoi.clear()
    fieldWrites.clear()
  }

  def solve(query: ForwardQuery): ForwardBoomerangResults[W] = {
    if (!options.allowMultipleQueries && solving) {
      throw new RuntimeException(
        "One cannot re-use the same Boomerang solver for more than one query, unless option allowMultipleQueries is enabled. If allowMultipleQueries is enabled, ensure to call unregisterAllListeners() on this instance upon termination of all queries.")
    }
    solving = true
    if (!analysisWatch.isRunning) analysisWatch.start()
    queryGraph.addRoot(query)
    forwardSolve(query)
    icfg.computeFallback()
    if (!options.allowMultipleQueries) {
      unregisterAllListeners()
    }
    if (analysisWatch.isRunning) analysisWatch.stop()
    new ForwardBoomerangResults[W](query, icfg, cfg, this.queryToSolvers, stats, analysisWatch, visitedMethods)
  }

  def solve(query: BackwardQuery): BackwardBoomerangResults[W] = {
    if (!options.allowMultipleQueries && solving) {
      throw new RuntimeException(
        "One cannot re-use the same Boomerang solver for more than one query, unless option allowMultipleQueries is enabled. If allowMultipleQueries is enabled, ensure to call unregisterAllListeners() on this instance upon termination of all queries.")
    }
    solving = true
    if (!analysisWatch.isRunning) analysisWatch.start()
    queryGraph.addRoot(query)
    backwardSolve(query)
    icfg.computeFallback()
    if (!options.allowMultipleQueries) {
      unregisterAllListeners()
    }
    if (analysisWatch.isRunning) analysisWatch.stop()
    new BackwardBoomerangResults[W](query, this.queryToSolvers, backwardSolverIns, stats, analysisWatch)
  }

  protected def forwardSolve(query: ForwardQuery): AbstractBoomerangSolver[W] = {
    val cfgEdge = query.asNode.stmt
    val solver = queryToSolvers.getOrCreate(query)
    val fieldTarget = solver.createQueryNodeField(query)
    val callTarget = solver.generateCallState(new SingleNode(query.variable), query.cfgEdge)
    val stmt = cfgEdge.start
    val field = stmt match {
      case statement: FieldStoreStatement => statement.getFieldStore.y
      case _ => Field.empty
    }
    val v = stmt match {
      case statement: FieldStoreStatement => statement.getFieldStore.x
      case _ => query.variable.asInstanceOf[AllocVal].delegate
    }
    query match {
      case q: WeightedForwardQuery[W] => solver.solve(new Node(cfgEdge, v), field, fieldTarget, cfgEdge, callTarget, q.weight)
      case _ => solver.solve(new Node(cfgEdge, v), field, fieldTarget, cfgEdge, callTarget)
    }
    solver
  }

  protected def backwardSolve(query: BackwardQuery): Unit = {
    val solver = queryToBackwardSolvers.getOrCreate(query)
    val fieldTarget =solver.createQueryNodeField(query)
    val callTarget = solver.generateCallState(new SingleNode(query.variable), query.cfgEdge)
    if (rootQuery == null) rootQuery = callTarget
    solver.solve(query.asNode, Field.empty, fieldTarget, query.cfgEdge, callTarget)
  }

  protected def createForwardSolver(sourceQuery: ForwardQuery): ForwardBoomerangSolver[W] = {
    val solver = new ForwardBoomerangSolver[W](icfg, cfg, sourceQuery, genField, options,
      createCallSummaries(sourceQuery, forwardCallSummaries), createFieldSummaries(sourceQuery, forwardFieldSummaries),
      scope, options.getForwardFlowFunctions, cg.fieldLoadStatements, cg.fieldStoreStatements, sourceQuery.getType) {

      override protected def overwriteFieldAtStatement(fieldWriteStatementEdge: Edge[Statement, Statement], killedTransition: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]]): Unit = {
        val backwardQuery = new BackwardQuery(killedTransition.target.fact.stmt, fieldWriteStatementEdge.target.asInstanceOf[CallSiteStatement].rhs)
        val copyAccessPathChain = new CopyAccessPathChain[W](
          queryToSolvers(sourceQuery), queryToBackwardSolvers.getOrCreate(backwardQuery),
          fieldWriteStatementEdge, killedTransition)
        copyAccessPathChain.exec()
        queryGraph.addEdge(sourceQuery, killedTransition.start.fact, backwardQuery)
      }

      override def getFieldWeights: WeightFunctions[Edge[Statement, Statement], Val, Field, W] = WeightedBoomerang.this.getForwardFieldWeights

      override def getCallWeights: WeightFunctions[Edge[Statement, Statement], Val, Edge[Statement, Statement], W] = WeightedBoomerang.this.getForwardCallWeights(sourceQuery)

      override def forceUnbalanced(iNode: INode[Val], collection: scala.collection.Set[INode[Val]]): Boolean = {
        queryGraph.isRoot(sourceQuery)
      }

      override def addCallRule(rule: Rule[Edge[Statement, Statement], INode[Val], W]): Unit = {
        if (!preventCallRuleAdd(sourceQuery, rule)) super.addCallRule(rule)
      }

      override def preventCallTransitionAdd(t: Transition[Edge[Statement, Statement], INode[Val]], weight: W): Boolean = {
        checkTimeout()
        super.preventCallTransitionAdd(t, weight)
      }

      override def preventFieldTransitionAdd(t: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]], weight: W): Boolean = {
        checkTimeout()
        super.preventFieldTransitionAdd(t, weight)
      }
    }

    solver.registerListener((node: Node[Edge[Statement, Statement], Val]) => {
      if (node.stmt.start.isInstanceOf[FieldStoreStatement]) {
        forwardHandleFieldWRite(node, createFieldStore(node.stmt), sourceQuery)
      }
      addVisitedMethod(node.stmt.start.method)
    })
    solver
  }

  def forwardHandleFieldWRite(node: Node[Edge[Statement, Statement], Val], fieldWritePoi: WeightedBoomerang.this.FieldWritePOI, sourceQuery: ForwardQuery): Unit = {
    val backwardQuery = new BackwardQuery(node.stmt, fieldWritePoi.baseVar)
    if (node.fact.equals(fieldWritePoi.storedVar)) {
      backwardSolve(backwardQuery)
      queryGraph.addEdge(sourceQuery, node, backwardQuery)
      queryToSolvers(sourceQuery)
        .registerStatementFieldTransitionListener(new ForwardHandleFieldWrite(sourceQuery, fieldWritePoi, node.stmt))
    }
    if (node.fact.equals(fieldWritePoi.baseVar)) {
      queryToSolvers.getOrCreate(sourceQuery).fieldAutomaton
        .registerListener(new TriggerBaseAllocationAtFieldWrite(new SingleNode(node), fieldWritePoi, sourceQuery))
    }
  }

  def createFieldStore(edge: Edge[Statement, Statement]): FieldWritePOI = {
    val fs = edge.start.asInstanceOf[FieldStoreStatement]
    val fw = fs.getFieldStore
    fieldWrites.getOrCreate(new FieldWritePOI(edge, fw.x, fw.y, fs.rhs))
  }

  def preventCallRuleAdd(sourceQuery: ForwardQuery, rule: Rule[ControlFlowGraph.Edge[Statement, Statement], INode[Val], W]): Boolean = {
    false
  }

  protected def createCallSummaries(sourceQuery: ForwardQuery,
                                    summaries: NestedWeightedPAutomatons[Edge[Statement, Statement], INode[Val], W]): NestedWeightedPAutomatons[Edge[Statement, Statement], INode[Val], W] = {
    new NestedWeightedPAutomatons[Edge[Statement, Statement], INode[Val], W] {

      override def putSummaryAutomaton(target: INode[Val], aut: WeightedPAutomaton[Edge[Statement, Statement], INode[Val], W]): Unit = {
        summaries.putSummaryAutomaton(target, aut)
      }

      override def getSummaryAutomaton(target: INode[Val]): WeightedPAutomaton[Edge[Statement, Statement], INode[Val], W] = {
        sourceQuery.variable match {
          case allocVal: AllocVal =>
            val f = if (target.fact.isUnbalanced) target.fact.asUnbalanced(null) else target.fact
            if (f.equals(allocVal.delegate)) {
              queryToSolvers.getOrCreate(sourceQuery).callAutomaton
            } else summaries.getSummaryAutomaton(target)
          case _ => summaries.getSummaryAutomaton(target)
        }
      }
    }
  }

  protected def createFieldSummaries(sourceQuery: ForwardQuery,
                                     summaries: NestedWeightedPAutomatons[Field, INode[Node[Edge[Statement, Statement], Val]], W]): NestedWeightedPAutomatons[Field, INode[Node[Edge[Statement, Statement], Val]], W] = {
    new NestedWeightedPAutomatons[Field, INode[Node[Edge[Statement, Statement], Val]], W] {

      override def putSummaryAutomaton(target: INode[Node[Edge[Statement, Statement], Val]], aut: WeightedPAutomaton[Field, INode[Node[Edge[Statement, Statement], Val]], W]): Unit = {
        summaries.putSummaryAutomaton(target, aut)
      }

      override def getSummaryAutomaton(target: INode[Node[Edge[Statement, Statement], Val]]): WeightedPAutomaton[Field, INode[Node[Edge[Statement, Statement], Val]], W] = {
        if (target.fact.equals(sourceQuery.asNode)) {
          queryToSolvers.getOrCreate(sourceQuery).fieldAutomaton
        } else {
          summaries.getSummaryAutomaton(target)
        }
      }
    }
  }

  def checkTimeout(): Unit = {
    if (options.analysisTimeoutMS > 0) {
      val elapsed = analysisWatch.elapsed(TimeUnit.MILLISECONDS)
      if (elapsed - lastTick > 15000) {
        lastTick = elapsed
      }
      if (options.analysisTimeoutMS < elapsed) {
        if (analysisWatch.isRunning) analysisWatch.stop()
        throw new RuntimeException("Timeout") // TODO: Custom timeout exception
      }
    }
  }

  def activateAllPois(pair: SolverPair, start: INode[Node[ControlFlowGraph.Edge[Statement, Statement], Val]]): Unit = {
    if (!activatedPoi.containsEntry(pair, start)) {
      activatedPoi.addOne(pair, start)
      poiListeners.get(pair).foreach(l => l.trigger(start))
    }
  }

  def registerActivationListener(solverPair: SolverPair, exec: ExecuteImportFieldStmtPOI[W]): Unit = {
    activatedPoi.get(solverPair).foreach(node => exec.trigger(node))
    poiListeners.addOne(solverPair, exec)
  }

  def getResults(seed: ForwardQuery): Table[Edge[Statement, Statement], Val, W] = {
    val results = HashBasedTable.create[Edge[Statement, Statement], Val, W]()
    val callAut = queryToSolvers.getOrCreate(seed).callAutomaton
    callAut.getTransitionsToFinalWeights.foreach(e => {
      val t = e._1
      if (t.label.start.method.equals(t.start.fact.method)) {
        results.put(t.label, t.start.fact, e._2)
      }
    })
    results
  }

  class FieldWritePOI(statement: Edge[Statement, Statement], base: Val, field: Field, stored: Val)
    extends AbstractPOI[Edge[Statement, Statement], Val, Field](statement, base, field, stored) {

    override def execute(baseAllocation: ForwardQuery, flowAllocation: Query): Unit = {
      flowAllocation match {
        case query: ForwardQuery => {
          val baseSolver = queryToSolvers(baseAllocation)
          val flowSolver = queryToSolvers(query)
          val exec = new ExecuteImportFieldStmtPOI[W](baseSolver, flowSolver, this) {

            override def activate(start: INode[Node[Edge[Statement, Statement], Val]]): Unit = {
              activateAllPois(new SolverPair(flowSolver, baseSolver), start)
            }
          }
          registerActivationListener(new SolverPair(flowSolver, baseSolver), exec)
          exec.solve()
        }
        case _ =>
      }
    }
  }

  protected class SolverPair(protected val flowSolver: AbstractBoomerangSolver[W],
                             protected val baseSolver: AbstractBoomerangSolver[W]) {

    def getOuterType: WeightedBoomerang[W] = WeightedBoomerang.this

    override def hashCode(): Int = Objects.hashCode(getOuterType, baseSolver, flowSolver)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: SolverPair => Objects.equals(other.getOuterType, getOuterType) &&
          Objects.equals(other.flowSolver, flowSolver) && Objects.equals(other.baseSolver, baseSolver)
        case _ => false
      }
    }
  }

  protected class EmptyFieldListener(key: BackwardQuery,
                                     node: Node[ControlFlowGraph.Edge[Statement, Statement], Val]) extends WPAStateListener[Field, INode[Node[Edge[Statement, Statement], Val]], W](new SingleNode(node)) {

    def getOuterType: WeightedBoomerang[W] = WeightedBoomerang.this

    override def onOutTransitionAdded(t: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]], w: W, weightedPAutomaton: WeightedPAutomaton[Field, INode[Node[Edge[Statement, Statement], Val]], W]): Unit = {
      if (t.label.equals(Field.empty)) {
        val allocNode = isAllocationNode(node.stmt, node.fact)
        if (allocNode.nonEmpty) {
          val forwardQuery = new ForwardQuery(node.stmt, allocNode.get)
          forwardSolve(forwardQuery)
          queryGraph.addEdge(key, node, forwardQuery)
        }
      }
    }

    override def onInTransitionAdded(t: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]], w: W, weightedPAutomaton: WeightedPAutomaton[Field, INode[Node[Edge[Statement, Statement], Val]], W]): Unit = {}

    override def hashCode: Int = super.hashCode + Objects.hashCode(getOuterType)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: EmptyFieldListener => super.equals(other) && Objects.equals(other.getOuterType, getOuterType)
        case _ => false
      }
    }
  }

  protected class ForwardHandleFieldWrite(protected val sourceQuery: ForwardQuery,
                                          protected val fieldWritePoi: WeightedBoomerang.this.FieldWritePOI,
                                          protected val statement: ControlFlowGraph.Edge[Statement, Statement]) extends ControlFlowEdgeBasedFieldTransitionListener[W](statement) {

    override def onAddedTransition(t: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]]): Unit = {
      if (!t.start.isInstanceOf[GeneratedState[_, _]] && t.start.fact.stmt.equals(statement)) {
        fieldWritePoi.addFlowAllocation(sourceQuery)
      }
    }

    def getOuterType: WeightedBoomerang[W] = WeightedBoomerang.this

    override def hashCode(): Int = Objects.hashCode(getOuterType, sourceQuery)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: ForwardHandleFieldWrite =>
          Objects.equals(other.getOuterType, getOuterType) && Objects.equals(other.sourceQuery, sourceQuery)
        case _ => false
      }
    }
  }

  protected class TriggerBaseAllocationAtFieldWrite(state: SingleNode[Node[ControlFlowGraph.Edge[Statement, Statement], Val]],
                                                    protected val fieldWritePoi: WeightedBoomerang.this.FieldWritePOI,
                                                    protected val sourceQuery: ForwardQuery) extends WPAStateListener[Field, INode[Node[Edge[Statement, Statement], Val]], W](state) {

    override def onOutTransitionAdded(t: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]], w: W, weightedPAutomaton: WeightedPAutomaton[Field, INode[Node[Edge[Statement, Statement], Val]], W]): Unit = {
      if (isAllocationNode(t.target.fact.fact, sourceQuery)) {
        fieldWritePoi.addBaseAllocation(sourceQuery)
      }
    }

    override def onInTransitionAdded(t: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]], w: W, weightedPAutomaton: WeightedPAutomaton[Field, INode[Node[Edge[Statement, Statement], Val]], W]): Unit = {}

    def getOuterType: WeightedBoomerang[W] = WeightedBoomerang.this

    override def hashCode: Int = super.hashCode + Objects.hashCode(getOuterType, fieldWritePoi, sourceQuery)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: TriggerBaseAllocationAtFieldWrite =>
          super.equals(other) && Objects.equals(other.getOuterType, getOuterType) &&
            Objects.equals(other.sourceQuery, sourceQuery) && Objects.equals(other.fieldWritePoi, fieldWritePoi)
        case _ => false
      }
    }
  }
}
