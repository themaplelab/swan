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

/*
 * This file contains SWIRL data structures and some helper functions.
 * SWIRL documentation is available here
 * https://github.com/themaplelab/swan/wiki/SWIRL
 */

package ca.ualberta.maple.swan.ir

import java.io.File
import java.util.UUID

import org.jgrapht.Graph
import org.jgrapht.graph.DefaultEdge

import scala.collection.mutable.ArrayBuffer
import scala.collection.{immutable, mutable}

class ModuleGroup(val functions: ArrayBuffer[CanFunction],
                  val entries: immutable.HashSet[CanFunction],
                  val ddgs: mutable.HashMap[String, DynamicDispatchGraph],
                  val silMap: Option[SILMap], var swirlSourceMap: Option[mutable.HashMap[Object, (Int, Int)]],
                  val metas: ArrayBuffer[ModuleMetadata]) extends Serializable {
  override def toString: String = {
    val sb = new StringBuilder
    sb.append("Module group: ")
    sb.append(metas.length)
    sb.append(" source modules\n")
    metas.foreach(m => {
      sb.append("  ")
      sb.append(m.toString)
      sb.append("\n")
    })
    sb.toString()
  }
}

class ModuleMetadata(val file: Option[File],
                     val silSource: Option[File]) extends Serializable {
  override def toString: String = {
    if (file.nonEmpty) {
      file.get.getName
    } else if (silSource.nonEmpty) {
      silSource.get.getName
    } else {
      "internal Raw SWIRL module " + UUID.randomUUID()
    }
  }
}

// Imports might be useful for modelling later, but probably not.
class Module(val functions: ArrayBuffer[Function],
             val ddg: Option[DynamicDispatchGraph],
             val silMap: Option[SILMap], val meta: ModuleMetadata) {
  // Also used as unique identifier
  override def toString: String = {
    meta.toString
  }
}

class CanModule(val functions: ArrayBuffer[CanFunction],
                val ddg: Option[DynamicDispatchGraph],
                val silMap: Option[SILMap], val meta: ModuleMetadata) {
  override def toString: String = {
    meta.toString
  }
}

class Function(val attribute: Option[FunctionAttribute], var name: String, val tpe: Type,
               val blocks: ArrayBuffer[Block], val refTable: RefTable,
               val instantiatedTypes: mutable.HashSet[String])

class CanFunction(var attribute: Option[FunctionAttribute], val name: String, val tpe: Type,
                  val arguments: ArrayBuffer[Argument], val blocks: ArrayBuffer[CanBlock],
                  val refTable: RefTable, val instantiatedTypes: mutable.HashSet[String],
                  val symbolTable: SymbolTable, var cfg: Graph[CanBlock, DefaultEdge]) extends Serializable {
  def getSymbol(name: String): Symbol = {
    symbolTable(name) match {
      case SymbolTableEntry.operator(symbol, _) => symbol
      case SymbolTableEntry.argument(argument) => argument
      case SymbolTableEntry.multiple(symbol, _) => symbol
    }
  }
}

class Block(val blockRef: BlockRef, val arguments: ArrayBuffer[Argument],
            val operators: ArrayBuffer[RawOperatorDef], var terminator: RawTerminatorDef)

class CanBlock(val blockRef: BlockRef, val operators: ArrayBuffer[CanOperatorDef],
               val terminator: CanTerminatorDef) extends Serializable

sealed trait FunctionAttribute extends Serializable
object FunctionAttribute {
  case object coroutine extends FunctionAttribute
  case object stub extends FunctionAttribute
  case object model extends FunctionAttribute
  case object modelOverride extends FunctionAttribute
  case object entry extends FunctionAttribute
  case object linked extends FunctionAttribute
}

class Type(val name: String = "Any") extends Serializable {
  override def hashCode: Int = {
    val prime = 31
    var result = 1
    result = prime * result + this.name.hashCode
    result
  }
  override def equals(obj: Any): Boolean = {
    obj match {
      case t: Type => t.name == this.name
      case _ => false
    }
  }
}

