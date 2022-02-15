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

package ca.ualberta.maple.swan.spds.cg

import boomerang.scene.jimple.JimpleStatement
import boomerang.scene.{AllocVal, ControlFlowGraph, Val}
import ca.ualberta.maple.swan.ir.{ModuleGroup, Operator, SymbolTableEntry}
import ca.ualberta.maple.swan.spds.Stats.{CallGraphStats, SpecificCallGraphStats}
import ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.PointerAnalysisStyle
import ca.ualberta.maple.swan.spds.cg.CallGraphConstructor.Options
import ca.ualberta.maple.swan.spds.cg.CallGraphUtils.addCGEdge
import ca.ualberta.maple.swan.spds.cg.VTA.VTAStats
import ca.ualberta.maple.swan.spds.structures.{SWANField, SWANInvokeExpr, SWANStatement, SWANVal}
import ca.ualberta.maple.swan.utils.Logging
import fj.data.Java
import org.jgrapht.Graph
import org.jgrapht.graph.{DefaultDirectedGraph, DefaultEdge}
import org.jgrapht.traverse.DepthFirstIterator
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector

import scala.collection.mutable
import scala.jdk.CollectionConverters._
import ujson.Value

import scala.collection.mutable.ArrayBuffer

class VTA(mg: ModuleGroup, pas: PointerAnalysisStyle.Style, options: Options) extends TrivialEdges(mg, options: Options) {

  pas match {
    case PointerAnalysisStyle.None =>
    case _ =>
      throw new RuntimeException("Pointer Analysis must not be set for VTA call graph construction")
  }

  val conservativeGraph = new CHA(mg,pas, options: Options)
  //val conservativeGraph = new UCGSound(mg,PointerAnalysisStyle.SPDS,true)
  conservativeGraph.buildCallGraph()

  type valGraphNode = Either[SWANVal,SWANField]
  type reachingTypes = SWANVal
  val reachingTypes = mutable.HashMap.empty[valGraphNode,reachingTypes]

  val valueGraph: Graph[valGraphNode, DefaultEdge] = new DefaultDirectedGraph(classOf[DefaultEdge])
  def addValNodes(): Unit = {
    methods.foreach{ case (_,m) =>
      m.allValues.foreach{ case (_,v) =>
        valueGraph.addVertex(Left(v.asInstanceOf[SWANVal]))
      }
      m.getStatements.iterator().asScala.foreach(s => s.asInstanceOf[SWANStatement])
      m.getStatements.iterator().asScala.foreach{
        case s: SWANStatement.FieldLoad =>
          valueGraph.addVertex(Right(s.getLoadedField.asInstanceOf[SWANField]))
        case s: SWANStatement.FieldWrite =>
          valueGraph.addVertex(Right(s.getWrittenField.asInstanceOf[SWANField]))
        case s: SWANStatement.StaticFieldLoad =>
          valueGraph.addVertex(Right(s.getLoadedField.asInstanceOf[SWANField]))
        case s: SWANStatement.StaticFieldStore =>
          valueGraph.addVertex(Right(s.getWrittenField.asInstanceOf[SWANField]))
        case _ =>
      }
    }
  }
  def addValFlow(from: SWANVal, to: SWANVal): Unit = {
    if (valueGraph.addEdge(Left(from), Left(to)) != null) stats.assignEdges += 1
  }
  def addFieldLoadFlow(from: SWANField, to: SWANVal): Unit = {
    if (valueGraph.addEdge(Right(from), Left(to)) != null) stats.assignEdges +=1
  }
  def addFieldStoreFlow(from: SWANVal, to: SWANField): Unit = {
    if (valueGraph.addEdge(Left(from), Right(to)) != null) stats.assignEdges +=1
  }

