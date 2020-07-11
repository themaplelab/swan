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

  implicit class ModulePrinter(val m : SILModule) {
    val description: String = {
      val p = new SILPrinter()
      p.print(m)
      p.description
    }
  }

  implicit class FunctionPrinter(val f : SILFunction) {
    val description: String = {
      val p = new SILPrinter()
      p.print(f)
      p.description
    }
  }

  implicit class BlockPrinter(val b : SILBlock) {
    val description: String = {
      val p = new SILPrinter()
      p.print(b)
      p.description
    }
  }

  implicit class OperatorDefPrinter(val od : SILOperatorDef) {
    val description: String = {
      val p = new SILPrinter()
      p.print(od)
      p.description
    }
  }

  implicit class TerminatorDefPrinter(val td : SILTerminatorDef) {
    val description: String = {
      val p = new SILPrinter()
      p.print(td)
      p.description
    }
  }

  implicit class InstructionDefPrinter(val id : SILInstructionDef) {
    val description: String = id match {
      case SILInstructionDef.operator(op: SILOperatorDef) => op.description
      case SILInstructionDef.terminator(t: SILTerminatorDef) => t.description
    }
  }

  implicit class OperatorPrinter(val o : SILOperator) {
    val description: String = {
      val p = new SILPrinter()
      p.print(o)
      p.description
    }
  }

  implicit class TerminatorPrinter(val t : SILTerminator) {
    val description: String = {
      val p = new SILPrinter()
      p.print(t)
      p.description
    }
  }

  implicit class InstructionPrinter(val i : SILInstruction) {
    val description: String = i match {
      case SILInstruction.operator(op: SILOperator) => op.description
      case SILInstruction.terminator(t: SILTerminator) => t.description
    }
  }
}

