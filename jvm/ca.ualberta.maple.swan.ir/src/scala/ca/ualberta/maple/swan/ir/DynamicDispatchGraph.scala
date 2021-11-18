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

package ca.ualberta.maple.swan.ir

import ca.ualberta.maple.swan.ir.raw.Utils
import ca.ualberta.maple.swan.parser.{SILDeclRef, SILModule, SILWitnessEntry}
import org.jgrapht._
import org.jgrapht.alg.TransitiveClosure
import org.jgrapht.graph._
import org.jgrapht.traverse.BreadthFirstIterator

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.reflect.ClassTag

/** Creates a DDG from a SIL Module's witness and value tables. */
class DynamicDispatchGraph extends Serializable {

  private val graph: Graph[Node, DefaultEdge] = new SimpleDirectedGraph(classOf[DefaultEdge])
  val nodes: mutable.HashMap[String, Node] = new mutable.HashMap[String, Node]()
  private val reachabilityCache: SimpleDirectedGraph[Node, DefaultEdge] = new SimpleDirectedGraph(classOf[DefaultEdge])

  /**
   * Query the graph with an index. Optionally, specify RTA types.
   */
  def query(index: String, types: Option[mutable.HashSet[String]]): Array[String] = {
    if (!nodes.contains(index)) return Array.empty
    val functions = ArrayBuffer[String]()
    val startNode = nodes(index)
    val classNodes: Option[Array[Node]] = {
      if (types.nonEmpty) {
        // TODO: .toArray not efficient
        Some(types.get.toArray.filter(s => nodes.contains(s) && nodes(s).isInstanceOf[Node.Class]).map(s => nodes(s)))
      } else {
        None
      }
    }
    val iterator = new BreadthFirstIterator(graph, startNode)
    var done = false
    while (iterator.hasNext && !done) {
      val cur = iterator.next()
      if (iterator.getDepth(cur) < 2) {
        cur match {
          case Node.Method(s) => {
            if (classNodes.nonEmpty) {
              val classes = classNodes.get
              if (classes.find(cls => reachabilityCache.containsEdge(cur,cls)).nonEmpty) {
                functions.append(s)
              }
            } else {
              functions.append(s)
            }
          }
          case _ =>
        }
      } else {
        done = true
      }
    }
    functions.toArray
  }

  private def addEdge(from: Node, to: Node) = {
    graph.addEdge(from, to)
    reachabilityCache.addEdge(from, to)
  }

  /** A node in the DDG. */
  sealed class Node(val name: String) extends Serializable {
    override def hashCode(): Int = {
      this.name.hashCode()
    }
    override def equals(obj: Any): Boolean = {
      obj match {
        case node: Node =>
          this.name == node.name
        case _ =>
          false
      }
    }
    override def toString: String = {
      this.name
    }
  }
  object Node {
    case class Class(s: String) extends Node(name = s)
    case class Protocol(s: String) extends Node(name = s)
    case class Index(s: String) extends Node(name = s)
    case class Method(s: String) extends Node(name = s)
  }

  /** Generate and populate `graph`. */
  def generate(module: SILModule): Unit = {
    def makeNode[T <: Node](name: String, tpe: String)(implicit tag: ClassTag[T]): Node = {
      if (nodes.contains(name)) {
        nodes(name)
      } else {
        val n = {
          tpe match {
            case "Class" => Node.Class(name)
            case "Protocol" => Node.Protocol(name)
            case "Index" => Node.Index(name)
            case "Method" => Node.Method(name)
            case _ => null
          }
        }
        nodes.put(name, n)
        graph.addVertex(n)
        reachabilityCache.addVertex(n)
        n
      }
    }

    // Handle witness tables.
    module.witnessTables.foreach(table => {
      val clsName = Utils.printer.clearNakedPrint(table.normalProtocolConformance.tpe)
      val protocolName = table.normalProtocolConformance.protocol
      // don't add edges when class name equals protocol name
      if (clsName != protocolName) {
      val cls = makeNode(clsName, "Class")
      val protocol = makeNode(protocolName, "Protocol")
        addEdge(cls, protocol)
        table.entries.foreach {
          case SILWitnessEntry.baseProtocol(identifier, _) => {
            addEdge(cls, makeNode(identifier, "Protocol"))
          }
          case SILWitnessEntry.method(declRef, _, functionName) => {
            if (functionName.nonEmpty) {
              val method = makeNode(functionName.get.demangled, "Method") // MethodType.implements
              addEdge(makeNode(declRefToString(declRef), "Index"), method)
              addEdge(method, cls)
            }
          }
          // TODO: investigate
          case SILWitnessEntry.associatedType(identifier0, identifier1) =>
          case SILWitnessEntry.associatedTypeProtocol(identifier0) =>
          //case SILWitnessEntry.associatedTypeProtocol(identifier0, identifier1, pc) =>
          case SILWitnessEntry.conditionalConformance(identifier) =>
        }
      }
    })

    // Handle vtables.
    module.vTables.foreach(table => {
      val cls = makeNode(table.name, "Class")
      table.entries.foreach(entry => {
        if (entry.declRef.name(0) != table.name) {
          val to = makeNode(entry.declRef.name(0), "Class")
          addEdge(cls, to)
        }
        val method = makeNode(entry.functionName.demangled, "Method")
        addEdge(method, cls)
        addEdge(makeNode(declRefToString(entry.declRef), "Index"), method)
      })
    })
    // System.out.println(printToDot()) // For debugging
    TransitiveClosure.INSTANCE.closeSimpleDirectedGraph(reachabilityCache)
  }

  private def declRefToString(decl: SILDeclRef): String = {
    Utils.printer.clearPrint(decl)
  }

  def printToDot(): String = {
    val dot: StringBuilder = new StringBuilder("digraph G {\n")
    def printNode(node: Node): String = {
      val base = {
        node match {
          case _: Node.Class => "CLASS "
          case _: Node.Protocol => "PROTOCOL "
          case _: Node.Index => "INDEX "
          case _: Node.Method => "METHOD "
          case _ => "Node "
        }
      }
      base + node.name
    }
    for (e <- graph.edgeSet().asScala) {
      dot.append("  \"")
      dot.append(printNode(graph.getEdgeSource(e)))
      dot.append("\" -> \"")
      dot.append(printNode(graph.getEdgeTarget(e)))
      dot.append("\" [color=black]")
      dot.append("\n")
    }
    nodes.foreach(node => {
      dot.append("  \"")
      dot.append(printNode(node._2))
      dot.append("\" [color=")
      dot.append({
        node._2 match {
          case Node.Class(s) => "red"
          case Node.Protocol(s) => "green"
          case Node.Index(s) => "purple"
          case Node.Method(s) => "blue"
          case _ => "black"
        }
      })
      dot.append("]\n")
    })
    dot.append("\n")
    dot.append("}")
    dot.toString()
  }
}
