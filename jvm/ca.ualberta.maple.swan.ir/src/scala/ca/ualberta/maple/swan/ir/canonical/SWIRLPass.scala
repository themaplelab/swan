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

package ca.ualberta.maple.swan.ir.canonical

import ca.ualberta.maple.swan.ir.Exceptions.{ExperimentalException, IncompleteRawSWIRLException, IncorrectRawSWIRLException, UnexpectedSILFormatException}
import ca.ualberta.maple.swan.ir.{Argument, BinaryOperation, Block, BlockRef, CanBlock, CanFunction, CanModule, CanOperator, CanOperatorDef, CanTerminator, CanTerminatorDef, Constants, FieldWriteAttribute, Function, Literal, Module, Operator, RawOperatorDef, RawTerminatorDef, SwitchCase, SwitchEnumCase, Symbol, SymbolRef, SymbolTable, SymbolTableEntry, Terminator, Type, UnaryOperation, WithResult}
import ca.ualberta.maple.swan.utils.Logging
import org.jgrapht.Graph
import org.jgrapht.graph.{DefaultDirectedGraph, DefaultEdge}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/** Converts raw SWIRL to canonical SWIRL in a series of passes. */
class SWIRLPass {

  // https://github.com/themaplelab/swan/wiki/SWIRL#swirlpass-and-canonical-swirl

  /** Convert a raw module to a canonical module. */
  def runPasses(module: Module): CanModule = {
    Logging.printInfo("Running SWIRL canonical passes on " + module)
    val startTime = System.nanoTime()
    resolveAliases(module)
    val functions = new ArrayBuffer[CanFunction]()
    module.functions.foreach(f => {
      simplify(f, module)
      val args = resolveBasicBlockArguments(f, module)
      val blocks = convertToCanonical(f, module)
      val cfg = generateCFG(blocks)
      val canFunction = new CanFunction(f.attribute, f.name, f.tpe, args, blocks, f.refTable,
        f.instantiatedTypes, new SymbolTable(), cfg)
      generateSymbolTable(canFunction)
      removeThunkApplication(canFunction)
      // Analysis specific mutations
      new Mutations(canFunction, module).doMutations()
      functions.append(canFunction)
    })
    Logging.printTimeStamp(1, startTime, "passes", module.functions.length, "functions")
    new CanModule(functions, module.ddg, module.silMap, module.meta)
  }

  @throws[IncompleteRawSWIRLException]
  private def generateSymbolTable(function: CanFunction): Unit = {
    // Create symbol table.
    // Mapping of result values to their creating operator.
    val table = function.symbolTable
    function.arguments.foreach(argument => {
      table.putArg(argument.ref.name, argument)
    })
    function.blocks.foreach(block => {
      block.operators.foreach(op => {
        op.operator match {
          case inst: WithResult =>
            table.putOp(inst.value, op.operator)
          case _ =>
        }
      })
    })
    // Verify that all values referenced are either block arguments or created with an operator.
    function.refTable.symbols.foreach(ref => {
      // Use the value (not the key) due to COPY operations.
      // Keys are effectively non-concrete aliases for the actual values.
      if (!table.contains(ref._2.name)) {
        // TODO: Recover with `new` instruction.
        throw new IncompleteRawSWIRLException("Symbol reference to value '" + ref._2.name + "' in function `" + function.name +
          "` is invalid. Value is not a block argument nor the result of an operator.")
      }
    })
  }

