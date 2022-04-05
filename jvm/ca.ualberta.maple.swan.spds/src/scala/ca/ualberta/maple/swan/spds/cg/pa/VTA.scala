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

package ca.ualberta.maple.swan.spds.cg.pa

import ca.ualberta.maple.swan.ir.{Operator, SymbolTableEntry}
import ca.ualberta.maple.swan.spds.structures.{SWANCallGraph, SWANField, SWANMethod, SWANStatement, SWANVal}
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector
import org.jgrapht.graph.{DefaultDirectedGraph, DefaultEdge, DirectedAcyclicGraph}

import scala.collection.mutable
import scala.jdk.CollectionConverters.{IteratorHasAsScala, ListHasAsScala}

class VTA(val stats: VTAPAStats) {

  type valGraphNode = Either[SWANVal,SWANField]
  type reachingTypes = SWANVal

  def getTypeFlow(methods: mutable.HashMap[String, SWANMethod], conservativeCallGraph: SWANCallGraph): TypeFlow = {
    val valueGraph = getValueGraph(methods)
    val reachingTypes = addLocalFlowAndGetRechableTypes(valueGraph, methods, conservativeCallGraph)
    val (sccRootMap, finalDag, finalReachingTypes) = vtaSCC(valueGraph, reachingTypes)
    propagateReachingTypes(finalDag, finalReachingTypes)
    new VTAResults(sccRootMap, finalReachingTypes)
  }

  private def getValueGraph(methods: mutable.HashMap[String, SWANMethod]): DefaultDirectedGraph[valGraphNode, DefaultEdge] = {
    val valueGraph: DefaultDirectedGraph[valGraphNode, DefaultEdge] = new DefaultDirectedGraph(classOf[DefaultEdge])
    methods.foreach{ case (_,m) =>
      m.allValues.foreach{ case (_,v) =>
        valueGraph.addVertex(Left(v.asInstanceOf[SWANVal]))
      }
      m.getStatements.iterator().asScala.foreach{
        case s: SWANStatement.FieldLoad =>
          valueGraph.addVertex(Right(s.getLoadedField.asInstanceOf[SWANField]))
        case s: SWANStatement.FieldWrite =>
          valueGraph.addVertex(Right(s.getWrittenField.asInstanceOf[SWANField]))
          /*
        case s: SWANStatement.StaticFieldLoad =>
          valueGraph.addVertex(Right(s.getLoadedField.asInstanceOf[SWANField]))
        case s: SWANStatement.StaticFieldStore =>
          valueGraph.addVertex(Right(s.getWrittenField.asInstanceOf[SWANField]))
           */
        case _ =>
      }
    }
    valueGraph
  }


