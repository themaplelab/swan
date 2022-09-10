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

package ca.ualberta.maple.swan.parser

import java.io.File

import ca.ualberta.maple.swan.parser.SILFunctionAttribute.specialize.Kind

import scala.collection.mutable.ArrayBuffer

// Only file is used for now
class SILModuleMetadata(val file: File,
                        val platform: String,
                        val target: String,
                        val project: String)

class SILModule(val functions: ArrayBuffer[SILFunction], val witnessTables: ArrayBuffer[SILWitnessTable],
                val vTables: ArrayBuffer[SILVTable], val imports: ArrayBuffer[String],
                val globalVariables: ArrayBuffer[SILGlobalVariable], val scopes: ArrayBuffer[SILScope],
                var properties: ArrayBuffer[SILProperty], val inits: ArrayBuffer[StructInit], val meta: SILModuleMetadata) {
  override def toString: String = {
    meta.file.getName
  }
}

class SILFunction(val linkage: SILLinkage, val attributes: ArrayBuffer[SILFunctionAttribute],
                  val name: SILMangledName, val tpe: SILType, val blocks: ArrayBuffer[SILBlock]) {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + linkage.hashCode
    attributes.foreach(a => result = prime * result + a.hashCode)
    result = prime * result + name.hashCode
    result = prime * result + tpe.hashCode
    blocks.foreach(b => result = prime * result + b.hashCode)
    result
  }
}

class SILBlock(val identifier: String, val arguments: ArrayBuffer[SILArgument],
               val operatorDefs: ArrayBuffer[SILOperatorDef], val terminatorDef: SILTerminatorDef) {
  def ==(that: SILBlock): Boolean = {
    (identifier, arguments, operatorDefs, terminatorDef) == (that.identifier, that.arguments, that.operatorDefs, that.terminatorDef)
  }
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + identifier.hashCode
    arguments.foreach( a => result = prime * result + a.hashCode)
    operatorDefs.foreach( o => result = prime * result + o.hashCode)
    result = prime * result + terminatorDef.hashCode
    result
  }
}

class SILOperatorDef(val res: Option[SILResult], val operator: SILOperator, val sourceInfo: Option[SILSourceInfo]) {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    if (res.nonEmpty) result = prime * result + res.get.hashCode
    result = prime * result + operator.hashCode
    if (sourceInfo.nonEmpty) result = prime * result + sourceInfo.get.hashCode
    result
  }
}

class SILTerminatorDef(val terminator: SILTerminator, val sourceInfo: Option[SILSourceInfo]) {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + terminator.hashCode
    if (sourceInfo.nonEmpty) result = prime * result + sourceInfo.get.hashCode
    result
  }
}

sealed trait SILInstructionDef {
  val instruction : SILInstruction
}
object SILInstructionDef {
  case class operator(operatorDef: SILOperatorDef) extends SILInstructionDef {
    val instruction: SILInstruction = SILInstruction.operator(operatorDef.operator)
  }
  case class terminator(terminatorDef: SILTerminatorDef) extends SILInstructionDef {
    val instruction: SILInstruction = SILInstruction.terminator(terminatorDef.terminator)
  }
}

// NSIP: Not seen in practice
sealed trait SILOperator
object SILOperator {
  /***** ALLOCATION AND DEALLOCATION *****/
  case class allocStack(tpe: SILType, dynamicLifetime: Boolean, lexical: Boolean, moved: Boolean, attributes: ArrayBuffer[SILDebugAttribute]) extends SILOperator
  case class allocRef(attributes: ArrayBuffer[SILAllocAttribute], tailElems: ArrayBuffer[(SILType, SILOperand)], tpe: SILType) extends SILOperator
  case class allocRefDynamic(objc: Boolean, tailElems: ArrayBuffer[(SILType, SILOperand)], operand: SILOperand, tpe: SILType) extends SILOperator
  case class allocBox(tpe: SILType, attributes: ArrayBuffer[SILDebugAttribute]) extends SILOperator
  case class allocValueBuffer(tpe: SILType, operand: SILOperand) extends SILOperator
  case class allocGlobal(name: SILMangledName) extends SILOperator
  case class deallocStack(operand: SILOperand) extends SILOperator
  case class deallocBox(operand: SILOperand) extends SILOperator
  case class projectBox(operand: SILOperand, fieldIndex: Int) extends SILOperator
  case class deallocRef(stack: Boolean, operand: SILOperand) extends SILOperator
  case class deallocPartialRef(operand1: SILOperand, operand2: SILOperand) extends SILOperator
  case class deallocValueBuffer(tpe: SILType, operand: SILOperand) extends SILOperator

  // NSIP: dealloc_value_buffer
  // NSIP: project_value_buffer

  /***** DEBUG INFORMATION *****/
  case class debugValue(operand: SILOperand, attributes: ArrayBuffer[SILDebugAttribute]) extends SILOperator
  case class debugValueAddr(operand: SILOperand, attributes: ArrayBuffer[SILDebugAttribute]) extends SILOperator

