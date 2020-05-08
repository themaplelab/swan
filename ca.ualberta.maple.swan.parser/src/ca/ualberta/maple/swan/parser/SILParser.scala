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

  @throws[Error]
  protected def parseNilOrMany[T](pre: String, parseOne: () => T): Option[Array[T]] = {
    if (!peek(pre)) {
      return None
    }
    Some(parseMany(pre, parseOne))
  }

  @throws[Error]
  protected def parseUntilNil[T](parseOne: () => Option[T]): Array[T] = {
    val result = new ArrayBuffer[T]
    var break = false;
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
      val element = parseOne()
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
      parseInstructionBody(instructionName)
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
    instructionName match {
      case "alloc_stack" => {
        val `type` = parseType();
        val attributes = parseUntilNil( parseDebugAttribute )
        Instruction.operator(Operator.allocStack(`type`, attributes))
      } case "apply" => {
        val nothrow = skip("[nothrow]")
        val value = parseValue()
        val substitutions = parseNilOrMany("<", ",",">", parseNakedType).get
        val arguments = parseMany("(",",",")", parseValue)
        take(":")
        val `type` = parseType()
        Instruction.operator(Operator.apply(nothrow,value,substitutions,arguments,`type`))
      } case "begin_access" => {
        take("[")
        val access = parseAccess()
        take("]")
        take("[")
        val enforcement = parseEnforcement()
        take("]")
        val noNestedConflict = skip("[no_nested_conflict]")
        val builtin = skip("[builtin]")
        val operand = parseOperand()
        Instruction.operator(Operator.beginAccess(access, enforcement, noNestedConflict, builtin, operand))
      } case "begin_apply" => { // TODO: Continue instructions
      } case "begin_borrow" => {
      } case "br" => {
      } case "builtin" => {
      } case "cond_br" => {
      } case "cond_fail" => {
      } case "convert_escape_to_noescape" => {
      } case "convert_function" => {
      } case "copy_addr" => {
      } case "copy_value" => {
      } case "dealloc_stack" => {
      } case "debug_value" => {
      } case "debug_value_addr" => {
      } case "destroy_value" => {
      } case "destructure_tuple" => {
      } case "end_access" => {
      } case "end_apply" => {
      } case "end_borrow" => {
      } case "enum" => {
      } case "float_literal" => {
      } case "function_ref" => {
      } case "global_addr" => {
      } case "index_addr" => {
      } case "integer_literal" => {
      } case "load" => {
      } case "metatype" => {
      } case "mark_dependence" => {
      } case "partial_apply" => {
      } case "pointer_to_address" => {
      } case "return" => {
      } case "release_value" => {
      } case "retain_value" => {
      } case "select_enum" => {
      } case "store" => {
      } case "string_literal" => {
      } case "strong_release" => {
      } case "strong_retain" => {
      } case "struct" => {
      } case "struct_element_addr" => {
      } case "struct_extract" => {
      } case "switch_enum" => {
      } case "thin_to_thick_function" => {
      } case "tuple" => {
      } case "tuple_extract" => {
      } case "unreachable" => {
      } case "witness_method" => {
      } case _ => {
        val _ = skip(_ != "\n")
        Instruction.operator(Operator.unknown(instructionName))
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
        val declRef = parseDeclRef()
        take(":")
        val identifier = parseElement()
        Some(Case.cs(declRef, identifier))
      } else if (skip("default")) {
        val identifier = parseElement()
        Some(Case.default(identifier))
      } else {
        None
      }
    })
  }

  @throws[Error]
  def parseConvention(): Convention = {
    take(":")
    var result: Convention = null
    if (skip("c")) {
      result = Convention.c
    } else if (skip("method")) {
      result = Convention.method
    } else if (skip("thin")) {
      result = Convention.thin
    } else if (skip("witness_method")) {
      take(":")
      val `type` = parseNakedType()
      result = Convention.witnessMethod(`type`)
    } else {
      throw parseError("unknown convention")
    }
    take(")")
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
      new DeclRef(name.toArray, None, None)
    }
    val kind = parseDeclKind()
    if (kind.nonEmpty && !skip(".")) {
      new DeclRef(name.toArray, kind, None)
    }
    val level = parseInt()
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
      val start = position()
      // TODO(#14): Make name parsing more thorough.
      val identifier = take(x => x.isLetterOrDigit || x == "_")
      if (!identifier.isEmpty) return identifier
      throw parseError("identifier expected", Some(start))
    }
  }

  @throws[Error]
  def parseInt(): Int = {
    // TODO(#26): Make number parsing more thorough.
    val start = position()
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
        val name = parseTypeName()
        params.append(name)
        if (peek("where") || (peek(">"))) {
          break = true
        } else {
          take(",")
        }
      }
      var reqs = new ArrayBuffer[TypeRequirement]
      if (peek("where")) {
        reqs = parseMany("where", ",", ">", parseTypeRequirement).to(ArrayBuffer)
      } else {
        reqs.clear()
        take(">")
      }
      val `type` = parseNakedType()
      Type.genericType(params.toArray, reqs.toArray, `type`)
    } else if (peek("@")) {
      val attrs = parseMany("@", parseTypeAttribute)
      val `type` = parseNakedType()
      Type.attributedType(attrs, `type`)
    } else if (skip("*")) {
      val `type` = parseNakedType()
      Type.addressType(`type`)
    } else if (skip("[")) {
      val subtype = parseNakedType()
      take("]")
      Type.specializedType(Type.namedType("Array"), Array[Type]{subtype})
    } else if (peek("(")) {
      val types: Array[Type] = parseMany("(",",",")", parseNakedType)
      if (skip("->")) {
        val result = parseNakedType()
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
      def grow(`type`: Type): Type = {
        if (peek("<")) {
          val types = parseMany("<",",",">", parseNakedType)
          grow(Type.specializedType(`type`, types))
        } else if (skip(".")) {
          val name = parseTypeName()
          grow(Type.selectType(`type`, name))
        } else {
          `type`
        }
      }
      val name = parseTypeName()
      val base: Type = if (name != "Self") Type.namedType(name) else Type.selfType()
      grow(base)
    }
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#values-and-operands
  @throws[Error]
  def parseOperand(): Operand = {
    val valueName = parseValueName()
    take(":")
    val `type` = parseType()
    new Operand(valueName, `type`)
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
    val s = take(_ != "\"")
    try take("\"")
    s
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#tuple
  def parseTupleElements(): TupleElements = {
    if (peek("$")) {
      val `type` = parseType()
      val values = parseMany("(",",",")", parseValue)
      TupleElements.labeled(`type`, values)
    } else {
      val operands = parseMany("(",",",")", parseOperand)
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
    if(skip("@convention")) TypeAttribute.convention
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
      name
    }
    throw parseError("type name expected", Some(start))
  }

  @throws[Error]
  def parseTypeRequirement(): TypeRequirement = {
    val lhs = parseNakedType()
    if (skip(":")) {
      val rhs = parseNakedType()
      TypeRequirement.conformance(lhs, rhs)
    } else if (skip("==")) {
      val rhs = parseNakedType()
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
