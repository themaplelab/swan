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

class Module(val functions: Array[Function])

class Function(val attribute: Option[FunctionAttribute], val name: String, val tpe: Type,
               val blocks: Array[Block])

class Block(val label: String, val arguments: Array[Argument],
            val operators: Array[OperatorDef], val terminator: TerminatorDef)

sealed trait FunctionAttribute
object FunctionAttribute {
  case object coroutine extends FunctionAttribute
  case object stub extends FunctionAttribute
  case object model extends FunctionAttribute
}

class Type(name: String = "$Any") // Only String for now

class Position(path: String, line: Int, col: Int)

class Argument(val name: String, val tpe: Type)


sealed abstract trait InstructionDef {
  val instruction: Instruction
}
object InstructionDef {
  case class operator(val operatorDef: OperatorDef) extends InstructionDef {
    override val instruction: Instruction = Instruction.operator(operatorDef.operator)
  }
  case class terminator(val terminatorDef: TerminatorDef) extends InstructionDef {
    override val instruction: Instruction = Instruction.terminator(terminatorDef.terminator)
  }
}

class OperatorDef(val operator: Operator)

class TerminatorDef(val terminator: Terminator)

sealed trait Instruction
object Instruction {
  case class operator(op: Operator) extends Instruction
  case class terminator(t: Terminator) extends Instruction
}

sealed trait Operator
object Operator { // WIP
  // *** SHARED ***
  case class newGlobal(val name: String) extends Operator
  case class neww(val result: Symbol) extends Operator
  case class assignGlobal(val result: Symbol, val name: String) extends Operator
  case class assign(val result: Symbol, val from: String) extends Operator
  case class literal(val result: Symbol, val literal: Literal) extends Operator
  case class functionRef(val result: Symbol, val name: String) extends Operator
  case class print(val name: String) extends Operator
  case class arrayRead(val result: Symbol, val alias: Boolean, val arr: String) extends Operator
  case class arrayWrite(val value: String, val arr: String) extends Operator
  case class fieldRead(val result: Symbol, val alias: Boolean, val obj: String, val field: String) extends Operator
  case class fieldWrite(val value: String, obj: String, val field: String) extends Operator
  case class unaryOp(val result: Symbol, operation: UnaryOperation, operand: String) extends Operator
  case class binaryOp(val result: Symbol, operation: BinaryOperation, lhs: String, rhs: String) extends Operator
  // *** RAW ONLY ***
  case class pointerRead(val result: Symbol, val pointer: String) extends Operator
  case class pointerWrite(val value: String, val pointer: String) extends Operator
  case class symbolCopy(val from: String, to: String) extends Operator
}

sealed trait UnaryOperation
object UnaryOperation {
  case object arbitrary extends UnaryOperation
}

sealed trait BinaryOperation
object BinaryOperation {
  case object arbitrary extends BinaryOperation
}

sealed trait Literal
object Literal {
  case class string(val value: String) extends Literal
  case class int(val value: BigInt) extends Literal
  case class float(val value: Float) extends Literal
}

sealed trait Terminator
object Terminator { // WIP

}

class Symbol(val name: String, val tpe: Type)