  /***** ACCESSING MEMORY *****/
  case class load(kind: Option[SILLoadOwnership], operand: SILOperand) extends SILOperator
  case class store(from: String, kind: Option[SILStoreOwnership], to: SILOperand) extends SILOperator
  // SIL.rst says that load_borrow takes a sil-value, but in reality it takes a sil-operand.
  case class loadBorrow(operand: SILOperand) extends SILOperator
  // begin_borrow has T0D0 in SIL.rst and I think it's NSIP, but tensorflow had parsing for it so use it.
  case class beginBorrow(operand: SILOperand) extends SILOperator
  // NOTE: The SIL.rst for end_borrow is not consistent with in-practice instructions at all.
  case class endBorrow(operand: SILOperand) extends SILOperator
  // Not documented in SIL.rst
  case class endLifetime(operand: SILOperand) extends SILOperator
  // Raw SIL only: assign
  // Raw SIL only: assign_by_wrapper
  // Raw SIL only: mark_uninitialized
  // Raw SIL only: mark_function_escape
  // Raw SIL only: mark_uninitialized_behaviour
  case class copyAddr(take: Boolean, value: String, initialization: Boolean, operand: SILOperand) extends SILOperator
  case class destroyAddr(operand: SILOperand) extends SILOperator
  case class indexAddr(addr: SILOperand, index: SILOperand) extends SILOperator
  // NSIP: tail_addr
  case class indexRawPointer(pointer: SILOperand, offset: SILOperand) extends SILOperator
  case class bindMemory(operand1: SILOperand, operand2: SILOperand, toType: SILType) extends SILOperator
  case class beginAccess(access: SILAccess, enforcement: SILEnforcement, noNestedConflict: Boolean,
                         builtin: Boolean, operand: SILOperand) extends SILOperator
  case class endAccess(abort: Boolean, operand: SILOperand) extends SILOperator
  case class beginUnpairedAccess(access: SILAccess, enforcement: SILEnforcement, noNestedConflict: Boolean,
                         builtin: Boolean, operand: SILOperand, buffer: SILOperand) extends SILOperator
  case class endUnpairedAccess(abort: Boolean, enforcement: SILEnforcement, operand: SILOperand) extends SILOperator


  /***** REFERENCE COUNTING *****/
  case class strongRetain(operand: SILOperand) extends SILOperator
  case class strongRelease(operand: SILOperand) extends SILOperator
  case class setDeallocating(operand: SILOperand) extends SILOperator
  // This is an old (outdated) instruction.
  case class copyUnownedValue(operand: SILOperand) extends SILOperator
  case class strongCopyUnownedValue(operand: SILOperand) extends SILOperator
  case class strongRetainUnowned(operand: SILOperand) extends SILOperator
  case class unownedRetain(operand: SILOperand) extends SILOperator
  case class unownedRelease(operand: SILOperand) extends SILOperator
  case class loadWeak(take: Boolean, operand: SILOperand) extends SILOperator
  case class storeWeak(from: String, initialization: Boolean, to: SILOperand) extends SILOperator
  case class loadUnowned(operand: SILOperand) extends SILOperator
  case class storeBorrow(from: String, to: SILOperand) extends SILOperator
  case class storeUnowned(from: String, initialization: Boolean, to: SILOperand) extends SILOperator
  case class fixLifetime(operand: SILOperand) extends SILOperator
  case class markDependence(operand: SILOperand, on: SILOperand) extends SILOperator
  case class isUnique(operand: SILOperand) extends SILOperator

  case class beginCowMutation(operand: SILOperand, native: Boolean) extends SILOperator
  case class endCowMutation(operand: SILOperand, keepUnique: Boolean) extends SILOperator
  case class isEscapingClosure(operand: SILOperand, objc: Boolean) extends SILOperator
  case class copyBlock(operand: SILOperand) extends SILOperator
  case class copyBlockWithoutEscaping(operand1: SILOperand, operand2: SILOperand) extends SILOperator
  // builtin "unsafeGuaranteed" not sure what to do about this one
  // builtin "unsafeGuaranteedEnd" not sure what to do about this one

  /***** LITERALS *****/
  case class functionRef(name: SILMangledName, tpe: SILType) extends SILOperator
  case class dynamicFunctionRef(name: SILMangledName, tpe: SILType) extends SILOperator
  case class prevDynamicFunctionRef(name: SILMangledName, tpe: SILType) extends SILOperator
  case class globalAddr(name: SILMangledName, tpe: SILType) extends SILOperator
  // NSIP: global_value
  case class integerLiteral(tpe: SILType, value: BigInt) extends SILOperator
  case class floatLiteral(tpe: SILType, value: String) extends SILOperator
  case class stringLiteral(encoding: SILEncoding, value: String) extends SILOperator
  // Skip base_addr_for_offset for now (new instruction)

  /***** DYNAMIC DISPATCH *****/
  // NOTE: All of the dynamic dispatch instructions have a "sil-method-attributes?" component.
  //       It is unclear what this attribute is. I've never seen it used.
  case class classMethod(operand: SILOperand, declRef: SILDeclRef, declType: SILType, tpe: SILType) extends SILOperator
  case class objcMethod(operand: SILOperand, declRef: SILDeclRef, declType: SILType, tpe: SILType) extends SILOperator
  case class superMethod(operand: SILOperand, declRef: SILDeclRef, declType: SILType, tpe: SILType) extends SILOperator
  case class objcSuperMethod(operand: SILOperand, declRef: SILDeclRef, declType: SILType, tpe: SILType) extends SILOperator
  case class witnessMethod(archetype: SILType, declRef: SILDeclRef,
                           declType: SILType, value: Option[SILOperand], tpe: SILType) extends SILOperator

