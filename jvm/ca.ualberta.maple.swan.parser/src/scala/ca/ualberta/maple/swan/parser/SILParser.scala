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

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util

import ca.ualberta.maple.swan.utils.Logging

import scala.sys.process._

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag
import scala.util.control.Breaks
import scala.util.control.Breaks.{break, breakable}

// Canonical SIL Parser. Most of it is reverse engineered. It does not
// necessarily follow the naming or store properties in the same
// way that the Swift compiler does. e.g. SILDeclRef and SILType.
class SILParser extends SILPrinter {

  // Default constructor should not be called.

  private[parser] var path: String = _
  private[parser] var chars: Array[Char] = _
  private[parser] var cursor: Int = 0
  def position(): Int = { cursor }

  private[parser] val toDemangle: ArrayBuffer[SILMangledName] = ArrayBuffer.empty[SILMangledName]

  private[parser] val inits: ArrayBuffer[StructInit] = StructInit.populateInits()

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

  def this(s: String) = {
    this()
    this.path = "<memory>"
    this.chars = s.toCharArray
    skipTrivia()
  }

  // ***** Token level *****

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
    result.nonEmpty
  }

  protected def skipTrivia(): Unit = {
    if (cursor >= chars.length) return
    if (Character.isWhitespace(chars(cursor))) {
      cursor += 1
      skipTrivia()
    } else if (skip("//")) {
      // See if it is a init function comment.
      // These are needed to inform struct semantics for users of SIL.
      if (chars(cursor - 3) == '\n') {
        maybeParse(() => {
          val tpe: InitType = {
            if(skip("@objc")) InitType.objc
            else if(skip("@nonobjc")) InitType.nonobjc
            else InitType.normal
          }
          skipTrivia()
          val name = parseIdentifier()
          take(".init")
          val args = parseMany("(", ":", ")", parseIdentifier)
          // take("\n")
          this.inits.append(new StructInit(name, args, tpe))
          None
        })
      }
      while (cursor < chars.length && chars(cursor) != '\n') {
        cursor += 1
      }
      skipTrivia()
    }
  }

  // ***** Tree level *****

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
      case e : Error => {
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

  // ***** Error reporting *****

  protected def parseError(message: String, at: Option[Int] = None): Error = {
    val position = if (at.isDefined) at.get else cursor
    val newlines: ArrayBuffer[Int] = ArrayBuffer.empty
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

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#syntax
  @throws[Error]
  def parseModule(): SILModule = {
    Logging.printInfo("Parsing " + new File(this.path).getName)
    val startTime = System.nanoTime()
    if (!skip("sil_stage canonical")) {
      throw parseError("This parser only supports canonical SIL")
    }
    val functions = ArrayBuffer[SILFunction]()
    val witnessTables = ArrayBuffer[SILWitnessTable]()
    val vTables = ArrayBuffer[SILVTable]()
    val imports = ArrayBuffer[String]()
    val globalVariables = ArrayBuffer[SILGlobalVariable]()
    val scopes = ArrayBuffer[SILScope]()
    val properties = ArrayBuffer[SILProperty]()
    var done = false
    while(!done) {
      if(peek("sil ")) {
        val function = parseFunction()
        functions.append(function)
      } else if (peek("sil_witness_table ")) {
        witnessTables.append(parseWitnessTable())
      } else if (peek("sil_default_witness_table ")) {
        // T0D0: Has not appeared yet. Leave for now.
      } else if (peek("sil_vtable ")) {
        vTables.append(parseVTable())
      } else if (peek("sil_global ")) {
        globalVariables.append(parseGlobalVariable())
      // } else if (peek("sil_property")) { T0D0: Need to parse component
        // properties.append(parseProperty())
      } else if (peek("import ")) {
        take("import")
        // Identifier should be OK here.
        imports.append(parseIdentifier())
      } else if (peek("sil_scope")) {
        scopes.append(parseScope())
      } else {
        Breaks.breakable {
          if(skip(_ != '\n')) Breaks.break()
        }
        if (this.cursor == this.chars.length) {
          done = true
        }
      }
    }
    demangleNames()
    Logging.printTimeStamp(0, startTime, "parsing", chars.count(_ == '\n'), "lines")
    new SILModule(functions, witnessTables, vTables, imports, globalVariables, scopes, properties, inits, new SILModuleMetadata(new File(path), "", "", ""))
  }

  def demangleNames(): Unit = {
    // The swift-demangle I/O operation is expensive
    // Demangling the strings all at once at the end is significantly quicker
    // swift-demangle can only take a certain amount at a time, so batches are used
    // Actual length limit is character based, not count based, and seems to
    // vary from system to system.
    val swiftDemangle = "/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/swift-demangle -compact "
    val batches = new ArrayBuffer[ArrayBuffer[SILMangledName]]()
    var currBatch = new ArrayBuffer[SILMangledName]()
    batches.append(currBatch)
    toDemangle.foreach(m => {
      currBatch.append(m)
      if (currBatch.length > 1000) {
        currBatch = new ArrayBuffer[SILMangledName]()
        batches.append(currBatch)
      }
    })
    batches.foreach(batch => {
      val strings = new ArrayBuffer[String]()
      batch.foreach(m => {
        strings.append("\'" + m.mangled + "\'")
      })
      val demangled = (swiftDemangle + strings.mkString(" ")).!!.split(System.lineSeparator())
      batch.zipWithIndex.foreach(m => m._1.demangled = demangled(m._2))
    })
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#functions
  @throws[Error]
  def parseFunction(): SILFunction = {
    take("sil")
    val linkage = parseLinkage()
    val attributes = { parseNilOrMany("[", parseFunctionAttribute) }.getOrElse(ArrayBuffer.empty[SILFunctionAttribute])
    val name = parseMangledName()
    take(":")
    val tpe = parseType()
    val blocks = { parseNilOrMany("{", "", "}", parseBlock) }.getOrElse(ArrayBuffer.empty[SILBlock])
    new SILFunction(linkage, attributes, name, tpe, blocks)
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#basic-blocks
  @throws[Error]
  def parseBlock(): SILBlock = {
    val identifier = parseIdentifier()
    val arguments = { parseNilOrMany("(", ",", ")", parseArgument) }.getOrElse(ArrayBuffer.empty[SILArgument])
    take(":")
    val (operatorDefs, terminatorDef) = parseInstructionDefs()
    new SILBlock(identifier, arguments, operatorDefs, terminatorDef)
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#basic-blocks
  @throws[Error]
  def parseInstructionDefs(): (ArrayBuffer[SILOperatorDef], SILTerminatorDef) = {
    val operatorDefs = ArrayBuffer[SILOperatorDef]()
    var done = false
    while(!done){
      parseInstructionDef() match {
        case SILInstructionDef.operator(operatorDef) => operatorDefs.append(operatorDef)
        case SILInstructionDef.terminator(terminatorDef) => return (operatorDefs, terminatorDef)
      }
      if(peek("bb") || peek("}")) {
        done = true
      }
    }
    throw parseError("block is missing a terminator")
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#basic-blocks
  @throws[Error]
  def parseInstructionDef(): SILInstructionDef = {
    nakedStack.clear()
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
    parseInstructionBody(instructionName)
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#global-variables
  @throws[Error]
  def parseGlobalVariable(): SILGlobalVariable = {
    take("sil_global")
    val linkage = parseLinkage()
    val serialized = skip("[serialized]")
    val let = skip("[let]")
    val name = parseMangledName()
    take(":")
    val tpe = parseType()
    var entries: Option[ArrayBuffer[SILOperatorDef]] = None
    if (skip("=")) {
      entries = parseNilOrMany("{", "", "}", () => {
        parseInstructionDef().asInstanceOf[SILInstructionDef.operator].operatorDef
      })
    }
    new SILGlobalVariable(linkage, serialized, let, name, tpe, entries)
  }

  @throws[Error]
  def parseProperty(): SILProperty = {
    take("sil_property")
    val serialized = skip("[serialized]")
    val declRef = parseDeclRef()
    take("(")
    val tpe = parseNakedType() // This is wrong.
    take(")")
    new SILProperty(serialized, declRef, tpe)
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#vtables
  @throws[Error]
  def parseVTable(): SILVTable = {
    take("sil_vtable")
    val serialized = skip("[serialized]")
    val name = parseIdentifier()
    val entries = { parseNilOrMany("{", "", "}", parseVEntry) }.getOrElse(ArrayBuffer.empty[SILVEntry])
    new SILVTable(name, serialized, entries)
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#witness-tables
  @throws[Error]
  def parseWitnessTable(): SILWitnessTable = {
    take("sil_witness_table")
    val linkage = parseLinkage()
    val functionAttribute = if (peek("[")) Some(parseFunctionAttribute()) else None
    val normalProtocolConformance = parseNormalProtocolConformance()
    val entries = { parseNilOrMany("{", "", "}", parseWitnessEntry) }.getOrElse(ArrayBuffer.empty[SILWitnessEntry])
    new SILWitnessTable(linkage, functionAttribute, normalProtocolConformance, entries)
  }

  @throws[Error]
  def parseInstructionBody(instructionName: String): SILInstruction = {
    // NSIP: Not seen in practice
    // Case instruction ordering based on apple/swift tag swift-5.2-RELEASE SIL.rst
    instructionName match {

        // *** ALLOCATION AND DEALLOCATION ***

      case "alloc_stack" => {
        val dynamicLifetime = skip("[dynamic_lifetime]")
        val tpe = parseType()
        val attributes = parseUntilNil( parseDebugAttribute )
        SILInstruction.operator(SILOperator.allocStack(tpe, dynamicLifetime, attributes))
      }
      case "alloc_ref" => {
        var allocAttributes = ArrayBuffer.empty[SILAllocAttribute]
        if(skip("[objc]")) { allocAttributes = allocAttributes.append(SILAllocAttribute.objc) }
        if(skip("[stack]")) { allocAttributes = allocAttributes.append(SILAllocAttribute.stack) }
        val tailElems: ArrayBuffer[(SILType, SILOperand)] = ArrayBuffer.empty
        while(peek("[")) {
          take("[")
          take("tail_elems")
          val tailType: SILType = parseType()
          take("*")
          val operand: SILOperand = parseOperand()
          take("]")
          tailElems.append((tailType, operand))
        }
        val tpe: SILType = parseType()
        SILInstruction.operator(SILOperator.allocRef(allocAttributes, tailElems, tpe))
      }
      case "alloc_ref_dynamic" => {
        val objc: Boolean =  skip("[objc]")
        val tailElems = ArrayBuffer.empty[(SILType, SILOperand)]
        while(peek("[")) {
          take("[")
          take("tail_elems")
          val tailType: SILType = parseType()
          take("*")
          val operand: SILOperand = parseOperand()
          take("]")
          tailElems.append((tailType, operand))
        }
        val operand: SILOperand = parseOperand()
        take(",")
        val tpe: SILType = parseType()
        SILInstruction.operator(SILOperator.allocRefDynamic(objc, tailElems, operand, tpe))
      }
      case "alloc_box" => {
        val tpe = parseType()
        val attributes = parseUntilNil( parseDebugAttribute )
        SILInstruction.operator(SILOperator.allocBox(tpe, attributes))
      }
      case "alloc_value_buffer" => {
        val tpe = parseType()
        take("in")
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.allocValueBuffer(tpe, operand))
      }
      case "alloc_global" => {
        val name = parseMangledName()
        SILInstruction.operator(SILOperator.allocGlobal(name))
      }
      case "dealloc_stack" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.deallocStack(operand))
      }
      case "dealloc_box" => {
        varGrowOverride = true
        val operand = parseOperand()
        take("<")
        val tpe = parseNakedType()
        take(">")
        SILInstruction.operator(SILOperator.deallocBox(operand, tpe))
      }
      case "project_box" => {
        val operand = parseOperand()
        take(",")
        val fieldIndex = parseInt()
        SILInstruction.operator(SILOperator.projectBox(operand, fieldIndex))
      }
      case "dealloc_ref" => {
        val stack: Boolean = skip("[stack]")
        val operand: SILOperand = parseOperand()
        SILInstruction.operator(SILOperator.deallocRef(stack, operand))
      }
      case "dealloc_partial_ref" => {
        val operand1 = parseOperand()
        take(",")
        val operand2 = parseOperand()
        SILInstruction.operator(SILOperator.deallocPartialRef(operand1, operand2))
      }
      case "dealloc_value_buffer" => {
        val tpe = parseType()
        take("in")
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.deallocValueBuffer(tpe, operand))
      }
      case "project_value_buffer" => {
        // Tricky, NSIP
        throw parseError("unhandled instruction")
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
        } else if (skip("[assign]")) {
          ownership = Some(SILStoreOwnership.assign)
        }
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.store(value, ownership, operand))
      }
      case "load_borrow" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.loadBorrow(operand))
      }
      case "store_borrow" => {
        val from = parseValue()
        take("to")
        val to = parseOperand()
        SILInstruction.operator(SILOperator.storeBorrow(from, to))
      }
      case "begin_borrow" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.beginBorrow(operand))
      }
      case "end_borrow" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.endBorrow(operand))
      }
      case "end_lifetime" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.endLifetime(operand))
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
        val pointer = parseOperand()
        take(",")
        val offset = parseOperand()
        SILInstruction.operator(SILOperator.indexRawPointer(pointer, offset))
      }
      case "bind_memory" => {
        val operand1 = parseOperand()
        take(",")
        val operand2 = parseOperand()
        take("to")
        val toType = parseType()
        SILInstruction.operator(SILOperator.bindMemory(operand1, operand2, toType))
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
        take("[")
        val access = parseAccess()
        take("]")
        take("[")
        val enforcement = parseEnforcement()
        take("]")
        val noNestedConflict = skip("[no_nested_conflict]")
        val builtin = skip("[builtin]")
        val operand = parseOperand()
        take(",")
        val buffer = parseOperand()
        SILInstruction.operator(SILOperator.beginUnpairedAccess(access, enforcement, noNestedConflict, builtin, operand, buffer))
      }
      case "end_unpaired_access" => {
        val abort = skip("[abort]")
        take("[")
        val enforcement = parseEnforcement()
        take("]")
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.endUnpairedAccess(abort, enforcement, operand))
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
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.setDeallocating(operand))
      }
      case "copy_unowned_value" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.copyUnownedValue(operand))
      }
      case "strong_copy_unowned_value" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.strongCopyUnownedValue(operand))
      }
      case "strong_retain_unowned" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.strongRetainUnowned(operand))
      }
      case "unowned_retain" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.unownedRetain(operand))
      }
      case "unowned_release" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.unownedRelease(operand))
      }
      case "load_weak" => {
        val take = skip("[take]")
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.loadWeak(take, operand))
      }
      case "store_weak" => {
        val from = parseValue()
        take("to")
        val initialization = skip("[initialization]")
        val to = parseOperand()
        SILInstruction.operator(SILOperator.storeWeak(from, initialization, to))
      }
      case "load_unowned" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.loadUnowned(operand))
      }
      case "store_unowned" => {
        val from = parseValue()
        take("to")
        val initialization = skip("[initialization]")
        val to = parseOperand()
        SILInstruction.operator(SILOperator.storeUnowned(from, initialization, to))
      }
      case "fix_lifetime" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.fixLifetime(operand))
      }
      case "mark_dependence" => {
        val operand = parseOperand()
        take("on")
        val on = parseOperand()
        SILInstruction.operator(SILOperator.markDependence(operand, on))
      }
      case "is_unique" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.isUnique(operand))
      }
      case "is_escaping_closure" => {
        val objc = skip("[objc]")
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.isEscapingClosure(operand, objc))
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
        val name = parseMangledName()
        take(":")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.functionRef(name, tpe))
      }
      case "dynamic_function_ref" => {
        val name = parseMangledName()
        take(":")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.dynamicFunctionRef(name, tpe))
      }
      case "prev_dynamic_function_ref" => {
        val name = parseMangledName()
        take(":")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.prevDynamicFunctionRef(name, tpe))
      }
      case "global_addr" => {
        val name = parseMangledName()
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
        val value = parseBigInt()
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
        val operand = parseOperand()
        take(",")
        val declRef = parseDeclRef()
        take(":")
        val declType = parseNakedType()
        take(",")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.classMethod(operand, declRef, declType, tpe))
      }
      case "objc_method" => {
        val operand = parseOperand()
        take(",")
        val declRef = parseDeclRef()
        take(":")
        val declType = parseNakedType()
        take(",")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.objcMethod(operand, declRef, declType, tpe))
      }
      case "super_method" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "objc_super_method" => {
        val operand = parseOperand()
        take(",")
        val declRef = parseDeclRef()
        take(":")
        val declType = parseNakedType()
        // Not sure why this is "," and not ":".
        // This is not consistent with SIL.rst.
        take(",")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.objcSuperMethod(operand, declRef, declType, tpe))
      }
      case "witness_method" => {
        val archeType = parseType()
        take(",")
        val declRef = parseDeclRef()
        take(":")
        val declType = parseNakedType()
        val value = {
          if (skip(",")) {
            Some(parseOperand())
          } else {
            None
          }
        }
        take(":")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.witnessMethod(archeType, declRef, declType, value, tpe))
      }

        // *** FUNCTION APPLICATION ***

      case "apply" => {
        val nothrow = skip("[nothrow]")
        val value = parseValue()
        val substitutions = parseNilOrMany("<", ",",">", parseNakedType)
        // I'm sure there's a more elegant way to do this.
        val substitutionsNonOptional: ArrayBuffer[SILType] = {
          if (substitutions.isEmpty) {
            ArrayBuffer.empty
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
        val s = parseNilOrMany("<",",",">", parseNakedType)
        val substitutions = if (s.nonEmpty) s.get else ArrayBuffer.empty[SILType]
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
        val substitutions = if (s.nonEmpty) s.get else ArrayBuffer.empty[SILType]
        val arguments = parseMany("(",",",")", parseValue)
        take(":")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.partialApply(calleeGuaranteed,onStack,value,substitutions,arguments,tpe))
      }
      case "builtin" => {
        val name = parseString()
        val templateTpe = {
          if (skip("<")) {
            val tpe = parseNakedType()
            skip(">")
            Some(tpe)
          } else {
            None
          }
        }
        val operands = parseMany("(", ",", ")", parseOperand)
        take(":")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.builtin(name, templateTpe, operands, tpe))
      }

        // *** METATYPES ***

      case "metatype" => {
        val tpe = parseType()
        SILInstruction.operator(SILOperator.metatype(tpe))
      }
      case "value_metatype" => {
        val tpe = parseType()
        take(",")
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.valueMetatype(tpe, operand))
      }
      case "existential_metatype" => {
        val tpe = parseType()
        take(",")
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.existentialMetatype(tpe, operand))
      }
      case "objc_protocol" => {
        val protocolDecl = parseDeclRef()
        take(":")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.objcProtocol(protocolDecl, tpe))
      }

        // *** AGGREGATE TYPES ***

      case "retain_value" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.retainValue(operand))
      }
      case "retain_value_addr" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.retainValueAddr(operand))
      }
      case "unmanaged_retain_value" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.unmanagedRetainValue(operand))
      }
      case "copy_value" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.copyValue(operand))
      }
      case "strong_copy_unmanaged_value" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.strongCopyUnmanagedValue(operand))

      }
      case "release_value" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.releaseValue(operand))
      }
      case "release_value_addr" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.releaseValueAddr(operand))
      }
      case "unmanaged_release_value" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.unmanagedReleaseValue(operand))
      }
      case "destroy_value" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.destroyValue(operand))
      }
      case "autorelease_value" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.autoreleaseValue(operand))
      }
      case "unmanaged_autorelease_value" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.unmanagedAutoreleaseValue(operand))
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
        val tpe = parseType()
        val operands: ArrayBuffer[SILOperand] = ArrayBuffer()
        val tailElems: ArrayBuffer[SILOperand] = ArrayBuffer()
        var tail = false
        parseMany("(",",",")", () => {
          if (skip("[tail_elems]")) {
            tail = true
          }
          val op = parseOperand()
          if (tail) {
            tailElems.append(op)
          } else {
            operands.append(op)
          }
          ()
        })
        SILInstruction.operator(SILOperator.objct(tpe, operands, tailElems))
      }
      case "ref_element_addr" => {
        val immutable: Boolean = skip("[immutable]")
        val operand: SILOperand = parseOperand()
        take(",")
        val declRef: SILDeclRef = parseDeclRef()
        SILInstruction.operator(SILOperator.refElementAddr(immutable, operand, declRef))
      }
      case "ref_tail_addr" => {
        val immutable: Boolean = skip("[immutable]")
        val operand: SILOperand = parseOperand()
        take(",")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.refTailAddr(immutable, operand, tpe))
      }

        // *** ENUMS ***

      case "enum" => {
        val tpe = parseType()
        take(",")
        val declRef = parseDeclRef()
        // Just because we see "," doesn't mean there will be an operand.
        // It could be `[...], scope [...]`
        val operand = {
          val c = this.cursor
          if (skip(",") && peek ("%")) {
            Some(parseOperand())
          } else {
            this.cursor = c
            None
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
        val operand = parseOperand()
        take(",")
        val declRef = parseDeclRef()
        SILInstruction.operator(SILOperator.initEnumDataAddr(operand, declRef))
      }
      case "inject_enum_addr" => {
        val operand = parseOperand()
        take(",")
        val declRef = parseDeclRef()
        SILInstruction.operator(SILOperator.injectEnumAddr(operand, declRef))
      }
      case "unchecked_take_enum_data_addr" => {
        val operand = parseOperand()
        take(",")
        val declRef = parseDeclRef()
        SILInstruction.operator(SILOperator.uncheckedTakeEnumDataAddr(operand, declRef))
      }
      case "select_enum" => {
        val operand = parseOperand()
        val cases = parseUntilNil[SILSwitchEnumCase](() => parseSwitchEnumCase(parseValue))
        take(":")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.selectEnum(operand, cases, tpe))
      }
      case "select_enum_addr" => {
        val operand = parseOperand()
        val cases = parseUntilNil[SILSwitchEnumCase](() => parseSwitchEnumCase(parseValue))
        take(":")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.selectEnumAddr(operand, cases, tpe))
      }

        // *** PROTOCOL AND PROTOCOL COMPOSITION TYPES ***

      case "init_existential_addr" => {
        val operand = parseOperand()
        take(",")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.initExistentialAddr(operand, tpe))
      }
      case "init_existential_value" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "deinit_existential_addr" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.deinitExistentialAddr(operand))
      }
      case "deinit_existential_value" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "open_existential_addr" => {
        val access = parseAllowedAccess()
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.openExistentialAddr(access, operand, tpe))
      }
      case "open_existential_value" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "init_existential_ref" => {
        val operand = parseOperand()
        take(":")
        val tpeC = parseType()
        take(",")
        val tpeP = parseType()
        SILInstruction.operator(SILOperator.initExistentialRef(operand, tpeC, tpeP))
      }
      case "open_existential_ref" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.openExistentialRef(operand, tpe))
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
        val tpe = parseType()
        take("in")
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.projectExistentialBox(tpe, operand))
      }
      case "open_existential_box" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.openExistentialBox(operand, tpe))
      }
      case "open_existential_box_value" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "dealloc_existential_box" => {
        val operand = parseOperand()
        take(",")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.deallocExistentialBox(operand, tpe))
      }

        // *** BLOCKS ***

      case "project_block_storage" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.projectBlockStorage(operand))
      }
      case "init_block_storage_header" => {
        val operand = parseOperand()
        take(",")
        take("invoke")
        val invoke = parseOperand()
        take(",")
        take("type")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.initBlockStorageHeader(operand, invoke, tpe))
      }

        // *** UNCHECKED CONVERSIONS ***

      case "upcast" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.upcast(operand, tpe))
      }
      case "address_to_pointer" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.addressToPointer(operand, tpe))
      }
      case "pointer_to_address" => {
        val operand = parseOperand()
        take("to")
        val strict = skip("[strict]")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.pointerToAddress(operand, strict, tpe))
      }
      case "unchecked_ref_cast" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.uncheckedRefCast(operand, tpe))
      }
      case "unchecked_ref_cast_addr" => {
        val fromTpe = parseType(naked = true)
        take("in")
        val fromOperand = parseOperand()
        take("to")
        val toTpe = parseType(naked = true)
        take("in")
        val toOperand = parseOperand()
        SILInstruction.operator(SILOperator.uncheckedRefCastAddr(fromTpe, fromOperand, toTpe, toOperand))
      }
      case "unchecked_addr_cast" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.uncheckedAddrCast(operand, tpe))
      }
      case "unchecked_trivial_bit_cast" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.uncheckedTrivialBitCast(operand, tpe))
      }
      case "unchecked_bitwise_cast" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.uncheckedBitwiseCast(operand, tpe))
      }
      case "unchecked_ownership_conversion" => {
        val operand = parseOperand()
        take(",")
        val from = parseTypeAttribute()
        take("to")
        val to = parseTypeAttribute()
        SILInstruction.operator(SILOperator.uncheckedOwnershipConversion(operand, from, to))
      }
      case "ref_to_raw_pointer" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.refToRawPointer(operand, tpe))
      }
      case "raw_pointer_to_ref" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.rawPointerToRef(operand, tpe))
      }
      case "ref_to_unowned" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.refToUnowned(operand, tpe))
      }
      case "unowned_to_ref" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.unownedToRef(operand, tpe))
      }
      case "ref_to_unmanaged" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.refToUnmanaged(operand, tpe))
      }
      case "unmanaged_to_ref" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.unmanagedToRef(operand, tpe))
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
        // NSIP, no documentation
        throw parseError("unhandled instruction")
      }
      case "pointer_to_thin_function" => {
        // NSIP, no documentation
        throw parseError("unhandled instruction")
      }
      case "classify_bridge_object" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.classifyBridgeObject(operand))
      }
      case "value_to_bridge_object" => {
        val operand = parseOperand()
        SILInstruction.operator(SILOperator.valueToBridgeObject(operand))
      }
      case "ref_to_bridge_object" => {
        val operand1 = parseOperand()
        take(",")
        val operand2 = parseOperand()
        SILInstruction.operator(SILOperator.refToBridgeObject(operand1, operand2))
      }
      case "bridge_object_to_ref" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.bridgeObjectToRef(operand, tpe))
      }
      case "bridge_object_to_word" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.bridgeObjectToWord(operand, tpe))
      }
      case "thin_to_thick_function" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.thinToThickFunction(operand, tpe))
      }
      case "thick_to_objc_metatype" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.thickToObjcMetatype(operand, tpe))
      }
      case "objc_to_thick_metatype" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.objcToThickMetatype(operand, tpe))
      }
      case "objc_metatype_to_object" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.objcMetatypeToObject(operand, tpe))
      }
      case "objc_existential_metatype_to_object" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.objcExistentialMetatypeToObject(operand, tpe))
      }

        // *** CHECKED CONVERSIONS ***

      case "unconditional_checked_cast" => {
        val operand = parseOperand()
        take("to")
        val tpe = parseType(naked = true)
        SILInstruction.operator(SILOperator.unconditionalCheckedCast(operand, tpe))
      }
      case "unconditional_checked_cast_addr" => {
        // For some reason fromTpe nor toTpe have a "$" prefix.
        // e.g. unconditional_checked_cast_addr Card in %101 : $*Card to Any in %91 : $*Any
        val fromTpe = parseType(true)
        take("in")
        val fromOperand = parseOperand()
        take("to")
        val toTpe = parseType(true)
        take("in")
        val toOperand = parseOperand()
        SILInstruction.operator(SILOperator.unconditionalCheckedCastAddr(fromTpe, fromOperand, toTpe, toOperand))
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
          if (peek(", \"")) {
            take(",")
            Some(parseString())
          } else {
            None
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
        val operands: ArrayBuffer[SILOperand] = {
          if(peek("(")) {
            parseMany("(", ",", ")", parseOperand)
          } else {
            ArrayBuffer(parseOperand())
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
        val o : Option[ArrayBuffer[SILOperand]] = parseNilOrMany("(",",",")", parseOperand)
        val operands = if (o.nonEmpty) o.get else ArrayBuffer.empty[SILOperand]
        SILInstruction.terminator(SILTerminator.br(label, operands))
      }
      case "cond_br" => {
        val cond = parseValueName()
        take(",")
        val trueLabel = parseIdentifier()
        val to : Option[ArrayBuffer[SILOperand]] = parseNilOrMany("(",",",")", parseOperand)
        val trueOperands = if (to.nonEmpty) to.get else ArrayBuffer.empty[SILOperand]
        take(",")
        val falseLabel = parseIdentifier()
        val fo : Option[ArrayBuffer[SILOperand]] = parseNilOrMany("(",",",")", parseOperand)
        val falseOperands = if (fo.nonEmpty) fo.get else ArrayBuffer.empty[SILOperand]
        SILInstruction.terminator(SILTerminator.condBr(cond, trueLabel, trueOperands, falseLabel, falseOperands))
      }
      case "switch_value" => {
        val operand = parseOperand()
        val cases = parseUntilNil[SILSwitchValueCase](() => parseSwitchValueCase(parseIdentifier))
        SILInstruction.terminator(SILTerminator.switchValue(operand, cases))
      }
      case "select_value" => {
        val operand = parseOperand()
        val cases = parseUntilNil[SILSelectValueCase](() => parseSelectValueCase())
        take(":")
        val tpe = parseType()
        SILInstruction.operator(SILOperator.selectValue(operand, cases, tpe))
      }
      case "switch_enum" => {
        val operand = parseOperand()
        val cases = parseUntilNil[SILSwitchEnumCase](() => parseSwitchEnumCase(parseIdentifier))
        SILInstruction.terminator(SILTerminator.switchEnum(operand, cases))
      }
      case "switch_enum_addr" => {
        val operand = parseOperand()
        val cases = parseUntilNil[SILSwitchEnumCase](() => parseSwitchEnumCase(parseIdentifier))
        SILInstruction.terminator(SILTerminator.switchEnumAddr(operand, cases))
      }
      case "dynamic_method_br" => {
        val operand = parseOperand()
        take(",")
        val declRef = parseDeclRef()
        take(",")
        val namedLabel = parseIdentifier()
        take(",")
        val notNamedLabel = parseIdentifier()
        SILInstruction.terminator(SILTerminator.dynamicMethodBr(operand, declRef, namedLabel, notNamedLabel))
      }
      case "checked_cast_br" => {
        val exact = skip("[exact]")
        val operand = parseOperand()
        take("to")
        val naked = skip("$")
        val tpe = parseNakedType()
        take(",")
        val succeedLabel = parseIdentifier()
        take(",")
        val failureLabel = parseIdentifier()
        SILInstruction.terminator(SILTerminator.checkedCastBr(exact, operand, tpe, naked, succeedLabel, failureLabel))
      }
      case "checked_cast_value_br" => {
        throw parseError("unhandled instruction") // NSIP
      }
      case "checked_cast_addr_br" => {
        // For some reason fromTpe nor toTpe have a "$" prefix.
        val kind = parseCastConsumptionKind()
        val fromTpe = parseType(true)
        take("in")
        val fromOperand = parseOperand()
        take("to")
        val toTpe = parseType(true)
        take("in")
        val toOperand = parseOperand()
        take(",")
        val succeedLabel = parseIdentifier()
        take(",")
        val failureLabel = parseIdentifier()
        SILInstruction.terminator(SILTerminator.checkedCastAddrBr(
          kind, fromTpe, fromOperand, toTpe, toOperand, succeedLabel, failureLabel))
      }
      case "try_apply" => {
        val value = parseValue()
        val s = parseNilOrMany("<",",",">", parseNakedType)
        val substitutions = if (s.nonEmpty) s.get else ArrayBuffer.empty[SILType]
        val arguments = parseMany("(",",",")", parseValue)
        take(":")
        val tpe = parseType()
        take(",")
        take("normal")
        val normal = parseIdentifier()
        take(",")
        take("error")
        val error = parseIdentifier()
        SILInstruction.terminator(SILTerminator.tryApply(value, substitutions, arguments, tpe, normal, error))
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

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#open-existential-addr
  @throws[Error]
  def parseAllowedAccess(): SILAllowedAccess = {
    if(skip("immutable_access")) return SILAllowedAccess.immutable
    if(skip("mutable_access")) return SILAllowedAccess.mutable
    throw parseError("unknown allowed access")
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#checked-cast-addr-br
  @throws[Error]
  def parseCastConsumptionKind(): SILCastConsumptionKind = {
    if(skip("take_always")) return SILCastConsumptionKind.takeAlways
    if(skip("take_on_success")) return SILCastConsumptionKind.takeOnSuccess
    if(skip("copy_on_success")) return SILCastConsumptionKind.copyOnSuccess
    throw parseError("unknown cast consumption kind")
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#basic-blocks
  @throws[Error]
  def parseArgument(): SILArgument = {
    val valueName = parseValueName()
    take(":")
    val tpe = {
      if (peek("@")) {
        val attr = parseTypeAttribute()
        SILType.withOwnership(attr, parseType())
      } else {
        parseType()
      }
    }
    new SILArgument(valueName, tpe)
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#switch-enum
  @throws[Error]
  def parseSwitchEnumCase(parseElement: () => String): Option[SILSwitchEnumCase] = {
    val c = this.cursor
    if(!skip(",")) return None
    if (skip("case")) {
      val declRef = parseDeclRef()
      take(":")
      val identifier = parseElement()
      Some(SILSwitchEnumCase.cs(declRef, identifier))
    } else if (skip("default")) {
      val identifier = parseElement()
      Some(SILSwitchEnumCase.default(identifier))
    } else {
      this.cursor = c
      None
    }
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#switch-value
  @throws[Error]
  def parseSwitchValueCase(parseElement: () => String): Option[SILSwitchValueCase] = {
    val c = this.cursor
    if(!skip(",")) return None
    if (skip("case")) {
      val value = parseValue()
      take(":")
      val label = parseElement()
      Some(SILSwitchValueCase.cs(value, label))
    } else if (skip("default")) {
      val label = parseElement()
      Some(SILSwitchValueCase.default(label))
    } else {
      this.cursor = c
      None
    }
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#select-value
  @throws[Error]
  def parseSelectValueCase(): Option[SILSelectValueCase] = {
    val c = this.cursor
    if(!skip(",")) return None
    if (skip("case")) {
      val value = parseValue()
      take(":")
      val select = parseValue()
      Some(SILSelectValueCase.cs(value, select))
    } else if (skip("default")) {
      val select = parseValue()
      Some(SILSelectValueCase.default(select))
    } else {
      this.cursor = c
      None
    }
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
    } else if (skip("objc_method")) {
      result = SILConvention.objc
    } else {
      throw parseError("unknown convention")
    }
    take(")")
    result
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#debug-value
  @throws[Error]
  def parseDebugAttribute(): Option[SILDebugAttribute] = {
    val c = this.cursor
    if(!skip(",")) return None
    if(skip("argno")) return Some(SILDebugAttribute.argno(parseInt()))
    if(skip("name")) return Some(SILDebugAttribute.name(parseString()))
    if(skip("let")) return Some(SILDebugAttribute.let)
    if(skip("var")) return Some(SILDebugAttribute.variable)
    this.cursor = c
    None
  }

  @throws[Error]
  def parseDeclKind(): SILDeclKind = {
    if(skip("allocator")) return SILDeclKind.allocator
    if(skip("initializer")) return SILDeclKind.initializer
    if(skip("enumelt")) return SILDeclKind.enumElement
    if(skip("destroyer")) return SILDeclKind.destroyer
    if(skip("deallocator")) return SILDeclKind.deallocator
    if(skip("globalaccessor")) return SILDeclKind.globalAccessor
    if(skip("defaultarg")) {
      take(".")
      val index = parseIdentifier()
      return SILDeclKind.defaultArgGenerator(index)
    }
    if(skip("propertyinit")) return SILDeclKind.storedPropertyInitalizer
    if(skip("ivarinitializer")) return SILDeclKind.ivarInitializer
    if(skip("ivardestroyer")) return SILDeclKind.ivarDestroyer
    if(skip("backinginit")) return SILDeclKind.propertyWrappingBackingInitializer
    SILDeclKind.func
  }

  @throws[Error]
  def parseAccessorKind(): Option[SILAccessorKind] = {
    if(skip("getter")) return Some(SILAccessorKind.get)
    if(skip("setter")) return Some(SILAccessorKind.set)
    if(skip("willSet")) return Some(SILAccessorKind.willSet)
    if(skip("didSet")) return Some(SILAccessorKind.didSet)
    if(skip("addressor")) return Some(SILAccessorKind.address)
    if(skip("mutableAddressor")) return Some(SILAccessorKind.mutableAddress)
    if(skip("read")) return Some(SILAccessorKind.read)
    if(skip("modify")) return Some(SILAccessorKind.modify)
    None
  }

  @throws[Error]
  def parseAutoDiff(): Option[SILAutoDiff] = {
    if (peek("jvp") || peek("vjp")) {
      val jvp = skip("jvp")
      if (!jvp) skip("vjp")
      take(".")
      // [SU]+ but identifier is fine
      val indices = parseIdentifier()
      return if (jvp) Some(SILAutoDiff.jvp(indices))
        else Some(SILAutoDiff.vjp(indices))
    }
    None
  }

  @throws[Error]
  def parseDeclSubRef(): Option[SILDeclSubRef] = {
    // sil-decl-subref?
    // sil-decl-subref always start with '!'
    if (skip("!")) {
      // 4 choices: level (int), sil-decl-lang ("foreign"), sil-decl-autodiff, or sil-decl-subref-part
      val level = {
        if (Character.isDigit(chars(cursor))) {
          Some(parseInt())
        } else {
          None
        }
      }
      if (level.nonEmpty) {
        return Some(SILDeclSubRef.level(level.get, skip(".foreign")))
      }
      // sil-decl-lang ("foreign")
      if (skip("foreign")) {
        return Some(SILDeclSubRef.lang)
      } else { // sil-decl-subref-part or sil-decl-autodiff
        // sil-decl-autodiff
        val autoDiff = parseAutoDiff()
        // sil-decl-subref-part
        if (autoDiff.isEmpty) {
          val accessorKind = parseAccessorKind()
          var declKind: SILDeclKind = SILDeclKind.func
          var level: Option[Int] = None
          if (accessorKind.isEmpty) {
            declKind = parseDeclKind()
          }
          var foreign = false
          var ad: Option[SILAutoDiff] = None
          if (skip(".")) {
            level = {
              if (Character.isDigit(chars(cursor))) {
                Some(parseInt())
              } else {
                None
              }
            }
            if (level.nonEmpty) {
              skip(".")
            }
            foreign = skip("foreign")
            if (foreign && skip(".")) {
              ad = parseAutoDiff()
            }
          }
          return Some(SILDeclSubRef.part(accessorKind, declKind, level, foreign, ad))
        } else {
          return Some(SILDeclSubRef.autoDiff(autoDiff.get))
        }
      }
    }
    None
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#declaration-references
  @throws[Error]
  def parseDeclRef(): SILDeclRef = {
    // sil-identifier ('.' sil-identifier)*
    val name = new ArrayBuffer[String]
    take("#")
    var break = false
    while (!break) {
      val identifier = parseIdentifier()
      name.append(identifier)
      if (!skip(".")) {
        break = true
      }
    }
    val subref = parseDeclSubRef()
    new SILDeclRef(name, subref)
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
    if(skip("static")) return SILEnforcement.static
    if(skip("dynamic")) return SILEnforcement.unknown
    if(skip("unsafe")) return SILEnforcement.unsafe
    throw parseError("unknown enforcement")
  }

  @throws[Error]
  def parseFunctionAttribute(): SILFunctionAttribute = {
    if(skip("[canonical]")) return SILFunctionAttribute.canonical
    if(skip("[differentiable")) {
      val spec = take(_ != ']' )
      take("]")
      return SILFunctionAttribute.differentiable(spec)
    }
    if(skip("[dynamically_replacable]")) return SILFunctionAttribute.dynamicallyReplacable
    if(skip("[always_inline]")) return SILFunctionAttribute.alwaysInline
    if(skip("[noinline]")) return SILFunctionAttribute.noInline
    if(skip("[ossa]")) return SILFunctionAttribute.ossa
    if(skip("[serialized]")) return SILFunctionAttribute.serialized
    if(skip("[serializable]")) return SILFunctionAttribute.serializable
    if(skip("[transparent]")) return SILFunctionAttribute.transparent
    if(skip("[thunk]")) return SILFunctionAttribute.Thunk.thunk
    if(skip("[signature_optimized_thunk]")) return SILFunctionAttribute.Thunk.signatureOptimized
    if(skip("[reabstraction_thunk]")) return SILFunctionAttribute.Thunk.reabstraction
    if(skip("[dynamic_replacement_for")) {
      val func = parseString()
      take("]")
      return SILFunctionAttribute.dynamicReplacement(func)
    }
    if(skip("[objc_replacement_for")) {
      val func = parseString()
      take("]")
      return SILFunctionAttribute.objcReplacement(func)
    }
    if(skip("[exact_self_class]")) return SILFunctionAttribute.exactSelfClass
    if(skip("[without_actually_escaping]")) return SILFunctionAttribute.withoutActuallyEscaping
    if(skip("[global_init]")) return SILFunctionAttribute.FunctionPurpose.globalInit
    if(skip("[lazy_getter]")) return SILFunctionAttribute.FunctionPurpose.lazyGetter
    if(skip("[weak_imported]")) return SILFunctionAttribute.weakImported
    if(skip("[available ")) {
      val version = new ArrayBuffer[String]
      var break = false
      while (!break) {
        val identifier = parseStringInt()
        version.append(identifier)
        if (!skip(".")) {
          break = true
        }
      }
      take("]")
      return SILFunctionAttribute.available(version)
    }
    if(skip("[never]")) return SILFunctionAttribute.FunctionInlining.never
    if(skip("[always]")) return SILFunctionAttribute.FunctionInlining.always
    if(skip("[Onone]")) return SILFunctionAttribute.FunctionOptimization.Onone
    if(skip("[Ospeed]")) return SILFunctionAttribute.FunctionOptimization.Ospeed
    if(skip("[Osize]")) return SILFunctionAttribute.FunctionOptimization.Osize
    if(skip("[readonly]")) return SILFunctionAttribute.FunctionEffects.readonly
    if(skip("[readnone]")) return SILFunctionAttribute.FunctionEffects.readnone
    if(skip("[readwrite]")) return SILFunctionAttribute.FunctionEffects.readwrite
    if(skip("[releasenone]")) return SILFunctionAttribute.FunctionEffects.releasenone
    if(skip("[_semantics")) {
      val value = parseString()
      take("]")
      return SILFunctionAttribute.semantics(value)
    }
    if(skip("[_specialize")) {
      var exported: Option[Boolean] = None
      if (skip("exported:")) {
        if (skip("true")) {
          exported = Some(true)
        } else {
          take("false")
          exported = Some(false)
        }
        take(",")
      }
      var kind: Option[SILFunctionAttribute.specialize.Kind] = None
      if (skip("kind:")) {
        if (skip("partial")) {
          kind = Some(SILFunctionAttribute.specialize.Kind.partial)
        } else {
          take("full")
          kind = Some(SILFunctionAttribute.specialize.Kind.full)
        }
        take(",")
      }
      val reqs = parseMany("where", ",", "]", parseTypeRequirement)
      return SILFunctionAttribute.specialize(exported, kind, reqs)
    }
    if(skip("[clang")) {
      val value = take(_ != ']')
      take("]")
      return SILFunctionAttribute.clang(value)
    }
    throw parseError("unknown function attribute")
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#functions
  @throws[Error]
  def parseMangledName(): SILMangledName = {
    val start = position()
    if(skip("@")) {
      val name = take(x => x == '$' || x.isLetterOrDigit || x == '_' )
      if(!name.isEmpty) {
        val m = new SILMangledName(name)
        toDemangle.append(m)
        return m
      }
    }
    throw parseError("function or global name expected", Some(start))
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
      val identifier = take(x => x.isLetterOrDigit || x == '_' || x == '`' || x == '$')
      if (!identifier.isEmpty) return identifier
      throw parseError("identifier expected", Some(start))
    }
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

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#linkage
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

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#witness-tables
  def parseNormalProtocolConformance(existingTpe: Option[SILType] = None): SILNormalProtocolConformance = {
    val tpe = {
      if (existingTpe.nonEmpty) {
        existingTpe.get
      } else {
        val t = parseType(true)
        take(":")
        t
      }
    }
    val protocol = parseIdentifier()
    take("module")
    val module = parseIdentifier()
    new SILNormalProtocolConformance(tpe, protocol, module)
  }

  def parseProtocolConformance(): SILProtocolConformance = {
    val tpe = parseType(true)
    take(":")
    if(skip("inherit")) {
      take("(")
      val protocolConformance = parseProtocolConformance()
      take(")")
      SILProtocolConformance.inherit(tpe, protocolConformance)
    } else if(skip("specialize")) { // Not yet tested
      val s = parseNilOrMany("<",",",">", parseNakedType)
      val substitutions = if (s.nonEmpty) s.get else ArrayBuffer.empty[SILType]
      take("(")
      val protocolConformance = parseProtocolConformance()
      take(")")
      SILProtocolConformance.specialize(substitutions, protocolConformance)
    } else if(skip("dependent")) { // Not yet tested
      SILProtocolConformance.dependent
    } else {
      // Otherwise assume normal-protocol-conformance
      SILProtocolConformance.normal(parseNormalProtocolConformance(Some(tpe)))
    }
  }

  @throws[Error]
  def parseWitnessEntry(): SILWitnessEntry = {
    if(skip("base_protocol")) {
      val identifier = parseIdentifier()
      take(":")
      val protocolConformance = parseProtocolConformance()
      return SILWitnessEntry.baseProtocol(identifier, protocolConformance)
    }
    if(skip("method")) {
      val declRef = parseDeclRef()
      take(":")
      val declType = parseNakedType()
      take(":")
      val functionName = if (!skip("nil")) Some(parseMangledName()) else None
      return SILWitnessEntry.method(declRef, declType, functionName)
    }
    // NOTE: Must come before associated_type
    if(skip("associated_type_protocol")) {
      val identifier = parseTypeName(true)
      return SILWitnessEntry.associatedTypeProtocol(identifier)
      /*
      take("(")
      val identifier0 = parseIdentifier()
      take(":")
      val identifier1 = parseIdentifier()
      take(")")
      take(":")
      val protocolConformance = parseProtocolConformance()
      return SILWitnessEntry.associatedTypeProtocol(identifier0, identifier1, protocolConformance)
       */
    }
    if(skip("associated_type")) {
      val identifier0 = parseIdentifier()
      take(":")
      val identifier1 = parseTypeName(allowOther = true)
      return SILWitnessEntry.associatedType(identifier0, identifier1)
    }
    if(skip("conditional_conformance")) {
      val identifier = parseTypeName(true)
      return SILWitnessEntry.conditionalConformance(identifier)
    }
    throw parseError("Unknown witness entry")
  }

  @throws[Error]
  def parseVEntry(): SILVEntry = {
    val declRef = parseDeclRef()
    take(":")
    var tpe: Option[SILType] = None
    if (!peek("@")) {
      tpe = Some(parseNakedType())
      take(":")
    }
    val linkage = {
      if (peek("@")) {
        None
      } else {
        Some(parseLinkage())
      }
    }
    val functionName = parseMangledName()
    val kind = parseVTableEntryKind()
    val nonoverridden = skip("[nonoverridden]")
    new SILVEntry(declRef, tpe, kind, nonoverridden, linkage, functionName)
  }

  def parseVTableEntryKind(): SILVTableEntryKind = {
    if(skip("[inherited]")) return SILVTableEntryKind.inherited
    if(skip("[override]")) return SILVTableEntryKind.overide
    SILVTableEntryKind.normal
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
  //
  // SIL types are extremely complicated because they are a combination of
  // SIL, Swift, and ObjC types. The documentation in SIL.rst and in the code
  // (Swift's parser and printer, for instance) is inconsistent/outdated and
  // it makes parsing types "correctly" difficult. Verbatim is good enough
  // as long as we can consistently print what we parse. These types get
  // converted to String form anyway in SWANIR.
  //
  // This is a context flag signifying whether the type parsing is inside of
  // params. Needed for named arg parsing. Ideally we would add a param to
  // parseNakedType, but that is not possible due to generics. The recursion
  // is linear anyway so it doesn't matter.

  class NakedStack {
    private val stack: util.Stack[Char] = new util.Stack[Char]()
    sealed trait Type
    object Type {
      case object Paren extends Type
      case object Square extends Type
      case object Arrows extends Type
    }
    def clear(): Unit = {
      this.stack.clear()
    }
    def openParen(): Unit = {
      stack.push('(')
    }
    def closeParen(): Unit = {
      if (this.stack.peek() == '(') {
        stack.pop()
      } else {
        throw new RuntimeException("Unmatched paren")
      }
    }
    def openSquare(): Unit = {
      stack.push('[')
    }
    def closeSquare(): Unit = {
      if (this.stack.peek() == '[') {
        stack.pop()
      } else {
        throw new RuntimeException("Unmatched square")
      }
    }
    def openArrows(): Unit = {
      stack.push('<')
    }
    def closeArrows(): Unit = {
      if (this.stack.peek() == '<') {
        stack.pop()
      } else {
        throw new RuntimeException("Unmatched arrow")
      }
    }
    def getCurrentType: Option[Type] = {
      var tpe: Option[Type] = None
      if (this.stack.size() > 0) {
        this.stack.peek() match {
          case '(' => tpe = Some(Type.Paren)
          case '[' => tpe = Some(Type.Square)
          case '<' => tpe = Some(Type.Arrows)
          case _ =>
        }
      }
      tpe
    }
    def inParen: Boolean = {
      val cur = this.getCurrentType
      if (cur.nonEmpty) {
        cur.get match {
          case Type.Paren => true
          case Type.Square => false
          case Type.Arrows => false
        }
      } else {
        false
      }
    }
    def inSquare: Boolean = {
      val cur = this.getCurrentType
      if (cur.nonEmpty) {
        cur.get match {
          case Type.Paren => false
          case Type.Square => true
          case Type.Arrows => false
        }
      } else {
        false
      }
    }
    def inArrows: Boolean = {
      val cur = this.getCurrentType
      if (cur.nonEmpty) {
        cur.get match {
          case Type.Paren => false
          case Type.Square => false
          case Type.Arrows => true
        }
      } else {
        false
      }
    }
  }

  var varGrowOverride = false
  val nakedStack = new NakedStack()

  @throws[Error]
  def parseNakedType(): SILType = {
    @throws[Error]
    @tailrec
    def grow(tpe: SILType): SILType = {
      if (peek("<")) {
        val types = parseMany("<",",",">", parseNakedType)
        val growType = {
          tpe match {
            case namedType: SILType.namedType if namedType.name == "Array" =>
              SILType.arrayType(types, nakedStyle = false, optional = false)
            case _ =>
              SILType.specializedType(tpe, types, optional = skip("?"))
          }
        }
        grow(growType)
      } else if (skip(".")) {
        val name = parseTypeName()
        grow(SILType.selectType(tpe, name))
      } else if (skip("for")) {
        val types = parseMany("<",",",">", parseNakedType)
        SILType.forType(tpe, types)
      } else if (skip("&")) {
        SILType.andType(tpe, parseNakedType())
      } else {
        tpe
      }
    }
    if (skip("<")) {
      nakedStack.openArrows()
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
      nakedStack.closeArrows()
      val tpe = parseNakedType()
      SILType.genericType(params, reqs, tpe)
    } else if (peek("@")) {
      val attrs = parseMany("@", parseTypeAttribute)
      val tpe = parseNakedType()
      SILType.attributedType(attrs, tpe)
    } else if (peek("inout")) {
      take("inout")
      val tpe = parseNakedType()
      SILType.attributedType(ArrayBuffer(SILTypeAttribute.typeSpecifierInOut), tpe)
    } else if (peek("__owned")) {
      take("__owned")
      val tpe = parseNakedType()
      SILType.attributedType(ArrayBuffer(SILTypeAttribute.typeSpecifierOwned), tpe)
    } else if (peek("__shared")) {
      take("__shared")
      val tpe = parseNakedType()
      SILType.attributedType(ArrayBuffer(SILTypeAttribute.typeSpecifierUnowned), tpe)
    } else if (skip("*")) {
      val tpe = parseNakedType()
      SILType.addressType(tpe)
    } else if (skip("[")) {
      nakedStack.openSquare()
      val subtype = parseNakedType()
      take("]")
      nakedStack.closeSquare()
      SILType.arrayType(ArrayBuffer(subtype), nakedStyle = true, optional = skip("?"))
    } else if (peek("(")) {
      nakedStack.openParen()
      val types = parseMany("(", ",", ")", parseNakedType)
      nakedStack.closeParen()
      if (skip(".Type")) {
        SILType.dotType(types)
      } else {
        val optional = skip("?")
        val throws = skip("throws")
        val dots = skip("...")
        if (skip("->")) {
          var result = parseNakedType()
          if (peek("for")) {
            result = grow(result)
          }
          SILType.functionType(types, optional, throws, result)
        } else {
          SILType.tupleType(types, optional, dots)
        }
      }
    } else if (peek("{")) {
      take("{")
      take("var")
      val tpe = SILType.varType(parseNakedType())
      take("}")
      if (varGrowOverride) {
        varGrowOverride = false
        tpe
      } else {
        grow(tpe)
      }
    } else {
      val name = parseTypeName()
      val arg: Option[SILType] = {
        if ((nakedStack.inSquare || nakedStack.inParen) && skip(":")) {
          Some(parseNakedType())
        } else {
          None
        }
      }
      if (arg.nonEmpty) return SILType.namedArgType(name, arg.get, nakedStack.inSquare)
      val base: SILType = {
        name match {
          case "Self?" => SILType.selfTypeOptional
          case "Self" => SILType.selfType
          case _ => SILType.namedType(name)
        }
      }
      grow(base)
    }
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#values-and-operands
  @throws[Error]
  def parseOperand(): SILOperand = {
    val valueName = parseValue()
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
      Some(new SILResult(ArrayBuffer(valueName)))
    } else if (peek("(")) {
      val valueNames = parseMany("(", ",", ")", parseValueName)
      take("=")
      Some(new SILResult(valueNames))
    } else {
      None
    }
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#debug-information
  @throws[Error]
  def parseScopeRef(): Option[SILScopeRef] = {
    if(!skip("scope")) return None
    Some(new SILScopeRef(parseInt()))
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
  def parseScope(): SILScope = {
    take("sil_scope")
    val num = parseInt()
    take("{")
    val loc = parseLoc()
    take("parent")
    val parent = parseScopeParent()
    var inlinedAt: Option[SILScopeRef] = None
    if (skip("inlined_at")) {
      inlinedAt = parseScopeRef()
    }
    take("}")
    new SILScope(num, loc, parent, inlinedAt)
  }

  @throws[Error]
  def parseScopeParent(): SILScopeParent = {
    val ref = {
      if (Character.isDigit(chars(cursor))) {
        Some(parseInt())
      } else {
        None
      }
    }
    if (ref.nonEmpty) return SILScopeParent.ref(ref.get)
    val name = parseMangledName()
    take(":")
    val tpe = parseType()
    SILScopeParent.func(name, tpe)
  }

  @throws[Error]
  def parseString(): String = {
    // T0D0: Parse string literals with control characters.
    //  - Already jankily handle escaped " character
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
  def parseNakedString(): String = {
    take(_ != ' ')
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
  // Naked arg is for parsing types without a $ prefix. This can be seen
  // in unconditional_checked_cast_addr instruction.
  @throws[Error]
  def parseType(naked: Boolean = false): SILType = {
    // Ownership SSA has a surprising convention of printing the
    // ownership type before the actual type, so we first try to
    // parse the type attribute.
    if (naked) {
      skip("$")
    } else if (!skip("$")) {
        val attr : Option[SILTypeAttribute] = {
          if (peek("@")) {
            Some(parseTypeAttribute())
          } else {
            None
          }
        }
        // Take the $ for real even if the attribute was not there, because
        // that's the error message we want to show anyway.
        take("$")
        // We want to throw our own exception type here so we rethrow
        // if attr.get fails (it's needed just below the try/catch).
        try {
          //noinspection ScalaUnusedExpression
          attr.get
        } catch {
          case e : Throwable => {
            throw parseError(e.getMessage)
          }
        }
        SILType.withOwnership(attr.get, parseNakedType())
      }
    parseNakedType()
  }

  @throws[Error]
  // IMPORTANT: TypeSpecifiers are handled in parseNakedType() because they
  // don't start with '@'.
  def parseTypeAttribute(): SILTypeAttribute = {
    if(skip("@pseudogeneric")) return SILTypeAttribute.pseudoGeneric
    if(skip("@callee_guaranteed")) return SILTypeAttribute.calleeGuaranteed
    if(skip("@substituted")) return SILTypeAttribute.substituted
    if(skip("@convention")) return SILTypeAttribute.convention(parseConvention())
    if(skip("@guaranteed")) return SILTypeAttribute.guaranteed
    if(skip("@in_guaranteed")) return SILTypeAttribute.inGuaranteed
    // Must appear before "inout" to parse correctly.
    if(skip("@inout_aliasable")) return SILTypeAttribute.inoutAliasable
    // Must appear before "in" to parse correctly.
    if(skip("@inout")) return SILTypeAttribute.inout
    if(skip("@in")) return SILTypeAttribute.in
    if(skip("@noescape")) return SILTypeAttribute.noescape
    if(skip("@thick")) return SILTypeAttribute.thick
    if(skip("@out")) return SILTypeAttribute.out
    if(skip("@unowned_inner_pointer")) return SILTypeAttribute.unownedInnerPointer
    if(skip("@unowned")) return SILTypeAttribute.unowned
    if(skip("@owned")) return SILTypeAttribute.owned
    if(skip("@thin")) return SILTypeAttribute.thin
    if(skip("@yield_once")) return SILTypeAttribute.yieldOnce
    if(skip("@yields")) return SILTypeAttribute.yields
    if(skip("@error")) return SILTypeAttribute.error
    if(skip("@objc_metatype")) return SILTypeAttribute.objcMetatype
    if(skip("@sil_weak")) return SILTypeAttribute.silWeak
    if(skip("@sil_unowned")) return SILTypeAttribute.silUnowned
    if(skip("@sil_unmanaged")) return SILTypeAttribute.silUnmanaged
    if(skip("@autoreleased")) return SILTypeAttribute.autoreleased
    if(skip("@dynamic_self")) return SILTypeAttribute.dynamicSelf
    if(skip("@block_storage")) return SILTypeAttribute.blockStorage
    if(skip("@escaping")) return SILTypeAttribute.escaping
    if(skip("@opened")) {
      take("(")
      val value = parseString()
      take(")")
      return SILTypeAttribute.opened(value)
    }
    throw parseError("unknown attribute")
  }

  // https://github.com/apple/swift/blob/master/docs/SIL.rst#mark_uninitialized
  @throws[Error]
  def parseMUKind(): SILMUKind = {
    if(skip("var")) return SILMUKind.varr
    if(skip("rootself")) return SILMUKind.rootSelf
    if(skip("crossmodulerootself")) return SILMUKind.crossModuleRootSelf
    if(skip("derivedself")) return SILMUKind.derivedSelf
    if(skip("derivedselfonly")) return SILMUKind.derivedSelfOnly
    if(skip("delegatingself")) return SILMUKind.delegatingSelf
    if(skip("delegatingselfallocated")) return SILMUKind.delegatingSelfAllocated
    throw parseError("unknown MU kind")
  }

  @throws[Error]
  def parseTypeName(allowOther: Boolean = false): String = {
    val start = position()
    var name: String = take(x => x.isLetter || Character.isDigit(x)
      || x == '_' || x == '?' || x == '.' || (allowOther && (x != '\n')))
    if(skip("...")) {
      name += "..."
    }
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

class Error(path : String, message : String, val chars: Array[Char]) extends Exception {
  private[parser] var line : Option[Int] = None
  private[parser] var column : Option[Int] = None

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
