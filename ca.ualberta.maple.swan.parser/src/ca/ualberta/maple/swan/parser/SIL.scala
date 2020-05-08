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

import java.nio.file.Path

import ca.ualberta.maple.swan

class Module(val functions: Array[Function]) {

  object Parse {
    @throws[Error]
    def parsePath(silPath: Path): Module = {
      val parser = new SILParser(silPath)
      parser.parseModule()
    }

    @throws[Error]
    def parseString(silString: String): Module = {
      val parser = new SILParser(silString)
      parser.parseModule()
    }
  }
}

class Function(val linkage: Linkage, val attributes: Array[FunctionAttribute],
               val name: String, val tpe: Type, val blocks: Array[Block])

class Block(val identifier: String, val arguments: Array[Argument],
            val operatorDefs: Array[OperatorDef], val terminatorDef: TerminatorDef) {

  def ==(that: Block): Boolean = {
    (identifier, arguments, operatorDefs, terminatorDef) == (that.identifier, that.arguments, that.operatorDefs, that.terminatorDef)
  }
}

class OperatorDef(val result: Option[Result], val operator: Operator, val sourceInfo: Option[SourceInfo])

class TerminatorDef(val terminator: Terminator, val sourceInfo: Option[SourceInfo])

sealed trait InstructionDef {
  val instruction : Instruction
}
object InstructionDef {
  case class operator(val operatorDef: OperatorDef) extends InstructionDef {
    val instruction: Instruction = Instruction.operator(operatorDef.operator)
  }
  case class terminator(val terminatorDef: TerminatorDef) extends InstructionDef {
    val instruction: Instruction = Instruction.terminator(terminatorDef.terminator)
  }
}

sealed trait Operator
object Operator {
  case class allocStack(tpe: Type, attributes: Array[DebugAttribute]) extends Operator
  case class apply(
                    nothrow: Boolean, value: String,
                    substitutions: Array[Type], arguments: Array[String], tpe: Type
                  ) extends Operator
  case class beginAccess(
                          access: Access, enforcement: Enforcement, noNestedConflict: Boolean, builtin: Boolean,
                          operand: Operand
                        ) extends Operator
  case class beginApply(
                         nothrow: Boolean, value: String,
                         substitutions: Array[Type], arguments: Array[String], tpe: Type
                       ) extends Operator
  case class beginBorrow(operand: Operand) extends Operator
  case class builtin(name: String, operands: Array[Operand], tpe: Type) extends Operator
  case class condFail(operand: Operand, message: String) extends Operator
  case class convertEscapeToNoescape(notGuaranteed: Boolean, escaped: Boolean, operand: Operand, tpe: Type) extends Operator
  case class convertFunction(operand: Operand, withoutActuallyEscaping: Boolean, tpe: Type) extends Operator
  case class copyAddr(take: Boolean, value: String, initialization: Boolean, operand: Operand) extends Operator
  case class copyValue(operand: Operand) extends Operator
  case class deallocStack(operand: Operand) extends Operator
  case class debugValue(operand: Operand, attributes: Array[DebugAttribute]) extends Operator
  case class debugValueAddr(operand: Operand, attributes: Array[DebugAttribute]) extends Operator
  case class destroyValue(operand: Operand) extends Operator
  case class destructureTuple(operand: Operand) extends Operator
  case class endAccess(abort: Boolean, operand: Operand) extends Operator
  case class endApply(value: String) extends Operator
  case class endBorrow(operand: Operand) extends Operator
  case class enum(tpe: Type, declRef: DeclRef, operand: Option[Operand]) extends Operator
  case class floatLiteral(tpe: Type, value: String) extends Operator
  case class functionRef(name: String, tpe: Type) extends Operator
  case class globalAddr(name: String, tpe: Type) extends Operator
  case class indexAddr(addr: Operand, index: Operand) extends Operator
  case class integerLiteral(tpe: Type, value: Int) extends Operator
  case class load(kind: Option[LoadOwnership], operand: Operand) extends Operator
  case class markDependence(operand: Operand, on: Operand) extends Operator
  case class metatype(tpe: Type) extends Operator
  case class partialApply(
                           calleeGuaranteed: Boolean, onStack: Boolean, value: String,
                           substitutions: Array[Type], arguments: Array[String], tpe: Type
                         ) extends Operator
  case class pointerToAddress(operand: Operand, strict: Boolean, tpe: Type) extends Operator
  case class releaseValue(operand: Operand) extends Operator
  case class retainValue(operand: Operand) extends Operator
  case class selectEnum(operand: Operand, cases: Array[Case], tpe: Type) extends Operator
  case class store(value: String, kind: Option[StoreOwnership], operand: Operand) extends Operator
  case class stringLiteral(encoding: Encoding, value: String) extends Operator
  case class strongRelease(operand: Operand) extends Operator
  case class strongRetain(operand: Operand) extends Operator
  case class struct(tpe: Type, operands: Array[Operand]) extends Operator
  case class structElementAddr(operand: Operand, declRef: DeclRef) extends Operator
  case class structExtract(operand: Operand, declRef: DeclRef) extends Operator
  case class thinToThickFunction(operand: Operand, tpe: Type) extends Operator
  case class tuple(elements: TupleElements) extends Operator
  case class tupleExtract(operand: Operand, declRef: Int) extends Operator
  case class unknown(name: String) extends Operator
  case class witnessMethod(archeType: Type, declRef: DeclRef, declType: Type, tpe: Type) extends Operator
}

