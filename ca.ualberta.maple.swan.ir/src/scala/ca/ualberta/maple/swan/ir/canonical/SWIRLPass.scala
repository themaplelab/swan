/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.ir.canonical

import ca.ualberta.maple.swan.ir.Exceptions.{IncompleteRawSWIRLException}
import ca.ualberta.maple.swan.ir.{Block, BlockRef, CanFunction, CanModule, Function, Module, Operator, OperatorDef, SymbolTableEntry, Terminator, WithResult}
import org.jgrapht.Graph
import org.jgrapht.graph.{DefaultDirectedGraph, DefaultEdge}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

// TODO: Resolve block arguments (as assigns?)
// TODO: Perhaps change unwind to return new object of function return type?
object SWIRLPass {

  def runPasses(module: Module): CanModule = {
    resolveAliases(module)
    val functions = new ArrayBuffer[CanFunction]()
    module.functions.foreach(f => {
      val symbolTable = generateSymbolTable(f)
      val cfg = generateCFG(f)
      val canFunction = new CanFunction(f, symbolTable, cfg)
      functions.append(canFunction)
    })
    val lineNumbers = generateLineNumbers(functions)
    new CanModule(module, functions, lineNumbers)
  }

  @throws[IncompleteRawSWIRLException]
  def generateSymbolTable(function: Function): mutable.HashMap[String, SymbolTableEntry] = {
    // Create symbol table.
    // Mapping of result values to their creating operator.
    val table = new mutable.HashMap[String, SymbolTableEntry]
    function.blocks.foreach(block => {
      block.arguments.foreach(argument => {
        table.put(argument.ref.name, SymbolTableEntry.argument(argument))
      })
      block.operators.foreach(op => {
        op.operator match {
          case inst: WithResult =>
            table.put(inst.value.ref.name, SymbolTableEntry.operator(inst.value, op.operator))
          case _ =>
        }
      })
    })
    // Verify that all values referenced are either block arguments or created with an operator.
    // This is NOT part of the guarantees! This is just here in case the initial program
    // is missing some values.
    function.refTable.symbols.foreach(ref => {
      // Use the value (not the key) due to COPY operations.
      // Keys are effectively non-concrete aliases for the actual values.
      if (!table.contains(ref._2.name)) {
        // TODO: Recover with `new` instruction.
        throw new IncompleteRawSWIRLException("Symbol reference to value " + ref._2.name + " in function `" + function.name +
          "` is invalid. Value is not a block argument nor the result of an operator.")
      }
    })
    table
  }

  def resolveAliases(module: Module): Unit = {
    // For every function F,
    //   For every block B,
    //     For every operator O in B,
    //       If O is aliasing (O_alias),
    //         Search every block in F for pointer_writes
    //         that write to the aliased value.
    //         Then, replace the pointer_write with a field_write
    //         Ignore the pointer_write if it immediately comes after O_alias.
    module.functions.foreach(f => {
      f.blocks.zipWithIndex.foreach(b => {
        val bIdx = b._2
        b._1.operators.zipWithIndex.foreach(operator => {
          val operatorIdx = operator._2
          operator._1.operator match {
            case fieldRead: Operator.fieldRead => {
              if (fieldRead.alias.nonEmpty) {
                val aliased = fieldRead.alias.get
                f.blocks.zipWithIndex.foreach(b2 => {
                  val b2Idx = b2._2
                  b2._1.operators.zipWithIndex.foreach(op2 => {
                    val op2Idx = op2._2
                    op2._1.operator match {
                      case Operator.pointerWrite(value, pointer) => {
                        if ((pointer == aliased) && { if (bIdx == b2Idx) operatorIdx != op2Idx - 1 else true }) {
                          b2._1.operators(op2._2) =
                            new OperatorDef(Operator.fieldWrite(
                              value, fieldRead.obj, fieldRead.field), op2._1.position)
                        }
                      }
                      case _ =>
                    }
                  })
                })
              }
            }
            case _ =>
          }
        })
      })
    })
  }

  // Line numbers needed for SPDS. These do NOT correspond to how a module
  // is printed by SWIRLPrinter.
  // For now, include wrapper classes in the hashmap (e.g., CanX, XDef)
  def generateLineNumbers(functions: ArrayBuffer[CanFunction]): mutable.HashMap[Object, Int] = {
    var counter = 1
    val lines = new mutable.HashMap[Object, Int]()
    functions.foreach(canFunction => {
      val f = canFunction.f
      lines.put(f, counter)
      lines.put(canFunction, counter)
      counter += 1
      f.blocks.foreach(b => {
        lines.put(b, counter)
        counter += 1
        b.operators.foreach(op => {
          lines.put(op, counter)
          lines.put(op.operator, counter)
          counter += 1
        })
        lines.put(b.terminator, counter)
        lines.put(b.terminator.terminator, counter)
        counter += 1
      })
    })
    lines
  }

  @throws[IncompleteRawSWIRLException]
  def generateCFG(f: Function): Graph[Block, DefaultEdge] = {
    val graph: Graph[Block, DefaultEdge] = new DefaultDirectedGraph(classOf[DefaultEdge])
    val exitBlock = new Block(new BlockRef("EXIT"), null, null, null)
    graph.addVertex(exitBlock)
    def getTarget(ref: BlockRef): Block = {
      // Not really efficient
      val to = f.blocks.find(p => p.blockRef.label == ref.label)
      if (to.isEmpty) {
        throw new IncompleteRawSWIRLException("br instruction 'to' block does not exist: " + ref.label)
      }
      graph.addVertex(to.get)
      to.get
    }
    f.blocks.foreach(b => {
      graph.addVertex(b)
      b.terminator.terminator match {
        case Terminator.br(to, _) => {
          graph.addEdge(b, getTarget(to))
        }
        case Terminator.condBr(_, trueBlock, _, falseBlock, _) => {
          graph.addEdge(b, getTarget(trueBlock))
          graph.addEdge(b, getTarget(falseBlock))
        }
        case Terminator.switch(_, cases, default) => {
          cases.foreach(cse => {
            graph.addEdge(b, getTarget(cse.destination))
          })
          if (default.nonEmpty) {
            graph.addEdge(b, getTarget(default.get))
          }
        }
        case Terminator.switchEnum(_, cases, default) => {
          cases.foreach(cse => {
            graph.addEdge(b, getTarget(cse.destination))
          })
          if (default.nonEmpty) {
            graph.addEdge(b, getTarget(default.get))
          }
        }
        case Terminator.ret(_) => {
          graph.addEdge(b, exitBlock)
        }
        case Terminator.thro(_) => {
          graph.addEdge(b, exitBlock)
        }
        case Terminator.tryApply(_, _, normal, error) => {
          graph.addEdge(b, getTarget(normal))
          graph.addEdge(b, getTarget(error))
        }
        case Terminator.unreachable =>
          // Every unreachable must be preceded by a no-return apply ($Never),
          // which usually (always?) means a total program abort.
        case Terminator.yld(_, resume, unwind) => {
          graph.addEdge(b, exitBlock)
          graph.addEdge(b, getTarget(resume))
          graph.addEdge(b, getTarget(unwind))
        }
        case Terminator.unwind => {
          graph.addEdge(b, exitBlock)
        }
      }
    })
    graph
  }
}
