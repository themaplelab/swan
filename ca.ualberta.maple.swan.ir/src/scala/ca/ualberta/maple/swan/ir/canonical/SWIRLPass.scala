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


import ca.ualberta.maple.swan.ir.Exceptions.{ExperimentalException, IncompleteRawSWIRLException, UnexpectedSILFormatException}
import ca.ualberta.maple.swan.ir.{Block, Function, Module, Operator, OperatorDef, SymbolRef, SymbolTableEntry, SymbolTables, WithResult}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


object SWIRLPass {

  def runPasses(module: Module): Unit = {
    resolveAliases(module)
    val symbolTables = generateSymbolTables(module)
    module.raw = false
  }

  @throws[IncompleteRawSWIRLException]
  def generateSymbolTables(module: Module): SymbolTables = {
    // Create symbol table.
    // Mapping of result values to their creating operator.
    val table = new SymbolTables()
    module.functions.foreach(function => {
      table.tables.put(function.name, new mutable.HashMap())
      val fTable = table.tables(function.name)
      function.blocks.foreach(block => {
        block.arguments.foreach(argument => {
          fTable.put(argument.name.name, SymbolTableEntry.argument(argument))
        })
        block.operators.foreach(op => {
          op.operator match {
            case inst: WithResult =>
              fTable.put(inst.value.ref.name, SymbolTableEntry.operator(inst.value, op.operator))
            case _ =>
          }
        })
      })
    })
    // Verify that all values referenced are either block arguments or created with an operator.
    // This is NOT part of the guarantees! This is just here in case the initial program
    // is missing some values.
    module.functions.foreach(f => {
      f.refTable.symbols.foreach(ref => {
        // Use the value (not the key) due to COPY operations.
        // Keys are effectively non-concrete aliases for the actual values.
        if (!table.tables(f.name).contains(ref._2.name)) {
          // TODO: Recover with `new` instruction.
          throw new IncompleteRawSWIRLException("Symbol reference to value " + ref._2.name + " in function `" + f.name +
            "` is invalid. Value is not a block argument nor the result of an operator.")
        }
      })
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
}