  /**
   * Converts and apply statements that apply thunks into assignments.
   * Assumes thunk function references are simple and intra-procedural,
   * and thunk apply has one argument.
   *
   * This is likely a naive implementation. It also assumes that a thunk
   * can take a closure function reference that expects no arguments as an
   * argument (SWAN-24).
   *
   * TODO: Not exactly ideal to have an entire pass for this, but the symbol
   *   table needs to be generated first.
   */
  private def removeThunkApplication(function: CanFunction): Unit = {
    function.blocks.foreach(b => {
      b.operators.zipWithIndex.foreach(opDefIdx => {
        opDefIdx._1.operator match {
          case apply: Operator.apply => {
            function.symbolTable(apply.functionRef.name) match {
              case SymbolTableEntry.operator(_, operator) => {
                operator match {
                  case Operator.functionRef(_, name) => {
                    if (name.startsWith("reabstraction thunk helper from ")) {
                      val arg = apply.arguments(0)
                      var argIsFuncRef = false
                      function.symbolTable(arg.name) match {
                        case SymbolTableEntry.operator(_, operator) => {
                          operator match {
                            case Operator.functionRef(_, name) => {
                              if (name.startsWith("reabstraction thunk helper from ")) {
                                throw new ExperimentalException("unexpected: thunk reference given to a thunk")
                              }
                              argIsFuncRef = true
                            }
                            case _ =>
                          }
                        }
                        case _ =>
                      }
                      val replacement = {
                        if (argIsFuncRef) {
                          new CanOperatorDef(Operator.apply(apply.result, arg, new ArrayBuffer[SymbolRef]()), opDefIdx._1.position)
                        } else {
                          new CanOperatorDef(Operator.assign(apply.result, arg), opDefIdx._1.position)
                        }
                      }
                      b.operators.remove(opDefIdx._2)
                      b.operators.insert(opDefIdx._2, replacement)
                      function.symbolTable.replace(apply.result.ref.name, SymbolTableEntry.operator(apply.result, replacement.operator))
                    }
                  }
                  case _ =>
                }
              }
              case _ =>
            }
          }
          case _ =>
        }
      })
    })
  }