  /***** FUNCTION APPLICATION *****/
  case class apply(nothrow: Boolean, value: String, substitutions: ArrayBuffer[SILType],
                   arguments: ArrayBuffer[String], tpe: SILType) extends SILOperator
  case class beginApply(nothrow: Boolean, value: String, substitutions: ArrayBuffer[SILType],
                        arguments: ArrayBuffer[String], tpe: SILType) extends SILOperator
  case class abortApply(value: String) extends SILOperator
  case class endApply(value: String) extends SILOperator
  case class partialApply(
                           calleeGuaranteed: Boolean, onStack: Boolean, value: String,
                           substitutions: ArrayBuffer[SILType], arguments: ArrayBuffer[String], tpe: SILType
                         ) extends SILOperator
  case class builtin(name: String, templateTpe: Option[SILType], operands: ArrayBuffer[SILOperand], tpe: SILType) extends SILOperator

  /***** METATYPES *****/
  case class metatype(tpe: SILType) extends SILOperator
  case class valueMetatype(tpe: SILType, operand: SILOperand) extends SILOperator
  case class existentialMetatype(tpe: SILType, operand: SILOperand) extends SILOperator
  case class objcProtocol(protocolDecl: SILDeclRef, tpe: SILType) extends SILOperator

  /***** AGGREGATE TYPES *****/
  case class retainValue(operand: SILOperand) extends SILOperator
  case class retainValueAddr(operand: SILOperand) extends SILOperator
  case class unmanagedRetainValue(operand: SILOperand) extends SILOperator
  case class strongCopyUnmanagedValue(operand: SILOperand) extends SILOperator
  case class copyValue(operand: SILOperand) extends SILOperator
  case class releaseValue(operand: SILOperand) extends SILOperator
  case class releaseValueAddr(operand: SILOperand) extends SILOperator
  case class unmanagedReleaseValue(operand: SILOperand) extends SILOperator
  case class destroyValue(operand: SILOperand) extends SILOperator
  case class autoreleaseValue(operand: SILOperand) extends SILOperator
  case class unmanagedAutoreleaseValue(operand: SILOperand) extends SILOperator
  case class tuple(elements: SILTupleElements) extends SILOperator
  case class tupleExtract(operand: SILOperand, declRef: Int) extends SILOperator
  case class tupleElementAddr(operand: SILOperand, declRef: Int) extends SILOperator
  case class destructureTuple(operand: SILOperand) extends SILOperator
  case class struct(tpe: SILType, operands: ArrayBuffer[SILOperand]) extends SILOperator
  case class structExtract(operand: SILOperand, declRef: SILDeclRef) extends SILOperator
  case class structElementAddr(operand: SILOperand, declRef: SILDeclRef) extends SILOperator
  case class destructureStruct(operand: SILOperand) extends SILOperator
  case class objct(tpe: SILType, operands: ArrayBuffer[SILOperand], tailElems: ArrayBuffer[SILOperand]) extends SILOperator
  case class refElementAddr(immutable: Boolean, operand: SILOperand, declRef: SILDeclRef) extends SILOperator
  case class refTailAddr(immutable: Boolean, operand: SILOperand, tpe: SILType) extends SILOperator

  /***** ENUMS *****/
  case class enm(tpe: SILType, declRef: SILDeclRef, operand: Option[SILOperand]) extends SILOperator
  case class uncheckedEnumData(operand: SILOperand, declRef: SILDeclRef) extends SILOperator
  case class initEnumDataAddr(operand: SILOperand, declRef: SILDeclRef) extends SILOperator
  case class injectEnumAddr(operand: SILOperand, declRef: SILDeclRef) extends SILOperator
  case class uncheckedTakeEnumDataAddr(operand: SILOperand, declRef: SILDeclRef) extends SILOperator
  case class selectEnum(operand: SILOperand, cases: ArrayBuffer[SILSwitchEnumCase], tpe: SILType) extends SILOperator
  case class selectEnumAddr(operand: SILOperand, cases: ArrayBuffer[SILSwitchEnumCase], tpe: SILType) extends SILOperator

  /***** PROTOCOL AND PROTOCOL COMPOSITION TYPES *****/
  case class initExistentialAddr(operand: SILOperand, tpe: SILType) extends SILOperator
  // NSIP: init_existential_value
  case class deinitExistentialAddr(operand: SILOperand) extends SILOperator
  // NSIP: deinit_existential_value
  case class openExistentialAddr(access: SILAllowedAccess, operand: SILOperand, tpe: SILType) extends SILOperator
  // NSIP: open_existential_value
  case class initExistentialRef(operand: SILOperand, tpeC: SILType, tpeP: SILType) extends SILOperator
  case class openExistentialRef(operand: SILOperand, tpe: SILType) extends SILOperator
  case class initExistentialMetatype(operand: SILOperand, tpe: SILType) extends SILOperator
  case class openExistentialMetatype(operand: SILOperand, tpe: SILType) extends SILOperator
  case class allocExistentialBox(tpeP: SILType, tpeT: SILType) extends SILOperator
  case class projectExistentialBox(tpe: SILType, operand: SILOperand) extends SILOperator
  case class openExistentialBox(operand: SILOperand, tpe: SILType) extends SILOperator
  // NSIP: open_existential_box_value
  case class deallocExistentialBox(operand: SILOperand, tpe: SILType) extends SILOperator