sealed trait Terminator
object Terminator {
  case class br(label: String, operands: Array[Operand]) extends Terminator
  case class condBr(cond: String,
                    trueLabel: String, trueOperands: Array[Operand],
                    falseLabel: String, falseOperands: Array[Operand]) extends Terminator
  case class ret(operand: Operand) extends Terminator
  case class switchEnum(operand: Operand, cases: Array[Case]) extends Terminator
  case class unknown(name: String) extends Terminator
  case class unreachable() extends Terminator
}

sealed trait Instruction
object Instruction {
  case class operator(op: Operator) extends Instruction
  case class terminator(t: Terminator) extends  Instruction
}

sealed trait Access
object Access {
  case object deinit extends Access
  case object init extends Access
  case object modify extends Access
  case object read extends Access
}

class Argument(val valueName: String, val tpe: Type)

sealed trait Case
object Case {
  case class cs(declRef: DeclRef, result: String) extends Case
  case class default(result: String) extends Case
}

sealed trait Convention
object Convention {
  case object c extends Convention
  case object method extends Convention
  case object thin extends Convention
  case class witnessMethod(tpe: Type) extends Convention
}

sealed trait DebugAttribute
object DebugAttribute {
  case class argno(index: Int) extends DebugAttribute
  case class name(name: String) extends DebugAttribute
  case class let() extends DebugAttribute
  case class variable() extends DebugAttribute
}

sealed trait DeclKind
object DeclKind {
  case object allocator extends DeclKind
  case object deallocator extends DeclKind
  case object destroyer extends DeclKind
  case object enumElement extends DeclKind
  case object getter extends DeclKind
  case object globalAccessor extends DeclKind
  case object initializer extends DeclKind
  case object ivarDestroyer extends DeclKind
  case object ivarInitializer extends DeclKind
  case object setter extends DeclKind
}

class DeclRef(val name: Array[String], val kind: Option[DeclKind], val level: Option[Int])

sealed trait Encoding
object Encoding {
  case object objcSelector extends Encoding
  case object utf8 extends Encoding
  case object utf16 extends Encoding
}

sealed trait Enforcement
object Enforcement {
  case object dynamic extends Enforcement
  case object static extends Enforcement
  case object unknown extends Enforcement
  case object unsafe extends Enforcement
}

sealed trait FunctionAttribute
object FunctionAttribute {
  case object alwaysInline extends FunctionAttribute
  case class differentiable(spec: String) extends FunctionAttribute
  case object dynamicallyReplacable extends FunctionAttribute
  case object noInline extends FunctionAttribute
  case object readonly extends FunctionAttribute
  case class semantics(value: String) extends FunctionAttribute
  case object serialized extends FunctionAttribute
  case object thunk extends FunctionAttribute
  case object transparent extends FunctionAttribute
  case class noncanonical(attr: NoncanonicalFunctionAttribute) extends FunctionAttribute
}

sealed trait NoncanonicalFunctionAttribute
object NoncanonicalFunctionAttribute {
  case object ownershipSSA extends NoncanonicalFunctionAttribute
}

sealed trait Linkage
object Linkage {
  case object hidden extends Linkage
  case object hiddenExternal extends Linkage
  case object priv extends Linkage
  case object privateExternal extends Linkage
  case object public extends Linkage
  case object publicExternal extends Linkage
  case object publicNonABI extends Linkage
  case object shared extends Linkage
  case object sharedExternal extends Linkage
}

class Loc(val path: String, val line: Int, val column: Int)

class Operand(val value: String, val tpe: Type)

class Result(val valueNames: Array[String])

class SourceInfo(val scopeRef: Option[Int], val loc: Option[Loc])

sealed trait TupleElements
object TupleElements {
  case class labeled(tpe: Type, values: Array[String]) extends TupleElements
  case class unlabeled(operands: Array[Operand]) extends TupleElements
}

sealed trait Type
object Type {
  case class addressType(tpe: Type) extends Type
  case class attributedType(attributes: Array[TypeAttribute], tpe: Type) extends Type
  case class coroutineTokenType() extends Type
  case class functionType(parameters: Array[Type], result: Type) extends Type
  case class genericType(parameters: Array[String], requirements: Array[TypeRequirement], tpe: Type) extends Type
  case class namedType(name: String) extends Type
  case class selectType(tpe: Type, name: String) extends Type
  case class selfType() extends Type
  case class specializedType(tpe: Type, arguments: Array[Type]) extends Type
  case class tupleType(parameters: Array[Type]) extends Type
  case class withOwnership(attribute: TypeAttribute, tpe: Type) extends Type

  @throws[Error]
  def parse(silString: String): Type = {
    val parser = new SILParser(silString)
    parser.parseType()
  }
}

sealed trait TypeAttribute
object TypeAttribute {
  case class calleeGuaranteed() extends TypeAttribute
  case class convention(convention: Convention) extends TypeAttribute
  case class guaranteed() extends TypeAttribute
  case class inGuaranteed() extends TypeAttribute
  case class in() extends TypeAttribute
  case class inout() extends TypeAttribute
  case class noescape() extends TypeAttribute
  case class out() extends TypeAttribute
  case class owned() extends TypeAttribute
  case class thick() extends TypeAttribute
  case class thin() extends TypeAttribute
  case class yieldOnce() extends TypeAttribute
  case class yields() extends TypeAttribute
}

sealed trait TypeRequirement
object TypeRequirement {
  case class conformance(lhs: Type, rhs: Type) extends TypeRequirement
  case class equality(lhs: Type, rhs: Type) extends TypeRequirement
}

sealed trait LoadOwnership
object LoadOwnership{
  case class copy() extends LoadOwnership
  case class take() extends LoadOwnership
  case class trivial() extends LoadOwnership
}

sealed trait StoreOwnership
object StoreOwnership {
  case class init() extends StoreOwnership
  case class trivial() extends StoreOwnership
}

