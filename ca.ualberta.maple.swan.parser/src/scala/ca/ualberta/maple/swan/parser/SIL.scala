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

import ca.ualberta.maple.swan.parser.SILFunctionAttribute.specialize.Kind

import scala.collection.mutable.ArrayBuffer
import sys.process._

class SILModule(val functions: Array[SILFunction], val witnessTables: Array[SILWitnessTable],
                val vTables: Array[SILVTable], val imports: Array[String],
                val globalVariables: Array[SILGlobalVariable], val scopes: Array[SILScope],
                var properties: Array[SILProperty], val inits: Array[StructInit]) {

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
                  val name: SILMangledName, val tpe: SILType, val blocks: Array[SILBlock])

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
  case class allocStack(tpe: SILType, attributes: Array[SILDebugAttribute]) extends SILOperator
  case class allocRef(attributes: Array[SILAllocAttribute], tailElems: Array[(SILType, SILOperand)], tpe: SILType) extends SILOperator
  case class allocRefDynamic(objc: Boolean, tailElems: Array[(SILType, SILOperand)], operand: SILOperand, tpe: SILType) extends SILOperator
  case class allocBox(tpe: SILType, attributes: Array[SILDebugAttribute]) extends SILOperator
  case class allocValueBuffer(tpe: SILType, operand: SILOperand) extends SILOperator
  case class allocGlobal(name: SILMangledName) extends SILOperator
  case class deallocStack(operand: SILOperand) extends SILOperator
  case class deallocBox(operand: SILOperand, tpe: SILType) extends SILOperator
  case class projectBox(operand: SILOperand, fieldIndex: Int) extends SILOperator
  case class deallocRef(stack: Boolean, operand: SILOperand) extends SILOperator
  case class deallocPartialRef(operand1: SILOperand, operand2: SILOperand) extends SILOperator
  // NSIP: dealloc_value_buffer
  // NSIP: project_value_buffer

  /***** DEBUG INFORMATION *****/
  case class debugValue(operand: SILOperand, attributes: Array[SILDebugAttribute]) extends SILOperator
  case class debugValueAddr(operand: SILOperand, attributes: Array[SILDebugAttribute]) extends SILOperator

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
  // NSIP: index_raw_pointer
  // NSIP: bind_memory
  case class beginAccess(access: SILAccess, enforcement: SILEnforcement, noNestedConflict: Boolean,
                         builtin: Boolean, operand: SILOperand) extends SILOperator
  case class endAccess(abort: Boolean, operand: SILOperand) extends SILOperator
  // NSIP: begin_unpaired_access
  // NSIP: end_unpaired_access

  /***** REFERENCE COUNTING *****/
  case class strongRetain(operand: SILOperand) extends SILOperator
  case class strongRelease(operand: SILOperand) extends SILOperator
  // NSIP: set_deallocating
  // This is an old (outdated) instruction.
  case class copyUnownedValue(operand: SILOperand) extends SILOperator
  // NSIP: strong_copy_unowned_value
  // NSIP: strong_retain_unowned
  case class unownedRetain(operand: SILOperand) extends SILOperator
  // NSIP: unowned_release
  case class loadWeak(take: Boolean, operand: SILOperand) extends SILOperator
  case class storeWeak(from: String, initialization: Boolean, to: SILOperand) extends SILOperator
  case class loadUnowned(operand: SILOperand) extends SILOperator
  case class storeUnowned(from: String, initialization: Boolean, to: SILOperand) extends SILOperator
  // NSIP: fix_lifetime
  case class markDependence(operand: SILOperand, on: SILOperand) extends SILOperator
  // NSIP: is_unique
  // Skip begin_cow_mutation and end_cow_mutation for now (new instructions)
  case class isEscapingClosure(operand: SILOperand) extends SILOperator
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
  // NSIP: super_method
  case class objcSuperMethod(operand: SILOperand, declRef: SILDeclRef, declType: SILType, tpe: SILType) extends SILOperator
  case class witnessMethod(archetype: SILType, declRef: SILDeclRef,
                           declType: SILType, value: Option[SILOperand], tpe: SILType) extends SILOperator

  /***** FUNCTION APPLICATION *****/
  case class apply(nothrow: Boolean, value: String, substitutions: Array[SILType],
                   arguments: Array[String], tpe: SILType) extends SILOperator
  case class beginApply(nothrow: Boolean, value: String, substitutions: Array[SILType],
                        arguments: Array[String], tpe: SILType) extends SILOperator
  case class abortApply(value: String) extends SILOperator
  case class endApply(value: String) extends SILOperator
  case class partialApply(
                           calleeGuaranteed: Boolean, onStack: Boolean, value: String,
                           substitutions: Array[SILType], arguments: Array[String], tpe: SILType
                         ) extends SILOperator
  case class builtin(name: String, templateTpe: Option[SILType], operands: Array[SILOperand], tpe: SILType) extends SILOperator

  /***** METATYPES *****/
  case class metatype(tpe: SILType) extends SILOperator
  case class valueMetatype(tpe: SILType, operand: SILOperand) extends SILOperator
  case class existentialMetatype(tpe: SILType, operand: SILOperand) extends SILOperator
  case class objcProtocol(protocolDecl: SILDeclRef, tpe: SILType) extends SILOperator

  /***** AGGREGATE TYPES *****/
  case class retainValue(operand: SILOperand) extends SILOperator
  // NSIP: retain_value_addr
  // NSIP: unmanaged_retain_value
  // Skip strong_copy_unmanaged_value for now (new instruction?).
  case class copyValue(operand: SILOperand) extends SILOperator
  case class releaseValue(operand: SILOperand) extends SILOperator
  // NSIP: release_value_addr
  // NSIP: unmanaged_release_value
  case class destroyValue(operand: SILOperand) extends SILOperator
  case class autoreleaseValue(operand: SILOperand) extends SILOperator
  case class tuple(elements: SILTupleElements) extends SILOperator
  case class tupleExtract(operand: SILOperand, declRef: Int) extends SILOperator
  case class tupleElementAddr(operand: SILOperand, declRef: Int) extends SILOperator
  case class destructureTuple(operand: SILOperand) extends SILOperator
  case class struct(tpe: SILType, operands: Array[SILOperand]) extends SILOperator
  case class structExtract(operand: SILOperand, declRef: SILDeclRef) extends SILOperator
  case class structElementAddr(operand: SILOperand, declRef: SILDeclRef) extends SILOperator
  // NSIP: destructure_struct
  case class objct(tpe: SILType, operands: Array[SILOperand], tailElems: Array[SILOperand]) extends SILOperator
  case class refElementAddr(immutable: Boolean, operand: SILOperand, declRef: SILDeclRef) extends SILOperator
  case class refTailAddr(immutable: Boolean, operand: SILOperand, tpe: SILType) extends SILOperator

  /***** ENUMS *****/
  case class enm(tpe: SILType, declRef: SILDeclRef, operand: Option[SILOperand]) extends SILOperator
  case class uncheckedEnumData(operand: SILOperand, declRef: SILDeclRef) extends SILOperator
  case class initEnumDataAddr(operand: SILOperand, declRef: SILDeclRef) extends SILOperator
  case class injectEnumAddr(operand: SILOperand, declRef: SILDeclRef) extends SILOperator
  case class uncheckedTakeEnumDataAddr(operand: SILOperand, declRef: SILDeclRef) extends SILOperator
  case class selectEnum(operand: SILOperand, cases: Array[SILSwitchEnumCase], tpe: SILType) extends SILOperator
  case class selectEnumAddr(operand: SILOperand, cases: Array[SILSwitchEnumCase], tpe: SILType) extends SILOperator

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
  case class initBlockStorageHeader(operand: SILOperand, invoke: SILOperand, tpe: SILType) extends SILOperator

  /***** UNCHECKED CONVERSIONS *****/
  case class upcast(operand: SILOperand, tpe: SILType) extends SILOperator
  case class addressToPointer(operand: SILOperand, tpe: SILType) extends SILOperator
  case class pointerToAddress(operand: SILOperand, strict: Boolean, tpe: SILType) extends SILOperator
  case class uncheckedRefCast(operand: SILOperand, tpe: SILType) extends SILOperator
  // NSIP: unchecked_ref_cast_addr
  case class uncheckedAddrCast(operand: SILOperand, tpe: SILType) extends SILOperator
  case class uncheckedTrivialBitCast(operand: SILOperand, tpe: SILType) extends SILOperator
  // NSIP: unchecked_bitwise_cast
  case class uncheckedOwnershipConversion(operand: SILOperand, from: SILTypeAttribute, to: SILTypeAttribute) extends SILOperator
  // NSIP: ref_to_raw_pointer
  case class rawPointerToRef(operand: SILOperand, tpe: SILType) extends SILOperator
  // SIL.rst: sil-instruction ::= 'ref_to_unowned' sil-operand
  // reality: sil-instruction ::= 'ref_to_unowned' sil-operand 'to' sil-type
  case class refToUnowned(operand: SILOperand, tpe: SILType) extends SILOperator
  // NSIP: unowned_to_ref
  case class refToUnmanaged(operand: SILOperand, tpe: SILType) extends SILOperator
  case class unmanagedToRef(operand: SILOperand, tpe: SILType) extends SILOperator
  case class convertFunction(operand: SILOperand, withoutActuallyEscaping: Boolean, tpe: SILType) extends SILOperator
  case class convertEscapeToNoescape(notGuaranteed: Boolean, escaped: Boolean,
                                     operand: SILOperand, tpe: SILType) extends SILOperator
  // NSIP: thin_function_to_pointer
  // NSIP: pointer_to_thin_function
  // NSIP: classify_bridge_object
  case class valueToBridgeObject(operand: SILOperand) extends SILOperator
  // NSIP: ref_to_bridge_object
  case class bridgeObjectToRef(operand: SILOperand, tpe: SILType) extends SILOperator
  // NSIP: bridge_object_to_word
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

  /***** RUNTIME FAILURES *****/
  case class condFail(operand: SILOperand, message: Option[String]) extends SILOperator
}

