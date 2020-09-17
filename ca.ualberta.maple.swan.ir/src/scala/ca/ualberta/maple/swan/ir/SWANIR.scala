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
  case class operator(operatorDef: OperatorDef) extends InstructionDef {
    override val instruction: Instruction = Instruction.operator(operatorDef.operator)
  }
  case class terminator(terminatorDef: TerminatorDef) extends InstructionDef {
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
  case class newGlobal(name: String) extends Operator
  case class neww(result: Symbol) extends Operator
  case class assignGlobal(result: Symbol, name: String) extends Operator
  case class assign(result: Symbol, from: String) extends Operator
  case class literal(result: Symbol, literal: Literal) extends Operator
  case class builtinRef(result: Symbol, name: String) extends Operator
  case class functionRef(result: Symbol, name: Array[String]) extends Operator
  case class print(name: String) extends Operator
  case class arrayRead(result: Symbol, alias: Boolean, arr: String) extends Operator
  case class arrayWrite(value: String, arr: String) extends Operator
  case class fieldRead(result: Symbol, alias: Boolean, obj: String, field: String) extends Operator
  case class fieldWrite(value: String, obj: String, field: String) extends Operator
  case class unaryOp(result: Symbol, operation: UnaryOperation, operand: String) extends Operator
  case class binaryOp(result: Symbol, operation: BinaryOperation, lhs: String, rhs: String) extends Operator
  // *** RAW ONLY ***
  case class pointerRead(result: Symbol, pointer: String) extends Operator
  case class pointerWrite(value: String, pointer: String) extends Operator
  case class symbolCopy(from: String, to: String) extends Operator
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
  case class string(value: String) extends Literal
  case class int(value: BigInt) extends Literal
  case class float(value: Float) extends Literal
}

sealed trait Terminator
object Terminator { // WIP

}

class Symbol(val name: String, val tpe: Type)
