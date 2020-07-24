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

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util

import ca.ualberta.maple.swan.utils.ExceptionReporter

import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag
import scala.util.control.Breaks

class SILParser {

  /********** THIS CODE IS ORIGINALLY PART OF "Parser" **********************/

  // Default constructor should not be called.
  // I can't think of an elegant way to address the exclusive or
  // of Path/String parameters.

  private[parser] var path: String = _
  private[parser] var chars: Array[Char] = _
  private[parser] var cursor: Int = 0
  def position(): Int = { cursor }

  def this(path: Path) = {
    this()
    this.path = path.toString
    val data : Array[Byte] = if (Files.exists(path)) {
      Files.readAllBytes(path: Path)
    } else {
      // TODO: This is a bit janky and probably doesn't work.
      ExceptionReporter.report(new Error(this.path, "file not found"))
      null
    }: Array[Byte]
    val text = new String(data, StandardCharsets.UTF_8)
    this.chars = text.toCharArray
    skipTrivia()
  }

  def this(s: String) = {
    this()
    this.path = "<memory>"
    this.chars = s.toCharArray
    skipTrivia()
  }

  // ***** Token level *****

  protected def peek(query: String): Boolean = {
    assert(!query.isEmpty)
    util.Arrays.equals(
      util.Arrays.copyOfRange(chars, cursor, cursor + query.length),
      query.toCharArray)
  }

  @throws[Error]
  protected def take(query: String): Unit = {
    if (!peek(query)) {
      ExceptionReporter.report(new Error(path, query + " expected"))
    }
    cursor += query.length
    skipTrivia()
  }

  protected def skip(query: String): Boolean = {
    if (!peek(query)) return false
    cursor += query.length
    skipTrivia()
    true
  }

  protected def take(whileFn: Char => Boolean): String = {
    val result : Array[Char] = chars.takeRight(chars.length - cursor).takeWhile(whileFn)
    cursor += result.length
    skipTrivia()
    new String(result)
  }

  protected def skip(whileFn: Char => Boolean): Boolean = {
    val result : String = take(whileFn)
    !result.isEmpty
  }

  protected def skipTrivia(): Unit = {
    if (cursor >= chars.length) return
    if (Character.isWhitespace(chars(cursor))) {
      cursor += 1
      skipTrivia()
    } else if (skip("//")) {
      while (cursor < chars.length && chars(cursor) != '\n') { // Optimize?
        cursor += 1
      }
      skipTrivia()
    }
  }

  // ***** Tree level *****

  @throws[Error]
  protected def maybeParse[T](f: () => Option[T]) : Option[T] = {
    val savedCursor = cursor
    try {
      val result = f()
      if (result.isEmpty) {
        cursor = savedCursor
      }
      result
    } catch {
      case e : Error => {
        cursor = savedCursor
        throw e
      }
    }
  }

  @throws[Error]
  protected def parseNilOrMany[T:ClassTag](pre: String, sep: String = "", suf: String = "", parseOne: () => T): Option[Array[T]] = {
    if (!peek(pre)) return None
    Some(parseMany(pre, sep, suf, parseOne))
  }

  @throws[Error]
  protected def parseMany[T:ClassTag](pre: String, sep: String, suf: String, parseOne: () => T): Array[T] = {
    take(pre)
    val result = new ArrayBuffer[T]
    if (!peek(suf)) {
      var break = false
      while (!break) {
        val element = parseOne()
        result.append(element)
        if (peek(suf)) {
          break = true
        } else {
          if (!sep.isEmpty) {
            take(sep)
          }
        }
      }
    }
    take(suf)
    result.toArray
  }

  @throws[Error]
  protected def parseNilOrMany[T:ClassTag](pre: String, parseOne: () => T): Option[Array[T]] = {
    if (!peek(pre)) return None
    Some(parseMany(pre, parseOne))
  }

  @throws[Error]
  protected def parseUntilNil[T:ClassTag](parseOne: () => Option[T]): Array[T] = {
    val result = new ArrayBuffer[T]
    var break = false
    while (!break) {
      val element = parseOne()
      if (element.isEmpty) {
        break = true
      } else {
        result.append(element.get)
      }
    }
    result.toArray
  }

  @throws[Error]
  protected def parseMany[T:ClassTag](pre: String, parseOne: () => T): Array[T] = {
    val result = new ArrayBuffer[T]
    do {
      val element = parseOne()
      result.append(element)
    } while (peek(pre))
    result.toArray
  }

  // ***** Error reporting *****

  protected def parseError(message: String, at: Option[Int] = None): Error = {
    val position = if (at.isDefined) at.get else cursor
    val newlines = chars.take(position).filter(_ == '\n')
    val line = newlines.length + 1
    val column = position - (if (chars.lastIndexOf('\n') == - 1) 0 else chars.lastIndexOf('\n')) + 1
    new Error(path, line, column, message)
  }

