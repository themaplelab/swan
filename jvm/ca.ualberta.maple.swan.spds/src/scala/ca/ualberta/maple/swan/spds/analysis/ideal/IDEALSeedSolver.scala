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

package ca.ualberta.maple.swan.spds.analysis.ideal

import ca.ualberta.maple.swan.spds.analysis.boomerang.{BackwardQuery, ForwardQuery, WeightedBoomerang}
import ca.ualberta.maple.swan.spds.analysis.boomerang.results.ForwardBoomerangResults
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.ControlFlowGraph.Edge
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{Assignment, CallSiteStatement, ControlFlowGraph, Field, Statement, Val}
import ca.ualberta.maple.swan.spds.analysis.boomerang.solver.AbstractBoomerangSolver
import ca.ualberta.maple.swan.spds.analysis.ideal.IDEALSeedSolver.Phases
import ca.ualberta.maple.swan.spds.analysis.pds.solver.nodes.{GeneratedState, INode, Node, SingleNode}
import ca.ualberta.maple.swan.spds.analysis.pds.solver.{OneWeightFunctions, WeightFunctions}
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.{NormalRule, PushRule, Rule, StackListener, Transition, Weight, WeightedPAutomaton}
import ca.ualberta.maple.swan.spds.analysis.wpds.interfaces.{WPAStateListener, WPAUpdateListener}

import java.util.Objects
import scala.collection.mutable

