/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

/*
 * This file contains SWIRL data structures and some helper functions.
 * SWIRL documentation is available in the SWAN repository wiki.
 */

package ca.ualberta.maple.swan.ir

import org.jgrapht.Graph
import org.jgrapht.graph.DefaultEdge

import scala.collection.{immutable, mutable}
import scala.collection.mutable.ArrayBuffer

// Imports might be useful for modelling later, but probably not.
class Module(val functions: ArrayBuffer[Function], val ddg: DynamicDispatchGraph,
             val silMap: SILMap)

class CanModule(val functions: ArrayBuffer[CanFunction], val ddg: DynamicDispatchGraph,
                val silMap: SILMap)

class Function(val attribute: Option[FunctionAttribute], val name: String, val tpe: Type,
               val blocks: ArrayBuffer[Block], val refTable: RefTable,
               val instantiatedTypes: immutable.HashSet[String])

class CanFunction(val attribute: Option[FunctionAttribute], val name: String, val tpe: Type,
                  val arguments: Array[Argument], val blocks: ArrayBuffer[CanBlock],
                  val refTable: RefTable, val instantiatedTypes: immutable.HashSet[String],
                  val symbolTable: mutable.HashMap[String, SymbolTableEntry],
                  val cfg: Graph[CanBlock, DefaultEdge]) {
  def getSymbol(name: String): Symbol = {
    symbolTable(name) match {
      case SymbolTableEntry.operator(symbol, _) => symbol
      case SymbolTableEntry.argument(argument) => argument
    }
  }
}

class Block(val blockRef: BlockRef, val arguments: Array[Argument],
            val operators: ArrayBuffer[RawOperatorDef], var terminator: RawTerminatorDef)

class CanBlock(val blockRef: BlockRef, val operators: ArrayBuffer[CanOperatorDef],
               val terminator: CanTerminatorDef)

sealed trait FunctionAttribute
object FunctionAttribute {
  case object coroutine extends FunctionAttribute
  case object stub extends FunctionAttribute
  case object model extends FunctionAttribute
}

class Type(val name: String = "Any")

class Position(val path: String, val line: Int, val col: Int)

sealed trait InstructionDef {
  val instruction: Instruction
}
object InstructionDef {
  case class rawOperator(operatorDef: RawOperatorDef) extends InstructionDef {
    override val instruction: Instruction = Instruction.rawOperator(operatorDef.operator)
  }
  case class canOperator(operatorDef: CanOperatorDef) extends InstructionDef {
    override val instruction: Instruction = Instruction.canOperator(operatorDef.operator)
  }
  case class rawTerminator(terminatorDef: RawTerminatorDef) extends InstructionDef {
    override val instruction: Instruction = Instruction.rawTerminator(terminatorDef.terminator)
  }
  case class canTerminator(terminatorDef: CanTerminatorDef) extends InstructionDef {
    override val instruction: Instruction = Instruction.canTerminator(terminatorDef.terminator)
  }
}

class RawOperatorDef(val operator: RawOperator, val position: Option[Position])
class CanOperatorDef(val operator: CanOperator, val position: Option[Position])

class RawTerminatorDef(val terminator: RawTerminator, val position: Option[Position])
class CanTerminatorDef(val terminator: CanTerminator, val position: Option[Position])

sealed trait Instruction
object Instruction {
  case class rawOperator(op: RawOperator) extends Instruction
  case class canOperator(op: CanOperator) extends Instruction
  case class rawTerminator(t: RawTerminator) extends Instruction
  case class canTerminator(t: CanTerminator) extends Instruction
}

abstract class Operator
sealed trait RawOperator extends Operator
sealed trait CanOperator extends Operator

object Operator {
  case class neww(result: Symbol) extends WithResult(result) with RawOperator with CanOperator
  case class assign(result: Symbol, from: SymbolRef, bbArg: Boolean = false) extends WithResult(result) with RawOperator with CanOperator
  case class literal(result: Symbol, literal: Literal) extends WithResult(result) with RawOperator with CanOperator
  case class dynamicRef(result: Symbol, index: String) extends WithResult(result) with RawOperator with CanOperator
  case class builtinRef(result: Symbol, name: String) extends WithResult(result) with RawOperator with CanOperator
  case class functionRef(result: Symbol, var name: String) extends WithResult(result) with RawOperator with CanOperator
  case class apply(result: Symbol, functionRef: SymbolRef, arguments: Array[SymbolRef]) extends WithResult(result) with RawOperator with CanOperator
  case class arrayRead(result: Symbol, arr: SymbolRef) extends WithResult(result) with RawOperator with CanOperator
  case class singletonRead(result: Symbol, tpe: String, field: String) extends WithResult(result) with RawOperator with CanOperator
  case class singletonWrite(value: SymbolRef, tpe: String, field: String) extends Operator with RawOperator with CanOperator
  case class fieldRead(result: Symbol, alias: Option[SymbolRef], obj: SymbolRef, field: String, pointer: Boolean = false) extends WithResult(result) with RawOperator with CanOperator
  case class fieldWrite(value: SymbolRef, obj: SymbolRef, field: String, pointer: Boolean = false) extends Operator with RawOperator with CanOperator
  case class unaryOp(result: Symbol, operation: UnaryOperation, operand: SymbolRef) extends WithResult(result) with RawOperator with CanOperator
  case class binaryOp(result: Symbol, operation: BinaryOperation, lhs: SymbolRef, rhs: SymbolRef) extends WithResult(result) with RawOperator with CanOperator
  case class condFail(value: SymbolRef) extends Operator with RawOperator with CanOperator
  case class switchEnumAssign(result: Symbol, switchOn: SymbolRef,
                              cases: Array[EnumAssignCase], default: Option[SymbolRef]) extends WithResult(result) with RawOperator
  case class pointerRead(result: Symbol, pointer: SymbolRef) extends WithResult(result) with RawOperator
  case class pointerWrite(value: SymbolRef, pointer: SymbolRef) extends Operator with RawOperator
}