  /********** THIS CODE IS ORIGINALLY PART OF "SILParser" *******************/

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#syntax
  @throws[Error]
  def parseModule(): SILModule = {
    var functions = Array[SILFunction]()
    var done = false
    while(!done) {
      // TODO(#8): Parse sections of SIL printouts that don't start with "sil @".
      // Meanwhile, skip those sections since we don't have a representation for them yet.
      // Concretely: if the current line begins with "sil @", try to parse a Function.
      // Otherwise, skip to the end of line and repeat.
      if(peek("sil ")) {
        val function = parseFunction()
        functions :+= function
      } else {
        Breaks.breakable {
          if(skip(_ != '\n')) Breaks.break()
        }
        done = true
      }
    }
    new SILModule(functions)
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#functions
  @throws[Error]
  def parseFunction(): SILFunction = {
    take("sil")
    val linkage = parseLinkage()
    val attributes = { parseNilOrMany("[", parseFunctionAttribute) }.getOrElse(Array.empty[SILFunctionAttribute])
    val name = parseGlobalName()
    take(":")
    val tpe = parseType()
    val blocks = { parseNilOrMany("{", "", "}", parseBlock) }.getOrElse(Array.empty[SILBlock])
    new SILFunction(linkage, attributes, name, tpe, blocks)
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#basic-blocks
  @throws[Error]
  def parseBlock(): SILBlock = {
    val identifier = parseIdentifier()
    val arguments = { parseNilOrMany("(", ",", ")", parseArgument) }.getOrElse(Array.empty[SILArgument])
    take(":")
    val (operatorDefs, terminatorDef) = parseInstructionDefs()
    new SILBlock(identifier, arguments, operatorDefs, terminatorDef)
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#basic-blocks
  @throws[Error]
  def parseInstructionDefs(): (Array[SILOperatorDef], SILTerminatorDef) = {
    var operatorDefs = Array[SILOperatorDef]()
    var done = false
    var termDef: SILTerminatorDef = null
    while(!done){
      parseInstructionDef() match {
        case SILInstructionDef.operator(operatorDef) => operatorDefs :+= operatorDef
        case SILInstructionDef.terminator(terminatorDef) => return (operatorDefs, terminatorDef)
      }
      if(peek("bb") || peek("}")) {
        if(operatorDefs.lastOption.nonEmpty) {
          operatorDefs.last.operator match {
            case SILOperator.unknown(instructionName) => {
              done = true
              termDef = new SILTerminatorDef(SILTerminator.unknown(instructionName), None)
            }
            case _ => throw parseError("block is missing a terminator")
          }
        }
      }
    }
    (operatorDefs, termDef)
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#basic-blocks
  @throws[Error]
  def parseInstructionDef(): SILInstructionDef = {
    val result = parseResult()
    val body = parseInstruction()
    val sourceInfo = parseSourceInfo()
    body match {
      case SILInstruction.operator(op) => SILInstructionDef.operator(new SILOperatorDef(result, op, sourceInfo))
      case SILInstruction.terminator(terminator) => {
        if(result.nonEmpty) throw parseError("terminator instruction shouldn't have any results")
        SILInstructionDef.terminator(new SILTerminatorDef(terminator, sourceInfo))
      }
    }
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#instruction-set
  @throws[Error]
  def parseInstruction(): SILInstruction = {
    val instructionName = take(x => x.isLetter || x == '_')
    // In the original parser this method would return
    // Instruction.operator(Operator.unknown(instructionName)) if
    // parseInstructionBody failed. I think we should just blow up
    // for now and maybe have some error recovery options for
    // production later.
    // try {
      parseInstructionBody(instructionName)
    /*
    } catch {
      // Try to recover to a point where resuming the parsing is sensible
      // by skipping until the end of this line. This is only a heuristic:
      // I don't think that the SIL specification guarantees that.
      case _ : Throwable => {
        skip(_ != '\n' )
        Instruction.operator(Operator.unknown(instructionName))
      }
    }
     */
  }

  @throws[Error]
  def parseInstructionBody(instructionName: String): SILInstruction = {
    // NPOTP: Not part of tensorflow parser
    //
    // NSIP: Not seen in practice (generated SIL from apple/swift benchmarks and
    // never saw these instructions). Things could have changed since then as that
    // was in Fall 2019. It also appears that tensorflow handles some instructions
    // that never showed up in practice for us.
    //
    // LP: Low priority (most likely because it doesn't affect analysis, we treat it
    // as a NOP)
    //
    // Case instruction ordering based on apple/swift tag swift-5.2-RELEASE SIL.rst
    instructionName match {

        // *** ALLOCATION AND DEALLOCATION ***

      case "alloc_stack" => {
        val tpe = parseType()
        val attributes = parseUntilNil( parseDebugAttribute )
        SILInstruction.operator(SILOperator.allocStack(tpe, attributes))
      }
      case "alloc_ref" => {
        var allocAttributes = new Array[SILAllocAttribute](0)
        if(skip("[objc]")) allocAttributes = allocAttributes :+ SILAllocAttribute.objc
        if(skip("[stack]")) { allocAttributes = allocAttributes :+ SILAllocAttribute.stack }
        var tailElems: Array[(SILType, SILOperand)] = new Array[(SILType, SILOperand)](0)
        while(peek("[")) {
          take("[")
          take("tail_elems")
          val tailType: SILType = parseType()
          take("*")
          val operand: SILOperand = parseOperand()
          take("]")
          tailElems = tailElems :+ (tailType, operand)
        }
        val tpe: SILType = parseType()
        SILInstruction.operator(SILOperator.allocRef(allocAttributes, tailElems, tpe))
      }
      case "alloc_ref_dynamic" => {
        null //  TODO: NPOTP
      }
      case "alloc_box" => {
        val tpe = parseType()
        val attributes = parseUntilNil( parseDebugAttribute )
        SILInstruction.operator(SILOperator.allocBox(tpe, attributes))
      }
      case "alloc_value_buffer" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "alloc_global" => {
        val name = parseGlobalName()
        SILInstruction.operator(SILOperator.allocGlobal(name))
      }
      case "dealloc_stack" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.deallocStack(operand))
      }
      case "dealloc_box" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.deallocBox(operand))
      }
      case "project_box" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.projectBox(operand))
      }
      case "dealloc_ref" => {
        val stack: Boolean = skip("[stack]")
        val operand: SILOperand = parseOperand()
        SILInstruction.operator(SILOperator.deallocRef(stack, operand))
      }
      case "dealloc_partial_ref" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "dealloc_value_buffer" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "project_value_buffer" => {
        throw parseError("unhandled instruction") // NSIP
      }

        // *** DEBUG INFORMATION ***

      case "debug_value" => {
        val operand = parseOperand()
        val attributes = parseUntilNil(parseDebugAttribute)
        SILInstruction.operator(SILOperator.debugValue(operand, attributes))
      }
      case "debug_value_addr" => {
        val operand = parseOperand()
        val attributes = parseUntilNil(parseDebugAttribute)
        SILInstruction.operator(SILOperator.debugValueAddr(operand, attributes))
      }

        // *** ACCESSING MEMORY ***

      case "load" => {
        // According to sil.rst, there is no optional
        // ownership for load. Original parser has ownership, though.
        var ownership : Option[SILLoadOwnership] = None
        if (skip("[copy]")) {
          ownership = Some(SILLoadOwnership.copy)
        } else if (skip("[take]")) {
          ownership = Some(SILLoadOwnership.take)
        } else if (skip("[trivial]")) {
          ownership = Some(SILLoadOwnership.trivial)
        }
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.load(ownership, operand))
      }
      case "store" => {
        val value = parseValue()
        take("to")
        var ownership : Option[SILStoreOwnership] = None
        if (skip("[init]")) {
          ownership = Some(SILStoreOwnership.init)
        } else if (skip("[trivial]")) {
          ownership = Some(SILStoreOwnership.trivial)
        }
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.store(value, ownership, operand))
      }
      case "load_borrow" => {
        null // TODO: NPOTP
      }
      case "begin_borrow" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.beginBorrow(operand))
      }
      case "end_borrow" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.endBorrow(operand))
      }
      case "assign" => {
        null // TODO: NPOTP
      }
      case "assign_by_wrapper" => {
        null // TODO: NPOTP
      }
      case "mark_uninitialized" => {
        null // TODO: NPOTP
      }
      case "mark_function_escape" => {
        null // TODO: NPOTP
      }
      case "mark_uninitialized_behaviour" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "copy_addr" => {
        val take = skip("[take]")
        val value = parseValue()
        this.take("to")
        val initialization = skip("[initialization]")
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.copyAddr(take, value, initialization, operand))
      }
      case "destroy_addr" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.destroyAddr(operand))
      }
      case "index_addr" => {
        val addr = parseOperand()
        take(",")
        val index = parseOperand()
        SILInstruction.operator(SILOperator.indexAddr(addr, index))
      }
      case "tail_addr" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "index_raw_pointer" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "bind_memory" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "begin_access" => {
        take("[")
        val access = parseAccess()
        take("]")
        take("[")
        val enforcement = parseEnforcement()
        take("]")
        val noNestedConflict = skip("[no_nested_conflict]")
        val builtin = skip("[builtin]")
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.beginAccess(access, enforcement, noNestedConflict, builtin, operand))
      }
      case "end_access" => {
        val abort = skip("[abort]")
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.endAccess(abort, operand))
      }
      case "begin_unpaired_access" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "end_unpaired_access" => {
        throw parseError("unhandled instruction") // NSIP
      }

        // *** REFERENCE COUNTING ***

      case "strong_retain" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.strongRetain(operand))
      }
      case "strong_release" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.strongRelease(operand))
      }
      case "set_deallocating" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "strong_copy_unowned_value" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "strong_retain_unowned" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "unowned_retain" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "unowned_release" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "load_weak" => {
        val take = skip("[take]")
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.loadWeak(take, operand))
      }
      case "store_weak" => {
        var value = parseValue()
        take("to")
        val initialization = skip("[initialization]")
        var operand = parseOperand()
        SILInstruction.operator(SILOperator.storeWeak(value, initialization, operand))
      }
      case "load_unowned" => {
        null // TODO: NPOTP
      }
      case "store_unowned" => {
        null // TODO: NPOTP
      }
      case "fix_lifetime" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "mark_dependence" => {
        val operand = parseOperand()
        take("on")
        val on = parseOperand()
        SILInstruction.operator(SILOperator.markDependence(operand, on))
      }
      case "is_unique" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "is_escaping_closure" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.isEscapingClosure(operand))
      }
      case "copy_block" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.copyBlock(operand))
      }
      case "copy_block_without_escaping" => {
        val operand1 = parseOperand()
        take("withoutEscaping")
        val operand2 = parseOperand()
        SILInstruction.operator(SILOperator.copyBlockWithoutEscaping(operand1, operand2))
      }
      // builtin "unsafeGuaranteed" not sure what to do about this one
      // builtin "unsafeGuaranteedEnd" not sure what to do about this one

        // *** LITERALS ***

      case "function_ref" => {
        val name = parseGlobalName()
        take(":")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.functionRef(name, tpe))
      }
      case "dynamic_function_ref" => {
        null // TODO: NPOTP
      }
      case "prev_dynamic_function_ref" => {
        null // TODO: NPOTP
      }
      case "global_addr" => {
        val name = parseGlobalName()
        take(":")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.globalAddr(name, tpe))
      }
      case "global_value" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "integer_literal" => {
        val tpe = parseType()
        take(",")
        val value = parseInt()
        SILInstruction.operator(SILOperator.integerLiteral(tpe, value))
      }
      case "float_literal" => {
        val tpe = parseType()
        take(",")
        take("0x")
        val value = take((x : Char) => x.toString.matches("^[0-9a-fA-F]+$"))
        SILInstruction.operator(SILOperator.floatLiteral(tpe, value))
      }
      case "string_literal" => {
        val encoding = parseEncoding()
        val value = parseString()
        SILInstruction.operator(SILOperator.stringLiteral(encoding, value))
      }

        // *** DYNAMIC DISPATCH ***

      case "class_method" => {
        null // TODO: NPOTP
      }
      case "objc_method" => {
        null // TODO: NPOTP
      }
      case "super_method" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "objc_super_method" => {
        null // TODO: NPOTP
      }
      case "witness_method" => {
        val attribute: Option[SILType] = maybeParse(() => {
            val tpe = parseType()
            Some(tpe)
          })
        val operand = parseOperand()
        take(",")
        val declRef = parseDeclRef()
        take(":")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.witnessMethod(attribute, operand, declRef, tpe))
      }

        // *** FUNCTION APPLICATION ***

      case "apply" => {
        val nothrow = skip("[nothrow]")
        val value = parseValue()
        val substitutions = parseNilOrMany("<", ",",">", parseNakedType)
        // I'm sure there's a more elegant way to do this.
        val substitutionsNonOptional: Array[SILType] = {
          if (substitutions.isEmpty) {
            new Array[SILType](0)
          } else {
            substitutions.get
          }
        }
        val arguments = parseMany("(",",",")", parseValue)
        take(":")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.apply(nothrow,value,substitutionsNonOptional,arguments,tpe))
      }
      case "begin_apply" => {
        val nothrow = skip("[nothrow]")
        val value = parseValue()
        val s : Option[Array[SILType]] = parseNilOrMany("<",",",">",parseNakedType)
        val substitutions = if (s.nonEmpty) s.get else new Array[SILType](0)
        val arguments = parseMany("(",",",")", parseValue)
        take(":")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.beginApply(nothrow, value, substitutions, arguments, tpe))
      }
      case "abort_apply" => {
        val value = parseValue()
        SILInstruction.operator(SILOperator.abortApply(value))
      }
      case "end_apply" => {
        val value = parseValue()
        SILInstruction.operator(SILOperator.endApply(value))
      }
      case "partial_apply" => {
        val calleeGuaranteed = skip("[callee_guaranteed]")
        val onStack = skip("[on_stack]")
        val value = parseValue()
        val s = parseNilOrMany("<",",",">", parseNakedType)
        val substitutions = if (s.nonEmpty) s.get else new Array[SILType](0)
        val arguments = parseMany("(",",",")", parseValue)
        take(":")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.partialApply(calleeGuaranteed,onStack,value,substitutions,arguments,tpe))
      }
      case "builtin" => {
        val name = parseString()
        val operands = parseMany("(", ",", ")", parseOperand)
        take(":")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.builtin(name, operands, tpe))
      }

        // *** METATYPES ***

      case "metatype" => {
        val tpe = parseType()
        SILInstruction.operator(SILOperator.metatype(tpe))
      }
      case "value_metatype" => {
        null // TODO: NPOTP
      }
      case "existential_metatype" => {
        null // TODO: NPOTP
      }
      case "objc_protocol" => {
        null // TODO: NPOTP
      }

        // *** AGGREGATE TYPES ***

      case "retain_value" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.retainValue(operand))
      }
      case "retain_value_addr" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "unmanaged_retain_value" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "copy_value" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.copyValue(operand))
      }
      case "release_value" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.releaseValue(operand))
      }
      case "release_value_addr" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "unmanaged_release_value" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "destroy_value" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.destroyValue(operand))
      }
      case "autorelease_value" => {
        null // TODO: NPOTP
      }
      case "tuple" => {
        val elements = parseTupleElements()
        SILInstruction.operator(SILOperator.tuple(elements))
      }
      case "tuple_extract" => {
        val operand = parseOperand()
        take(",")
        val declRef = parseInt()
        SILInstruction.operator(SILOperator.tupleExtract(operand, declRef))
      }
      case "tuple_element_addr" => {
        val operand = parseOperand()
        take(",")
        val declRef = parseInt()
        SILInstruction.operator(SILOperator.tupleElementAddr(operand, declRef))
      }
      case "destructure_tuple" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.destructureTuple(operand))
      }
      case "struct" => {
        val tpe = parseType()
        val operands = parseMany("(",",",")", parseOperand)
        SILInstruction.operator(SILOperator.struct(tpe, operands))
      }
      case "struct_extract" => {
        val operand = parseOperand()
        take(",")
        val declRef = parseDeclRef()
        SILInstruction.operator(SILOperator.structExtract(operand, declRef))
      }
      case "struct_element_addr" => {
        val operand = parseOperand()
        take(",")
        val declRef = parseDeclRef()
        SILInstruction.operator(SILOperator.structElementAddr(operand, declRef))
      }
      case "destructure_struct" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "object" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "ref_element_addr" => {
        val immutable: Boolean = skip("[immutable]")
        val operand: SILOperand = parseOperand()
        take(",")
        val declRef: SILDeclRef = parseDeclRef()
        SILInstruction.operator(SILOperator.refElementAddr(immutable, operand, declRef))
      }
      case "ref_tail_addr" => {
        throw parseError("unhandled instruction") // NSIP
      }

        // *** ENUMS ***

      case "enum" => {
        val tpe = parseType()
        take(",")
        val declRef = parseDeclRef()
        // Just because we see "," doesn't mean there will be an operand.
        // It could be `[...], scope [...]`
        val operand = {
          try {
            maybeParse(() => {
              if (skip(",")) {
                Some(parseOperand())
              } else {
                None
              }
            }: Option[SILOperand])
          } catch {
            case _ : Error => None
          }

        }
        SILInstruction.operator(SILOperator.enm(tpe, declRef, operand))
      }
      case "unchecked_enum_data" => {
        val operand = parseOperand()
        take(",")
        val declRef = parseDeclRef()
        SILInstruction.operator(SILOperator.uncheckedEnumData(operand, declRef))
      }
      case "init_enum_data_addr" => {
        null // TODO: NPOTP
      }
      case "inject_enum_addr" => {
        null // TODO: NPOTP LP
      }
      case "unchecked_take_enum_data_addr" => {
        null // TODO: NPOTP
      }
      case "select_enum" => {
        val operand = parseOperand()
        val cases = parseUntilNil[SILCase](() => parseCase(parseValue))
        take(":")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.selectEnum(operand, cases, tpe))
      }
      case "select_enum_addr" => {
        null // TODO: NPOTP
      }

        // *** PROTOCOL AND PROTOCOL COMPOSITION TYPES ***

      case "init_existential_addr" => {
        null // TODO: NPOTP
      }
      case "init_existential_value" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "deinit_existential_addr" => {
        null // TODO: NPOTP LP
      }
      case "deinit_existential_value" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "open_existential_addr" => {
        null // TODO: NPOTP
      }
      case "open_existential_value" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "init_existential_ref" => {
        null // TODO: NPOTP
      }
      case "open_existential_ref" => {
        null // TODO: NPOTP
      }
      case "init_existential_metatype" => {
        val operand = parseOperand()
        take(",")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.initExistentialMetatype(operand,tpe))
      }
      case "open_existential_metatype" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.openExistentialMetatype(operand,tpe))
      }
      case "alloc_existential_box" => {
        val tpeP = parseType()
        take(",")
        val tpeT = parseType()
        SILInstruction.operator(SILOperator.allocExistentialBox(tpeP, tpeT))
      }
      case "project_existential_box" => {
        null // TODO: NPOTP
      }
      case "open_existential_box" => {
        null // TODO: NPOTP
      }
      case "open_existential_box_value" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "dealloc_existential_box" => {
        null // TODO: NPOTP LP
      }

        // *** BLOCKS ***

      case "project_block_storage" => {
        null // TODO: NPOTP
      }
      case "init_block_storage_header" => {
        null // TODO: NPOTP
      }

        // *** UNCHECKED CONVERSIONS ***

      case "upcast" => {
        null // TODO: NPOTP
      }
      case "address_to_pointer" => {
        null // TODO: NPOTP
      }
      case "pointer_to_address" => {
        val operand = parseOperand()
        take("to")
        val strict = skip("[strict]")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.pointerToAddress(operand, strict, tpe))
      }
      case "unchecked_ref_cast" => {
        null // TODO: NPOTP
      }
      case "unchecked_ref_cast_addr" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "unchecked_addr_cast" => {
        null // TODO: NPOTP
      }
      case "unchecked_trivial_bit_cast" => {
        null // TODO: NPOTP
      }
      case "unchecked_bitwise_cast" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "ref_to_raw_pointer" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "raw_pointer_to_ref" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "ref_to_unowned" => {
        null // TODO: NPOTP
      }
      case "unowned_to_ref" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "ref_to_unmanaged" => {
        null // TODO: NPOTP
      }
      case "unmanaged_to_ref" => {
        null // TODO: NPOTP
      }
      case "convert_function" => {
        val operand = parseOperand()
        take("to")
        val withoutActuallyEscaping = skip("[without_actually_escaping]")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.convertFunction(operand, withoutActuallyEscaping, tpe))
      }
      case "convert_escape_to_noescape" => {
        val notGuaranteed = skip("[not_guaranteed]")
        val escaped = skip("[escaped]")
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.convertEscapeToNoescape(notGuaranteed, escaped, operand, tpe))
      }
      case "thin_function_to_pointer" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "pointer_to_thin_function" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "classify_bridge_object" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "value_to_bridge_object" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "ref_to_bridge_object" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "bridge_object_to_ref" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "bridge_object_to_word" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "thin_to_thick_function" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.thinToThickFunction(operand, tpe))
      }
      case "thick_to_objc_metatype" => {
        null // TODO: NPOTP
      }
      case "objc_to_thick_metatype" => {
        null // TODO: NPOTP
      }
      case "objc_metatype_to_object" => {
        null // TODO: NPOTP
      }
      case "objc_existential_metatype_to_object" => {
        null // TODO: NPOTP
      }

        // *** CHECKED CONVERSIONS ***

      case "unconditional_checked_cast" => {
        null // TODO: NPOTP
      }
      case "unconditional_checked_cast_addr" => {
        null // TODO: NPOTP
      }
      case "unconditional_checked_cast_value" => {
        throw parseError("unhandled instruction") // NSIP
      }

        // *** RUNTIME FAILURES ***

      case "cond_fail" => {
        val operand = parseOperand()
        // According to the spec, the message is non-optional but
        // that doesn't seem to be the case in practice.
        val message = {
          try {
            maybeParse(() => {
              take(",")
              Some(parseString())
            } : Option[String] )
          } catch {
            case _: Error => None
          }
        }
        SILInstruction.operator(SILOperator.condFail(operand, message))
      }

        // *** TERMINATORS ***

      case "unreachable" => {
        SILInstruction.terminator(SILTerminator.unreachable)
      }
      case "return" => {
        val operand = parseOperand()
        SILInstruction.terminator(SILTerminator.ret(operand))
      }
      case "throw" => {
        val operand = parseOperand()
        SILInstruction.terminator(SILTerminator.thro(operand))
      }
      case "yield" => {
        // Multiple yielded values -> values are inside parentheses.
        // Single yielded value -> no parentheses.
        val operands: Array[SILOperand] = {
          if(peek("(")) {
            parseMany("(", ",", ")", parseOperand)
          } else {
            val arr = new Array[SILOperand](1)
            arr(0) = parseOperand()
            arr
          }
        }
        take(",")
        take("resume")
        val resumeLabel: String = parseIdentifier()
        take(",")
        take("unwind")
        val unwindLabel: String = parseIdentifier()
        SILInstruction.terminator(SILTerminator.yld(operands, resumeLabel, unwindLabel))
      }
      case "unwind" => {
        SILInstruction.terminator(SILTerminator.unwind)
      }
      case "br" => {
        val label = parseIdentifier()
        val o : Option[Array[SILOperand]] = parseNilOrMany("(",",",")", parseOperand)
        val operands = if (o.nonEmpty) o.get else new Array[SILOperand](0)
        SILInstruction.terminator(SILTerminator.br(label, operands))
      }
      case "cond_br" => {
        val cond = parseValueName()
        take(",")
        val trueLabel = parseIdentifier()
        val to : Option[Array[SILOperand]] = parseNilOrMany("(",",",")",parseOperand)
        val trueOperands = if (to.nonEmpty) to.get else new Array[SILOperand](0)
        take(",")
        val falseLabel = parseIdentifier()
        val fo : Option[Array[SILOperand]] = parseNilOrMany("(",",",")",parseOperand)
        val falseOperands = if (fo.nonEmpty) to.get else new Array[SILOperand](0)
        SILInstruction.terminator(SILTerminator.condBr(cond, trueLabel, trueOperands, falseLabel, falseOperands))
      }
      case "switch_value" => {
        null // TODO: NPOTP
      }
      case "select_value" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "switch_enum" => {
        val operand = parseOperand()
        val cases = parseUntilNil[SILCase](() => parseCase(parseIdentifier))
        SILInstruction.terminator(SILTerminator.switchEnum(operand, cases))
      }
      case "switch_enum_addr" => {
        val operand = parseOperand()
        val cases = parseUntilNil[SILCase](() => parseCase(parseIdentifier))
        SILInstruction.terminator(SILTerminator.switchEnumAddr(operand, cases))
      }
      case "dynamic_method_br" => {
        null // TODO: NPOTP
      }
      case "checked_cast_br" => {
        null // TODO: NPOTP
      }
      case "checked_cast_value_br" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "checked_cast_addr_br" => {
        null // TODO: NPOTP
      }
      case "try_apply" => {
        // NOTE: This is a multi-line instruction.
        null // TODO: NPOTP
      }

        // *** DEFAULT FALLBACK ***

      case _ : String => {
        //val _ = skip(_ != "\n")
        //Instruction.operator(Operator.unknown(instructionName))
        throw parseError("unknown instruction")
      }
    }
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#begin-access
  @throws[Error]
  def parseAccess(): SILAccess = {
    if(skip("deinit")) return SILAccess.deinit
    if(skip("init")) return SILAccess.init
    if(skip("modify")) return SILAccess.modify
    if(skip("read")) return SILAccess.read
    throw parseError("unknown access")
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#basic-blocks
  @throws[Error]
  def parseArgument(): SILArgument = {
    val valueName = parseValueName()
    take(":")
    val tpe = parseType()
    new SILArgument(valueName, tpe)
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#switch-enum
  @throws[Error]
  def parseCase(parseElement: () => String): Option[SILCase] = {
    maybeParse(() => {
      if(!skip(",")) return None
      if (skip("case")) {
        val declRef = parseDeclRef()
        take(":")
        val identifier = parseElement()
        Some(SILCase.cs(declRef, identifier))
      } else if (skip("default")) {
        val identifier = parseElement()
        Some(SILCase.default(identifier))
      } else {
        None
      }
    })
  }

  @throws[Error]
  def parseConvention(): SILConvention = {
    take("(")
    var result: SILConvention = null
    if (skip("c")) {
      result = SILConvention.c
    } else if (skip("method")) {
      result = SILConvention.method
    } else if (skip("thin")) {
      result = SILConvention.thin
    } else if (skip("block")) {
      result = SILConvention.block
    } else if (skip("witness_method")) {
      take(":")
      val tpe = parseNakedType()
      result = SILConvention.witnessMethod(tpe)
    } else {
      throw parseError("unknown convention")
    }
    take(")")
    result
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#debug-value
  @throws[Error]
  def parseDebugAttribute(): Option[SILDebugAttribute] = {
    maybeParse(() => {
      if(!skip(",")) return None
      if(skip("argno")) return Some(SILDebugAttribute.argno(parseInt()))
      if(skip("name")) return Some(SILDebugAttribute.name(parseString()))
      if(skip("let")) return Some(SILDebugAttribute.let)
      if(skip("var")) return Some(SILDebugAttribute.variable)
      None
    })
  }

  @throws[Error]
  def parseDeclKind(): Option[SILDeclKind] = {
    if(skip("allocator")) return Some(SILDeclKind.allocator)
    if(skip("deallocator")) return Some(SILDeclKind.deallocator)
    if(skip("destroyer")) return Some(SILDeclKind.destroyer)
    if(skip("enumelt")) return Some(SILDeclKind.enumElement)
    if(skip("getter")) return Some(SILDeclKind.getter)
    if(skip("globalaccessor")) return Some(SILDeclKind.globalAccessor)
    if(skip("initializer")) return Some(SILDeclKind.initializer)
    if(skip("ivardestroyer")) return Some(SILDeclKind.ivarDestroyer)
    if(skip("ivarinitializer")) return Some(SILDeclKind.ivarInitializer)
    if(skip("setter")) return Some(SILDeclKind.setter)
    None
  }

  @throws[Error]
  def parseDeclRef(): SILDeclRef = {
    take("#")
    val name = new ArrayBuffer[String]
    var break = false
    while (!break) {
      val identifier = parseIdentifier()
      name.append(identifier)
      if (!skip(".")) {
        break = true
      }
    }
    if (!skip("!")) {
      new SILDeclRef(name.toArray, None, None)
    }
    val kind = parseDeclKind()
    if (kind.nonEmpty && !skip(".")) {
      new SILDeclRef(name.toArray, kind, None)
    }
    // Note: In the original parser there was no try/catch here.
    // However, parseInt() can fail in practice when there is no level,
    // so I just threw a try/catch around it. Not sure if this is the final
    // solution.
    try {
      val level = parseInt()
      new SILDeclRef(name.toArray, kind, Some(level))
    } catch {
      case _ : Error => { }
    }
    new SILDeclRef(name.toArray, kind, None)
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#string-literal
  @throws[Error]
  def parseEncoding(): SILEncoding = {
    if(skip("objc_selector")) return SILEncoding.objcSelector
    if(skip("utf8")) return SILEncoding.utf8
    if(skip("utf16")) return SILEncoding.utf16
    throw parseError("unknown encoding")
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#begin-access
  @throws[Error]
  def parseEnforcement(): SILEnforcement = {
    if(skip("dynamic")) return SILEnforcement.dynamic
    if(skip("")) return SILEnforcement.static
    if(skip("")) return SILEnforcement.unknown
    if(skip("")) return SILEnforcement.unsafe
    throw parseError("unknown enforcement")
  }

  // Reverse-engineered from -emit-sil
  @throws[Error]
  def parseFunctionAttribute(): SILFunctionAttribute = {
    @throws[Error]
    def parseDifferentiable(): SILFunctionAttribute = {
      take("[differentiable")
      val spec = take(_ != ']' )
      take("]")
      SILFunctionAttribute.differentiable(spec)
    }

    @throws[Error]
    def parseSemantics(): SILFunctionAttribute = {
      take("[_semantics")
      val value = parseString()
      take("]")
      SILFunctionAttribute.semantics(value)
    }

    if(skip("[always_inline]")) return SILFunctionAttribute.alwaysInline
    if(peek("[differentiable")) return parseDifferentiable()
    if(skip("[dynamically_replacable]")) return SILFunctionAttribute.dynamicallyReplacable
    if(skip("[noinline]")) return SILFunctionAttribute.noInline
    if(skip("[ossa]")) return SILFunctionAttribute.noncanonical(SILNoncanonicalFunctionAttribute.ownershipSSA)
    if(skip("[readonly]")) return SILFunctionAttribute.readonly
    if(peek("[_semantics")) return parseSemantics()
    if(skip("[serialized]")) return SILFunctionAttribute.serialized
    if(skip("[thunk]")) return SILFunctionAttribute.thunk
    if(skip("[transparent]")) return SILFunctionAttribute.transparent
    throw parseError("unknown function attribute")
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#functions
  @throws[Error]
  def parseGlobalName(): String = {
    val start = position()
    if(skip("@")) {
      // TODO(#14): Make name parsing more thorough.
      val name = take(x => x == '$' || x.isLetterOrDigit || x == '_' )
      if(!name.isEmpty) {
        return name
      }
    }
    throw parseError("function name expected", Some(start))
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#values-and-operands
  @throws[Error]
  def parseIdentifier(): String = {
    if(peek("\"")) {
      // https://github.com/scala/bug/issues/6476
      // https://stackoverflow.com/questions/21086263/how-to-insert-double-quotes-into-string-with-interpolation-in-scala
      s""""${parseString()}""""
    } else {
      val start = position()
      // TODO(#14): Make name parsing more thorough.
      val identifier = take(x => x.isLetterOrDigit || x == '_')
      if (!identifier.isEmpty) return identifier
      throw parseError("identifier expected", Some(start))
    }
  }

  @throws[Error]
  def parseInt(): Int = {
    // TODO(#26): Make number parsing more thorough.
    val start = position()
    val radix = if(skip("0x")) 16 else 10
    val s = take(x => x == '-' || x == '+' || Character.digit(x, 16) != -1)
    val value = try {
        Integer.parseInt(s, radix)
      } catch {
        case _ : Throwable => throw parseError("integer literal expected", Some(start))
      }
    value
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#linkage
  @throws[Error]
  def parseLinkage(): SILLinkage = {
    // The order in here is a bit relaxed because longer words need to come
    // before the shorter ones to parse correctly.
    if(skip("hidden_external")) return SILLinkage.hiddenExternal
    if(skip("hidden")) return SILLinkage.hidden
    if(skip("private_external")) return SILLinkage.privateExternal
    if(skip("private")) return SILLinkage.priv
    if(skip("public_external")) return SILLinkage.publicExternal
    if(skip("non_abi")) return SILLinkage.publicNonABI
    if(skip("public")) return SILLinkage.public
    if(skip("shared_external")) return SILLinkage.sharedExternal
    if(skip("shared")) return SILLinkage.shared
    SILLinkage.public
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#debug-information
  @throws[Error]
  def parseLoc(): Option[SILLoc] = {
    if(!skip("loc")) return None
    val path = parseString()
    take(":")
    val line = parseInt()
    take(":")
    val column = parseInt()
    Some(new SILLoc(path, line, column))
  }

  // Parses verbatim string representation of a type.
  // This is different from `parseType` because most usages of types in SIL are prefixed with
  // `$` (so it made sense to have a shorter name for that common case).
  // Type format has been reverse-engineered since it doesn't seem to be mentioned in the spec.
  // TODO: Handle types used in [at least] alloc_box.
  //  e.g. ${ var @sil_weak Optional<CardCell> }
  // TODO: Handle types with ":"
  //  tuple type e.g. $*(lower: Bound, upper: Bound)
  @throws[Error]
  def parseNakedType(): SILType = {
    if (skip("<")) {
      val params = new ArrayBuffer[String]
      var break = false
      while (!break) {
        val name = parseTypeName()
        params.append(name)
        if (peek("where") || peek(">")) {
          break = true
        } else {
          take(",")
        }
      }
      var reqs = new ArrayBuffer[SILTypeRequirement]
      if (peek("where")) {
        reqs = parseMany("where", ",", ">", parseTypeRequirement).to(ArrayBuffer)
      } else {
        reqs.clear()
        take(">")
      }
      val tpe = parseNakedType()
      SILType.genericType(params.toArray, reqs.toArray, tpe)
    } else if (peek("@")) {
      val attrs = parseMany("@", parseTypeAttribute)
      val tpe = parseNakedType()
      SILType.attributedType(attrs, tpe)
    } else if (skip("*")) {
      val tpe = parseNakedType()
      SILType.addressType(tpe)
    } else if (skip("[")) {
      val subtype = parseNakedType()
      take("]")
      SILType.specializedType(SILType.namedType("Array"), Array[SILType]{subtype})
    } else if (peek("(")) {
      val types: Array[SILType] = parseMany("(",",",")", parseNakedType)
      if (skip("->")) {
        val result = parseNakedType()
        SILType.functionType(types, result)
      } else {
        if (types.length == 1) {
         types(0)
        } else {
          SILType.tupleType(types)
        }
      }
    } else {
      @throws[Error]
      def grow(tpe: SILType): SILType = {
        if (peek("<")) {
          val types = parseMany("<",",",">", parseNakedType)
          grow(SILType.specializedType(tpe, types))
        } else if (skip(".")) {
          val name = parseTypeName()
          grow(SILType.selectType(tpe, name))
        } else {
          tpe
        }
      }
      val name = parseTypeName()
      val base: SILType = if (name != "Self") SILType.namedType(name) else SILType.selfType
      grow(base)
    }
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#values-and-operands
  @throws[Error]
  def parseOperand(): SILOperand = {
    val valueName = parseValueName()
    take(":")
    val tpe = parseType()
    new SILOperand(valueName, tpe)
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#basic-blocks
  @throws[Error]
  def parseResult(): Option[SILResult] = {
    if(peek("%")) {
      val valueName = parseValueName()
      take("=")
      Some(new SILResult(Array(valueName)))
    } else if(peek("(")) {
      val valueNames = parseMany("(", ",", ")", parseValueName)
      take("=")
      Some(new SILResult(valueNames))
    } else {
      None
    }
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#debug-information
  @throws[Error]
  def parseScopeRef(): Option[Int] = {
    if(!skip("scope")) return None
    Some(parseInt())
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#basic-blocks
  @throws[Error]
  def parseSourceInfo(): Option[SILSourceInfo] = {
    // NB: The SIL docs say that scope refs precede locations, but this is
    //     not true once you look at the compiler outputs or its source code.
    if(!skip(",")) return None
    val loc = parseLoc()
    // NB: No skipping if we failed to parse the location.
    val scopeRef = if(loc.isEmpty || skip(",")) parseScopeRef() else return None
    // We've skipped the comma, so failing to parse any of those two
    // components is an error.
    if(scopeRef.isEmpty && loc.isEmpty) throw parseError("Failed to parse source info")
    Some(new SILSourceInfo(scopeRef, loc))
  }

  @throws[Error]
  def parseString(): String = {
    // TODO(#24): Parse string literals with control characters.
    take("\"")
    val s = take(_ != '\"')
    take("\"")
    s
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#tuple
  def parseTupleElements(): SILTupleElements = {
    if (peek("$")) {
      val tpe = parseType()
      val values = parseMany("(",",",")", parseValue)
      SILTupleElements.labeled(tpe, values)
    } else {
      val operands = parseMany("(",",",")", parseOperand)
      SILTupleElements.unlabeled(operands)
    }
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#sil-types
  @throws[Error]
  def parseType(): SILType = {
    // NB: Ownership SSA has a surprising convention of printing the
    //     ownership type before the actual type, so we first try to
    //     parse the type attribute.
    try {
      take("$")
    } catch {
      case _: Error => {
        val attr : Option[SILTypeAttribute] = {
          try {
            Some(parseTypeAttribute())
          } catch {
            case _: Error => None
          }
        }
        // Take the $ for real even if the attribute was not there, because
        // that's the error message we want to show anyway.
        take("$")
        // We want to throw our own exception type here so we rethrow
        // if attr.get fails (it's needed just below the try/catch).
        try {
          attr.get
        } catch {
          case e : Throwable => {
            throw parseError(e.getMessage)
          }
        }
        SILType.withOwnership(attr.get, parseNakedType())
      }
    }
    parseNakedType()
  }

  @throws[Error]
  def parseTypeAttribute(): SILTypeAttribute = {
    if(skip("@callee_guaranteed")) return SILTypeAttribute.calleeGuaranteed
    if(skip("@convention")) return SILTypeAttribute.convention(parseConvention())
    if(skip("@guaranteed")) return SILTypeAttribute.guaranteed
    if(skip("@in_guaranteed")) return SILTypeAttribute.inGuaranteed
    // Must appear before "in" to parse correctly.
    if(skip("@inout")) return SILTypeAttribute.inout
    if(skip("@in")) return SILTypeAttribute.in
    if(skip("@noescape")) return SILTypeAttribute.noescape
    if(skip("@thick")) return SILTypeAttribute.thick
    if(skip("@out")) return SILTypeAttribute.out
    if(skip("@owned")) return SILTypeAttribute.owned
    if(skip("@thin")) return SILTypeAttribute.thin
    if(skip("@yield_once")) return SILTypeAttribute.yieldOnce
    if(skip("@yields")) return SILTypeAttribute.yields
    if(skip("@error")) return SILTypeAttribute.error
    if(skip("@objc_metatype")) return SILTypeAttribute.objcMetatype
    if(skip("@sil_weak")) return SILTypeAttribute.silWeak
    throw parseError("unknown attribute")
  }

  @throws[Error]
  def parseTypeName(): String = {
    val start = position()
    val name : String = take(x => x.isLetter || Character.isDigit(x) || x == '_')
    if (!name.isEmpty) {
      return name
    }
    throw parseError("type name expected", Some(start))
  }

  @throws[Error]
  def parseTypeRequirement(): SILTypeRequirement = {
    val lhs = parseNakedType()
    if (skip(":")) {
      val rhs = parseNakedType()
      SILTypeRequirement.conformance(lhs, rhs)
    } else if (skip("==")) {
      val rhs = parseNakedType()
      SILTypeRequirement.equality(lhs, rhs)
    } else {
      throw parseError("expected '==' or ':'")
    }
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#values-and-operands
  @throws[Error]
  def parseValue(): String = {
    if(peek("%")) {
      parseValueName()
    } else if(skip("undef")) {
      "undef"
    } else {
      throw parseError("value expected")
    }
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#values-and-operands
  @throws[Error]
  def parseValueName(): String = {
    val start = position()
    if(!skip("%")) throw parseError("value expected", Some(start))
    val identifier = parseIdentifier()
    "%" + identifier
  }
}

class Error(path : String, message : String) extends Exception {
  private[parser] var line : Option[Int] = None
  private[parser] var column : Option[Int] = None

  def this(path : String, line: Int, column : Int, message : String) = {
    this(path, message)
    this.line = Some(line)
    this.column = Some(column)
  }

  override def getMessage: String = {
    if (line.isEmpty) {
      return path + ": " + message
    }
    if (column.isEmpty) {
      return path + ":" + line + ": " + message
    }
    path + ":" + line + ":" + column + ": " + message
  }
}