  /**
   * Converts all raw-only instructions in the given module to
   * canonical instructions (in-place transformation).
   */
  def simplify(f: Function, module: Module): Unit = {
    val intermediateBlocks: mutable.HashMap[String, Integer] = new mutable.HashMap()
    val intermediateSymbols: mutable.HashMap[String, Integer] = new mutable.HashMap()
    def makeBlockRef(ref: String): BlockRef = {
      val blocks = f.refTable.blocks
      if (blocks.contains(ref)) {
        blocks.put(ref, blocks(ref))
        blocks(ref)
      } else {
        val blockRef = new BlockRef(ref)
        blocks.put(ref, blockRef)
        blockRef
      }
    }
    def makeSymbolRef(ref: String): SymbolRef = {
      val symbols = f.refTable.symbols
      if (symbols.contains(ref)) {
        symbols.put(ref, symbols(ref))
        symbols(ref)
      } else {
        val symbolRef = new SymbolRef(ref)
        symbols.put(ref, symbolRef)
        symbolRef
      }
    }
    def generateBlockName(baseLabel: String): BlockRef = {
      if (!intermediateBlocks.contains(baseLabel)) {
        intermediateBlocks.put(baseLabel, 0)
      }
      val ret = baseLabel + "i" + intermediateBlocks(baseLabel).toString
      intermediateBlocks(baseLabel) = intermediateBlocks(baseLabel) + 1
      makeBlockRef(ret)
    }
    def generateSymbolName(value: String): SymbolRef = {
      if (!intermediateSymbols.contains(value)) {
        intermediateSymbols.put(value, 0)
      }
      val ret = value + "i" + intermediateSymbols(value).toString
      intermediateSymbols(value) = intermediateSymbols(value) + 1
      makeSymbolRef(ret)
    }
    // First handle operators because some them generate terminators.
    var i: Int = 0
    while (i < f.blocks.length) {
      val b = f.blocks(i)
      val newBlocks: ArrayBuffer[Block] = ArrayBuffer[Block]()
      var j: Int = 0
      while (j < b.operators.length) {
        val op = b.operators(j)
        op.operator match {
          case Operator.switchEnumAssign(result, switchOn, cases, default) => {
            val blockRefs: ArrayBuffer[BlockRef] = new ArrayBuffer
            val newCases: ArrayBuffer[SwitchEnumCase] = new ArrayBuffer
            cases.foreach(cse => {
              val blockRef = generateBlockName(b.blockRef.label)
              blockRefs.append(blockRef)
              newCases.append(new SwitchEnumCase(cse.decl, blockRef))
            })
            if (default.nonEmpty) {
              blockRefs.append(generateBlockName(b.blockRef.label))
            }
            val continueRef = generateBlockName(b.blockRef.label)
            cases.zipWithIndex.foreach(cse => {
              val br = new RawTerminatorDef(Terminator.br(continueRef, ArrayBuffer(cse._1.value)), op.position)
              mapToSIL(op, br, module)
              val newBlock = new Block(blockRefs(cse._2), new ArrayBuffer, new ArrayBuffer, br)
              mapToSIL(b, newBlock, module)
              newBlocks.append(newBlock)
            })
            if (default.nonEmpty) {
              val br = new RawTerminatorDef(Terminator.br(continueRef, ArrayBuffer(default.get)), op.position)
              mapToSIL(op, br, module)
              val newBlock = new Block(blockRefs(blockRefs.length - 1), new ArrayBuffer, new ArrayBuffer, br)
              mapToSIL(b, newBlock, module)
              newBlocks.append(newBlock)
            }
            val continueBlock = new Block(continueRef, ArrayBuffer(new Argument(result.ref, result.tpe, None)),
              b.operators.slice(j + 1, b.operators.length), b.terminator)
            mapToSIL(b, continueBlock, module)
            newBlocks.append(continueBlock)
            val switchEnum = new RawTerminatorDef(Terminator.switchEnum(switchOn, newCases,
              if (default.nonEmpty) Some(blockRefs.last) else None), op.position)
            mapToSIL(op, switchEnum, module)
            b.terminator = switchEnum
            b.operators.remove(j, continueBlock.operators.length + 1)
          }
          case Operator.switchValueAssign(result, switchOn, cases, default) => {
            val blockRefs: ArrayBuffer[BlockRef] = new ArrayBuffer
            val newCases: ArrayBuffer[SwitchCase] = new ArrayBuffer
            cases.foreach(cse => {
              val blockRef = generateBlockName(b.blockRef.label)
              blockRefs.append(blockRef)
              newCases.append(new SwitchCase(cse.value, blockRef))
            })
            if (default.nonEmpty) {
              blockRefs.append(generateBlockName(b.blockRef.label))
            }
            val continueRef = generateBlockName(b.blockRef.label)
            cases.zipWithIndex.foreach(cse => {
              val br = new RawTerminatorDef(Terminator.br(continueRef, ArrayBuffer(cse._1.value)), op.position)
              mapToSIL(op, br, module)
              val newBlock = new Block(blockRefs(cse._2), new ArrayBuffer, new ArrayBuffer, br)
              mapToSIL(b, newBlock, module)
              newBlocks.append(newBlock)
            })
            if (default.nonEmpty) {
              val br = new RawTerminatorDef(Terminator.br(continueRef, ArrayBuffer(default.get)), op.position)
              mapToSIL(op, br, module)
              val newBlock = new Block(blockRefs(blockRefs.length - 1), new ArrayBuffer, new ArrayBuffer, br)
              mapToSIL(b, newBlock, module)
              newBlocks.append(newBlock)
            }
            val continueBlock = new Block(continueRef, ArrayBuffer(new Argument(result.ref, result.tpe, None)),
              b.operators.slice(j + 1, b.operators.length), b.terminator)
            mapToSIL(b, continueBlock, module)
            newBlocks.append(continueBlock)
            val switch = new RawTerminatorDef(Terminator.switch(switchOn, newCases,
              if (default.nonEmpty) Some(blockRefs.last) else None), op.position)
            mapToSIL(op, switch, module)
            b.terminator = switch
            b.operators.remove(j, continueBlock.operators.length + 1)
          }
          case Operator.pointerRead(result, pointer) => {
            val fr = new RawOperatorDef(Operator.fieldRead(result, None, pointer, Constants.pointerField, pointer = true), op.position)
            mapToSIL(op, fr, module)
            b.operators(j) = fr
          }
          case Operator.pointerWrite(value, pointer, weak) => {
            val fw = new RawOperatorDef(Operator.fieldWrite(value, pointer, Constants.pointerField, {
              if (weak) { Some(FieldWriteAttribute.weakPointer) } else None
            }), op.position)
            mapToSIL(op, fw, module)
            b.operators(j) = fw
          }
          case _ =>
        }
        j = j + 1
      }
      f.blocks.insertAll(i + 1, newBlocks)
      i = i + /* newBlocks.length + */ 1
    }
    // Handle terminators.
    i = 0
    while (i < f.blocks.length) {
      val b = f.blocks(i)
      val newBlocks: ArrayBuffer[Block] = ArrayBuffer[Block]()
      val position = b.terminator.position
      b.terminator.terminator match {
        case Terminator.condBr(cond, trueBlock, trueArgs, falseBlock, falseArgs) => {
          val brIf = new RawTerminatorDef(Terminator.brIf(cond, trueBlock, trueArgs), position)
          val br = new RawTerminatorDef(Terminator.br(falseBlock, falseArgs), position)
          mapToSIL(b.terminator, brIf, module)
          mapToSIL(b.terminator, brIf, module)
          b.terminator = brIf
          val newBlock = new Block(generateBlockName(b.blockRef.label), new ArrayBuffer, new ArrayBuffer, br)
          mapToSIL(b, newBlock, module)
          newBlocks.append(newBlock)
        }
        case Terminator.switch(switchOn, cases, default) => {
          var currBlock = b
          cases.zipWithIndex.foreach(c => {
            val cse = c._1
            val cond = new Symbol(generateSymbolName(cse.value.name), new Type("Builtin.Int1"))
            val binaryOp = new RawOperatorDef(Operator.binaryOp(cond, BinaryOperation.equals, cse.value, switchOn), position)
            val brIf = new RawTerminatorDef(Terminator.brIf(cond.ref, cse.destination, new ArrayBuffer), position)
            mapToSIL(b.terminator, binaryOp, module)
            mapToSIL(b.terminator, brIf, module)
            currBlock.operators.append(binaryOp)
            currBlock.terminator = new RawTerminatorDef(Terminator.br(cse.destination, new ArrayBuffer), position)// brIf
            if (c._2 > 0) {
              newBlocks.append(currBlock)
            }
            if (c._2 + 1 < cases.length) {
              currBlock = new Block(generateBlockName(b.blockRef.label), new ArrayBuffer, new ArrayBuffer, null)
              mapToSIL(b, currBlock, module)
            }
          })
          if (default.nonEmpty) {
            val br = new RawTerminatorDef(Terminator.br(default.get, new ArrayBuffer), position)
            val newBlock = new Block(generateBlockName(b.blockRef.label), new ArrayBuffer, new ArrayBuffer, br)
            newBlocks.append(newBlock)
          } else if (newBlocks.nonEmpty) {
            val td = newBlocks.last.terminator
            td.terminator match {
              case Terminator.brIf(_, target, args) => {
                val br = new RawTerminatorDef(Terminator.br(target, args), td.position)
                mapToSIL(b.terminator, br, module)
                newBlocks.last.terminator = br
              }
              case _ =>
            }
          }
        }
        case Terminator.switchEnum(switchOn, cases, default) => {
          val dataSymbol = new Symbol(generateSymbolName(switchOn.name), new Type())
          val frData = new RawOperatorDef(Operator.fieldRead(dataSymbol, None, switchOn, "data"), position)
          val typeSymbol = new Symbol(generateSymbolName(switchOn.name), new Type("Builtin.RawPointer"))
          val frType = new RawOperatorDef(Operator.fieldRead(typeSymbol, None, switchOn, "type"), position)
          mapToSIL(b.terminator, frData, module)
          mapToSIL(b.terminator, frType, module)
          b.operators.append(frData)
          b.operators.append(frType)
          var currBlock = b
          cases.zipWithIndex.foreach(c => {
            val cse = c._1
            val typeLiteralSymbol = new Symbol(generateSymbolName(switchOn.name), new Type("Builtin.RawPointer"))
            val typeLiteral = new RawOperatorDef(Operator.literal(typeLiteralSymbol, Literal.string(c._1.decl)), position)
            val cond = new Symbol(generateSymbolName(switchOn.name), new Type("Builtin.Int1"))
            val binaryOp = new RawOperatorDef(Operator.binaryOp(cond, BinaryOperation.equals, typeSymbol.ref, typeLiteralSymbol.ref), position)
            // T0DO: SLOW
            val targetBlock = f.blocks.find(p => p.blockRef.equals(cse.destination)).get
            val brIf = new RawTerminatorDef(Terminator.brIf(cond.ref, cse.destination,
              if (targetBlock.arguments.nonEmpty) ArrayBuffer(dataSymbol.ref) else new ArrayBuffer), position)
            mapToSIL(b.terminator, typeLiteral, module)
            mapToSIL(b.terminator, binaryOp, module)
            mapToSIL(b.terminator, brIf, module)
            currBlock.operators.append(typeLiteral)
            currBlock.operators.append(binaryOp)
            currBlock.terminator = brIf
            if (c._2 > 0) {
              newBlocks.append(currBlock)
            }
            if (c._2 + 1 < cases.length) {
              currBlock = new Block(generateBlockName(b.blockRef.label), new ArrayBuffer, new ArrayBuffer, null)
              mapToSIL(b, currBlock, module)
            }
          })
          if (default.nonEmpty) {
            val br = new RawTerminatorDef(Terminator.br(default.get, new ArrayBuffer), position)
            val newBlock = new Block(generateBlockName(b.blockRef.label), new ArrayBuffer, new ArrayBuffer, br)
            newBlocks.append(newBlock)
          } else if (newBlocks.nonEmpty) {
            val td = newBlocks.last.terminator
            td.terminator match {
              case Terminator.brIf(_, target, args) => {
                val br = new RawTerminatorDef(Terminator.br(target, args), td.position)
                mapToSIL(b.terminator, br, module)
                newBlocks.last.terminator = br
              }
              case _ =>
            }
          }
        }
        case _ =>
      }
      f.blocks.insertAll(i + 1, newBlocks)
      i = i + newBlocks.length + 1
    }
    // Take care of operators last because terminators can generate them
    f.blocks.foreach(b => {
      b.operators.zipWithIndex.foreach(opDef => {
        val op = opDef._1
        val idx = opDef._2
        op.operator match {
          case Operator.binaryOp(result, operation, lhs, rhs) => {
            operation match {
              case BinaryOperation.regular => {
                val a1 = new RawOperatorDef(Operator.assign(result, lhs), op.position)
                val a2 = new RawOperatorDef(Operator.assign(result, rhs), op.position)
                mapToSIL(op, a1, module)
                mapToSIL(op, a2, module)
                b.operators(idx) = a2
                b.operators.insert(idx, a1)
              }
              case BinaryOperation.arbitrary | BinaryOperation.equals => {
                val ni = new RawOperatorDef(Operator.neww(result), op.position)
                mapToSIL(op, ni, module)
                b.operators(idx) = ni
              }
            }
          }
          case Operator.unaryOp(result, operation, operand) => {
            operation match {
              case UnaryOperation.arbitrary => {
                val ni = new RawOperatorDef(Operator.neww(result), op.position)
                mapToSIL(op, ni, module)
                b.operators(idx) = ni
              }
            }
          }
          case _ =>
        }
      })
    })
  }