  private def addLocalFlowAndGetRechableTypes(valueGraph: DefaultDirectedGraph[valGraphNode, DefaultEdge], methods: mutable.HashMap[String, SWANMethod], conservativeCallGraph: SWANCallGraph): mutable.HashMap[valGraphNode,reachingTypes] = {
    val reachingTypes = mutable.HashMap.empty[valGraphNode,reachingTypes]

    def addValFlow(from: SWANVal, to: SWANVal): Unit = {
      if (valueGraph.addEdge(Left(from), Left(to)) != null) stats.assignEdges += 1
    }
    def addFieldLoadFlow(from: SWANField, to: SWANVal): Unit = {
      if (valueGraph.addEdge(Right(from), Left(to)) != null) stats.assignEdges +=1
    }
    def addFieldStoreFlow(from: SWANVal, to: SWANField): Unit = {
      if (valueGraph.addEdge(Left(from), Right(to)) != null) stats.assignEdges +=1
    }

    methods.foreach{ case (_,m) => {
      m.getCFG.blocks.foreach{ case (_,b) => {
        b.stmts.foreach {
          case s: SWANStatement.Allocation => {
            reachingTypes.addOne(Left(s.getLeftOp.asInstanceOf[SWANVal]),s.getRightOp.asInstanceOf[SWANVal])
            stats.allocations += 1
          }
          case s: SWANStatement.FunctionRef => {
            reachingTypes.addOne(Left(s.getLeftOp.asInstanceOf[SWANVal]),s.getRightOp.asInstanceOf[SWANVal])
          }
          case s: SWANStatement.BuiltinFunctionRef => {
            reachingTypes.addOne(Left(s.getLeftOp.asInstanceOf[SWANVal]),s.getRightOp.asInstanceOf[SWANVal])
          }
          case s: SWANStatement.DynamicFunctionRef => {
            reachingTypes.addOne(Left(s.getLeftOp.asInstanceOf[SWANVal]),s.getRightOp.asInstanceOf[SWANVal])
          }
          case s: SWANStatement.Assign => {
            addValFlow(from = s.getRightOp.asInstanceOf[SWANVal], to = s.getLeftOp.asInstanceOf[SWANVal])
          }
          case s: SWANStatement.FieldLoad => {
            // x = y.f
            val xVal = s.getLeftOp.asInstanceOf[SWANVal]
            val fieldLoad = s.getFieldLoad
            val f = fieldLoad.getY.asInstanceOf[SWANField]
            // don't discriminate pointerField
            addFieldLoadFlow(to = xVal, from = f)
            /*
            if (f.name == Constants.pointerField) {
              val yVal = fieldLoad.getX.asInstanceOf[SWANVal]
              addValFlow(to = xVal, from = yVal)
            }
            else {
              addFieldLoadFlow(to = xVal, from = f)
            }*/
          }
          /*
          case s: SWANStatement.StaticFieldLoad => {
            // x = Static z
            val xVal = s.getLeftOp.asInstanceOf[SWANVal]
            // val yVal = fieldLoad.getX.asInstanceOf[SWANVal]
            val z = s.getLoadedField.asInstanceOf[SWANField]
            addFieldLoadFlow(to = xVal, from = z)
          }
           */
          case s: SWANStatement.FieldWrite => {
            // x.f = y
            val fieldStore = s.getFieldStore
            // val xVal = fieldStore.getX.asInstanceOf[SWANVal]
            val f = fieldStore.getY.asInstanceOf[SWANField]
            val yVal = s.getRightOp.asInstanceOf[SWANVal]
            addFieldStoreFlow(to = f, from = yVal)
          }
          /*
          case s: SWANStatement.StaticFieldStore => {
            // statif f = y
            val f = s.getWrittenField.asInstanceOf[SWANField]
            val yVal = s.getRightOp.asInstanceOf[SWANVal]
            addFieldStoreFlow(to = f, from = yVal)
          }
           */
          case s: SWANStatement.Return => {
            val yVal = s.getReturnOp.asInstanceOf[SWANVal]
            conservativeCallGraph.edgesInto(s.getSWANMethod).forEach{ edge =>
              // x = foo() // foo() { return y }
              val xVal = edge.src().getLeftOp.asInstanceOf[SWANVal]
              addValFlow(from = yVal, to = xVal)
            }
          }
          case s: SWANStatement.Yield => {
            val yVal = s.getReturnOp.asInstanceOf[SWANVal]
            conservativeCallGraph.edgesInto(s.getSWANMethod).forEach{ edge =>
              val xVal = edge.src().getLeftOp.asInstanceOf[SWANVal]
              addValFlow(from = yVal, to = xVal)
            }
          }
          case s: SWANStatement.ApplyFunctionRef => {
            val ref = m.delegate.symbolTable(s.inst.functionRef.name)
            ref match {
              // lifecycle
              case SymbolTableEntry.operator(_, Operator.builtinRef(_, "#UIView.init!initializer.foreign")) =>
                // %n = super.init(..., %i) ~~ %n = %i
                val vi = s.getInvokeExpr.getArgs.asScala.last.asInstanceOf[SWANVal]
                val vn = s.getLeftOp.asInstanceOf[SWANVal]
                addValFlow(from = vi, to = vn)
              case _ =>
                val iterator = conservativeCallGraph.edgesOutOf(s).iterator()
                while (iterator.hasNext) {
                  val target = iterator.next().tgt()
                  val args = s.getInvokeExpr.getArgs.iterator()
                  val params = target.getParameterLocals.iterator()
                  while (args.hasNext && params.hasNext) {
                    val arg = args.next().asInstanceOf[SWANVal]
                    val param = params.next().asInstanceOf[SWANVal]
                    addValFlow(from = arg, to = param)
                  }
                }
            }
          }
          case _ =>
        }
      }}
    }}
    reachingTypes
  }