class Position(val path: String, val line: Int, val col: Int) extends Serializable {
  override def toString: String = {
    path + ":" + line.toString + ":" + col.toString
  }
  def sameLine(other: Position): Boolean = {
    path == other.path && line == other.line
  }
}

sealed trait RawInstructionDef {
  val instruction: Instruction
}
sealed trait CanInstructionDef {
  val instruction: Instruction
}

object RawInstructionDef {
  case class operator(operatorDef: RawOperatorDef) extends RawInstructionDef {
    override val instruction: Instruction = Instruction.rawOperator(operatorDef.operator)
  }
  case class terminator(terminatorDef: RawTerminatorDef) extends RawInstructionDef {
    override val instruction: Instruction = Instruction.rawTerminator(terminatorDef.terminator)
  }
}
object CanInstructionDef {
  case class operator(operatorDef: CanOperatorDef) extends CanInstructionDef {
    override val instruction: Instruction = Instruction.canOperator(operatorDef.operator)
  }
  case class terminator(terminatorDef: CanTerminatorDef) extends CanInstructionDef {
    override val instruction: Instruction = Instruction.canTerminator(terminatorDef.terminator)
  }
}

class RawOperatorDef(val operator: RawOperator, val position: Option[Position])
class CanOperatorDef(val operator: CanOperator, val position: Option[Position]) extends Serializable

class RawTerminatorDef(val terminator: RawTerminator, val position: Option[Position])
class CanTerminatorDef(val terminator: CanTerminator, val position: Option[Position]) extends Serializable

sealed trait Instruction
object Instruction {
  case class rawOperator(op: RawOperator) extends Instruction
  case class canOperator(op: CanOperator) extends Instruction
  case class rawTerminator(t: RawTerminator) extends Instruction
  case class canTerminator(t: CanTerminator) extends Instruction
}

abstract class Operator extends Serializable
sealed trait RawOperator extends Operator
sealed trait CanOperator extends Operator
sealed trait FunctionRef extends Operator

object Operator {
  case class neww(result: Symbol) extends WithResult(result) with RawOperator with CanOperator
  case class assign(result: Symbol, from: SymbolRef, bbArg: Boolean = false) extends WithResult(result) with RawOperator with CanOperator
  case class literal(result: Symbol, literal: Literal) extends WithResult(result) with RawOperator with CanOperator
  case class dynamicRef(result: Symbol, obj: SymbolRef, index: String) extends WithResult(result) with RawOperator with CanOperator with FunctionRef
  case class builtinRef(result: Symbol, name: String) extends WithResult(result) with RawOperator with CanOperator with FunctionRef
  case class functionRef(result: Symbol, name: String) extends WithResult(result) with RawOperator with CanOperator with FunctionRef
  case class apply(result: Symbol, functionRef: SymbolRef, arguments: ArrayBuffer[SymbolRef]) extends WithResult(result) with RawOperator with CanOperator {
    // Used by CG debugInfo for printing
    override def hashCode(): Int = System.identityHashCode(this)
  }
  case class singletonRead(result: Symbol, tpe: String, field: String) extends WithResult(result) with RawOperator with CanOperator
  case class singletonWrite(value: SymbolRef, tpe: String, field: String) extends Operator with RawOperator with CanOperator
  case class fieldRead(result: Symbol, alias: Option[SymbolRef], obj: SymbolRef, field: String, pointer: Boolean = false) extends WithResult(result) with RawOperator with CanOperator
  case class fieldWrite(value: SymbolRef, obj: SymbolRef, field: String, attr: Option[FieldWriteAttribute]) extends Operator with RawOperator with CanOperator
  case class unaryOp(result: Symbol, operation: UnaryOperation, operand: SymbolRef) extends WithResult(result) with RawOperator
  case class binaryOp(result: Symbol, operation: BinaryOperation, lhs: SymbolRef, rhs: SymbolRef) extends WithResult(result) with RawOperator
  case class condFail(value: SymbolRef) extends Operator with RawOperator with CanOperator
  case class switchEnumAssign(result: Symbol, switchOn: SymbolRef,
                              cases: ArrayBuffer[EnumAssignCase], default: Option[SymbolRef]) extends WithResult(result) with RawOperator
  case class switchValueAssign(result: Symbol, switchOn: SymbolRef,
                               cases: ArrayBuffer[ValueAssignCase], default: Option[SymbolRef]) extends WithResult(result) with RawOperator
  case class pointerRead(result: Symbol, pointer: SymbolRef) extends WithResult(result) with RawOperator
  case class pointerWrite(value: SymbolRef, pointer: SymbolRef, weak: Boolean = false) extends Operator with RawOperator
}