class IDEALSeedSolver[W <: Weight](analysisDefinition: IDEALAnalysisDefinition[W],
                                   seed: ForwardQuery) {

  protected val idealWeightFunctions = new IDEALWeightFunctions[W](analysisDefinition.weightFunctions, analysisDefinition.enableStrongUpdates)
  protected val one: W = analysisDefinition.weightFunctions.getOne
  protected val phase1Solver: WeightedBoomerang[W] = createSolver(Phases.ObjectFlow)
  protected val phase2Solver: WeightedBoomerang[W] = createSolver(Phases.ValueFlow)
  protected var killedRules: Int = 0
  protected val affectedStrongUpdateStmt: mutable.MultiDict[Node[Edge[Statement, Statement], Val], Edge[Statement, Statement]] = mutable.MultiDict.empty
  protected val weakUpdates: mutable.HashSet[Node[Edge[Statement, Statement], Val]] = mutable.HashSet.empty

  def run(): ForwardBoomerangResults[W] = {
    val resultPhase1 = runPhase(this.phase1Solver, Phases.ObjectFlow)
    val resultPhase2 = runPhase(this.phase2Solver, Phases.ValueFlow)
    resultPhase2
  }

  def createSolver(phase: Phases): WeightedBoomerang[W] = {
    new WeightedBoomerang[W](analysisDefinition.callGraph, analysisDefinition.getDataFlowScope, analysisDefinition.boomerangOptions) {

      override protected def getForwardCallWeights(sourceQuery: ForwardQuery): WeightFunctions[ControlFlowGraph.Edge[Statement, Statement], Val, ControlFlowGraph.Edge[Statement, Statement], W] = {
        if (sourceQuery.equals(seed)) idealWeightFunctions else new OneWeightFunctions(one)
      }

      override protected def getForwardFieldWeights: WeightFunctions[ControlFlowGraph.Edge[Statement, Statement], Val, Field, W] = {
        new OneWeightFunctions(one)
      }

      override protected def getBackwardCallWeights: WeightFunctions[ControlFlowGraph.Edge[Statement, Statement], Val, ControlFlowGraph.Edge[Statement, Statement], W] = {
        new OneWeightFunctions(one)
      }

      override protected def getBackwardFieldWeights: WeightFunctions[ControlFlowGraph.Edge[Statement, Statement], Val, Field, W] = {
        new OneWeightFunctions(one)
      }

      override def preventCallRuleAdd(sourceQuery: ForwardQuery, rule: Rule[ControlFlowGraph.Edge[Statement, Statement], INode[Val], W]): Boolean = {
        phase.equals(Phases.ValueFlow) && sourceQuery.equals(seed) && preventStrongUpdateFlows(rule)
      }
    }
  }

  def preventStrongUpdateFlows(rule: Rule[ControlFlowGraph.Edge[Statement, Statement], INode[Val], W]): Boolean = {
    if (rule.s1.equals(rule.s2) && idealWeightFunctions.isStrongUpdateStatement(rule.l2.get) && idealWeightFunctions.isKillFlow(new Node(rule.l2.get, rule.s2.fact))) {
      killedRules += 1
      true
    } else {
      rule match {
        case pushRule: PushRule[_, _, _]
          if idealWeightFunctions.isStrongUpdateStatement(pushRule.callSite.asInstanceOf[Edge[Statement, Statement]]) &&
            idealWeightFunctions.isKillFlow(new Node(pushRule.callSite.asInstanceOf[Edge[Statement, Statement]], rule.s1.fact)) => {
          killedRules += 1
          true
        }
        case _ => false
      }
    }
  }

  def runPhase(boomerang: WeightedBoomerang[W], phase: IDEALSeedSolver.Phases): ForwardBoomerangResults[W] = {
    idealWeightFunctions.setPhase(phase)
    if (phase.equals(Phases.ValueFlow)) {
      registerIndirectFlowListener(boomerang.queryToSolvers.getOrCreate(seed))
    }
    idealWeightFunctions.registerListener((curr: Node[Edge[Statement, Statement], Val]) => {
      if (!phase.equals(Phases.ValueFlow)) {
        val seedSolver = boomerang.queryToSolvers.getOrCreate(seed)
        seedSolver.fieldAutomaton.registerListener(new TriggerBackwardQuery(seedSolver, boomerang, curr))
      }
    })
    val res = boomerang.solve(seed)
    boomerang.unregisterAllListeners()
    res
  }

  protected def setWeakUpdate(curr: Node[ControlFlowGraph.Edge[Statement, Statement], Val]): Unit = {
    if (weakUpdates.add(curr)) {
      affectedStrongUpdateStmt.get(curr).foreach(s => idealWeightFunctions.weakUpdate(s))
    }
  }

  protected def addAffectedPotentialStrongUpdate(strongUpdateNode: Node[ControlFlowGraph.Edge[Statement, Statement], Val],
                                                 stmt: ControlFlowGraph.Edge[Statement, Statement]): Unit = {
    if (!affectedStrongUpdateStmt.containsEntry(strongUpdateNode, stmt)) {
      affectedStrongUpdateStmt.addOne(strongUpdateNode, stmt)
      idealWeightFunctions.potentialStrongUpdate(stmt)
      if (weakUpdates.contains(strongUpdateNode)) idealWeightFunctions.weakUpdate(stmt)
    }
  }

  protected def registerIndirectFlowListener(solver: AbstractBoomerangSolver[W]): Unit = {
    solver.callAutomaton.registerListener((t: Transition[Edge[Statement, Statement], INode[Val]],
                                           w: W, aut: WeightedPAutomaton[Edge[Statement, Statement], INode[Val], W]) => {
      if (!t.start.isInstanceOf[GeneratedState[_, _]]) {
        val source = new Node(t.label, t.start.fact)
        val indirectFlows = idealWeightFunctions.getAliasesFor(source)
        indirectFlows.foreach(indirectFlow => {
          solver.addCallRule(new NormalRule(
            new SingleNode(source.fact),
            source.stmt,
            new SingleNode(indirectFlow.fact),
            indirectFlow.stmt,
            one))
          solver.addFieldRule(new NormalRule(
            solver.asFieldFact(source),
            solver.fieldWildCard,
            solver.asFieldFact(indirectFlow),
            solver.fieldWildCard,
            one))
        })
      }
    })
  }

  protected class AddIndirectFlowAtCallSite(protected val callSite: Edge[Statement, Statement],
                                            protected val returnedFact: Val) extends WPAUpdateListener[Edge[Statement, Statement], INode[Val], W] {

    def getOuterType: IDEALSeedSolver[W] = IDEALSeedSolver.this

    override def onWeightAdded(t: Transition[Edge[Statement, Statement], INode[Val]], w: W, aut: WeightedPAutomaton[Edge[Statement, Statement], INode[Val], W]): Unit = {
      if (t.label.equals(callSite)) {
        idealWeightFunctions.addNonKillFlow(new Node(callSite, returnedFact))
        idealWeightFunctions.addIndirectFlow(new Node(callSite, returnedFact), new Node(callSite, t.start.fact))
      }
    }

    override def hashCode(): Int = Objects.hashCode(getOuterType, callSite, returnedFact)

    override def equals(obj: Any): Boolean = {
      obj match {
        case other: AddIndirectFlowAtCallSite => {
          Objects.equals(other.getOuterType, getOuterType) && Objects.equals(other.callSite, callSite) &&
            Objects.equals(other.returnedFact, returnedFact)
        }
        case _ => false
      }
    }
  }

  protected class TriggerBackwardQuery(seedSolver: AbstractBoomerangSolver[W], boomerang: WeightedBoomerang[W],
                                       strongUpdateNode: Node[ControlFlowGraph.Edge[Statement, Statement], Val])
    extends WPAStateListener[Field, INode[Node[Edge[Statement, Statement], Val]], W](new SingleNode(strongUpdateNode)) {

    override def onOutTransitionAdded(t: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]], w: W, weightedPAutomaton: WeightedPAutomaton[Field, INode[Node[Edge[Statement, Statement], Val]], W]): Unit = {
      if (t.getLabel.equals(Field.empty)) {
        addAffectedPotentialStrongUpdate(strongUpdateNode, strongUpdateNode.stmt)
        strongUpdateNode.stmt.getMethod.getCFG.getPredsOf(strongUpdateNode.stmt.start).foreach(u => {
          val query = new BackwardQuery(new Edge(u, strongUpdateNode.stmt.start), strongUpdateNode.fact)
          val queryResults = boomerang.solve(query)
          val queryAllocationSites = mutable.HashSet.from(queryResults.getAllocationSites.keySet)
          setWeakUpdateIfNecessary()
          injectAliasesAtStrongUpdates(queryAllocationSites)
          injectAliasesAtStrongUpdatesAtCallStack(queryAllocationSites)
        })
      }
    }

    protected def setWeakUpdateIfNecessary(): Unit = {
      boomerang.queryToSolvers.foreach(entry => {
        entry._2.synchedEmptyStackReachable(strongUpdateNode, (_: Node[Edge[Statement, Statement], Val]) => {
          if (!entry._1.asNode.equals(seed.asNode)) setWeakUpdate(strongUpdateNode)
        })
      })
    }

    protected def injectAliasesAtStrongUpdates(queryAllocationSites: mutable.HashSet[ForwardQuery]): Unit = {
      queryAllocationSites.foreach(e => {
        boomerang.queryToSolvers(e).callAutomaton.registerListener((t: Transition[Edge[Statement, Statement], INode[Val]],
                                                                    w: W, aut: WeightedPAutomaton[Edge[Statement, Statement], INode[Val], W]) => {
          if (t.getLabel.equals(strongUpdateNode.stmt)) {
            idealWeightFunctions.addNonKillFlow(strongUpdateNode)
            idealWeightFunctions.addIndirectFlow(strongUpdateNode, new Node(strongUpdateNode.stmt, t.getStart.fact))
          }
        })
      })
    }

    protected def injectAliasesAtStrongUpdatesAtCallStack(queryAllocationSites: mutable.HashSet[ForwardQuery]): Unit = {
      seedSolver.callAutomaton.registerListener(
        new StackListener[Edge[Statement, Statement], INode[Val], W](
          seedSolver.callAutomaton, new SingleNode(strongUpdateNode.fact), strongUpdateNode.stmt) {

          override def stackElement(callSiteEdge: Edge[Statement, Statement]): Unit = {
            val callSite = callSiteEdge.start
            boomerang.checkTimeout()
            addAffectedPotentialStrongUpdate(strongUpdateNode, callSiteEdge)
            queryAllocationSites.foreach(e => {
              val solver = boomerang.queryToSolvers(e)
              solver.addApplySummaryListener((summaryCallSite: Edge[Statement, Statement], factInCallee: Val,
                                              spInCallee: Edge[Statement, Statement],
                                              exitStmt: Edge[Statement, Statement], returnedFact: Val) => {
                if (callSiteEdge.equals(summaryCallSite) && callSite.isInstanceOf[CallSiteStatement]) {
                  if (returnedFact.isReturnLocal && callSite.isInstanceOf[Assignment]) {
                    solver.callAutomaton.registerListener(new AddIndirectFlowAtCallSite(callSiteEdge, callSite.asInstanceOf[Assignment].lhs))
                  }
                  callSite.asInstanceOf[CallSiteStatement].getInvokeExpr.getArgs.zipWithIndex.foreach(arg => {
                    if (returnedFact.isParameterLocal(arg._2)) {
                      solver.callAutomaton.registerListener(new AddIndirectFlowAtCallSite(callSiteEdge,
                        callSite.asInstanceOf[CallSiteStatement].getInvokeExpr.getArg(arg._2)))
                    }
                  })
                }
              })
            })
          }

          override def anyContext(end: Edge[Statement, Statement]): Unit = {}
        })
    }

    override def onInTransitionAdded(t: Transition[Field, INode[Node[Edge[Statement, Statement], Val]]], w: W, weightedPAutomaton: WeightedPAutomaton[Field, INode[Node[Edge[Statement, Statement], Val]], W]): Unit = {

    }
  }
}

object IDEALSeedSolver {

  trait Phases
  object Phases {
    case object ObjectFlow extends Phases
    case object ValueFlow extends Phases
  }
}
