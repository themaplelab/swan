/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.parser

/*
 * TODO: This would be fine if we didn't use Scala in Java code, but using
 *  implicit Scala classes in Java is ugly. We should probably just write
 *  methods for printing, as opposed to method extensions, instead.
 */
object PrintExtensions {

  // Add description field to types.

  implicit class ModulePrinter(val m : Module) {
    val description: String = {
      val p = new SILPrinter()
      p.print(m)
      p.description
    }
  }

  implicit class FunctionPrinter(val f : Function) {
    val description: String = {
      val p = new SILPrinter()
      p.print(f)
      p.description
    }
  }

  implicit class BlockPrinter(val b : Block) {
    val description: String = {
      val p = new SILPrinter()
      p.print(b)
      p.description
    }
  }

  implicit class OperatorDefPrinter(val od : OperatorDef) {
    val description: String = {
      val p = new SILPrinter()
      p.print(od)
      p.description
    }
  }

  implicit class TerminatorDefPrinter(val td : TerminatorDef) {
    val description: String = {
      val p = new SILPrinter()
      p.print(td)
      p.description
    }
  }

  implicit class InstructionDefPrinter(val id : InstructionDef) {
    val description: String = id match {
      case InstructionDef.operator(op: OperatorDef) => op.description
      case InstructionDef.terminator(t: TerminatorDef) => t.description
    }
  }

  implicit class OperatorPrinter(val o : Operator) {
    val description: String = {
      val p = new SILPrinter()
      p.print(o)
      p.description
    }
  }

  implicit class TerminatorPrinter(val t : Terminator) {
    val description: String = {
      val p = new SILPrinter()
      p.print(t)
      p.description
    }
  }

  implicit class InstructionPrinter(val i : Instruction) {
    val description: String = i match {
      case Instruction.operator(op: Operator) => op.description
      case Instruction.terminator(t: Terminator) => t.description
    }
  }
}

