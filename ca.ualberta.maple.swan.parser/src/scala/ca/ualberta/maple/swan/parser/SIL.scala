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

class SILModule(val functions: Array[SILFunction]) {

  object Parse {
    @throws[Error]
    def parsePath(silPath: Path): SILModule = {
      val parser = new SILParser(silPath)
      parser.parseModule()
    }

    @throws[Error]
    def parseString(silString: String): SILModule = {
      val parser = new SILParser(silString)
      parser.parseModule()
    }
  }
}

class SILFunction(val linkage: SILLinkage, val attributes: Array[SILFunctionAttribute],
                  val name: String, val tpe: SILType, val blocks: Array[SILBlock])

class SILBlock(val identifier: String, val arguments: Array[SILArgument],
               val operatorDefs: Array[SILOperatorDef], val terminatorDef: SILTerminatorDef) {

  def ==(that: SILBlock): Boolean = {
    (identifier, arguments, operatorDefs, terminatorDef) == (that.identifier, that.arguments, that.operatorDefs, that.terminatorDef)
  }
}

class SILOperatorDef(val result: Option[SILResult], val operator: SILOperator, val sourceInfo: Option[SILSourceInfo])

class SILTerminatorDef(val terminator: SILTerminator, val sourceInfo: Option[SILSourceInfo])

sealed trait SILInstructionDef {
  val instruction : SILInstruction
}
// TODO: I don't really like this operator/terminator duality. It doesn't make much sense
//  and it isn't precise.
//  We should divide the instructions into the same categories that are in SIL.rst.
object SILInstructionDef {
  case class operator(val operatorDef: SILOperatorDef) extends SILInstructionDef {
    val instruction: SILInstruction = SILInstruction.operator(operatorDef.operator)
  }
  case class terminator(val terminatorDef: SILTerminatorDef) extends SILInstructionDef {
    val instruction: SILInstruction = SILInstruction.terminator(terminatorDef.terminator)
  }
}

sealed trait SILOperator
object SILOperator {
  case class allocStack(tpe: SILType, attributes: Array[SILDebugAttribute]) extends SILOperator
  case class allocBox(tpe: SILType, attributes: Array[SILDebugAttribute]) extends SILOperator
  case class allocGlobal(name: String) extends SILOperator
  case class apply(
                    nothrow: Boolean, value: String,
                    substitutions: Array[SILType], arguments: Array[String], tpe: SILType
                  ) extends SILOperator
  case class beginAccess(
                          access: SILAccess, enforcement: SILEnforcement, noNestedConflict: Boolean, builtin: Boolean,
                          operand: SILOperand
                        ) extends SILOperator
  case class beginApply(
                         nothrow: Boolean, value: String,
                         substitutions: Array[SILType], arguments: Array[String], tpe: SILType
                       ) extends SILOperator
  case class beginBorrow(operand: SILOperand) extends SILOperator
  case class builtin(name: String, operands: Array[SILOperand], tpe: SILType) extends SILOperator
  case class condFail(operand: SILOperand, message: Option[String]) extends SILOperator
  case class convertEscapeToNoescape(notGuaranteed: Boolean, escaped: Boolean, operand: SILOperand, tpe: SILType) extends SILOperator
  case class convertFunction(operand: SILOperand, withoutActuallyEscaping: Boolean, tpe: SILType) extends SILOperator
  case class copyAddr(take: Boolean, value: String, initialization: Boolean, operand: SILOperand) extends SILOperator
  case class copyValue(operand: SILOperand) extends SILOperator
  case class deallocStack(operand: SILOperand) extends SILOperator
  case class deallocBox(operand: SILOperand) extends SILOperator
  case class projectBox(operand: SILOperand) extends SILOperator
  case class debugValue(operand: SILOperand, attributes: Array[SILDebugAttribute]) extends SILOperator
  case class debugValueAddr(operand: SILOperand, attributes: Array[SILDebugAttribute]) extends SILOperator
  case class destroyAddr(operand: SILOperand) extends SILOperator
  case class destroyValue(operand: SILOperand) extends SILOperator
  case class destructureTuple(operand: SILOperand) extends SILOperator
  case class endAccess(abort: Boolean, operand: SILOperand) extends SILOperator
  case class endApply(value: String) extends SILOperator
  case class abortApply(value: String) extends SILOperator
  case class endBorrow(operand: SILOperand) extends SILOperator
  case class enm(tpe: SILType, declRef: SILDeclRef, operand: Option[SILOperand]) extends SILOperator
  case class floatLiteral(tpe: SILType, value: String) extends SILOperator
  case class functionRef(name: String, tpe: SILType) extends SILOperator
  case class globalAddr(name: String, tpe: SILType) extends SILOperator
  case class indexAddr(addr: SILOperand, index: SILOperand) extends SILOperator
  case class integerLiteral(tpe: SILType, value: Int) extends SILOperator
  case class load(kind: Option[SILLoadOwnership], operand: SILOperand) extends SILOperator
  case class loadWeak(take: Boolean, operand: SILOperand) extends SILOperator
  case class storeWeak(value: String, initialization: Boolean, operand: SILOperand) extends SILOperator
  case class markDependence(operand: SILOperand, on: SILOperand) extends SILOperator
  case class metatype(tpe: SILType) extends SILOperator
  case class partialApply(
                           calleeGuaranteed: Boolean, onStack: Boolean, value: String,
                           substitutions: Array[SILType], arguments: Array[String], tpe: SILType
                         ) extends SILOperator
  case class pointerToAddress(operand: SILOperand, strict: Boolean, tpe: SILType) extends SILOperator
  case class releaseValue(operand: SILOperand) extends SILOperator
  case class retainValue(operand: SILOperand) extends SILOperator
  case class selectEnum(operand: SILOperand, cases: Array[SILCase], tpe: SILType) extends SILOperator
  case class store(value: String, kind: Option[SILStoreOwnership], operand: SILOperand) extends SILOperator
  case class stringLiteral(encoding: SILEncoding, value: String) extends SILOperator
  case class strongRelease(operand: SILOperand) extends SILOperator
  case class strongRetain(operand: SILOperand) extends SILOperator
  case class struct(tpe: SILType, operands: Array[SILOperand]) extends SILOperator
  case class structElementAddr(operand: SILOperand, declRef: SILDeclRef) extends SILOperator
  case class structExtract(operand: SILOperand, declRef: SILDeclRef) extends SILOperator
  case class thinToThickFunction(operand: SILOperand, tpe: SILType) extends SILOperator
  case class tuple(elements: SILTupleElements) extends SILOperator
  case class tupleExtract(operand: SILOperand, declRef: Int) extends SILOperator
  case class unknown(name: String) extends SILOperator
  case class witnessMethod(archeType: SILType, declRef: SILDeclRef, declType: SILType, tpe: SILType) extends SILOperator
  case class initExistentialMetatype(operand: SILOperand, tpe: SILType) extends SILOperator
  case class openExistentialMetatype(operand: SILOperand, tpe: SILType) extends SILOperator
  case class allocExistentialBox(tpeP: SILType, tpeT: SILType) extends SILOperator
}

