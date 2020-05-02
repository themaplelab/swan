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
    val result : String = take(whileFn)
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
      val result = f()
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
    Some(parseMany(pre, sep, suf, parseOne))
  }

  @throws[Error]
  protected def parseMany[T](pre: String, sep: String, suf: String, parseOne: () => T): Array[T] = {
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
    take("sil")
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
            case Operator.unknown(instructionName) => { done = true; termDef = new TerminatorDef(Terminator.unknown(instructionName), None) }
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
    val instructionName = take(x => x.isLetter || x == "_")
    try {
      null // TODO: Swift: parseInstructionBody(instructionName)
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

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#functions
  @throws[Error]
  def parseGlobalName(): String = {
    val start = position
    if(skip("@")) {
      // TODO(#14): Make name parsing more thorough.
      val name = take(x => x == "$" || x.isLetterOrDigit || x == "_" )
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
      val start = position
      // TODO(#14): Make name parsing more thorough.
      val identifier = take(x => x.isLetterOrDigit || x == "_")
      if (!identifier.isEmpty) return identifier
      throw parseError("identifier expected", Some(start))
    }
  }

  @throws[Error]
  def parseInt(): Int = {
    // TODO(#26): Make number parsing more thorough.
    val start = position
    val radix = if(skip("0x")) 16 else 10
    val s = take(x => x == "-" || x == "+" || Character.digit(x, 16) != -1)
    val value = try { Integer.parseInt(s, radix) } catch { case _ => throw parseError("integer literal expected", Some(start))}
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

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#basic-blocks
  @throws[Error]
  def parseArgument(): Argument = {
    val valueName = try parseValueName()
    try take(":")
    val tpe = try parseType()
    new Argument(valueName, tpe)
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

  @throws[Error]
  def parseString(): String = {
    // TODO(#24): Parse string literals with control characters.
    try take("\"")
    val s = take(_ != "\"")
    try take("\"")
    s
  }

  @throws[Error]
  def parseType(): Type = {
    null // TODO: still needs conversion from the Swift codebase
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
    val start = position
    if(!skip("%")) throw parseError("value expected", Some(start))
    val identifier = try parseIdentifier()
    "%" + identifier
  }
}