  /**
   * Converts basic block arguments (phi-nodes) to assignments before
   * branch instructions.
   */
  private def resolveBasicBlockArguments(f: Function, module: Module): ArrayBuffer[Argument] = {
    val resolvedBlocks: mutable.HashSet[Block] = new mutable.HashSet
    f.blocks.zipWithIndex.foreach(bIdx => {
      val block = bIdx._1
      val assigns: ArrayBuffer[RawOperatorDef] = new ArrayBuffer
      block.terminator.terminator match {
        case Terminator.br(to, args) => {
          args.zipWithIndex.foreach(arg => {
            // T0DO: SLOW
            val b = f.blocks.find(b => b.blockRef.equals(to)).get
            resolvedBlocks.add(b)
            val targetArgument = b.arguments(arg._2)
            val assign = new RawOperatorDef(Operator.assign(
              new Symbol(targetArgument.ref, targetArgument.tpe), arg._1, bbArg = true), block.terminator.position)
            mapToSIL(block.terminator, assign, module)
            assigns.append(assign)
          })
        }
        case Terminator.brIf(_, to, args) => {
          args.zipWithIndex.foreach(arg => {
            // T0DO: SLOW
            val block = f.blocks.find(b => b.blockRef.equals(to)).get
            resolvedBlocks.add(block)
            val targetArgument = block.arguments(arg._2)
            val assign = new RawOperatorDef(Operator.assign(
              new Symbol(targetArgument.ref, targetArgument.tpe), arg._1, bbArg = true), block.terminator.position)
            mapToSIL(block.terminator, assign, module)
            assigns.append(assign)
          })
        }
        case _ =>
      }
      block.operators.appendAll(assigns)
    })
    // Uncalled blocks can have dangling references to dissolved block arguments
    // Therefore, create a fake value to reference
    f.blocks.foreach(b => {
      if (!resolvedBlocks.contains(b) && f.blocks(0) != b) {
        b.arguments.foreach(arg => {
          val newInstr = new RawOperatorDef(Operator.neww(new Symbol(arg.ref, arg.tpe)), arg.pos)
          b.operators.insert(0, newInstr)
        })
      }
    })
    f.blocks(0).arguments
  }