  def addLocalFlow(): Unit = {
    methods.foreach{ case (_,m) => {
      m.getCFG.blocks.foreach{ case (_,b) => {
        b.stmts.foreach {
          case s: SWANStatement.Allocation => {
            reachingTypes.addOne(Left(s.getLeftOp.asInstanceOf[SWANVal]),s.getRightOp.asInstanceOf[SWANVal])
            stats.allocations += 1
          }
          /*
          case s: SWANStatement.FunctionRef => {
            reachingTypes.addOne(Left(s.getLeftOp.asInstanceOf[SWANVal]),s.inst)
          }
          case s: SWANStatement.BuiltinFunctionRef => {
            reachingTypes.addOne(Left(s.getLeftOp.asInstanceOf[SWANVal]),s.inst)
          }
          case s: SWANStatement.DynamicFunctionRef => {
            reachingTypes.addOne(Left(s.getLeftOp.asInstanceOf[SWANVal]),s.inst)
          }
           */
          case s: SWANStatement.Assign => {
            addValFlow(from = s.getRightOp.asInstanceOf[SWANVal], to = s.getLeftOp.asInstanceOf[SWANVal])
          }
          case s: SWANStatement.FieldLoad => {
            // x = y.z
            val xVal = s.getLeftOp.asInstanceOf[SWANVal]
            val fieldLoad = s.getFieldLoad
            // val yVal = fieldLoad.getX.asInstanceOf[SWANVal]
            val z = fieldLoad.getY.asInstanceOf[SWANField]
            addFieldLoadFlow(to = xVal, from = z)
          }
          case s: SWANStatement.StaticFieldLoad => {
            // x = Static z
            val xVal = s.getLeftOp.asInstanceOf[SWANVal]
            // val yVal = fieldLoad.getX.asInstanceOf[SWANVal]
            val z = s.getLoadedField.asInstanceOf[SWANField]
            addFieldLoadFlow(to = xVal, from = z)
          }
          case s: SWANStatement.FieldWrite => {
            // x.f = y
            val fieldStore = s.getFieldStore
            // val xVal = fieldStore.getX.asInstanceOf[SWANVal]
            val f = fieldStore.getY.asInstanceOf[SWANField]
            val yVal = s.getRightOp.asInstanceOf[SWANVal]
            addFieldStoreFlow(to = f, from = yVal)
          }
          case s: SWANStatement.StaticFieldStore => {
            // statif f = y
            val f = s.getWrittenField.asInstanceOf[SWANField]
            val yVal = s.getRightOp.asInstanceOf[SWANVal]
            addFieldStoreFlow(to = f, from = yVal)
          }
          case s: SWANStatement.Return => {
            val yVal = s.getReturnOp.asInstanceOf[SWANVal]
            conservativeGraph.cg.edgesInto(s.getSWANMethod).forEach{ edge =>
              // x = foo() // foo() { return y }
              val xVal = edge.src().getLeftOp.asInstanceOf[SWANVal]
              addValFlow(from = yVal, to = xVal)
            }
          }
          case s: SWANStatement.Yield => {
            val yVal = s.getReturnOp.asInstanceOf[SWANVal]
            conservativeGraph.cg.edgesInto(s.getSWANMethod).forEach{ edge =>
              val xVal = edge.src().getLeftOp.asInstanceOf[SWANVal]
              addValFlow(from = yVal, to = xVal)
            }
          }
          case s: SWANStatement.ApplyFunctionRef => {
            val iterator = conservativeGraph.cg.edgesOutOf(s).iterator()
            while (iterator.hasNext) {
              val target = iterator.next().tgt()
              val args = s.getInvokeExpr.getArgs.iterator()
              val params = target.getParameterLocals.iterator()
              while (args.hasNext && params.hasNext) {
                val arg = args.next().asInstanceOf[SWANVal]
                val param = params.next().asInstanceOf[SWANVal]
                stats.paramArgValues += 1
                addValFlow(from = arg, to = param)
              }
            }
          }
          case _ =>
        }
      }}
    }}
  }

  val scc: mutable.HashMap[valGraphNode,valGraphNode] = mutable.HashMap.empty[valGraphNode,valGraphNode]
  val finalReachingTypes: mutable.MultiDict[valGraphNode, reachingTypes] = mutable.MultiDict.empty[valGraphNode,reachingTypes]
  val finalDag: Graph[valGraphNode, DefaultEdge] = new DefaultDirectedGraph(classOf[DefaultEdge])