  /***** BLOCKS *****/
  case class projectBlockStorage(operand: SILOperand) extends SILOperator
  case class initBlockStorageHeader(operand: SILOperand, invokeOperand: String, invokeTpe: SILType, tpe: SILType) extends SILOperator

  /***** UNCHECKED CONVERSIONS *****/
  case class upcast(operand: SILOperand, tpe: SILType) extends SILOperator
  case class addressToPointer(operand: SILOperand, tpe: SILType) extends SILOperator
  case class pointerToAddress(operand: SILOperand, strict: Boolean, tpe: SILType) extends SILOperator
  case class uncheckedRefCast(operand: SILOperand, tpe: SILType) extends SILOperator
  case class uncheckedRefCastAddr(fromTpe: SILType, fromOperand: SILOperand, toTpe: SILType, toOperand: SILOperand) extends SILOperator
  case class uncheckedAddrCast(operand: SILOperand, tpe: SILType) extends SILOperator
  case class uncheckedTrivialBitCast(operand: SILOperand, tpe: SILType) extends SILOperator
  case class uncheckedBitwiseCast(operand: SILOperand, tpe: SILType) extends SILOperator
  case class uncheckedOwnershipConversion(operand: SILOperand, from: SILTypeAttribute, to: SILTypeAttribute) extends SILOperator
  case class refToRawPointer(operand: SILOperand, tpe: SILType) extends SILOperator
  case class rawPointerToRef(operand: SILOperand, tpe: SILType) extends SILOperator
  // SIL.rst: sil-instruction ::= 'ref_to_unowned' sil-operand
  // reality: sil-instruction ::= 'ref_to_unowned' sil-operand 'to' sil-type
  // Same applies to similar instructions
  case class refToUnowned(operand: SILOperand, tpe: SILType) extends SILOperator
  case class unownedToRef(operand: SILOperand, tpe: SILType) extends SILOperator
  case class refToUnmanaged(operand: SILOperand, tpe: SILType) extends SILOperator
  case class unmanagedToRef(operand: SILOperand, tpe: SILType) extends SILOperator
  case class convertFunction(operand: SILOperand, withoutActuallyEscaping: Boolean, tpe: SILType) extends SILOperator
  case class convertEscapeToNoescape(notGuaranteed: Boolean, escaped: Boolean,
                                     operand: SILOperand, tpe: SILType) extends SILOperator
  // NSIP: thin_function_to_pointer
  // NSIP: pointer_to_thin_function
  case class classifyBridgeObject(operand: SILOperand) extends SILOperator
  case class valueToBridgeObject(operand: SILOperand) extends SILOperator
  case class refToBridgeObject(operand1: SILOperand, operand2: SILOperand) extends SILOperator
  case class bridgeObjectToRef(operand: SILOperand, tpe: SILType) extends SILOperator
  case class bridgeObjectToWord(operand: SILOperand, tpe: SILType) extends SILOperator
  case class thinToThickFunction(operand: SILOperand, tpe: SILType) extends SILOperator
  case class thickToObjcMetatype(operand: SILOperand, tpe: SILType) extends SILOperator
  case class objcToThickMetatype(operand: SILOperand, tpe: SILType) extends SILOperator
  case class objcMetatypeToObject(operand: SILOperand, tpe: SILType) extends SILOperator
  case class objcExistentialMetatypeToObject(operand: SILOperand, tpe: SILType) extends SILOperator

  /***** CHECKED CONVERSIONS *****/
  case class unconditionalCheckedCast(operand: SILOperand, tpe: SILType) extends SILOperator
  case class unconditionalCheckedCastAddr(fromTpe: SILType, fromOperand: SILOperand, toType: SILType,
                                          toOperand: SILOperand) extends SILOperator
  // NSIP: unconditional_checked_cast_value

  /***** OTHER *****/

  // Weird undocumented instruction
  case class keypath(tpe: SILType, elements: ArrayBuffer[SILKeypathElement], operands: Option[ArrayBuffer[String]]) extends SILOperator

  /***** RUNTIME FAILURES *****/
  case class condFail(operand: SILOperand, message: Option[String]) extends SILOperator

  // SIL.rst says this is a terminator but it is not.
  case class selectValue(operand: SILOperand, cases: ArrayBuffer[SILSelectValueCase], tpe: SILType) extends SILOperator

}