abstract class WithResult(val value: Symbol) extends Operator

abstract class Terminator
sealed trait RawTerminator extends Terminator
sealed trait CanTerminator extends Terminator

object Terminator {
  case class br(to: BlockRef, args: Array[SymbolRef]) extends RawTerminator
  case class br_can(to: BlockRef) extends CanTerminator
  case class brIf(cond: SymbolRef, target: BlockRef, args: Array[SymbolRef]) extends RawTerminator
  case class brIf_can(cond: SymbolRef, target: BlockRef) extends CanTerminator
  case class condBr(cond: SymbolRef, trueBlock: BlockRef, trueArgs: Array[SymbolRef],
                    falseBlock: BlockRef, falseArgs: Array[SymbolRef]) extends RawTerminator
  case class switch(switchOn: SymbolRef, cases: Array[SwitchCase], default: Option[BlockRef]) extends RawTerminator
  case class switchEnum(switchOn: SymbolRef, cases: Array[SwitchEnumCase], default: Option[BlockRef]) extends RawTerminator
  case class ret(value: SymbolRef) extends RawTerminator with CanTerminator
  case class thro(value: SymbolRef) extends RawTerminator with CanTerminator
  case class tryApply(functionRef: SymbolRef, arguments: Array[SymbolRef],
                      normal: BlockRef, normalType: Type, error: BlockRef, errorType: Type) extends RawTerminator
  case object unreachable extends RawTerminator with CanTerminator
  case class yld(yields: Array[SymbolRef], resume: BlockRef, unwind: BlockRef) extends RawTerminator with CanTerminator
  case object unwind extends RawTerminator
}

class EnumAssignCase(val decl: String, val value: SymbolRef)

class SwitchCase(val value: SymbolRef, val destination: BlockRef)

class SwitchEnumCase(val decl: String, val destination: BlockRef)

sealed trait UnaryOperation
object UnaryOperation {
  case object arbitrary extends UnaryOperation
}

sealed trait BinaryOperation
object BinaryOperation {
  case object arbitrary extends BinaryOperation
  case object equals extends BinaryOperation
}

sealed trait Literal
object Literal {
  case class string(value: String) extends Literal
  case class int(value: BigInt) extends Literal
  case class float(value: Float) extends Literal
}

class Symbol(val ref: SymbolRef, val tpe: Type)

// `pos` can be changed by debug_value and debug_value_addr.
class Argument(ref: SymbolRef, tpe: Type, var pos: Option[Position] = None) extends Symbol(ref, tpe)

// This is so that we can change symbol names throughout the program
// for things like symbol_copy folding.
class SymbolRef(var name: String) {
  override def equals(other: Any): Boolean = {
    other match {
      case ref: SymbolRef =>
        ref.name == name
      case _ =>
        false
    }
  }
}
class BlockRef(var label: String) {
  override def equals(other: Any): Boolean = {
    other match {
      case ref: BlockRef =>
        ref.label == label
      case _ =>
        false
    }
  }
}

class RefTable {
  val symbols = new mutable.HashMap[String, SymbolRef]()
  val blocks = new mutable.HashMap[String, BlockRef]()
  // Eliminate any args that are not used later
  // Otherwise, move to `symbols` cache
  val temporaryBBArgs = new mutable.HashMap[String, SymbolRef]()
}

sealed trait SymbolTableEntry
object SymbolTableEntry {
  case class operator(symbol: Symbol, var operator: CanOperator) extends SymbolTableEntry
  case class argument(argument: Argument) extends SymbolTableEntry
}

class SILMap {
  val silToSWIRL: mutable.HashMap[Object, Object] = new mutable.HashMap[Object, Object]()
  val swirlToSIL: mutable.HashMap[Object, Object] = new mutable.HashMap[Object, Object]()
  def map(sil: Object, swirl: Object): Unit = {
    silToSWIRL.put(sil, swirl)
    swirlToSIL.put(swirl, sil)
  }
  def tryMapNew(oldSWIRL: Object, newSWIRL: Object): Unit = {
    if (swirlToSIL.contains(oldSWIRL)) {
      val sil = swirlToSIL(oldSWIRL)
      // silToSWIRL for sil becomes many-to-one, so only map swirlToSIL.
      swirlToSIL.put(newSWIRL, sil)
    }
  }
}