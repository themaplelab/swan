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

package ca.ualberta.maple.swan.ir

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util

import ca.ualberta.maple.swan.utils.Logging

import scala.collection.mutable.ArrayBuffer
import scala.collection.{immutable, mutable}
import scala.reflect.ClassTag
import scala.util.control.Breaks
import scala.util.control.Breaks.{break, breakable}

// The purpose of this parser is to enable writing plain-text SWIRL models.
// Therefore, the printer and parser do not handle all SWIRL information, such
// as the DDG.
class SWIRLParser extends SWIRLPrinter {

  var refTable: RefTable = new RefTable()
  val instantiatedTypes: mutable.HashSet[String] = new mutable.HashSet[String]()

  // Default constructor should not be called.

  private var path: String = _
  private var chars: Array[Char] = _
  private var cursor: Int = 0
  def position(): Int = { cursor }

  def this(path: Path) = {
    this()
    this.path = path.toString
    val data : Array[Byte] = if (Files.exists(path)) {
      Files.readAllBytes(path: Path)
    } else {
      throw parseError("file not found: " + this.path)
      null
    }: Array[Byte]
    val text = new String(data, StandardCharsets.UTF_8)
    this.chars = text.toCharArray
    skipTrivia()
  }

  def this(s: String, model: Boolean = false) = {
    this()
    this.path = if (model) "<models>" else "<memory>"
    this.chars = s.toCharArray
    skipTrivia()
  }

  protected def peek(query: String): Boolean = {
    if (query.isEmpty) throw parseError("query is empty")
    util.Arrays.equals(
      util.Arrays.copyOfRange(chars, cursor, cursor + query.length),
      query.toCharArray)
  }

  @throws[Error]
  protected def take(query: String, skip: Boolean = true): Unit = {
    if (!peek(query)) {
      throw parseError(query + " expected")
    }
    cursor += query.length
    if (skip) {
      skipTrivia()
    }
  }

  protected def skipNoTrivia(query: String): Boolean = {
    if (!peek(query)) return false
    cursor += query.length
    true
  }

  protected def skip(query: String): Boolean = {
    if (!peek(query)) return false
    cursor += query.length
    skipTrivia()
    true
  }