sealed trait SILTerminator
object SILTerminator {
  case object unreachable extends SILTerminator
  case class ret(operand: SILOperand) extends SILTerminator
  case class thro(operand: SILOperand) extends SILTerminator
  case class yld(operands: ArrayBuffer[SILOperand], resumeLabel: String, unwindLabel: String) extends SILTerminator
  case object unwind extends SILTerminator
  case class br(label: String, operands: ArrayBuffer[SILOperand]) extends SILTerminator
  case class condBr(cond: String,
                    trueLabel: String, trueOperands: ArrayBuffer[SILOperand],
                    falseLabel: String, falseOperands: ArrayBuffer[SILOperand]) extends SILTerminator
  case class switchValue(operand: SILOperand, cases: ArrayBuffer[SILSwitchValueCase]) extends SILTerminator
  case class switchEnum(operand: SILOperand, cases: ArrayBuffer[SILSwitchEnumCase]) extends SILTerminator
  case class switchEnumAddr(operand: SILOperand, cases: ArrayBuffer[SILSwitchEnumCase]) extends SILTerminator
  case class dynamicMethodBr(operand: SILOperand, declRef: SILDeclRef,
                             namedLabel: String, notNamedLabel: String) extends SILTerminator
  case class checkedCastBr(exact: Boolean, operand: SILOperand, tpe: SILType, naked: Boolean,
                           succeedLabel: String, failureLabel: String) extends SILTerminator
  // NSIP: checked_cast_value_br
  case class checkedCastAddrBr(kind: SILCastConsumptionKind, fromTpe: SILType, fromOperand: SILOperand,
                               toTpe: SILType, toOperand: SILOperand, succeedLabel: String, failureLabel: String) extends SILTerminator
  case class tryApply(value: String, substitutions: ArrayBuffer[SILType],
                      arguments: ArrayBuffer[String], tpe: SILType, normalLabel: String, errorLabel: String) extends SILTerminator
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

sealed trait SILAllowedAccess
object SILAllowedAccess {
  case object immutable extends SILAllowedAccess
  case object mutable extends SILAllowedAccess
}

sealed trait SILCastConsumptionKind
object SILCastConsumptionKind {
  case object takeAlways extends SILCastConsumptionKind
  case object takeOnSuccess extends SILCastConsumptionKind
  case object copyOnSuccess extends SILCastConsumptionKind
}

class SILArgument(val valueName: String, val tpe: SILType) {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + valueName.hashCode
    result = prime * result + tpe.hashCode
    result
  }
}

sealed trait SILKeypathElement
object SILKeypathElement {
  case class objc(value: String) extends SILKeypathElement
  case class root(tpe: SILType) extends SILKeypathElement
  case class gettableProperty(tpe: SILType) extends SILKeypathElement
  case class storedProperty(decl: SILDeclRef, tpe: SILType) extends SILKeypathElement
  case class settableProperty(tpe: SILType) extends SILKeypathElement
  case class id(name: Option[SILMangledName], decl: Option[SILDeclRef], tpe: Option[SILType]) extends SILKeypathElement
  case class getter(name: SILMangledName, tpe: SILType) extends SILKeypathElement
  case class setter(name: SILMangledName, tpe: SILType) extends SILKeypathElement
  case class optionalForce(tpe: SILType) extends SILKeypathElement
  case class tupleElement(decl: SILDeclRef, tpe: SILType) extends SILKeypathElement
  case class external(decl: SILType) extends SILKeypathElement
  case class optionalChain(tpe: SILType) extends SILKeypathElement
  case class optionalWrap(tpe: SILType) extends SILKeypathElement
  case class indices(i: ArrayBuffer[(Int, SILType, SILType)]) extends SILKeypathElement
  case class indicesEquals(name: SILMangledName, tpe: SILType) extends SILKeypathElement
  case class indicesHash(name: SILMangledName, tpe: SILType) extends SILKeypathElement
}

sealed trait SILSwitchEnumCase
object SILSwitchEnumCase {
  case class cs(declRef: SILDeclRef, result: String) extends SILSwitchEnumCase
  case class default(result: String) extends SILSwitchEnumCase
}

sealed trait SILSwitchValueCase
object SILSwitchValueCase {
  case class cs(value: String, select: String) extends SILSwitchValueCase
  case class default(select: String) extends SILSwitchValueCase
}

sealed trait SILSelectValueCase
object SILSelectValueCase {
  case class cs(value: String, select: String) extends SILSelectValueCase
  case class default(select: String) extends SILSelectValueCase
}


sealed trait SILConvention
object SILConvention {
  case object c extends SILConvention
  case object method extends SILConvention
  case object thin extends SILConvention
  case object block extends SILConvention
  case class witnessMethod(tpe: SILType) extends SILConvention
  case object objc extends SILConvention
}

sealed trait SILDebugAttribute
object SILDebugAttribute {
  case class argno(index: Int) extends SILDebugAttribute
  case class name(name: String) extends SILDebugAttribute
  case object let extends SILDebugAttribute
  case object variable extends SILDebugAttribute
  case object _implicit extends SILDebugAttribute
}

sealed trait SILAccessorKind
object SILAccessorKind {
  case object get extends SILAccessorKind
  case object set extends SILAccessorKind
  case object willSet extends SILAccessorKind
  case object didSet extends SILAccessorKind
  case object address extends SILAccessorKind
  case object mutableAddress extends SILAccessorKind
  case object read extends SILAccessorKind
  case object modify extends SILAccessorKind
}