sealed trait SILTerminator
object SILTerminator {
  case class br(label: String, operands: Array[SILOperand]) extends SILTerminator
  case class condBr(cond: String,
                    trueLabel: String, trueOperands: Array[SILOperand],
                    falseLabel: String, falseOperands: Array[SILOperand]) extends SILTerminator
  case class ret(operand: SILOperand) extends SILTerminator
  case class thro(operand: SILOperand) extends SILTerminator
  case object unwind extends SILTerminator
  case class switchEnum(operand: SILOperand, cases: Array[SILCase]) extends SILTerminator
  case class switchEnumAddr(operand: SILOperand, cases: Array[SILCase]) extends SILTerminator
  case class unknown(name: String) extends SILTerminator
  case object unreachable extends SILTerminator
}

sealed trait SILInstruction
object SILInstruction {
  case class operator(op: SILOperator) extends SILInstruction
  case class terminator(t: SILTerminator) extends  SILInstruction
}

sealed trait SILAccess
object SILAccess {
  case object deinit extends SILAccess
  case object init extends SILAccess
  case object modify extends SILAccess
  case object read extends SILAccess
}

class SILArgument(val valueName: String, val tpe: SILType)

sealed trait SILCase
object SILCase {
  case class cs(declRef: SILDeclRef, result: String) extends SILCase
  case class default(result: String) extends SILCase
}

sealed trait SILConvention
object SILConvention {
  case object c extends SILConvention
  case object method extends SILConvention
  case object thin extends SILConvention
  case object block extends SILConvention
  case class witnessMethod(tpe: SILType) extends SILConvention
}

sealed trait SILDebugAttribute
object SILDebugAttribute {
  case class argno(index: Int) extends SILDebugAttribute
  case class name(name: String) extends SILDebugAttribute
  case object let extends SILDebugAttribute
  case object variable extends SILDebugAttribute
}

sealed trait SILDeclKind
object SILDeclKind {
  case object allocator extends SILDeclKind
  case object deallocator extends SILDeclKind
  case object destroyer extends SILDeclKind
  case object enumElement extends SILDeclKind
  case object getter extends SILDeclKind
  case object globalAccessor extends SILDeclKind
  case object initializer extends SILDeclKind
  case object ivarDestroyer extends SILDeclKind
  case object ivarInitializer extends SILDeclKind
  case object setter extends SILDeclKind
}