sealed trait SILTerminator
object SILTerminator {
  case object unreachable extends SILTerminator
  case class ret(operand: SILOperand) extends SILTerminator
  case class thro(operand: SILOperand) extends SILTerminator
  case class yld(operands: Array[SILOperand], resumeLabel: String, unwindLabel: String) extends SILTerminator
  case object unwind extends SILTerminator
  case class br(label: String, operands: Array[SILOperand]) extends SILTerminator
  case class condBr(cond: String,
                    trueLabel: String, trueOperands: Array[SILOperand],
                    falseLabel: String, falseOperands: Array[SILOperand]) extends SILTerminator
  case class switchValue(operand: SILOperand, cases: Array[SILSwitchValueCase]) extends SILTerminator
  case class switchEnum(operand: SILOperand, cases: Array[SILSwitchEnumCase]) extends SILTerminator
  case class switchEnumAddr(operand: SILOperand, cases: Array[SILSwitchEnumCase]) extends SILTerminator
  case class dynamicMethodBr(operand: SILOperand, declRef: SILDeclRef,
                             namedLabel: String, notNamedLabel: String) extends SILTerminator
  case class checkedCastBr(exact: Boolean, operand: SILOperand, tpe: SILType,
                           succeedLabel: String, failureLabel: String) extends SILTerminator
  // NSIP: checked_cast_value_br
  case class checkedCastAddrBr(kind: SILCastConsumptionKind, fromTpe: SILType, fromOperand: SILOperand,
                               toTpe: SILType, toOperand: SILOperand, succeedLabel: String, failureLabel: String) extends SILTerminator
  case class tryApply(value: String, substitutions: Array[SILType],
                      arguments: Array[String], tpe: SILType, normalLabel: String, errorLabel: String) extends SILTerminator
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

class SILArgument(val valueName: String, val tpe: SILType)

sealed trait SILSwitchEnumCase
object SILSwitchEnumCase {
  case class cs(declRef: SILDeclRef, result: String) extends SILSwitchEnumCase
  case class default(result: String) extends SILSwitchEnumCase
}

sealed trait SILSwitchValueCase
object SILSwitchValueCase {
  case class cs(value: String, label: String) extends SILSwitchValueCase
  case class default(label: String) extends SILSwitchValueCase
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
class SILAutoDiff(val paramIndices: String)
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
  case class defaultArgGenerator(val index: String) extends SILDeclKind
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

class SILDeclRef(val name: Array[String], val subRef: Option[SILDeclSubRef])

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

class SILMangledName(val mangled: String) {
  val demangled: String = {
    // TODO: ship demangler with SWAN
    ("/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/swift-demangle -compact \'" + mangled + '\'').!!.replaceAll(System.lineSeparator(), "")
  }
}

class StructInit(val name: String, val args: Array[String], val tpe: InitType)
object StructInit {
  // Add non-user struct init definitions here
  def populateInits(): Array[StructInit] = {
    val arr = new ArrayBuffer[StructInit]()
    val basicTypes = Array(
      "Double", "Int", "Int8", "Int32", "Int64", "UInt", "UInt8", "UInt32", "UInt64")
    basicTypes.foreach(t => {
      arr.append(new StructInit(t, Array("_value"), InitType.normal))
    })
    arr.toArray
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
  case class differentiable(val spec: String) extends SILFunctionAttribute
  case object dynamicallyReplacable extends SILFunctionAttribute
  case object alwaysInline extends SILFunctionAttribute
  case object noInline extends SILFunctionAttribute
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
  case class available(version: Array[String]) extends SILFunctionAttribute
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
                        reqs: Array[SILTypeRequirement]) extends SILFunctionAttribute
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

class SILScope(val num: Int, val loc: Option[SILLoc], val parent: SILScopeParent, val inlinedAt: Option[SILScopeRef])

class SILLoc(val path: String, val line: Int, val column: Int)

class SILSourceInfo(val scopeRef: Option[SILScopeRef], val loc: Option[SILLoc])

sealed trait SILScopeParent
object SILScopeParent {
  case class func(name: SILMangledName, val tpe: SILType) extends SILScopeParent
  case class ref(ref: Int) extends SILScopeParent
}

class SILScopeRef(val num: Int)

class SILOperand(val value: String, val tpe: SILType)

class SILResult(val valueNames: Array[String])

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
  case class functionType(parameters: Array[SILType], optional: Boolean, throws: Boolean, result: SILType) extends SILType
  case class genericType(parameters: Array[String], requirements: Array[SILTypeRequirement], tpe: SILType) extends SILType
  case class namedType(name: String) extends SILType
  case class selectType(tpe: SILType, name: String) extends SILType
  // squareBrackets is a needed space needed to make our test comparisons happy.
  case class namedArgType(name: String, tpe: SILType, squareBrackets: Boolean) extends SILType
  case object selfType extends SILType
  case object selfTypeOptional extends SILType
  case class specializedType(tpe: SILType, arguments: Array[SILType], optional: Boolean) extends SILType
  case class arrayType(arguments: Array[SILType], nakedStyle: Boolean, optional: Boolean) extends SILType
  case class tupleType(parameters: Array[SILType], optional: Boolean) extends SILType
  case class withOwnership(attribute: SILTypeAttribute, tpe: SILType) extends SILType
  case class varType(tpe: SILType) extends SILType

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
  case object inoutAliasable extends SILTypeAttribute
  case object noescape extends SILTypeAttribute
  case object out extends SILTypeAttribute
  case object unowned extends SILTypeAttribute
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
  case class opened(val value: String) extends SILTypeAttribute
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
                        val tpe: SILType, val instructions: Option[Array[SILOperatorDef]])

class SILWitnessTable(val linkage: SILLinkage, val attribute: Option[SILFunctionAttribute],
                      val normalProtocolConformance: SILNormalProtocolConformance, val entries: Array[SILWitnessEntry])

class SILProperty(val serialized: Boolean, val decl: SILDeclRef, val component: SILType)

class SILVTable(val name: String, val serialized: Boolean, val entries: Array[SILVEntry])

sealed trait SILVTableEntryKind
object SILVTableEntryKind {
  case object normal extends SILVTableEntryKind
  case object inherited extends SILVTableEntryKind
  case object overide extends SILVTableEntryKind
}

class SILVEntry(val declRef: SILDeclRef, val tpe: Option[SILType], val kind: SILVTableEntryKind,
                val nonoverridden: Boolean, val linkage: Option[SILLinkage], val functionName: SILMangledName)

sealed trait SILWitnessEntry
object SILWitnessEntry {
  case class baseProtocol(identifier: String, pc: SILProtocolConformance) extends SILWitnessEntry
  case class method(declRef: SILDeclRef, declType: SILType, functionName: SILMangledName) extends SILWitnessEntry
  case class associatedType(identifier0: String, identifier1: String) extends SILWitnessEntry
  case class associatedTypeProtocol(identifier0: String, identifier1: String, pc: SILProtocolConformance) extends SILWitnessEntry
}

class SILNormalProtocolConformance(val tpe: SILType, val protocol: String, val module: String)

sealed trait SILProtocolConformance
object SILProtocolConformance {
  case class normal(pc: SILNormalProtocolConformance) extends SILProtocolConformance
  // Not consistent with SIL.rst.
  case class inherit(tpe: SILType, pc: SILProtocolConformance) extends SILProtocolConformance
  case class specialize(substitutions: Array[SILType], pc: SILProtocolConformance) extends SILProtocolConformance
  case object dependent extends SILProtocolConformance
}