// I think that a type can come after, too. Leave for now.
// e.g. #Super.genericMethod!jvp.SUU.<T where T : Differentiable>: <T> (Super) -> (T, T) -> T : @[...]
class SILAutoDiff(val paramIndices: String) {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + paramIndices.hashCode
    result
  }
}
object SILAutoDiff {
  case class jvp(pi: String) extends SILAutoDiff(pi)
  case class vjp(pi: String) extends SILAutoDiff(pi)
}

sealed trait SILDeclKind
object SILDeclKind {
  case object func extends SILDeclKind
  case object allocator extends SILDeclKind
  case object initializer extends SILDeclKind
  case object enumElement extends SILDeclKind
  case object destroyer extends SILDeclKind
  case object deallocator extends SILDeclKind
  case object globalAccessor extends SILDeclKind
  case class defaultArgGenerator(index: String) extends SILDeclKind
  case object storedPropertyInitalizer extends SILDeclKind
  case object ivarInitializer extends SILDeclKind
  case object ivarDestroyer extends SILDeclKind
  case object propertyWrappingBackingInitializer extends SILDeclKind
}

// not sure why "level" exists, but it comes up in practice
sealed trait SILDeclSubRef
object SILDeclSubRef {
  case class part(accessorKind: Option[SILAccessorKind], declKind: SILDeclKind, level: Option[Int],
                  foreign: Boolean, autoDiff: Option[SILAutoDiff]) extends SILDeclSubRef
  case object lang extends SILDeclSubRef
  case class autoDiff(autoDiff: SILAutoDiff) extends SILDeclSubRef
  case class level(level: Int, foreign: Boolean) extends SILDeclSubRef
}

class SILDeclRef(val name: ArrayBuffer[String], val subRef: Option[SILDeclSubRef]) {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    name.foreach(s => result = prime * result + s.hashCode)
    if (subRef.nonEmpty) result = prime * result + subRef.get.hashCode
    result
  }
}

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

class SILMangledName(val mangled: String, var demangled: String = "") {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + mangled.hashCode
    result = prime * result + demangled.hashCode
    result
  }
}

class StructInit(val name: String, val args: ArrayBuffer[String], val tpe: InitType, val regex: Boolean = false) {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + name.hashCode
    args.foreach( a => result = prime * result + a.hashCode)
    result = prime * result + tpe.hashCode
    result
  }
}
object StructInit {
  // Add non-user struct init definitions here
  def populateInits(): ArrayBuffer[StructInit] = {
    val arr = new ArrayBuffer[StructInit]()
    val basicTypes = Array(
      "Double", "Int", "Int8", "Int32", "Int64", "UInt", "UInt8", "UInt32", "UInt64")
    basicTypes.foreach(t => {
      arr.append(new StructInit(t, ArrayBuffer("_value"), InitType.normal))
    })
    arr.append(new StructInit("IndexingIterator<Array<.*>>", ArrayBuffer("_elements", "_position"), InitType.normal, true))
    arr
  }
}

sealed trait InitType
object InitType {
  case object normal extends InitType
  case object objc extends InitType
  case object nonobjc extends InitType
}

sealed trait SILFunctionAttribute
object SILFunctionAttribute {
  case object canonical extends SILFunctionAttribute
  case class differentiable(spec: String) extends SILFunctionAttribute
  case object dynamicallyReplacable extends SILFunctionAttribute
  case object alwaysInline extends SILFunctionAttribute
  case object noInline extends SILFunctionAttribute
  case object globalInitOnceFn extends SILFunctionAttribute
  case object ossa extends SILFunctionAttribute
  case object serialized extends SILFunctionAttribute
  case object serializable extends SILFunctionAttribute
  case object transparent extends SILFunctionAttribute
  sealed trait Thunk extends SILFunctionAttribute
  object Thunk {
    case object thunk extends Thunk
    case object signatureOptimized extends Thunk
    case object reabstraction extends Thunk
  }
  case class dynamicReplacement(func: String) extends SILFunctionAttribute
  case class objcReplacement(func: String) extends SILFunctionAttribute
  case object exactSelfClass extends SILFunctionAttribute
  case object withoutActuallyEscaping extends SILFunctionAttribute
  sealed trait FunctionPurpose extends SILFunctionAttribute
  object FunctionPurpose {
    case object globalInit extends FunctionPurpose
    case object lazyGetter extends FunctionPurpose
  }
  case object weakImported extends SILFunctionAttribute
  // String because version numbers can start with 0
  case class available(version: ArrayBuffer[String]) extends SILFunctionAttribute
  sealed trait FunctionInlining extends SILFunctionAttribute
  object FunctionInlining {
    case object never extends FunctionInlining
    case object always extends FunctionInlining
  }
  sealed trait FunctionOptimization extends SILFunctionAttribute
  object FunctionOptimization {
    case object Onone extends FunctionOptimization
    case object Ospeed extends FunctionOptimization
    case object Osize extends FunctionOptimization
  }
  sealed trait FunctionEffects extends SILFunctionAttribute
  object FunctionEffects {
    case object readonly extends FunctionEffects
    case object readnone extends FunctionEffects
    case object readwrite extends FunctionEffects
    case object releasenone extends FunctionEffects
  }
  case class semantics(value: String) extends SILFunctionAttribute
  case class specialize(exported: Option[Boolean], kind: Option[Kind],
                        reqs: ArrayBuffer[SILTypeRequirement]) extends SILFunctionAttribute
  object specialize {
    sealed trait Kind
    object Kind {
      case object full extends Kind
      case object partial extends Kind
    }
  }
  case class clang(value: String) extends SILFunctionAttribute
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

class SILScope(val num: Int, val loc: Option[SILLoc], val parent: SILScopeParent, val inlinedAt: Option[SILScopeRef]) {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + num.hashCode
    if (loc.nonEmpty) result = prime * result + loc.get.hashCode
    result = prime * result + parent.hashCode
    if (inlinedAt.nonEmpty) result = prime * result + inlinedAt.get.hashCode
    result
  }
}