  def vtaSCC(): Unit = {
    val kosaraju = new KosarajuStrongConnectivityInspector[valGraphNode, DefaultEdge](valueGraph)
    val sccs = kosaraju.stronglyConnectedSets()
    sccs.iterator().asScala.foreach{ cc =>
      val iterator = cc.iterator()
      if (iterator.hasNext) {
        val root = iterator.next()
        finalDag.addVertex(root)
        scc.addOne(root, root)
        reachingTypes.get(root).map(value => finalReachingTypes.addOne(root,value))
        while (iterator.hasNext) {
          val tgt = iterator.next()
          scc.addOne(tgt, root)
          reachingTypes.get(tgt).map(value => finalReachingTypes.addOne(root,value))
        }
      }
    }

    val finalEdgeAdd = valueGraph.edgeSet().iterator()
    while (finalEdgeAdd.hasNext) {
      val edge = finalEdgeAdd.next()
      val src = scc(valueGraph.getEdgeSource(edge))
      val tgt = scc(valueGraph.getEdgeTarget(edge))
      finalDag.addEdge(src, tgt)
    }
  }

  def propagateReachingTypes(): Unit = {
    val iterator = new DepthFirstIterator(finalDag).asScala
    iterator.foreach{ node =>
      finalReachingTypes.get(node).foreach(typ =>
        finalDag.outgoingEdgesOf(node).iterator().asScala.foreach{ edge =>
          val target = finalDag.getEdgeTarget(edge)
          finalReachingTypes.addOne(target, typ)
        }
      )
    }
  }

  def addDDGEdges(stmt: SWANStatement.ApplyFunctionRef, predEdge: ControlFlowGraph.Edge, index: String, instanTypes: mutable.HashSet[String]): Unit = {
    //Logging.printInfo("DDG index: " + index)
    moduleGroup.ddgs.foreach{ case (_,ddg) =>
      ddg.query(index,Some(instanTypes)).foreach{ fn =>
        val tgt = methods(fn)
        if (addCGEdge(stmt.m,tgt,stmt,predEdge,cgs)) vtaEdges += 1
      }
    }
  }

  private def vtaRef(stmt: SWANStatement.ApplyFunctionRef, predEdge: ControlFlowGraph.Edge, instanTypes: mutable.HashSet[String]): Unit = {
    stats.vtaRefs += 1

    //Logging.printInfo(stmt.toString)

    val ref: Val = stmt.getInvokeExpr.asInstanceOf[SWANInvokeExpr].getFunctionRef
    val swanRef: SWANVal = ref.asInstanceOf[SWANVal]
    val refComponent: valGraphNode = scc(Left(swanRef))
    val m = stmt.m
    val types = finalReachingTypes.get(refComponent)

    if (types.isEmpty) stats.emptyVTATypes += 1

    /*if (types.nonEmpty) {
      Logging.printInfo(stmt.toString)
    }*/

    types.foreach{
      case v@SWANVal.FunctionRef(delegate, ref, method, unbalanced) =>
        val target = methods(ref)
        //Logging.printInfo("Function Ref " + ref)
        if (addCGEdge(from = stmt.m, to = target, stmt, predEdge, cgs)) vtaEdges += 1
      case v@SWANVal.BuiltinFunctionRef(delegate, ref, method, unbalanced) =>
        val target = methods(ref)
        //Logging.printInfo("Builtin Ref " + ref)
        if (addCGEdge(from = stmt.m, to = target, stmt, predEdge, cgs)) vtaEdges += 1
      case v@SWANVal.DynamicFunctionRef(delegate, index, method, unbalanced) =>
        //Logging.printInfo("Dynamic index " + index)
        addDDGEdges(stmt, predEdge, index, instanTypes)
      case neww: SWANVal.NewExpr => // dealt with via instantiated types
        //Logging.printInfo("New Expr " + neww.delegate.tpe.name)
      case _: SWANVal.Simple | _: SWANVal.Constant => // ignore simple or constant
        //Logging.printInfo("Simple/Constant" + stmt.toString)
    }
  }

  private def handleOperator(operator: Operator, stmt: SWANStatement.ApplyFunctionRef, predEdge: ControlFlowGraph.Edge, instantiatedTypes: mutable.HashSet[String]): Unit = {
    operator match {
      case Operator.dynamicRef(_, _, index) => addDDGEdges(stmt, predEdge, index, instantiatedTypes)
      case _: Operator.builtinRef | _: Operator.functionRef => // already done via trivial edges pass
      // The function ref must be being used in a more interesting
      // way (e.g., assignment).
      case _ => vtaRef(stmt, predEdge, instantiatedTypes)
    }
  }

