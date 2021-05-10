/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.ir

import ca.ualberta.maple.swan.ir.raw.Utils
import ca.ualberta.maple.swan.parser.{SILDeclRef, SILModule, SILWitnessEntry}
import org.jgrapht._
import org.jgrapht.alg.shortestpath.BellmanFordShortestPath
import org.jgrapht.graph._
import org.jgrapht.traverse.BreadthFirstIterator

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.reflect.ClassTag
import scala.util.control.Breaks.{break, breakable}

class DynamicDispatchGraph extends Serializable {

  private val graph: Graph[Node, DefaultEdge] = new SimpleGraph(classOf[DefaultEdge])
  private val nodes: mutable.HashMap[String, Node] = new mutable.HashMap[String, Node]()

  def query(index: String, types: Option[mutable.HashSet[String]]): Array[String] = {
    val paths = new BellmanFordShortestPath(graph)
    val functions = ArrayBuffer[String]()
    val startNode = nodes(index)
    val classNodes: Option[Array[Node]] = {
      if (types.nonEmpty) {
        types.get.clear() // RTA not working/tested yet
        // TODO: .toArray not efficient
        Some(types.get.toArray.map((s: String) => nodes(s)))
      } else {
        None
      }
    }
    val iterator = new BreadthFirstIterator(graph, startNode)
    breakable {
      while (iterator.hasNext) {
        val cur = iterator.next()
        if (iterator.getDepth(cur) > 1) {
          break()
        }
        cur match {
          case Node.Method(s) => {
            if (classNodes.nonEmpty) {
              breakable {
                classNodes.get.foreach(cls => {
                  if (paths.getPath(cls, cur) != null) {
                    functions.append(s)
                    break()
                  }
                })
              }
            } else {
              functions.append(s)
            }
          }
          case _ =>
        }
      }
    }
    functions.toArray
  }

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
    case class Class(s: String) extends Node(name = s) {}
    case class Protocol(s: String) extends Node(name = s)
    case class Index(s: String) extends Node(name = s)
    case class Method(s: String) extends Node(name = s)
  }
  
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
        n
      }
    }

    // Handle witness tables.
    module.witnessTables.foreach(table => {
      val cls = makeNode(Utils.printer.clearNakedPrint(table.normalProtocolConformance.tpe), "Class")
      val protocol = makeNode(table.normalProtocolConformance.protocol, "Protocol")
      graph.addEdge(cls, protocol)
      table.entries.foreach {
        case SILWitnessEntry.baseProtocol(identifier, pc) => {
          graph.addEdge(cls, makeNode(identifier, "Protocol"))
        }
        case SILWitnessEntry.method(declRef, declType, functionName) => {
          if (functionName.nonEmpty) {
            val method = makeNode(functionName.get.demangled, "Method") // MethodType.implements
            graph.addEdge(makeNode(declRef.name(0), "Protocol"), method) // MethodType.virtual
            graph.addEdge(makeNode(declRefToString(declRef), "Index"), method)
          }
        }
        // TODO: investigate
        case SILWitnessEntry.associatedType(identifier0, identifier1) =>
        case SILWitnessEntry.associatedTypeProtocol(identifier0) =>
        //case SILWitnessEntry.associatedTypeProtocol(identifier0, identifier1, pc) =>
        case SILWitnessEntry.conditionalConformance(identifier) =>
      }
    })

    // Handle vtables.
    module.vTables.foreach(table => {
      val cls = makeNode(table.name, "Class")
      table.entries.foreach(entry => {
        if (entry.declRef.name(0) != table.name) {
          val to = makeNode(entry.declRef.name(0), "Class")
          graph.addEdge(cls, to)
        }
        val method = makeNode(entry.functionName.demangled, "Method")
        graph.addEdge(method, cls)
        graph.addEdge(makeNode(declRefToString(entry.declRef), "Index"), method)
      })
    })
  }

  private def declRefToString(decl: SILDeclRef): String = {
    Utils.printer.clearPrint(decl)
  }

  def printToDot(): String = {
    val dot: StringBuilder = new StringBuilder("digraph G {\n")
    def printNode(node: Node): String = {
      val base = {
        node match {
          case Node.Class(s) => "CLASS "
          case Node.Protocol(s) => "PROTOCOL "
          case Node.Index(s) => "INDEX "
          case Node.Method(s) => "METHOD "
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