class SILLoc(val path: String, val line: Int, val column: Int) {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + path.hashCode
    result = prime * result + line.hashCode
    result = prime * result + column.hashCode
    result
  }
}

class SILSourceInfo(val scopeRef: Option[SILScopeRef], val loc: Option[SILLoc]) {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    if (scopeRef.nonEmpty) result = prime * result + scopeRef.get.hashCode
    if (loc.nonEmpty) result = prime * result + loc.get.hashCode
    result
  }
}

sealed trait SILScopeParent
object SILScopeParent {
  case class func(name: SILMangledName, tpe: SILType) extends SILScopeParent
  case class ref(ref: Int) extends SILScopeParent
}

class SILScopeRef(val num: Int) {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + num.hashCode
    result
  }
}

class SILOperand(val value: String, val tpe: SILType) {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + value.hashCode
    result = prime * result + tpe.hashCode
    result
  }
}

class SILResult(val valueNames: ArrayBuffer[String]) {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    valueNames.foreach(v => result = prime * result + v.hashCode)
    result
  }
}

sealed trait SILTupleElements
object SILTupleElements {
  case class labeled(tpe: SILType, values: ArrayBuffer[String]) extends SILTupleElements
  case class unlabeled(operands: ArrayBuffer[SILOperand]) extends SILTupleElements
}

sealed trait SILType
object SILType {
  case class addressType(tpe: SILType) extends SILType
  case class attributedType(attributes: ArrayBuffer[SILTypeAttribute], tpe: SILType) extends SILType
  case object coroutineTokenType extends SILType
  case class functionType(parameters: ArrayBuffer[SILType], optional: Boolean, throws: Boolean, result: SILType) extends SILType
  case class genericType(parameters: ArrayBuffer[String], requirements: ArrayBuffer[SILTypeRequirement], tpe: SILType) extends SILType
  case class namedType(name: String) extends SILType
  case class selectType(tpe: SILType, name: String) extends SILType
  // squareBrackets is a needed space needed to make our test comparisons happy.
  case class namedArgType(name: String, tpe: SILType, squareBrackets: Boolean) extends SILType
  case object selfType extends SILType
  case object selfTypeOptional extends SILType
  case class specializedType(tpe: SILType, arguments: ArrayBuffer[SILType], optional: Int) extends SILType
  case class arrayType(arguments: ArrayBuffer[SILType], nakedStyle: Boolean, optional: Int) extends SILType
  case class tupleType(parameters: ArrayBuffer[SILType], optional: Boolean, dots: Boolean) extends SILType
  case class withOwnership(attribute: SILTypeAttribute, tpe: SILType) extends SILType
  case class varType(tpe: SILType) extends SILType
  case class forType(tpe: SILType, fr: ArrayBuffer[SILType]) extends SILType // -> T for <T>
  case class andType(tpe1: SILType, tpe2: SILType) extends SILType // (T & T)
  case class dotType(tpes: ArrayBuffer[SILType]) extends SILType // (andType).Type

  @throws[Error]
  def parse(silString: String): SILType = {
    val parser = new SILParser(silString)
    parser.parseType()
  }
}

sealed trait SILTypeAttribute
object SILTypeAttribute {
  case object async extends SILTypeAttribute
  case object pseudoGeneric extends SILTypeAttribute
  case object calleeGuaranteed extends SILTypeAttribute
  case object substituted extends SILTypeAttribute
  case class convention(convention: SILConvention) extends SILTypeAttribute
  case object guaranteed extends SILTypeAttribute
  case object inGuaranteed extends SILTypeAttribute
  case object in extends SILTypeAttribute
  case object inout extends SILTypeAttribute
  case object inoutAliasable extends SILTypeAttribute
  case object noescape extends SILTypeAttribute
  case object out extends SILTypeAttribute
  case object unowned extends SILTypeAttribute
  case object unownedInnerPointer extends SILTypeAttribute
  case object owned extends SILTypeAttribute
  case object thick extends SILTypeAttribute
  case object thin extends SILTypeAttribute
  case object yieldOnce extends SILTypeAttribute
  case object yields extends SILTypeAttribute
  case object error extends SILTypeAttribute
  case object objcMetatype extends SILTypeAttribute
  case object silWeak extends SILTypeAttribute
  case object dynamicSelf extends SILTypeAttribute
  case object silUnowned extends SILTypeAttribute
  case object silUnmanaged extends SILTypeAttribute
  case object autoreleased extends SILTypeAttribute
  case object blockStorage extends SILTypeAttribute
  case object escaping extends SILTypeAttribute
  case object autoclosure extends SILTypeAttribute
  case class opaqueReturnTypeOf(value: String, num: Int) extends SILTypeAttribute
  case class opened(value: String) extends SILTypeAttribute
  // type-specifier -> 'inout' | '__owned' | '__unowned'
  // Not in SIL.rst but used in naked types. e.g. "[...] -> (__owned Self) [..]"
  case object typeSpecifierInOut extends SILTypeAttribute
  case object typeSpecifierOwned extends SILTypeAttribute
  case object typeSpecifierUnowned extends SILTypeAttribute
}

