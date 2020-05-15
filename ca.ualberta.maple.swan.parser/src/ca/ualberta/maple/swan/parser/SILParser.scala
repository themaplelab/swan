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

  def this(path: Path) {
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

  def this(s: String) {
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
    val result : Array[Char] = chars.takeRight(cursor).takeWhile(whileFn)
    cursor += result.length
    skipTrivia()
    new String(result)
  }

  protected def skip(whileFn: Char => Boolean): Boolean = {
    val result : String = try take(whileFn)
    !result.isEmpty
  }

  protected def skipTrivia(): Unit = {
    if (cursor < chars.length) return
    if (Character.isWhitespace(chars(cursor))) {
      cursor += 1
      skipTrivia()
    } else if (skip("//")) {
      while (cursor < chars.length && chars(cursor) != "\n") { // Optimize?
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
      val result = try f()
      if (result.isEmpty) {
        cursor = savedCursor
      }
      result
    } catch {
      case e : Error => throw e
    }
  }

  @throws[Error]
  protected def parseNilOrMany[T](pre: String, sep: String = "", suf: String = "", parseOne: () => T): Option[Array[T]] = {
    if (!peek(pre)) {
      return None
    }
    Some(try parseMany(pre, sep, suf, parseOne))
  }

  @throws[Error]
  protected def parseMany[T](pre: String, sep: String, suf: String, parseOne: () => T): Array[T] = {
    try take(pre)
    val result = new ArrayBuffer[T]
    if (!peek(suf)) {
      var break = false
      while (!break) {
        val element = try parseOne()
        result.append(element)
        if (peek(suf)) {
          break = true
        } else {
          if (!sep.isEmpty) {
            try take(sep)
          }
        }
      }
    }
    try take(suf)
    result.toArray
  }

  @throws[Error]
  protected def parseNilOrMany[T](pre: String, parseOne: () => T): Option[Array[T]] = {
    if (!peek(pre)) {
      return None
    }
    Some(try parseMany(pre, parseOne))
  }

  @throws[Error]
  protected def parseUntilNil[T](parseOne: () => Option[T]): Array[T] = {
    val result = new ArrayBuffer[T]
    var break = false
    while (!break) {
      val element = parseOne()
      if (element.isEmpty) {
        break = false
      } else {
        result.append(element.get)
      }
    }
    result.toArray
  }

  @throws[Error]
  protected def parseMany[T](pre: String, parseOne: () => T): Array[T] = {
    val result = new ArrayBuffer[T]
    do {
      val element = try parseOne()
      result.append(element)
    } while (peek(pre))
    result.toArray
  }

  // ***** Error reporting *****

  protected def parseError(message: String, at: Option[Int] = None[Int]): Error = {
    val position = if (at.isDefined) at.get else cursor
    val newlines = chars.take(position).filter(_ == '\n')
    val line = newlines.length + 1
    val column = position - (if (chars.lastIndexOf('\n') == - 1) 0 else chars.lastIndexOf('\n')) + 1
    new Error(path, line, column, message)
  }

  class Error(path : String, message : String) extends Exception {
    private[parser] var line : Option[Int] = None
    private[parser] var column : Option[Int] = None

    def this(path : String, line: Int, column : Int, message : String) {
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

  /********** THIS CODE IS ORIGINALLY PART OF "SILParser" *******************/

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#syntax
  @throws[Error]
  def parseModule(): Module = {
    var functions = Array[Function]()
    var done = false
    while(!done) {
      // TODO(#8): Parse sections of SIL printouts that don't start with "sil @".
      // Meanwhile, skip those sections since we don't have a representation for them yet.
      // Concretely: if the current line begins with "sil @", try to parse a Function.
      // Otherwise, skip to the end of line and repeat.
      if(peek("sil ")) {
        val function = try parseFunction()
        functions :+= function
      } else {
        Breaks.breakable {
          if(skip(_ != "\n")) Breaks.break
        }
        done = true
      }
    }
    new Module(functions)
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#functions
  @throws[Error]
  def parseFunction(): Function = {
    try take("sil")
    val linkage = try parseLinkage()
    val attributes = { try parseNilOrMany("[", parseOne = try parseFunctionAttribute) }.getOrElse(Array.empty[FunctionAttribute])
    val name = try parseGlobalName()
    try take(":")
    val tpe = try parseType()
    val blocks = { try parseNilOrMany("{", "", "}", try parseBlock) }.getOrElse(Array.empty[Block])
    new Function(linkage, attributes, name, tpe, blocks)
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#basic-blocks
  @throws[Error]
  def parseBlock(): Block = {
    val identifier = try parseIdentifier()
    val arguments = { try parseNilOrMany("(", ",", ")", try parseArgument) }.getOrElse(Array.empty[Argument])
    try take(":")
    val (operatorDefs, terminatorDef) = try parseInstructionDefs()
    new Block(identifier, arguments, operatorDefs, terminatorDef)
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#basic-blocks
  @throws[Error]
  def parseInstructionDefs(): (Array[OperatorDef], TerminatorDef) = {
    var operatorDefs = Array[OperatorDef]()
    var done = false
    var termDef: TerminatorDef = null
    while(!done){
      try parseInstructionDef() match {
        case InstructionDef.operator(operatorDef) => operatorDefs :+= operatorDef
        case InstructionDef.terminator(terminatorDef) => return (operatorDefs, terminatorDef)
      }
      if(peek("bb") || peek("}")) {
        if(operatorDefs.lastOption.nonEmpty) {
          operatorDefs.last.operator match {
            case Operator.unknown(instructionName) => {
              done = true
              termDef = new TerminatorDef(Terminator.unknown(instructionName), None)
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
  def parseInstructionDef(): InstructionDef = {
    val result = try parseResult()
    val body = parseInstruction()
    val sourceInfo = try parseSourceInfo()
    body match {
      case Instruction.operator(op) => InstructionDef.operator(new OperatorDef(result, op, sourceInfo))
      case Instruction.terminator(terminator) => {
        if(result.nonEmpty) throw parseError("terminator instruction shouldn't have any results")
        InstructionDef.terminator(new TerminatorDef(terminator, sourceInfo))
      }
    }
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#instruction-set
  @throws[Error]
  def parseInstruction(): Instruction = {
    val instructionName = try take(x => x.isLetter || x == "_")
    try {
      try parseInstructionBody(instructionName)
    } catch {
      // Try to recover to a point where resuming the parsing is sensible
      // by skipping until the end of this line. This is only a heuristic:
      // I don't think that the SIL specification guarantees that.
      case _ => {
        skip(_ != "\n" )
        Instruction.operator(Operator.unknown(instructionName))
      }
    }
  }

  @throws[Error]
  def parseInstructionBody(instructionName: String): Instruction = {
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
        val tpe = try parseType()
        val attributes = try parseUntilNil( try parseDebugAttribute )
        Instruction.operator(Operator.allocStack(tpe, attributes))
      }
      case "alloc_ref" => {
        null // TODO: NPOTP
      }
      case "alloc_ref_dynamic" => {
        null //  TODO: NPOTP
      }
      case "alloc_box" => {
        val tpe = try parseType()
        val attributes = try parseUntilNil( try parseDebugAttribute )
        Instruction.operator(Operator.allocBox(tpe, attributes))
      }
      case "alloc_value_buffer" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "alloc_global" => {
        val name = try parseGlobalName()
        Instruction.operator(Operator.allocGlobal(name))
      }
      case "dealloc_stack" => {
        val operand = try parseOperand()
        Instruction.operator(Operator.deallocStack(operand))
      }
      case "dealloc_box" => {
        null // TODO: NPTOP LP
      }
      case "project_box" => {
        null // TODO: NPOTP
      }
      case "dealloc_ref" => {
        null // TODO: NPOTP LP
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
        val operand = try parseOperand()
        val attributes = parseUntilNil(parseDebugAttribute)
        Instruction.operator(Operator.debugValue(operand, attributes))
      }
      case "debug_value_addr" => {
        val operand = try parseOperand()
        val attributes = parseUntilNil(parseDebugAttribute)
        Instruction.operator(Operator.debugValueAddr(operand, attributes))
      }

        // *** ACCESSING MEMORY ***

      case "load" => {
        var ownership : Option[LoadOwnership] = None
        if (skip("[copy]")) {
          ownership = Some(LoadOwnership.copy)
        } else if (skip("[take]")) {
          ownership = Some(LoadOwnership.take)
        } else if (skip("[trivial]")) {
          ownership = Some(LoadOwnership.trivial)
        }
        val operand = try parseOperand()
        Instruction.operator(Operator.load(ownership, operand))
      }
      case "store" => {
        val value = try parseValue()
        try take("to")
        var ownership : Option[StoreOwnership] = None
        if (skip("[init]")) {
          ownership = Some(StoreOwnership.init)
        } else if (skip("[trivial]")) {
          ownership = Some(StoreOwnership.trivial)
        }
        val operand = try parseOperand()
        Instruction.operator(Operator.store(value, ownership, operand))
      }
      case "load_borrow" => {
        null // TODO: NPOTP
      }
      case "end_borrow" => {
        val operand = try parseOperand()
        Instruction.operator(Operator.endBorrow(operand))
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
        val value = try parseValue()
        try this.take("to")
        val initialization = skip("[initialization]")
        val operand = try parseOperand()
        Instruction.operator(Operator.copyAddr(take, value, initialization, operand))
      }
      case "destroy_addr" => {
        null // TODO: NPOTP LP
      }
      case "index_addr" => {
        val addr = try parseOperand()
        try take(",")
        val index = try parseOperand()
        Instruction.operator(Operator.indexAddr(addr, index))
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
        try take("[")
        val access = parseAccess()
        try take("]")
        try take("[")
        val enforcement = parseEnforcement()
        try take("]")
        val noNestedConflict = skip("[no_nested_conflict]")
        val builtin = skip("[builtin]")
        val operand = try parseOperand()
        Instruction.operator(Operator.beginAccess(access, enforcement, noNestedConflict, builtin, operand))
      }
      case "end_access" => {
        val abort = skip("[abort]")
        val operand = try parseOperand()
        Instruction.operator(Operator.endAccess(abort, operand))
      }
      case "begin_unpaired_access" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "end_unpaired_access" => {
        throw parseError("unhandled instruction") // NSIP
      }

        // *** REFERENCE COUNTING ***

      case "strong_retain" => {
        val operand = try parseOperand()
        Instruction.operator(Operator.strongRetain(operand))
      }
      case "strong_release" => {
        val operand = try parseOperand()
        Instruction.operator(Operator.strongRelease(operand))
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
        null // TODO: NPOTP LP
      }
      case "store_weak" => {
        null // TODO: NPOTP
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
        val operand = try parseOperand()
        try take("on")
        val on = try parseOperand()
        Instruction.operator(Operator.markDependence(operand, on))
      }
      case "is_unique" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "is_escaping_closure" => {
        null // TODO: NPOTP
      }
      case "copy_block" => {
        null // TODO: NPOTP
      }
      case "copy_block_without_escaping" => {
        null // TODO: NPOTP
      }
      // builtin "unsafeGuaranteed" not sure what to do about this one
      // builtin "unsafeGuaranteedEnd" not sure what to do about this one

        // *** LITERALS ***

      case "function_ref" => {
        val name = try parseGlobalName()
        try take(":")
        val tpe = try parseType()
        Instruction.operator(Operator.functionRef(name, tpe))
      }
      case "dynamic_function_ref" => {
        null // TODO: NPOTP
      }
      case "prev_dynamic_function_ref" => {
        null // TODO: NPOTP
      }
      case "global_addr" => {
        val name = try parseGlobalName()
        try take(":")
        val tpe = try parseType()
        Instruction.operator(Operator.globalAddr(name, tpe))
      }
      case "global_value" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "integer_literal" => {
        val tpe = try parseType()
        try take(",")
        val value = try parseInt()
        Instruction.operator(Operator.integerLiteral(tpe, value))
      }
      case "float_literal" => {
        val tpe = try parseType()
        try take(",")
        try take("0x")
        val value = try take((x : Char) => x.toString.matches("^[0-9a-fA-F]+$"))
        Instruction.operator(Operator.floatLiteral(tpe, value))
      }
      case "string_literal" => {
        val encoding = try parseEncoding()
        val value = try parseString()
        Instruction.operator(Operator.stringLiteral(encoding, value))
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
        val archeType = try parseType()
        try take(",")
        val declRef = try parseDeclRef()
        try take(":")
        val declType = try parseNakedType()
        try take(":")
        val tpe = try parseType()
        Instruction.operator(Operator.witnessMethod(archeType, declRef, declType, tpe))
      }

        // *** FUNCTION APPLICATION ***

      case "apply" => {
        val nothrow = skip("[nothrow]")
        val value = try parseValue()
        val substitutions = parseNilOrMany("<", ",",">", parseNakedType).get
        val arguments = try parseMany("(",",",")", parseValue)
        try take(":")
        val tpe = try parseType()
        Instruction.operator(Operator.apply(nothrow,value,substitutions,arguments,tpe))
      }
      case "begin_apply" => {
        val nothrow = skip("[nothrow]")
        val value = try parseValue()
        val s : Option[Array[Type]] = parseNilOrMany("<",",",">",parseNakedType)
        val substitutions = if (s.nonEmpty) s.get else new Array[Type](0)
        val arguments = try parseMany("(",",",")", parseValue)
        try take(":")
        val tpe = try parseType()
        Instruction.operator(Operator.beginApply(nothrow, value, substitutions, arguments, tpe))
      }
      case "abort_apply" => {
        null // TODO: NPOTP
      }
      case "end_apply" => {
        val value = try parseValue()
        Instruction.operator(Operator.endApply(value))
      }
      case "partial_apply" => {
        val calleeGuaranteed = skip("[callee_guaranteed]")
        val onStack = skip("[on_stack]")
        val value = try parseValue()
        val s = parseNilOrMany("<",",",">", parseNakedType)
        val substitutions = if (s.nonEmpty) s.get else new Array[Type](0)
        val arguments = try parseMany("(",",",")", parseValue)
        try take(":")
        val tpe = try parseType()
        Instruction.operator(Operator.partialApply(calleeGuaranteed,onStack,value,substitutions,arguments,tpe))
      }
      case "builtin" => {
        val name = try parseString()
        val operands = try parseMany("(", ",", ")", parseOperand)
        try take(":")
        val tpe = try parseType()
        Instruction.operator(Operator.builtin(name, operands, tpe))
      }

        // *** METATYPES ***

      case "metatype" => {
        val tpe = try parseType()
        Instruction.operator(Operator.metatype(tpe))
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
        val operand = try parseOperand()
        Instruction.operator(Operator.retainValue(operand))
      }
      case "retain_value_addr" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "unmanaged_retain_value" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "copy_value" => {
        val operand = try parseOperand()
        Instruction.operator(Operator.copyValue(operand))
      }
      case "release_value" => {
        val operand = try parseOperand()
        Instruction.operator(Operator.releaseValue(operand))
      }
      case "release_value_addr" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "unmanaged_release_value" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "destroy_value" => {
        val operand = try parseOperand()
        Instruction.operator(Operator.destroyValue(operand))
      }
      case "autorelease_value" => {
        null // TODO: NPOTP
      }
      case "tuple" => {
        val elements = try parseTupleElements()
        Instruction.operator(Operator.tuple(elements))
      }
      case "tuple_extract" => {
        val operand = try parseOperand()
        try take(",")
        val declRef = try parseInt()
        Instruction.operator(Operator.tupleExtract(operand, declRef))
      }
      case "tuple_element_addr" => {
        null // TODO: NPOTP
      }
      case "destructure_tuple" => {
        val operand = try parseOperand()
        Instruction.operator(Operator.destructureTuple(operand))
      }
      case "struct" => {
        val tpe = try parseType()
        val operands = try parseMany("(",",",")", parseOperand)
        Instruction.operator(Operator.struct(tpe, operands))
      }
      case "struct_extract" => {
        val operand = try parseOperand()
        try take(",")
        val declRef = try parseDeclRef()
        Instruction.operator(Operator.structExtract(operand, declRef))
      }
      case "struct_element_addr" => {
        val operand = try parseOperand()
        try take(",")
        val declRef = try parseDeclRef()
        Instruction.operator(Operator.structElementAddr(operand, declRef))
      }
      case "destructure_struct" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "object" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "ref_element_addr" => {
        null // TODO: NPOTP
      }
      case "ref_tail_addr" => {
        throw parseError("unhandled instruction") // NSIP
      }

        // *** ENUMS ***

      case "enum" => {
        val tpe = try parseType()
        try take(",")
        val declRef = try parseDeclRef()
        val operand = if (skip(",")) Some(parseOperand()) else None
        Instruction.operator(Operator.`enum`(tpe, declRef, operand))
      }
      case "unchecked_enum_data" => {
        null // TODO: NPOTP
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
        val operand = try parseOperand()
        val cases = try parseUntilNil[Case](() => try parseCase(parseValue))
        try take(":")
        val tpe = try parseType()
        Instruction.operator(Operator.selectEnum(operand, cases, tpe))
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
        null // TODO: NPOTP
      }
      case "open_existential_metatype" => {
        null // TODO: NPOTP
      }
      case "alloc_existential_box" => {
        null // TODO: NPOTP
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
        val operand = try parseOperand()
        try take("to")
        val strict = skip("[strict]")
        val tpe = try parseType()
        Instruction.operator(Operator.pointerToAddress(operand, strict, tpe))
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
        val operand = try parseOperand()
        try take("to")
        val withoutActuallyEscaping = skip("[without_actually_escaping]")
        val tpe = try parseType()
        Instruction.operator(Operator.convertFunction(operand, withoutActuallyEscaping, tpe))
      }
      case "convert_escape_to_noescape" => {
        val notGuaranteed = skip("[not_guaranteed]")
        val escaped = skip("[escaped]")
        val operand = try parseOperand()
        try take("to")
        val tpe = try parseType()
        Instruction.operator(Operator.convertEscapeToNoescape(notGuaranteed, escaped, operand, tpe))
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
        val operand = try parseOperand()
        try take("to")
        val tpe = try parseType()
        Instruction.operator(Operator.thinToThickFunction(operand, tpe))
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
        val operand = try parseOperand()
        try take(",")
        val message = try parseString()
        Instruction.operator(Operator.condFail(operand, message))
      }

        // *** TERMINATORS ***

      case "unreachable" => {
        Instruction.terminator(Terminator.unreachable)
      }
      case "return" => {
        val operand = try parseOperand()
        Instruction.terminator(Terminator.ret(operand))
      }
      case "throw" => {
        null // TODO: NPOTP
      }
      case "yield" => {
        null // TODO: NPOTP
      }
      case "unwind" => {
        null // TODO: NPOTP
      }
      case "br" => {
        val label = try parseIdentifier()
        val o : Option[Array[Operand]] = parseNilOrMany("(",",",")", parseOperand)
        val operands = if (o.nonEmpty) o.get else new Array[Operand](0)
        Instruction.terminator(Terminator.br(label, operands))
      }
      case "cond_br" => {
        val cond = try parseValueName()
        try take(":")
        val trueLabel = try parseIdentifier()
        val to : Option[Array[Operand]] = parseNilOrMany("(",",",")",parseOperand)
        val trueOperands = if (to.nonEmpty) to.get else new Array[Operand](0)
        try take(",")
        val falseLabel = try parseIdentifier()
        val fo : Option[Array[Operand]] = parseNilOrMany("(",",",")",parseOperand)
        val falseOperands = if (fo.nonEmpty) to.get else new Array[Operand](0)
        Instruction.terminator(Terminator.condBr(cond, trueLabel, trueOperands, falseLabel, falseOperands))
      }
      case "switch_value" => {
        null // TODO: NPOTP
      }
      case "select_value" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "switch_enum" => {
        val operand = try parseOperand()
        val cases = try parseUntilNil[Case]( () => try parseCase(parseIdentifier))
        Instruction.terminator(Terminator.switchEnum(operand, cases))
      }
      case "switch_enum_addr" => {
        null // TODO: NPOTP
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
        null // TODO: NPOTP
      }

        // *** INSTRUCTIONS THAT TENSORFLOW PARSES BUT ARE NO LONGER IN SIL.rst ***

      case "begin_borrow" => {
        val operand = try parseOperand()
        Instruction.operator(Operator.beginBorrow(operand))
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
  def parseAccess(): Access = {
    if(skip("deinit")) Access.deinit
    if(skip("init")) Access.init
    if(skip("modify")) Access.modify
    if(skip("read")) Access.read
    throw parseError("unknown access")
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#basic-blocks
  @throws[Error]
  def parseArgument(): Argument = {
    val valueName = try parseValueName()
    try take(":")
    val tpe = try parseType()
    new Argument(valueName, tpe)
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#switch-enum
  @throws[Error]
  def parseCase(parseElement: () => String): Option[Case] = {
    maybeParse(() => {
      if(!skip(",")) None
      if (skip("case")) {
        val declRef = try parseDeclRef()
        try take(":")
        val identifier = try parseElement()
        Some(Case.cs(declRef, identifier))
      } else if (skip("default")) {
        val identifier = try parseElement()
        Some(Case.default(identifier))
      } else {
        None
      }
    })
  }

  @throws[Error]
  def parseConvention(): Convention = {
    try take(":")
    var result: Convention = null
    if (skip("c")) {
      result = Convention.c
    } else if (skip("method")) {
      result = Convention.method
    } else if (skip("thin")) {
      result = Convention.thin
    } else if (skip("witness_method")) {
      try take(":")
      val tpe = try parseNakedType()
      result = Convention.witnessMethod(tpe)
    } else {
      throw parseError("unknown convention")
    }
    try take(")")
    result
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#debug-value
  @throws[Error]
  def parseDebugAttribute(): Option[DebugAttribute] = {
    maybeParse(() => {
      if(!skip(",")) None
      if(skip("argno")) DebugAttribute.argno(parseInt())
      if(skip("name")) DebugAttribute.name(parseString())
      if(skip("let")) DebugAttribute.let
      if(skip("var")) DebugAttribute.variable
      None
    })
  }

  @throws[Error]
  def parseDeclKind(): Option[DeclKind] = {
    if(skip("allocator")) Some(DeclKind.allocator)
    if(skip("deallocator")) Some(DeclKind.deallocator)
    if(skip("destroyer")) Some(DeclKind.destroyer)
    if(skip("enumelt")) Some(DeclKind.enumElement)
    if(skip("getter")) Some(DeclKind.getter)
    if(skip("globalaccessor")) Some(DeclKind.globalAccessor)
    if(skip("initializer")) Some(DeclKind.initializer)
    if(skip("ivardestroyer")) Some(DeclKind.ivarDestroyer)
    if(skip("ivarinitializer")) Some(DeclKind.ivarInitializer)
    if(skip("setter")) Some(DeclKind.setter)
    None
  }

  @throws[Error]
  def parseDeclRef(): DeclRef = {
    try take("#")
    val name = new ArrayBuffer[String]
    var break = false
    while (!break) {
      val identifier = try parseIdentifier()
      name.append(identifier)
      if (!skip(".")) {
        break = true
      }
    }
    if (!skip("!")) {
      new DeclRef(name.toArray, None, None)
    }
    val kind = try parseDeclKind()
    if (kind.nonEmpty && !skip(".")) {
      new DeclRef(name.toArray, kind, None)
    }
    val level = try parseInt()
    new DeclRef(name.toArray, kind, Some(level))
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#string-literal
  @throws[Error]
  def parseEncoding(): Encoding = {
    if(skip("objc_selector")) Encoding.objcSelector
    if(skip("utf8")) Encoding.utf8
    if(skip("utf16")) Encoding.utf16
    throw parseError("unknown encoding")
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#begin-access
  @throws[Error]
  def parseEnforcement(): Enforcement = {
    if(skip("dynamic")) Enforcement.dynamic
    if(skip("")) Enforcement.static
    if(skip("")) Enforcement.unknown
    if(skip("")) Enforcement.unsafe
    throw parseError("unknown enforcement")
  }

  // Reverse-engineered from -emit-sil
  @throws[Error]
  def parseFunctionAttribute(): FunctionAttribute = {
    @throws[Error]
    def parseDifferentiable(): FunctionAttribute = {
      try take("[differentiable")
      val spec = try take(_ != "]" )
      try take("]")
      FunctionAttribute.differentiable(spec)
    }

    @throws[Error]
    def parseSemantics(): FunctionAttribute = {
      try take("[_semantics")
      val value = try parseString()
      try take("]")
      FunctionAttribute.semantics(value)
    }

    if(skip("[always_inline]")) FunctionAttribute.alwaysInline
    if(peek("[differentiable")) try parseDifferentiable()
    if(skip("[dynamically_replacable]")) FunctionAttribute.dynamicallyReplacable
    if(skip("[noinline]")) FunctionAttribute.noInline
    if(skip("[ossa]")) FunctionAttribute.noncanonical(NoncanonicalFunctionAttribute.ownershipSSA)
    if(skip("[readonly]")) FunctionAttribute.readonly
    if(peek("[_semantics")) try parseSemantics()
    if(skip("[serialized]")) FunctionAttribute.serialized
    if(skip("[thunk]")) FunctionAttribute.thunk
    if(skip("[transparent]")) FunctionAttribute.transparent
    throw parseError("unknown function attribute")
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#functions
  @throws[Error]
  def parseGlobalName(): String = {
    val start = position()
    if(skip("@")) {
      // TODO(#14): Make name parsing more thorough.
      val name = try take(x => x == "$" || x.isLetterOrDigit || x == "_" )
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
      s"\"${try parseString()}\""
    } else {
      val start = position()
      // TODO(#14): Make name parsing more thorough.
      val identifier = try take(x => x.isLetterOrDigit || x == "_")
      if (!identifier.isEmpty) return identifier
      throw parseError("identifier expected", Some(start))
    }
  }

  @throws[Error]
  def parseInt(): Int = {
    // TODO(#26): Make number parsing more thorough.
    val start = position()
    val radix = if(skip("0x")) 16 else 10
    val s = try take(x => x == "-" || x == "+" || Character.digit(x, 16) != -1)
    val value = try {
        Integer.parseInt(s, radix)
      } catch {
        case _ => throw parseError("integer literal expected", Some(start))
      }
    value
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#linkage
  @throws[Error]
  def parseLinkage(): Linkage = {
    // The order in here is a bit relaxed because longer words need to come
    // before the shorter ones to parse correctly.
    if(skip("hidden_external")) Linkage.hiddenExternal
    if(skip("hidden")) Linkage.hidden
    if(skip("private_external")) Linkage.privateExternal
    if(skip("private")) Linkage.priv
    if(skip("public_external")) Linkage.publicExternal
    if(skip("non_abi")) Linkage.publicNonABI
    if(skip("public")) Linkage.public
    if(skip("shared_external")) Linkage.sharedExternal
    if(skip("shared")) Linkage.shared
    Linkage.public
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#debug-information
  @throws[Error]
  def parseLoc(): Option[Loc] = {
    if(!skip("loc")) None
    val path = try parseString()
    try take(":")
    val line = try parseInt()
    try take(":")
    val column = try parseInt()
    Some(new Loc(path, line, column))
  }

  // Parses verbatim string representation of a type.
  // This is different from `parseType` because most usages of types in SIL are prefixed with
  // `$` (so it made sense to have a shorter name for that common case).
  // Type format has been reverse-engineered since it doesn't seem to be mentioned in the spec.
  @throws[Error]
  def parseNakedType(): Type = {
    if (skip("<")) {
      val params = new ArrayBuffer[String]
      var break = false
      while (!break) {
        val name = try parseTypeName()
        params.append(name)
        if (peek("where") || peek(">")) {
          break = true
        } else {
          try take(",")
        }
      }
      var reqs = new ArrayBuffer[TypeRequirement]
      if (peek("where")) {
        reqs = try parseMany("where", ",", ">", try parseTypeRequirement).to(ArrayBuffer)
      } else {
        reqs.clear()
        try take(">")
      }
      val tpe = try parseNakedType()
      Type.genericType(params.toArray, reqs.toArray, tpe)
    } else if (peek("@")) {
      val attrs = try parseMany("@", try parseTypeAttribute)
      val tpe = try parseNakedType()
      Type.attributedType(attrs, tpe)
    } else if (skip("*")) {
      val tpe = try parseNakedType()
      Type.addressType(tpe)
    } else if (skip("[")) {
      val subtype = try parseNakedType()
      try take("]")
      Type.specializedType(Type.namedType("Array"), Array[Type]{subtype})
    } else if (peek("(")) {
      val types: Array[Type] = try parseMany("(",",",")", try parseNakedType)
      if (skip("->")) {
        val result = try parseNakedType()
        Type.functionType(types, result)
      } else {
        if (types.count == 1) {
         types(0)
        } else {
          Type.tupleType(types)
        }
      }
    } else {
      @throws[Error]
      def grow(tpe: Type): Type = {
        if (peek("<")) {
          val types = try parseMany("<",",",">", try parseNakedType)
          try grow(Type.specializedType(tpe, types))
        } else if (skip(".")) {
          val name = try parseTypeName()
          try grow(Type.selectType(tpe, name))
        } else {
          tpe
        }
      }
      val name = try parseTypeName()
      val base: Type = if (name != "Self") Type.namedType(name) else Type.selfType
      try grow(base)
    }
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#values-and-operands
  @throws[Error]
  def parseOperand(): Operand = {
    val valueName = try parseValueName()
    try take(":")
    val tpe = try parseType()
    new Operand(valueName, tpe)
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#basic-blocks
  @throws[Error]
  def parseResult(): Option[Result] = {
    if(peek("%")) {
      val valueName = try parseValueName()
      try take("=")
      Some(new Result(Array(valueName)))
    } else if(peek("(")) {
      val valueNames = try parseMany("(", ",", ")", try parseValueName)
      try take("=")
      Some(new Result(valueNames))
    } else {
      None
    }
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#debug-information
  @throws[Error]
  def parseScopeRef(): Option[Int] = {
    if(!skip("scope")) None
    Some(try parseInt())
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#basic-blocks
  @throws[Error]
  def parseSourceInfo(): Option[SourceInfo] = {
    // NB: The SIL docs say that scope refs precede locations, but this is
    //     not true once you look at the compiler outputs or its source code.
    if(!skip(",")) None
    val loc = try parseLoc()
    // NB: No skipping if we failed to parse the location.
    val scopeRef = if(loc.isEmpty || skip(",")) try parseScopeRef() else None
    // We've skipped the comma, so failing to parse any of those two
    // components is an error.
    if(scopeRef.isEmpty && loc.isEmpty) throw parseError("Failed to parse source info")
    Some(new SourceInfo(scopeRef, loc))
  }

  @throws[Error]
  def parseString(): String = {
    // TODO(#24): Parse string literals with control characters.
    try take("\"")
    val s = try take(_ != "\"")
    try take("\"")
    s
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#tuple
  def parseTupleElements(): TupleElements = {
    if (peek("$")) {
      val tpe = try parseType()
      val values = try parseMany("(",",",")", try parseValue)
      TupleElements.labeled(tpe, values)
    } else {
      val operands = try parseMany("(",",",")", try parseOperand)
      TupleElements.unlabeled(operands)
    }
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#sil-types
  @throws[Error]
  def parseType(): Type = {
    null // TODO: still needs conversion from the Swift codebase
  }

  @throws[Error]
  def parseTypeAttribute(): TypeAttribute = {
    if(skip("@callee_guaranteed")) TypeAttribute.calleeGuaranteed
    if(skip("@convention")) TypeAttribute.convention(try parseConvention)
    if(skip("@guaranteed")) TypeAttribute.guaranteed
    if(skip("@in_guaranteed")) TypeAttribute.inGuaranteed
    // Must appear before "in" to parse correctly.
    if(skip("@inout")) TypeAttribute.inout
    if(skip("@in")) TypeAttribute.in
    if(skip("@noescape")) TypeAttribute.noescape
    if(skip("@thick")) TypeAttribute.thick
    if(skip("@out")) TypeAttribute.out
    if(skip("@owned")) TypeAttribute.owned
    if(skip("@thin")) TypeAttribute.thin
    if(skip("@yield_once")) TypeAttribute.yieldOnce
    if(skip("@yields")) TypeAttribute.yields
    throw parseError("unknown attribute")
  }

  @throws[Error]
  def parseTypeName(): String = {
    val start = position()
    val name : String = take(x => x.isLetter || Character.isDigit(x) || x == "_")
    if (!name.isEmpty) {
      return name
    }
    throw parseError("type name expected", Some(start))
  }

  @throws[Error]
  def parseTypeRequirement(): TypeRequirement = {
    val lhs = try parseNakedType()
    if (skip(":")) {
      val rhs = try parseNakedType()
      TypeRequirement.conformance(lhs, rhs)
    } else if (skip("==")) {
      val rhs = try parseNakedType()
      TypeRequirement.equality(lhs, rhs)
    } else {
      throw parseError("expected '==' or ':'")
    }
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#values-and-operands
  @throws[Error]
  def parseValue(): String = {
    if(peek("%")) {
      try parseValueName()
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
    val identifier = try parseIdentifier()
    "%" + identifier
  }
}