abstract class WithResult(val value: Symbol) extends Operator

abstract class Terminator extends Serializable
sealed trait RawTerminator extends Terminator
sealed trait CanTerminator extends Terminator

object Terminator {
  case class br(to: BlockRef, args: ArrayBuffer[SymbolRef]) extends RawTerminator
  case class br_can(to: BlockRef) extends CanTerminator
  case class brIf(cond: SymbolRef, target: BlockRef, args: ArrayBuffer[SymbolRef]) extends RawTerminator
  case class brIf_can(cond: SymbolRef, target: BlockRef) extends CanTerminator
  case class condBr(cond: SymbolRef, trueBlock: BlockRef, trueArgs: ArrayBuffer[SymbolRef],
                    falseBlock: BlockRef, falseArgs: ArrayBuffer[SymbolRef]) extends RawTerminator
  case class switch(switchOn: SymbolRef, cases: ArrayBuffer[SwitchCase], default: Option[BlockRef]) extends RawTerminator
  case class switchEnum(switchOn: SymbolRef, cases: ArrayBuffer[SwitchEnumCase], default: Option[BlockRef]) extends RawTerminator
  case class ret(value: SymbolRef) extends RawTerminator with CanTerminator
  case object unreachable extends RawTerminator with CanTerminator
  case class yld(yields: ArrayBuffer[SymbolRef], resume: BlockRef, unwind: BlockRef) extends RawTerminator with CanTerminator
}

sealed trait FieldWriteAttribute extends Serializable
object FieldWriteAttribute {
  case object pointer extends FieldWriteAttribute
  case object weakPointer extends FieldWriteAttribute
  case object weak extends FieldWriteAttribute
}

class EnumAssignCase(val decl: String, val value: SymbolRef) extends Serializable

class ValueAssignCase(val value: SymbolRef, val select: SymbolRef) extends Serializable

class SwitchCase(val value: SymbolRef, val destination: BlockRef) extends Serializable

class SwitchEnumCase(val decl: String, val destination: BlockRef) extends Serializable

sealed trait UnaryOperation extends Serializable
object UnaryOperation {
  case object arbitrary extends UnaryOperation
}

sealed trait BinaryOperation extends Serializable
object BinaryOperation {
  case object regular extends BinaryOperation
  case object arbitrary extends BinaryOperation
  case object equals extends BinaryOperation
}

sealed trait Literal extends Serializable
object Literal {
  case class string(value: String) extends Literal
  case class int(value: BigInt) extends Literal
  case class float(value: Double) extends Literal
}

class Symbol(val ref: SymbolRef, val tpe: Type) extends Serializable {
  override def hashCode: Int = {
    val prime = 31
    var result = 1
    result = prime * result + this.ref.hashCode
    result = prime * result + this.tpe.hashCode
    result
  }
  override def equals(other: Any): Boolean = {
    other match {
      case symbol: Symbol =>
        this.ref.equals(symbol.ref) && this.tpe.equals(symbol.tpe)
      case _ =>
        false
    }
  }
  override def toString: String = ref.name
}