  protected def take(whileFn: Char => Boolean): String = {
    val result = chars.view.takeRight(chars.length - cursor).view.takeWhile(whileFn).toArray
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
    } else if (skipNoTrivia("//")) { // single-line comments
      while (cursor < chars.length && chars(cursor) != '\n') {
        cursor += 1
      }
      skipTrivia()
    } else if (skipNoTrivia("/*")) { // multi-line comments
      var continue = true
      while (continue) {
        while (cursor < chars.length && chars(cursor) != '*') {
          cursor += 1
        }
        if (skip("*") && skip("/")) {
          continue = false
        }
      }
      skipTrivia()
    }
  }

  @throws[Error]
  // Really expensive call.
  protected def maybeParse[T](f: () => Option[T]) : Option[T] = {
    val savedCursor = cursor
    try {
      val result = f()
      if (result.isEmpty) {
        cursor = savedCursor
      }
      result
    } catch {
      case _: Error => {
        cursor = savedCursor
        None
      }
    }
  }

  @throws[Error]
  protected def parseNilOrMany[T:ClassTag](pre: String, sep: String = "", suf: String = "", parseOne: () => T): Option[ArrayBuffer[T]] = {
    if (!peek(pre)) return None
    Some(parseMany(pre, sep, suf, parseOne))
  }

  @throws[Error]
  protected def parseMany[T:ClassTag](pre: String, sep: String, suf: String, parseOne: () => T): ArrayBuffer[T] = {
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
          // In case (element,)
          if (peek(suf)) {
            break = true
          }
        }
      }
    }
    take(suf)
    result
  }

  @throws[Error]
  protected def parseNilOrMany[T:ClassTag](pre: String, parseOne: () => T): Option[ArrayBuffer[T]] = {
    if (!peek(pre)) return None
    Some(parseMany(pre, parseOne))
  }

  @throws[Error]
  protected def parseUntilNil[T:ClassTag](parseOne: () => Option[T]): ArrayBuffer[T] = {
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
    result
  }

  @throws[Error]
  protected def parseMany[T:ClassTag](pre: String, parseOne: () => T): ArrayBuffer[T] = {
    val result = new ArrayBuffer[T]
    do {
      val element = parseOne()
      result.append(element)
    } while (peek(pre))
    result
  }

  protected def parseError(message: String, at: Option[Int] = None): Error = {
    val position = if (at.isDefined) at.get else cursor
    val newlines: ArrayBuffer[Int] = new ArrayBuffer(0)
    chars.view.take(position).zipWithIndex.foreach(charIdx => {
      if (charIdx._1 == '\n') {
        newlines.append(charIdx._2)
      }
    })
    val newlineOffset = chars.view.slice(0, position).reverse.takeWhile(_ != '\n').size
    val line = newlines.length + 1
    val column = position - (if (newlines.isEmpty) 0 else newlines.last)
    val singleLine: Array[Char] =
      if (path == "<memory>") chars
      else chars.view.slice(position-newlineOffset, chars.length - 1).takeWhile(_ != '\n').toArray
    new Error(path, line, column, message, singleLine)
  }

  @throws[Error]
  def parseModule(): Module = {
    Logging.printInfo("Parsing " +
      { if (this.path == "<models>") "models module" else new File(this.path).getName })
    val startTime = System.nanoTime()
    if (!skip("swirl_stage raw")) {
      throw parseError("This parser only supports raw SWIRL")
    }
    val functions = ArrayBuffer[Function]()
    var done = false
    while(!done) {
      if(peek("func ")) {
        val function = parseFunction()
        if (function.name == "main") {
          functions.insert(0, function)
        } else {
          functions.append(function)
        }
      } else {
        Breaks.breakable {
          if(skip(_ != '\n')) Breaks.break()
        }
        if (this.cursor == this.chars.length) {
          done = true
        }
      }
    }
    Logging.printTimeStamp(2, startTime, "parsing", chars.count(_ == '\n'), "lines")
    val metadata = {
      this.path match {
        case "<models>" => new ModuleMetadata(None, None) {
          override def toString: String = {
            "models"
          }
        }
        case "<memory>" => new ModuleMetadata(None, None)
        case _ => new ModuleMetadata(Some(new File(path)), None)
      }
    }
    new Module(functions, None, None, metadata)
  }

  @throws[Error]
  def parseFunction(): Function = {
    refTable = new RefTable()
    instantiatedTypes.clear()
    take("func ")
    val attr = parseFunctionAttribute()
    val name = parseGlobalOrFunctionName()
    take(":")
    val tpe = parseType()
    val blocks = { parseNilOrMany("{", "", "}", parseBlock) }.getOrElse(ArrayBuffer.empty[Block])
    new Function(attr, name, tpe, blocks.to(ArrayBuffer), refTable, instantiatedTypes.to(immutable.HashSet))
  }


  protected def makeBlockRef(ref: String): BlockRef = {
    val blocks = refTable.blocks
    if (blocks.contains(ref)) {
      blocks.put(ref, blocks(ref))
      blocks(ref)
    } else {
      val blockRef = new BlockRef(ref)
      blocks.put(ref, blockRef)
      blockRef
    }
  }

  protected def parseBlockRef(): BlockRef = {
    makeBlockRef(parseIdentifier())
  }


  @throws[Error]
  def parseBlock(): Block = {
    val blockRef = parseBlockRef()
    val arguments = { parseNilOrMany("(", ",", ")", parseArgument) }.getOrElse(ArrayBuffer.empty[Argument])
    take(":")
    val (operatorDefs, terminatorDef) = parseInstructionDefs()
    new Block(blockRef, arguments, operatorDefs.to(ArrayBuffer), terminatorDef)
  }

  @throws[Error]
  def parseInstructionDefs(): (ArrayBuffer[RawOperatorDef], RawTerminatorDef) = {
    val operatorDefs = ArrayBuffer[RawOperatorDef]()
    var done = false
    while(!done){
      parseInstructionDef() match {
        case RawInstructionDef.operator(operatorDef) => operatorDefs.append(operatorDef)
        case RawInstructionDef.terminator(terminatorDef) => return (operatorDefs, terminatorDef)
      }
      if(peek("bb") || peek("}")) {
        done = true
      }
    }
    throw parseError("block is missing a terminator")
  }

  protected def makeSymbol(ref: String, tpe: Type): Symbol = {
    new Symbol(makeSymbolRef(ref), tpe)
  }

  protected def makeSymbolRef(ref: String): SymbolRef = {
    val symbols = refTable.symbols
    if (symbols.contains(ref)) {
      symbols.put(ref, symbols(ref))
      symbols(ref)
    } else {
      if (refTable.temporaryBBArgs.contains(ref)) {
        symbols.put(ref, refTable.temporaryBBArgs(ref))
        refTable.temporaryBBArgs.remove(ref)
        symbols(ref)
      } else {
        val symbolRef = new SymbolRef(ref)
        symbols.put(ref, symbolRef)
        symbolRef
      }
    }
  }

  @throws[Error]
  def parseInstructionDef(): RawInstructionDef = {
    // Operators do not necessarily need results
    val instruction = {
      if (peek("singleton_write") ||
          peek("field_write") ||
          peek("cond_fail") ||
          peek("pointer_write")) {
        parseOperatorWithNoResult()
      } else {
        val result = parseResult()
        parseInstruction(result)
      }
    }
    val sourceInfo = parseSourceInfo()
    instruction match {
      case Instruction.rawOperator(op) => RawInstructionDef.operator(new RawOperatorDef(op, sourceInfo))
      case Instruction.rawTerminator(terminator) => {
        RawInstructionDef.terminator(new RawTerminatorDef(terminator, sourceInfo))
      }
      case _ => throw new RuntimeException // impossible
    }
  }

  @throws[Error]
  def parseOperatorWithNoResult(): Instruction = {
    val instructionName = take(x => x.isLetter || x == '_')
    instructionName match {
      case "singleton_write" => {
        val value = makeSymbolRef(parseIdentifier())
        take("to")
        val field = parseBackTick()
        take("in")
        val tpe = parseIdentifier()
        Instruction.rawOperator(Operator.singletonWrite(value, tpe, field))
      }
      case "field_write" => {
        val attr = {
          if (skip("[")) {
            val a = Some(parseFieldWriteAttribute())
            take("]")
            a
          } else { None }
        }
        val value = makeSymbolRef(parseIdentifier())
        take("to")
        val obj = makeSymbolRef(parseIdentifier())
        take(",")
        val field = parseIdentifier()
        Instruction.rawOperator(Operator.fieldWrite(value, obj, field, attr))
      }
      case "cond_fail" => {
        val value = makeSymbolRef(parseIdentifier())
        Instruction.rawOperator(Operator.condFail(value))
      }
      case "pointer_write" => {
        val weak = skip("[weak]")
        val value = makeSymbolRef(parseIdentifier())
        take("to")
        val pointer = makeSymbolRef(parseIdentifier())
        Instruction.rawOperator(Operator.pointerWrite(value, pointer, weak))
      }
      case _ : String => {
        throw parseError("unknown instruction")
      }
    }
  }

  @throws[Error]
  def parseFieldWriteAttribute(): FieldWriteAttribute = {
    if (skip("pointer")) return FieldWriteAttribute.pointer
    if (skip("weak_pointer")) return FieldWriteAttribute.weakPointer
    throw parseError("unknown field write attribute")
  }

  @throws[Error]
  def parseResultSymbol(result: String): Symbol = {
    take(",")
    makeSymbol(result, parseType())
  }

  @throws[Error]
  def parseSymbolRef(): SymbolRef = {
    makeSymbolRef(parseIdentifier())
  }

  @throws[Error]
  def parseInstruction(possibleResult: Option[String]): Instruction = {
    val instructionName = take(x => x.isLetter || x == '_')
    if (possibleResult.nonEmpty) { // Operators with result
      val result = possibleResult.get
      instructionName match {
        case "new" => {
          val symbol = makeSymbol(result, parseType())
          instantiatedTypes.add(symbol.tpe.name)
          Instruction.rawOperator(Operator.neww(symbol))
        }
        case "assign" => {
          val from = parseSymbolRef()
          val bbArg = skip("[bb arg]")
          val symbol = parseResultSymbol(result)
          Instruction.rawOperator(Operator.assign(symbol, from, bbArg))
        }
        case "literal" => {
          if (skip("[string]")) {
            val value = parseString()
            val symbol = parseResultSymbol(result)
            Instruction.rawOperator(Operator.literal(symbol, Literal.string(value)))
          } else if (skip("[int]")) {
            val value = parseBigInt()
            val symbol = parseResultSymbol(result)
            Instruction.rawOperator(Operator.literal(symbol, Literal.int(value)))
          } else if (skip("[float]")) {
            val value = parseFloat()
            val symbol = parseResultSymbol(result)
            Instruction.rawOperator(Operator.literal(symbol, Literal.float(value)))
          } else {
            throw parseError("unknown literal type")
          }
        }
        case "dynamic_ref" => {
          val index = parseGlobalOrFunctionName()
          val symbol = parseResultSymbol(result)
          Instruction.rawOperator(Operator.dynamicRef(symbol, index))
        }
        case "builtin_ref" => {
          val index = parseGlobalOrFunctionNameWithTicks()
          val symbol = parseResultSymbol(result)
          Instruction.rawOperator(Operator.builtinRef(symbol, index))
        }
        case "function_ref" => {
          val index = parseGlobalOrFunctionName()
          val symbol = parseResultSymbol(result)
          Instruction.rawOperator(Operator.functionRef(symbol, index))
        }
        case "apply" => {
          val functionRef = parseSymbolRef()
          val arguments = parseMany("(",",",")", parseSymbolRef)
          val symbol = parseResultSymbol(result)
          Instruction.rawOperator(Operator.apply(symbol, functionRef, arguments))
        }
        case "singleton_read" => {
          val field = parseBackTick()
          take("from")
          val tpe = parseIdentifier()
          val symbol = parseResultSymbol(result)
          Instruction.rawOperator(Operator.singletonRead(symbol, tpe, field))
        }
        case "field_read" => {
          val alias: Option[SymbolRef] = {
            if (skip("[alias")) {
              val v = Some(parseSymbolRef())
              take("]")
              v
            } else {
              None
            }
          }
          val pointer = skip("[pointer]")
          val obj = parseSymbolRef()
          take(",")
          val field = parseIdentifier()
          val symbol = parseResultSymbol(result)
          Instruction.rawOperator(Operator.fieldRead(symbol, alias, obj, field, pointer))
        }
        case "unary_op" => {
          val operation = parseUnaryOperation()
          val operand = parseSymbolRef()
          val symbol = parseResultSymbol(result)
          Instruction.rawOperator(Operator.unaryOp(symbol, operation, operand))
        }
        case "binary_op" => {
          val lhs = parseSymbolRef()
          val operation = parseBinaryOperation()
          val rhs = parseSymbolRef()
          val symbol = parseResultSymbol(result)
          Instruction.rawOperator(Operator.binaryOp(symbol, operation, lhs, rhs))
        }
        case "switch_enum_assign" => {
          val switchOn = parseSymbolRef()
          def parseEnumAssignCase(): Option[EnumAssignCase]  = {
            val c = this.cursor
            if(!skip(",")) return None
            if (skip("case")) {
              val decl = parseString()
              take(":")
              val value = parseSymbolRef()
              Some(new EnumAssignCase(decl, value))
            } else {
              this.cursor = c
              None
            }
          }
          val cases = parseUntilNil[EnumAssignCase](() => parseEnumAssignCase())
          val c = this.cursor
          var default: Option[SymbolRef] = None
          if (skip(",") && skip("default")) {
            default = Some(parseSymbolRef())
          } else {
            this.cursor = c
          }
          val symbol = parseResultSymbol(result)
          Instruction.rawOperator(Operator.switchEnumAssign(symbol, switchOn, cases, default))
        }
        case "pointer_read" => {
          val pointer = parseSymbolRef()
          val symbol = parseResultSymbol(result)
          Instruction.rawOperator(Operator.pointerRead(symbol, pointer))
        }
        case _ => throw parseError("unknown instruction: " + instructionName)
      }
    } else { // Terminators
      instructionName match {
        case "br" => {
          val to = parseBlockRef()
          val arguments = { parseNilOrMany("(", ",", ")", parseSymbolRef) }.getOrElse(ArrayBuffer.empty[SymbolRef])
          Instruction.rawTerminator(Terminator.br(to, arguments))
        }
        case "br_if" => {
          val cond = parseSymbolRef()
          take(",")
          val target = parseBlockRef()
          val arguments = { parseNilOrMany("(", ",", ")", parseSymbolRef) }.getOrElse(ArrayBuffer.empty[SymbolRef])
          Instruction.rawTerminator(Terminator.brIf(cond, target, arguments))
        }
        case "cond_br" => {
          val cond = parseSymbolRef()
          take(",")
          take("true")
          val trueBlock = parseBlockRef()
          val trueArgs = { parseNilOrMany("(", ",", ")", parseSymbolRef) }.getOrElse(ArrayBuffer.empty[SymbolRef])
          take(",")
          take("false")
          val falseBlock = parseBlockRef()
          val falseArgs = { parseNilOrMany("(", ",", ")", parseSymbolRef) }.getOrElse(ArrayBuffer.empty[SymbolRef])
          Instruction.rawTerminator(Terminator.condBr(cond, trueBlock, trueArgs, falseBlock, falseArgs))
        }
        case "switch" => {
          val switchOn = parseSymbolRef()
          def parseSwitchCase(): Option[SwitchCase]  = {
            val c = this.cursor
            if(!skip(",")) return None
            if (skip("case")) {
              val value = parseSymbolRef()
              take(":")
              val dest = parseBlockRef()
              Some(new SwitchCase(value, dest))
            } else {
              this.cursor = c
              None
            }
          }
          val cases = parseUntilNil[SwitchCase](() => parseSwitchCase())
          val c = this.cursor
          var default: Option[BlockRef] = None
          if (skip(",") && skip("default")) {
            default = Some(parseBlockRef())
          } else {
            this.cursor = c
          }
          Instruction.rawTerminator(Terminator.switch(switchOn, cases, default))
        }
        case "switch_enum" => {
          val switchOn = parseSymbolRef()
          def parseSwitchEnumCase(): Option[SwitchEnumCase]  = {
            val c = this.cursor
            if(!skip(",")) return None
            if (skip("case")) {
              val decl = parseString()
              take(":")
              val dest = parseBlockRef()
              Some(new SwitchEnumCase(decl, dest))
            } else {
              this.cursor = c
              None
            }
          }
          val cases = parseUntilNil[SwitchEnumCase](() => parseSwitchEnumCase())
          val c = this.cursor
          var default: Option[BlockRef] = None
          if (skip(",") && skip("default")) {
            default = Some(parseBlockRef())
          } else {
            this.cursor = c
          }
          Instruction.rawTerminator(Terminator.switchEnum(switchOn, cases, default))
        }
        case "return" => {
          val value = parseSymbolRef()
          Instruction.rawTerminator(Terminator.ret(value))
        }
        case "throw" => {
          val value = parseSymbolRef()
          Instruction.rawTerminator(Terminator.thro(value))
        }
        case "try_apply" => {
          val functionRef = parseSymbolRef()
          val arguments = parseMany("(",",",")", parseSymbolRef)
          take(",")
          take("normal")
          val normalBlock = parseBlockRef()
          take(",")
          val normalType = parseType()
          take(",")
          take("error")
          val errorBlock = parseBlockRef()
          take(",")
          val errorType = parseType()
          Instruction.rawTerminator(Terminator.tryApply(functionRef, arguments, normalBlock, normalType, errorBlock, errorType))
        }
        case "unreachable" => Instruction.rawTerminator(Terminator.unreachable)
        case "yield" => {
          val args = { parseNilOrMany("(", ",", ")", parseSymbolRef) }.getOrElse(ArrayBuffer.empty[SymbolRef])
          take(",")
          take("resume")
          val resume = parseBlockRef()
          take(",")
          take("unwind")
          val unwind = parseBlockRef()
          Instruction.rawTerminator(Terminator.yld(args, resume, unwind))
        }
        case "unwind" => Instruction.rawTerminator(Terminator.unwind)
        case _ => throw parseError("unknown instruction: " + instructionName)
      }
    }
  }

  @throws[Error]
  def parseUnaryOperation(): UnaryOperation = {
    if(skip("[arb]")) return UnaryOperation.arbitrary
    throw parseError("unknown unary operation")
  }

  @throws[Error]
  def parseBinaryOperation(): BinaryOperation = {
    if(skip("[reg]")) return BinaryOperation.regular
    if(skip("[arb]")) return BinaryOperation.arbitrary
    if(skip("[eq]")) return BinaryOperation.equals
    throw parseError("unknown unary operation")
  }

  @throws[Error]
  def parseArgument(): Argument = {
    val value = parseSymbolRef()
    take(":")
    val tpe = parseType()
    new Argument(value, tpe)
  }

  @throws[Error]
  def parseFunctionAttribute(): Option[FunctionAttribute] = {
    if(skip("[coroutine]")) return Some(FunctionAttribute.coroutine)
    if(skip("[stub]")) return Some(FunctionAttribute.stub)
    if(skip("[model]")) return Some(FunctionAttribute.model)
    if(skip("[model_override]")) return Some(FunctionAttribute.modelOverride)
    if(skip("[entry]")) return Some(FunctionAttribute.entry)
    if(skip("[linked]")) return Some(FunctionAttribute.linked)
    None
  }

  @throws[Error]
  def parseIdentifier(): String = {
    if(peek("\"")) {
      s""""${parseString()}""""
    } else {
      val start = position()
      val identifier = take(x => (x.isLetterOrDigit || x == '_' || x == '%' || x == '$' || x == '.') && x != '`')
      if (!identifier.isEmpty) return identifier
      throw parseError("identifier expected", Some(start))
    }
  }

  @throws[Error]
  def parseFloat(): Double = {
    val s = take(x => (x == '-') || (x == '.') || (x == 'E') || (Character.digit(x, 10) != -1))
    java.lang.Double.parseDouble(s)
  }

  @throws[Error]
  def parseInt(): Int = {
    val start = position()
    val radix = if(skip("0x")) 16 else 10
    val s = take(x => (x == '-') || (x == '+') || (Character.digit(x, 16) != -1))
    try {
      Integer.parseInt(s, radix)
    } catch {
      case _ : Throwable => throw parseError("integer literal expected: " + s, Some(start))
    }
  }

  @throws[Error]
  def parseStringInt(): String = {
    take(whileFn = (x: Char) => x.isDigit)
  }

  @throws[Error]
  def parseBigInt(): BigInt = {
    val start = position()
    val radix = if(skip("0x")) 16 else 10
    val s = take(x => (x == '-') || (x == '+') || (Character.digit(x, 16) != -1))
    try {
      BigInt(s, radix)
    } catch {
      case _ : Throwable => throw parseError("integer literal expected: " + s, Some(start))
    }
  }

  @throws[Error]
  def parseResult(): Option[String] = {
    val c = this.cursor
    val possibleResultValue = parseIdentifier()
    if (skip("=")) {
      Some(possibleResultValue)
    } else {
      this.cursor = c
      None
    }
  }

  @throws[Error]
  def parseSourceInfo(): Option[Position] = {
    if(!skip(",")) return None
    if(!skip("loc")) return None
    val path = parseString()
    take(":")
    val line = parseInt()
    take(":")
    val col = parseInt()
    Some(new Position(path, line, col))
  }

  @throws[Error]
  def parseString(): String = {
    take("\"", skip = false)
    var s = ""
    def continue(): Boolean = {
      var i = 0
      breakable {
        s.reverse.foreach {
          case '\\' => i += 1
          case _ => break()
        }
      }
      if (i % 2 != 0) {
        s += "\""
        true
      } else {
        false
      }
    }
    do {
      s += take(_ != '\"')
      take("\"")
    } while (continue())
    s
  }

  @throws[Error]
  def parseGlobalOrFunctionName(): String = {
    take("@")
    parseBackTick()
  }

  // builtin_ref can have some functions with ticks
  // e.g., @`#UNNotificationSound.`default`!getter.1.foreign`,
  @throws[Error]
  def parseGlobalOrFunctionNameWithTicks(): String = {
    take("@")
    var s = ""
    while (!peek("`,")) {
      take("`")
      if (s != "") {
        s = s + "`"
      }
      s = s + take(_ != '`')
    }
    take("`")
    s
  }

  @throws[Error]
  def parseType(): Type = {
    take("$")
    new Type(parseBackTick())
  }

  @throws[Error]
  def parseBackTick(): String = {
    take("`")
    val v = take(_ != '`')
    take("`")
    v
  }
}

class Error(path : String, message : String, val chars: Array[Char]) extends Exception {
  private var line : Option[Int] = None
  private var column : Option[Int] = None

  def this(path : String, line: Int, column : Int, message : String, chars: Array[Char]) = {
    this(path, message, chars)
    this.line = Some(line)
    this.column = Some(column)
  }

  override def getMessage: String = {
    if (line.isEmpty) {
      return path + ": " + message
    }
    if (column.isEmpty) {
      return path + ":" + line.get + ": " + message
    }
    if (chars != null) {
      path + ":" + line.get + ":" + column.get + ": " + message + System.lineSeparator() + chars.mkString("") + System.lineSeparator() + (" " * column.get) + "^"
    } else {
      path + ":" + line.get + ":" + column.get + ": " + message
    }
  }
}