  /** Converts a given function to canonical form. */
  private def convertToCanonical(function: Function, module: Module): ArrayBuffer[CanBlock] = {
    val blocks: ArrayBuffer[CanBlock] = new ArrayBuffer
    function.blocks.foreach(b => {
      val operators: ArrayBuffer[CanOperatorDef] = new ArrayBuffer
      b.operators.foreach(op => {
        op.operator match {
          case operator: Operator with CanOperator => {
            val can = new CanOperatorDef(operator, op.position)
            mapToSIL(op, can, module)
            operators.append(can)
          }
          case operator: Operator => {
            throw new IncorrectRawSWIRLException("Raw operator found during conversion to canonical." +
              " All raw operators must be handled before this step: " + operator)
          }
        }
      })
      val terminator: CanTerminatorDef = {
        b.terminator.terminator match {
          case Terminator.br(to, _) => {
            new CanTerminatorDef(Terminator.br_can(to), b.terminator.position)
          }
          case Terminator.brIf(cond, target, _) => {
            new CanTerminatorDef(Terminator.brIf_can(cond, target), b.terminator.position)
          }
          case terminator: Terminator with CanTerminator => {
            new CanTerminatorDef(terminator, b.terminator.position)
          }
          case terminator: Terminator => {
            throw new IncorrectRawSWIRLException("Raw terminator found during conversion to canonical." +
              " All raw terminators must be handled before this step: " + terminator)
          }
        }
      }
      mapToSIL(b.terminator, terminator, module)
      blocks.append(new CanBlock(b.blockRef, operators, terminator))
    })
    blocks
  }