class SILDeclRef(val name: Array[String], val kind: Option[SILDeclKind], val level: Option[Int])

sealed trait SILEncoding
object SILEncoding {
  case object objcSelector extends SILEncoding
  case object utf8 extends SILEncoding
  case object utf16 extends SILEncoding
}

sealed trait SILEnforcement
object SILEnforcement {
  case object dynamic extends SILEnforcement
  case object static extends SILEnforcement
  case object unknown extends SILEnforcement
  case object unsafe extends SILEnforcement
}

sealed trait SILFunctionAttribute
object SILFunctionAttribute {
  case object alwaysInline extends SILFunctionAttribute
  case class differentiable(spec: String) extends SILFunctionAttribute
  case object dynamicallyReplacable extends SILFunctionAttribute
  case object noInline extends SILFunctionAttribute
  case object readonly extends SILFunctionAttribute
  case class semantics(value: String) extends SILFunctionAttribute
  case object serialized extends SILFunctionAttribute
  case object thunk extends SILFunctionAttribute
  case object transparent extends SILFunctionAttribute
  case class noncanonical(attr: SILNoncanonicalFunctionAttribute) extends SILFunctionAttribute
}

sealed trait SILNoncanonicalFunctionAttribute
object SILNoncanonicalFunctionAttribute {
  case object ownershipSSA extends SILNoncanonicalFunctionAttribute
}

sealed trait SILLinkage
object SILLinkage {
  case object hidden extends SILLinkage
  case object hiddenExternal extends SILLinkage
  case object priv extends SILLinkage
  case object privateExternal extends SILLinkage
  case object public extends SILLinkage
  case object publicExternal extends SILLinkage
  case object publicNonABI extends SILLinkage
  case object shared extends SILLinkage
  case object sharedExternal extends SILLinkage
}

class SILLoc(val path: String, val line: Int, val column: Int)

class SILOperand(val value: String, val tpe: SILType)

class SILResult(val valueNames: Array[String])

class SILSourceInfo(val scopeRef: Option[Int], val loc: Option[SILLoc])

sealed trait SILTupleElements
object SILTupleElements {
  case class labeled(tpe: SILType, values: Array[String]) extends SILTupleElements
  case class unlabeled(operands: Array[SILOperand]) extends SILTupleElements
}

sealed trait SILType
object SILType {
  case class addressType(tpe: SILType) extends SILType
  case class attributedType(attributes: Array[SILTypeAttribute], tpe: SILType) extends SILType
  case object coroutineTokenType extends SILType
  case class functionType(parameters: Array[SILType], result: SILType) extends SILType
  case class genericType(parameters: Array[String], requirements: Array[SILTypeRequirement], tpe: SILType) extends SILType
  case class namedType(name: String) extends SILType
  case class selectType(tpe: SILType, name: String) extends SILType
  case object selfType extends SILType
  case class specializedType(tpe: SILType, arguments: Array[SILType]) extends SILType
  case class tupleType(parameters: Array[SILType]) extends SILType
  case class withOwnership(attribute: SILTypeAttribute, tpe: SILType) extends SILType

  @throws[Error]
  def parse(silString: String): SILType = {
    val parser = new SILParser(silString)
    parser.parseType()
  }
}

sealed trait SILTypeAttribute
object SILTypeAttribute {
  case object calleeGuaranteed extends SILTypeAttribute
  case class convention(convention: SILConvention) extends SILTypeAttribute
  case object guaranteed extends SILTypeAttribute
  case object inGuaranteed extends SILTypeAttribute
  case object in extends SILTypeAttribute
  case object inout extends SILTypeAttribute
  case object noescape extends SILTypeAttribute
  case object out extends SILTypeAttribute
  case object owned extends SILTypeAttribute
  case object thick extends SILTypeAttribute
  case object thin extends SILTypeAttribute
  case object yieldOnce extends SILTypeAttribute
  case object yields extends SILTypeAttribute
  case object error extends SILTypeAttribute
  case object objcMetatype extends SILTypeAttribute
  case object silWeak extends SILTypeAttribute
}

sealed trait SILTypeRequirement
object SILTypeRequirement {
  case class conformance(lhs: SILType, rhs: SILType) extends SILTypeRequirement
  case class equality(lhs: SILType, rhs: SILType) extends SILTypeRequirement
}

sealed trait SILLoadOwnership
object SILLoadOwnership{
  case object copy extends SILLoadOwnership
  case object take extends SILLoadOwnership
  case object trivial extends SILLoadOwnership
}

sealed trait SILStoreOwnership
object SILStoreOwnership {
  case object init extends SILStoreOwnership
  case object trivial extends SILStoreOwnership
}