  private def vtaSCC(valueGraph: DefaultDirectedGraph[valGraphNode, DefaultEdge], reachingTypes: mutable.HashMap[valGraphNode,reachingTypes]): (mutable.HashMap[valGraphNode, valGraphNode], DirectedAcyclicGraph[valGraphNode, DefaultEdge], mutable.MultiDict[valGraphNode, reachingTypes]) = {
    val scc: mutable.HashMap[valGraphNode,valGraphNode] = mutable.HashMap.empty[valGraphNode,valGraphNode]
    val finalReachingTypes: mutable.MultiDict[valGraphNode, reachingTypes] = mutable.MultiDict.empty[valGraphNode,reachingTypes]
    val finalDag: DirectedAcyclicGraph[valGraphNode, DefaultEdge] = new DirectedAcyclicGraph(classOf[DefaultEdge])

    val kosaraju = new KosarajuStrongConnectivityInspector[valGraphNode, DefaultEdge](valueGraph)
    val sccs = kosaraju.stronglyConnectedSets()
    sccs.iterator().asScala.foreach{ cc =>
      val iterator = cc.iterator()
      if (iterator.hasNext) {
        val root = iterator.next()
        finalDag.addVertex(root)
        scc.addOne(root, root)
        reachingTypes.get(root).foreach(value => finalReachingTypes.addOne(root,value))
        while (iterator.hasNext) {
          val tgt = iterator.next()
          scc.addOne(tgt, root)
          reachingTypes.get(tgt).foreach(value => finalReachingTypes.addOne(root,value))
        }
      }
    }

    val finalEdgeAdd = valueGraph.edgeSet().iterator()
    while (finalEdgeAdd.hasNext) {
      val edge = finalEdgeAdd.next()
      val src = scc(valueGraph.getEdgeSource(edge))
      val tgt = scc(valueGraph.getEdgeTarget(edge))
      if (src != tgt) {
        finalDag.addEdge(src, tgt)
      }
    }

    (scc, finalDag, finalReachingTypes)
  }

  // This uses the fact that the underlying implementation of mutable.MultiDict[K, V] is mutable.Map[K, Set[V]]
  private def propagateReachingTypes(finalDag: DirectedAcyclicGraph[valGraphNode, DefaultEdge], finalReachingTypes: mutable.MultiDict[valGraphNode, reachingTypes]): Unit = {
    //val vertices = finalDag.vertexSet().iterator().asScala //new DepthFirstIterator(finalDag).asScala
    val vertices = finalDag.iterator().asScala // new TopologicalOrderIterator(finalDag).asScala
    val sets: mutable.Map[valGraphNode, mutable.Set[reachingTypes]] = finalReachingTypes.sets.asInstanceOf[mutable.Map[valGraphNode, mutable.Set[reachingTypes]]]
    vertices.foreach{ node =>
      sets.get(node) match {
        case Some(types) => {
          val incomingTypeSets = finalDag.incomingEdgesOf(node).iterator().asScala.flatMap(e => sets.get(finalDag.getEdgeSource(e)))
          incomingTypeSets.foreach(ts => types.addAll(ts))
        }
        case None => {
          // reuse if possible
          val incomingEdges = finalDag.incomingEdgesOf(node)
          val incomingMaped = List.from(incomingEdges.iterator().asScala.flatMap(e => sets.get(finalDag.getEdgeSource(e))))
          incomingMaped match {
            case Nil => () // don't add empty sets
            case types :: Nil => {
                sets.addOne(node, types) // types is reused
              }
            case _ => {
              val types = mutable.Set.from(incomingMaped.iterator.flatMap(set => set.iterator))
              sets.addOne(node, types)
            }
          }
        }
      }
    }
  }

  /*
  def propagateReachingTypes(finalDag: DirectedAcyclicGraph[valGraphNode, DefaultEdge], finalReachingTypes: mutable.MultiDict[valGraphNode, reachingTypes]): Unit = {
    //val vertices = finalDag.vertexSet().iterator().asScala //new DepthFirstIterator(finalDag).asScala
    val vertices = finalDag.iterator().asScala // new TopologicalOrderIterator(finalDag).asScala
    vertices.foreach{ node =>
      finalReachingTypes.get(node).foreach(typ =>
        finalDag.outgoingEdgesOf(node).iterator().asScala.foreach{ edge =>
          val target = finalDag.getEdgeTarget(edge)
          finalReachingTypes.addOne(target, typ)
        }
      )
    }
  }
   */

  private class VTAResults(sccRootMap: mutable.HashMap[valGraphNode,valGraphNode], rootTypeMap: mutable.MultiDict[valGraphNode, reachingTypes]) extends TypeFlow {
    override def getValTypes(x: reachingTypes): collection.Set[reachingTypes] = {
      val xSCC = sccRootMap(Left(x))
      val types = rootTypeMap.get(xSCC)
      types
    }
  }
}

trait VTAPAStats {
  var assignEdges: Int = 0
  var allocations: Int = 0
}