  var vtaEdges: Int = 0

  private def buildCG(): Unit = {
    methods.foreach { case (_, m) =>
      m.applyFunctionRefs.foreach {
        case stmt@SWANStatement.ApplyFunctionRef(opDef, inst, _m) =>
          val predEdge = new ControlFlowGraph.Edge(m.getCFG.getPredsOf(stmt).iterator().next(), stmt)
          val types = finalReachingTypes.get(Left(stmt.getFunctionRef.asInstanceOf[SWANVal]))
          val instantiatedTypes = mutable.HashSet.from(types.collect { case neww: SWANVal.NewExpr => neww.delegate.tpe.name })

          m.delegate.symbolTable(inst.functionRef.name) match {
            // regular entry
            case SymbolTableEntry.operator(_, operator) =>
            // Function ref has multiple symbol table entries (certainly from
            // non-SSA compliant basic block argument manipulation
            // from SWIRLPass). The function ref is a basic block argument.
            case SymbolTableEntry.multiple(_, operators) => operators.foreach{ operator =>
              handleOperator(operator,stmt,predEdge,instantiatedTypes)
            }
            // Function ref is an argument, which means it is inter-procedural,
            // so we need to use pointer analysis.
            case _: SymbolTableEntry.argument => vtaRef(stmt, predEdge, instantiatedTypes)
          }
      }
    }
  }

  val stats = new VTAStats()

  override def buildSpecificCallGraph(): Unit = {
    val startTimeMs = System.currentTimeMillis()
    addTrivialEdges()
    addValNodes()
    addLocalFlow()
    vtaSCC()
    propagateReachingTypes()
    buildCG()
    stats.edges = vtaEdges
    stats.time = (System.currentTimeMillis() - startTimeMs).toInt
    stats.reachingNodes = valueGraph.vertexSet().size()
    stats.reachingEdges = valueGraph.edgeSet().size()
    stats.finalNodes = finalDag.vertexSet().size()
    stats.finalEdges = finalDag.edgeSet().size()
    cgs.specificData.addOne(conservativeGraph.cgs.specificData.last)
    cgs.specificData.addOne(stats)
    /*
    valueGraph.edgeSet().iterator().asScala.foreach{e =>
      val src = valueGraph.getEdgeSource(e)
      val tgt = valueGraph.getEdgeTarget(e)
      Logging.printInfo(src.toString + " -> " + tgt.toString)
    }
     */
  }
}

object VTA {

  class VTAStats() extends SpecificCallGraphStats {
    var edges: Int = 0
    var time: Int = 0
    var reachingNodes: Int = 0
    var assignEdges: Int = 0
    var reachingEdges: Int = 0
    var finalNodes: Int = 0
    var finalEdges: Int = 0
    var allocations: Int = 0
    var vtaRefs: Int = 0
    var emptyVTATypes: Int = 0
    var emptyDDGqueries: Int = 0
    var paramArgValues: Int = 0
    override def toJSON: Value = {
      val u = ujson.Obj()
      u("vta_edges") = edges
      u("vta_time") = time
      u("reaching_nodes") = reachingNodes
      u("reaching_edges") = reachingEdges
      u("final_nodes") = finalNodes
      u("final_edges") = finalEdges
      u
    }

    override def toString: String = {
      val sb = new StringBuilder()
      sb.append(s"VTA\n")
      sb.append(s"  Edges: $edges\n")
      sb.append(s"  Time (ms): $time\n")
      sb.append(s"  Reaching Nodes: $reachingNodes\n")
      sb.append(s"  Final Nodes: $finalNodes\n")
      sb.append(s"  Reaching Edges: $reachingEdges\n")
      sb.append(s"  Final Edges: $finalEdges\n")
      sb.append(s"  Assign Edges: $assignEdges\n")
      sb.append(s"  Allocations: $allocations\n")
      sb.append(s"  vtaRefs: $vtaRefs\n")
      sb.append(s"  emptyVTATypes: $emptyVTATypes\n")
      sb.append(s"  emptyDDGqueries: $emptyDDGqueries\n")
      sb.append(s"  paramArgValues: $paramArgValues\n")
      sb.toString()
    }
  }
}