  // TODO: Add wrappers that automatically call this
  private def mapToSIL(old: Object, added: Object, module: Module): Unit = {
    if (module.silMap.nonEmpty) {
      module.silMap.get.tryMapNew(old, added)
    }
  }

  /** Resolve all field aliases. */
  private def resolveAliases(module: Module): Unit = {
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
                      case pw: Operator.pointerWrite => {
                        if ((pw.pointer == aliased) && { if (bIdx == b2Idx) operatorIdx != op2Idx - 1 else true }) {
                          val fw = new RawOperatorDef(Operator.fieldWrite(
                            pw.value, fieldRead.obj, fieldRead.field, None), op2._1.position)
                          b2._1.operators(op2._2) = fw
                          mapToSIL(pw, fw, module)
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

  /** Generate a control flow graph for the given blocks. */
  @throws[IncompleteRawSWIRLException]
  private def generateCFG(blocks: ArrayBuffer[CanBlock]): Graph[CanBlock, DefaultEdge] = {
    val graph: Graph[CanBlock, DefaultEdge] = new DefaultDirectedGraph(classOf[DefaultEdge])
    val exitBlock = new CanBlock(new BlockRef(Constants.exitBlock), null, null)
    graph.addVertex(exitBlock)
    def getTarget(ref: BlockRef): CanBlock = {
      // T0D0: Not really efficient
      val to = blocks.find(p => p.blockRef.equals(ref))
      if (to.isEmpty) {
        throw new IncompleteRawSWIRLException("control flow instruction 'to' block does not exist: " + ref.label)
      }
      graph.addVertex(to.get)
      to.get
    }
    blocks.zipWithIndex.foreach(bit => {
      val b = bit._1
      graph.addVertex(b)
      b.terminator.terminator match {
        case Terminator.br_can(to) => {
          graph.addEdge(b, getTarget(to))
        }
        case Terminator.brIf_can(_, target) => {
          graph.addEdge(b, getTarget(target))
          graph.addVertex(blocks(bit._2 + 1))
          graph.addEdge(b, blocks(bit._2 + 1))
        }
        case Terminator.ret(_) => {
          graph.addEdge(b, exitBlock)
        }
        case Terminator.unreachable =>
          // For now, "EXIT" means transferring execution to caller,
          // so just leave this blank.
        case Terminator.yld(_, resume, unwind) => {
          graph.addEdge(b, exitBlock)
          graph.addEdge(b, getTarget(resume))
          graph.addEdge(b, getTarget(unwind))
        }
      }
    })
    // Delete dead blocks? Does this even ever happen?
    // blocks.filterInPlace(b => !graph.edgesOf(b).isEmpty)
    graph
  }
}