sealed trait SILAllocAttribute
object SILAllocAttribute {
  case object objc extends SILAllocAttribute
  case object stack extends SILAllocAttribute
}

// mark_uninitialized kind
sealed trait SILMUKind
object SILMUKind {
  case object varr extends SILMUKind
  case object rootSelf extends SILMUKind
  case object crossModuleRootSelf extends SILMUKind
  case object derivedSelf extends SILMUKind
  case object derivedSelfOnly extends SILMUKind
  case object delegatingSelf extends SILMUKind
  case object delegatingSelfAllocated extends SILMUKind
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
  case object assign extends SILStoreOwnership
}

class SILGlobalVariable(val linkage: SILLinkage, val serialized: Boolean,
                        val let: Boolean, val globalName: SILMangledName,
                        val tpe: SILType, val instructions: Option[ArrayBuffer[SILOperatorDef]]) {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + linkage.hashCode
    result = prime * result + serialized.hashCode
    result = prime * result + let.hashCode
    result = prime * result + globalName.hashCode
    result = prime * result + tpe.hashCode
    if (instructions.nonEmpty) {
      instructions.get.foreach(op => result = prime * result + op.hashCode)
    }
    result
  }
}

class SILWitnessTable(val linkage: SILLinkage, val attribute: Option[SILFunctionAttribute],
                      val normalProtocolConformance: SILNormalProtocolConformance, val entries: ArrayBuffer[SILWitnessEntry]) {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + linkage.hashCode
    if (attribute.nonEmpty) result = prime * result + attribute.get.hashCode
    result = prime * result + normalProtocolConformance.hashCode
    entries.foreach(e => result = prime * result + e.hashCode)
    result
  }
}

class SILProperty(val serialized: Boolean, val decl: SILDeclRef, val component: SILType) {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + serialized.hashCode
    result = prime * result + decl.hashCode
    result = prime * result + component.hashCode
    result
  }
}

class SILVTable(val name: String, val serialized: Boolean, val entries: ArrayBuffer[SILVEntry]) {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + name.hashCode
    result = prime * result + serialized.hashCode
    entries.foreach(e => result = prime * result + e.hashCode)
    result
  }
}

sealed trait SILVTableEntryKind
object SILVTableEntryKind {
  case object normal extends SILVTableEntryKind
  case object inherited extends SILVTableEntryKind
  case object overide extends SILVTableEntryKind
}

class SILVEntry(val declRef: SILDeclRef, val tpe: Option[SILType], val kind: SILVTableEntryKind,
                val nonoverridden: Boolean, val linkage: Option[SILLinkage], val functionName: SILMangledName) {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + declRef.hashCode
    if (tpe.nonEmpty) result = prime * result + tpe.get.hashCode
    result = prime * result + kind.hashCode
    result = prime * result + nonoverridden.hashCode
    if (linkage.nonEmpty) result = prime * result + linkage.get.hashCode
    result = prime * result + functionName.hashCode
    result
  }
}

sealed trait SILWitnessEntry
object SILWitnessEntry {
  case class baseProtocol(identifier: String, pc: SILProtocolConformance) extends SILWitnessEntry
  case class method(declRef: SILDeclRef, declType: SILType, functionName: Option[SILMangledName]) extends SILWitnessEntry
  case class associatedType(identifier0: String, identifier1: String) extends SILWitnessEntry
  case class associatedTypeProtocol(identifier: String) extends SILWitnessEntry
  //case class associatedTypeProtocol(identifier0: String, identifier1: String, pc: SILProtocolConformance) extends SILWitnessEntry
  case class conditionalConformance(identifier: String) extends SILWitnessEntry
}

class SILNormalProtocolConformance(val tpe: SILType, val protocol: String, val module: String) {
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + tpe.hashCode
    result = prime * result + protocol.hashCode
    result = prime * result + module.hashCode
    result
  }
}

sealed trait SILProtocolConformance
object SILProtocolConformance {
  case class normal(pc: SILNormalProtocolConformance) extends SILProtocolConformance
  // Not consistent with SIL.rst.
  case class inherit(tpe: SILType, pc: SILProtocolConformance) extends SILProtocolConformance
  case class specialize(substitutions: ArrayBuffer[SILType], pc: SILProtocolConformance) extends SILProtocolConformance
  case object dependent extends SILProtocolConformance
}