// `pos` can be changed by debug_value and debug_value_addr.
class Argument(ref: SymbolRef, tpe: Type, var pos: Option[Position] = None) extends Symbol(ref, tpe) with Serializable

// This is so that we can change symbol names throughout the program
// for things like symbol_copy folding.
class SymbolRef(var name: String) extends Serializable {
  override def hashCode: Int = {
    val prime = 31
    var result = 1
    result = prime * result + this.name.hashCode
    result
  }
  override def equals(other: Any): Boolean = {
    other match {
      case ref: SymbolRef => ref.name == this.name
      case _ => false
    }
  }
}
class BlockRef(var label: String) extends Serializable {
  override def hashCode: Int = {
    val prime = 31
    var result = 1
    result = prime * result + this.label.hashCode
    result
  }
  override def equals(other: Any): Boolean = {
    other match {
      case ref: BlockRef => ref.label == this.label
      case _ => false
    }
  }
}

class RefTable extends Serializable {
  val symbols = new mutable.HashMap[String, SymbolRef]()
  val blocks = new mutable.HashMap[String, BlockRef]()
  // Eliminate any args that are not used later
  // Otherwise, move to `symbols` cache
  val temporaryBBArgs = new mutable.HashMap[String, SymbolRef]()
  def clear(): Unit = {
    symbols.clear()
    blocks.clear()
    temporaryBBArgs.clear()
  }
}

class SymbolTable extends mutable.HashMap[String, SymbolTableEntry] {
  override def put(key: String, value: SymbolTableEntry): Option[SymbolTableEntry] = {
    throw new RuntimeException("Do not use put() directly")
  }
  def putArg(key: String, arg: Argument): Unit = {
    if (this.contains(key)) {
      throw new RuntimeException("Attempted to add argument entry to symbol table when key already in table")
    }
    super.put(key, SymbolTableEntry.argument(arg))
  }
  def putOp(value: Symbol, operator: CanOperator): Unit = {
    val key = value.ref.name
    if (this.contains(key)) {
      this(key) match {
        case SymbolTableEntry.operator(symbol, op) => {
          val arr = new ArrayBuffer[CanOperator]()
          arr.append(operator)
          arr.append(op)
          super.put(key, SymbolTableEntry.multiple(symbol, arr))
        }
        case SymbolTableEntry.multiple(_, operators) => operators.append(operator)
        case SymbolTableEntry.argument(arg) => {
          val arr = new ArrayBuffer[CanOperator]()
          arr.append(operator)
          super.put(key, SymbolTableEntry.multiple(arg, arr))
        }
      }
    } else {
      super.put(key, SymbolTableEntry.operator(value, operator))
    }
  }
  def replace(key: String, entry: SymbolTableEntry): Unit = {
    super.put(key, entry)
  }
}

sealed trait SymbolTableEntry extends Serializable
object SymbolTableEntry {
  case class operator(symbol: Symbol, var operator: CanOperator) extends SymbolTableEntry
  case class argument(argument: Argument) extends SymbolTableEntry
  case class multiple(symbol: Symbol, var operators: ArrayBuffer[CanOperator]) extends SymbolTableEntry
}

class SILMap extends Serializable {
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
      silToSWIRL.put(sil, newSWIRL)
    }
  }
  def combine(other: SILMap): Unit = {
    silToSWIRL.addAll(other.silToSWIRL)
    swirlToSIL.addAll(other.swirlToSIL)
  }
  def nonEmpty(): Boolean = {
    silToSWIRL.nonEmpty || swirlToSIL.nonEmpty
  }
}

object Constants {
  final val pointerField = "value"
  final val globalsSingleton = "Globals_"
  final val exitBlock = "EXIT"
  final val fakeMain = "SWAN_FAKE_MAIN_"
  final val mainPrefix = "main_"
}
