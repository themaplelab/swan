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

import ca.ualberta.maple.swan.parser.SILFunctionAttribute._
import ca.ualberta.maple.swan.parser.SILFunctionAttribute.specialize.Kind

import scala.collection.mutable

class SILPrinterOptions {
  var printLocation = true
  var genLocationMap = false // expensive
  def printLocation(b: Boolean): SILPrinterOptions = {
    printLocation = b
    this
  }
  def genLocationMap(b: Boolean): SILPrinterOptions = {
    genLocationMap = b
    this
  }
}

// Many printX will return the description for convenience.
class SILPrinter extends Printer {

  val silLocMap: mutable.HashMap[Object, (Int, Int)] =
    new mutable.HashMap[Object, (Int, Int)]()

  // Is only correct for single files
  val swiftLocMap: mutable.HashMap[Object, (Int, Int)] =
    new mutable.HashMap[Object, (Int, Int)]()

  var options = new SILPrinterOptions()

  def clear(): Unit = { description.clear() }

  def print(module: SILModule, opts: SILPrinterOptions): String = {

    options = opts

    print("sil_stage canonical")
    printNewline();printNewline()
    // These are not actually all printed at once, but this is fine
    // because we can modify the expected input to expect it here.
    module.scopes.foreach(scope => {
      print(scope)
      printNewline()
    })
    module.imports.foreach(imprt => {
      print("import ")
      print(imprt)
      printNewline()
    })
    printNewline()
    module.globalVariables.foreach(gv => {
      print(gv)
    })
    module.functions.foreach(f => {
      print(f)
      printNewline()
    })
    module.vTables.foreach(v => {
      print(v)
      printNewline()
    })
    module.witnessTables.foreach(w => {
      print(w)
      printNewline()
    })
    module.properties.foreach(p => {
      print(p)
      printNewline()
    })
    this.toString
  }

  def print(function: SILFunction): Unit = {
    if (options.genLocationMap) silLocMap.put(function, (this.line, this.getCol))
    print("sil ")
    print(function.linkage)
    print(whenEmpty = false, "", function.attributes, " ", " ", (attribute: SILFunctionAttribute) => print(attribute))
    print(function.name)
    print(" : ")
    print(function.tpe)
    print(whenEmpty = false, " {\n", function.blocks, "\n", "}", (block: SILBlock) => print(block))
    printNewline()
  }

  def print(block: SILBlock): Unit = {
    if (options.genLocationMap) silLocMap.put(block, (this.line, this.getCol))
    print(block.identifier)
    print(whenEmpty = false, "(", block.arguments, ", ", ")", (arg: SILArgument) => print(arg))
    print(":")
    indent()
    block.operatorDefs.foreach(op => {
      printNewline()
      print(op)
    })
    printNewline()
    print(block.terminatorDef)
    printNewline()
    unindent()
  }

  def print(instructionDef: SILInstructionDef): Unit = {
    instructionDef match {
      case SILInstructionDef.operator(operatorDef) => print(operatorDef)
      case SILInstructionDef.terminator(terminatorDef) => print(terminatorDef)
    }
  }

  def print(operatorDef: SILOperatorDef): Unit = {
    print(operatorDef.res, " = ", (r: SILResult) => {
      print(r)
    })
    print(operatorDef.operator)
    if (options.genLocationMap) {
      silLocMap.put(operatorDef, (this.line, this.getCol))
      if (operatorDef.sourceInfo.nonEmpty) {
        val loc = operatorDef.sourceInfo.get.loc
        if (loc.nonEmpty) {
          swiftLocMap.put(operatorDef, (loc.get.line, loc.get.column))
        }
      }
    }
    print(operatorDef.sourceInfo, (si: SILSourceInfo) => print(si))
  }

  def print(terminatorDef: SILTerminatorDef): Unit = {
    print(terminatorDef.terminator)
    if (options.genLocationMap) {
      silLocMap.put(terminatorDef, (this.line, this.getCol))
      if (terminatorDef.sourceInfo.nonEmpty) {
        val loc = terminatorDef.sourceInfo.get.loc
        if (loc.nonEmpty) {
          swiftLocMap.put(terminatorDef, (loc.get.line, loc.get.column))
        }
      }
    }
    print(terminatorDef.sourceInfo, (si: SILSourceInfo) => print(si))
  }

  def print(op: SILOperator): Unit = {
    op match {

        // *** ALLOCATION AND DEALLOCATION ***

      case SILOperator.allocStack(tpe, dynamicLifetime, attributes) => {
        print("alloc_stack ")
        print("[dynamic_lifetime] ", when = dynamicLifetime)
        print(tpe)
        print(whenEmpty = false, ", ", attributes, ", ", "", (a: SILDebugAttribute) => print(a))
      }
      case SILOperator.allocRef(attributes, tailElems, tpe) => {
        print("alloc_ref ")
        print(whenEmpty = false, "", attributes, " ", "", (a: SILAllocAttribute) => print(a))
        if (attributes.nonEmpty) print(" ")
        if (tailElems.nonEmpty) {
          print(whenEmpty = false, "", tailElems, " ", "",
            (te: (SILType, SILOperand)) => {
              print("[tail_elems ")
              print(te._1)
              print(" * ")
              print(te._2)
              print("]")
            })
          print(" ")
        }
        print(tpe)
      }
      case SILOperator.allocRefDynamic(objc, tailElems, operand, tpe) => {
        print("alloc_ref_dynamic ")
        if (objc) {
          print(SILAllocAttribute.objc)
          print(" ")
        }
        if (tailElems.nonEmpty) {
          print(whenEmpty = false, "", tailElems, " ", "",
            (te: (SILType, SILOperand)) => {
              print("[tail_elems ")
              print(te._1)
              print(" * ")
              print(te._2)
              print("]")
            })
          print(" ")
        }
        print(operand)
        print(", ")
        print(tpe)
      }
      case SILOperator.allocBox(tpe, attributes) => {
        print("alloc_box ")
        if (tpe.isInstanceOf[SILType.genericType]) print("$")
        print(tpe)
        print(whenEmpty = false, ", ", attributes, ", ", "", (a: SILDebugAttribute) => print(a))
      }
      case SILOperator.allocValueBuffer(tpe, operand) => {
        print("alloc_value_buffer ")
        print(tpe)
        print(" in ")
        print(operand)
      }
      case SILOperator.allocGlobal(name) => {
        print("alloc_global ")
        print(name)
      }
      case SILOperator.deallocStack(operand) => {
        print("dealloc_stack ")
        print(operand)
      }
      case SILOperator.deallocBox(operand) => {
        print("dealloc_box ")
        print(operand.value)
        print(" : ")
        print("$") // unusual case where generic type needs "$"
        print(operand.tpe)
      }
      case SILOperator.projectBox(operand, fieldIndex) => {
        print("project_box ")
        print(operand)
        print(", ")
        literal(fieldIndex)
      }
      case SILOperator.deallocRef(stack, operand) => {
        print("dealloc_ref ")
        if (stack) {
          print(SILAllocAttribute.stack)
          print(" ")
        }
        print(operand)
      }
      case SILOperator.deallocPartialRef(operand1, operand2) => {
        print("dealloc_partial_ref ")
        print(operand1)
        print(", ")
        print(operand2)
      }
      case SILOperator.deallocValueBuffer(tpe, operand) => {
        print("dealloc_value_buffer ")
        print(tpe)
        print(" in ")
        print(operand)
      }

        // *** DEBUG INFORMATION ***

      case SILOperator.debugValue(operand, attributes) => {
        print("debug_value ")
        print(operand)
        print(whenEmpty = false, ", ", attributes, ", ", "", (a: SILDebugAttribute) => print(a))
      }
      case SILOperator.debugValueAddr(operand, attributes) => {
        print("debug_value_addr ")
        print(operand)
        print(whenEmpty = false, ", ", attributes, ", ", "", (a: SILDebugAttribute) => print(a))
      }

        // *** ACCESSING MEMORY ***

      case SILOperator.load(kind: Option[SILLoadOwnership], operand) => {
        print("load ")
        if (kind.nonEmpty) {
          print(kind.get)
          print(" ")
        }
        print(operand)
      }
      case SILOperator.store(value, kind: Option[SILStoreOwnership], operand) => {
        print("store ")
        print(value)
        print(" to ")
        if (kind.nonEmpty) {
          print(kind.get)
          print(" ")
        }
        print(operand)
      }
      case SILOperator.loadBorrow(operand) => {
        print("load_borrow ")
        print(operand)
      }
      case SILOperator.storeBorrow(from, to) => {
        print("store_borrow ")
        print(from)
        print(" to ")
        print(to)
      }
      case SILOperator.beginBorrow(operand) => {
        print("begin_borrow ")
        print(operand)
      }
      case SILOperator.endBorrow(operand) => {
        print("end_borrow ")
        print(operand)
      }
      case SILOperator.endLifetime(operand) => {
        print("end_lifetime ")
        print(operand)
      }
      case SILOperator.copyAddr(take, value, initialization, operand) => {
        print("copy_addr ")
        print( "[take] ", take)
        print(value)
        print(" to ")
        print( "[initialization] ", initialization)
        print(operand)
      }
      case SILOperator.destroyAddr(operand) => {
        print("destroy_addr ")
        print(operand)
      }
      case SILOperator.indexAddr(addr, index) => {
        print("index_addr ")
        print(addr)
        print(", ")
        print(index)
      }
      case SILOperator.indexRawPointer(pointer, offset) => {
        print("index_raw_pointer ")
        print(pointer)
        print(", ")
        print(offset)
      }
      case SILOperator.bindMemory(operand1, operand2, toType) => {
        print("bind_memory ")
        print(operand1)
        print(", ")
        print(operand2)
        print(" to ")
        print(toType)
      }
      case SILOperator.beginAccess(access, enforcement, noNestedConflict, builtin, operand) => {
        print("begin_access ")
        print("[")
        print(access)
        print("] ")
        print("[")
        print(enforcement)
        print("] ")
        print("[noNestedConflict] ", noNestedConflict)
        print("[builtin] ", builtin)
        print(operand)
      }
      case SILOperator.endAccess(abort, operand) => {
        print("end_access ")
        print( "[abort] ", abort)
        print(operand)
      }
      case SILOperator.beginUnpairedAccess(access, enforcement, noNestedConflict, builtin, operand, buffer) => {
        print("begin_unpaired_access ")
        print("[")
        print(access)
        print("] ")
        print("[")
        print(enforcement)
        print("] ")
        print("[noNestedConflict] ", noNestedConflict)
        print("[builtin] ", builtin)
        print(operand)
        print(", ")
        print(buffer)
      }
      case SILOperator.endUnpairedAccess(abort, enforcement, operand) => {
        print("end_access ")
        print( "[abort] ", when = abort)
        print("[")
        print(enforcement)
        print("]")
        print(operand)
      }

      // *** REFERENCE COUNTING ***

      case SILOperator.strongRetain(operand) => {
        print("strong_retain ")
        print(operand)
      }
      case SILOperator.strongRelease(operand) => {
        print("strong_release ")
        print(operand)
      }
      case SILOperator.setDeallocating(operand) => {
        print("set_deallocating ")
        print(operand)
      }
      case SILOperator.copyUnownedValue(operand) => {
        print("copy_unowned_value ")
        print(operand)
      }
      case SILOperator.strongCopyUnownedValue(operand) => {
        print("strong_copy_unowned_value ")
        print(operand)
      }
      case SILOperator.strongRetainUnowned(operand) => {
        print("strong_retain_unowned ")
        print(operand)
      }
      case SILOperator.unownedRetain(operand) => {
        print("unowned_retain ")
        print(operand)
      }
      case SILOperator.unownedRelease(operand) => {
        print("unowned_release ")
        print(operand)
      }
      case SILOperator.loadWeak(take: Boolean, operand) => {
        print("load_weak ")
        print("[take]", take)
        print(operand)
      }
      case SILOperator.storeWeak(from, initialization, to) => {
        print("store_weak ")
        print(from)
        print(" to ")
        print("[initialization] ", initialization)
        print(to)
      }
      case SILOperator.loadUnowned(operand) => {
        print("load_unowned ")
        print(operand)
      }
      case SILOperator.storeUnowned(from, initialization, to) => {
        print("store_unowned ")
        print(from)
        print(" to ")
        print("[initialization] ", initialization)
        print(to)
      }
      case SILOperator.fixLifetime(operand) => {
        print("fix_lifetime")
        print(operand)
      }
      case SILOperator.markDependence(operand, on) => {
        print("mark_dependence ")
        print(operand)
        print(" on ")
        print(on)
      }
      case SILOperator.isUnique(operand) => {
        print("is_unique ")
        print(operand)
      }
      case SILOperator.isEscapingClosure(operand, objc) => {
        print("is_escaping_closure ")
        print("[objc] ", when = objc)
        print(operand)
      }
      case SILOperator.copyBlock(operand) => {
        print("copy_block ")
        print(operand)
      }
      case SILOperator.copyBlockWithoutEscaping(operand1, operand2) => {
        print("copy_block_without_escaping ")
        print(operand1)
        print(" withoutEscaping ")
        print(operand2)
      }

        // *** LITERALS ***

      case SILOperator.functionRef(name, tpe) => {
        print("function_ref ")
        print(name)
        print(" : ")
        print(tpe)
      }
      case SILOperator.dynamicFunctionRef(name, tpe) => {
        print("dynamic_function_ref ")
        print(name)
        print(" : ")
        print(tpe)
      }
      case SILOperator.prevDynamicFunctionRef(name, tpe) => {
        print("prev_dynamic_function_ref ")
        print(name)
        print(" : ")
        print(tpe)
      }
      case SILOperator.globalAddr(name, tpe) => {
        print("global_addr ")
        print(name)
        print(" : ")
        print(tpe)
      }
      case SILOperator.integerLiteral(tpe, value) => {
        print("integer_literal ")
        print(tpe)
        print(", ")
        literal(value)
      }
      case SILOperator.floatLiteral(tpe, value) => {
        print("float_literal ")
        print(tpe)
        print(", 0x")
        print(value)
      }
      case SILOperator.stringLiteral(encoding, value) => {
        print("string_literal ")
        print(encoding)
        print(" ")
        literal(value)
      }

        // *** DYNAMIC DISPATCH ***

      case SILOperator.classMethod(operand, declRef, declType, tpe) => {
        print("class_method ")
        print(operand)
        print(", ")
        print(declRef)
        print(" : ")
        naked(declType)
        print(", ")
        print(tpe)
      }
      case SILOperator.objcMethod(operand, declRef, declType, tpe) => {
        print("objc_method ")
        print(operand)
        print(", ")
        print(declRef)
        print(" : ")
        naked(declType)
        print(", ")
        print(tpe)
      }
      case SILOperator.superMethod(operand, declRef, declType, tpe) => {
        print("super_method ")
        print(operand)
        print(", ")
        print(declRef)
        print(" : ")
        naked(declType)
        print(", ")
        print(tpe)
      }
      case SILOperator.objcSuperMethod(operand, declRef, declType, tpe) => {
        print("objc_super_method ")
        print(operand)
        print(", ")
        print(declRef)
        print(" : ")
        naked(declType)
        print(", ")
        print(tpe)
      }
      case SILOperator.witnessMethod(archetype, declRef, declType, value, tpe) => {
        print("witness_method ")
        print(archetype)
        print(", ")
        print(declRef)
        print(" : ")
        print(declType)
        if (value.nonEmpty) {
          print(", ")
          print(value.get)
        }
        print(" : ")
        print(tpe)
      }

        // *** FUNCTION APPLICATION ***

      case SILOperator.apply(nothrow, value, substitutions, arguments, tpe) => {
        print("apply ")
        print( "[nothrow] ", nothrow)
        print(value)
        print(whenEmpty = false, "<", substitutions, ", ", ">", (t: SILType) => naked(t))
        print(whenEmpty = true, "(", arguments, ", ", ")", (a: String) => print(a))
        print(" : ")
        print(tpe)
      }
      case SILOperator.beginApply(nothrow, value, substitutions, arguments, tpe) => {
        print("begin_apply ")
        print( "[nothrow] ", nothrow)
        print(value)
        print(whenEmpty = false, "<", substitutions, ", ", ">", (t: SILType) => naked(t))
        print(whenEmpty = true, "(", arguments, ", ", ")", (a: String) => print(a))
        print(" : ")
        print(tpe)
      }
      case SILOperator.abortApply(value) => {
        print("abort_apply ")
        print(value)
      }
      case SILOperator.endApply(value) => {
        print("end_apply ")
        print(value)
      }
      case SILOperator.partialApply(calleeGuaranteed, onStack, value, substitutions, arguments, tpe) => {
        print("partial_apply ")
        print( "[callee_guaranteed] ", calleeGuaranteed)
        print( "[on_stack] ", onStack)
        print(value)
        print(whenEmpty = false, "<", substitutions, ", ", ">", (t: SILType) => naked(t))
        print(whenEmpty = true, "(", arguments, ", ", ")", (a: String) => print(a))
        print(" : ")
        print(tpe)
      }
      case SILOperator.builtin(name, templateTpe, operands, tpe) => {
        print("builtin ")
        literal(name)
        if (templateTpe.nonEmpty) {
          print("<")
          naked(templateTpe.get)
          print(">")
        }
        print(whenEmpty = true, "(", operands, ", ", ")", (o: SILOperand) => print(o))
        print(" : ")
        print(tpe)
      }

        // *** METATYPES ***

      case SILOperator.metatype(tpe) => {
        print("metatype ")
        print(tpe)
      }
      case SILOperator.valueMetatype(tpe, operand) => {
        print("value_metatype ")
        print(tpe)
        print(", ")
        print(operand)
      }
      case SILOperator.existentialMetatype(tpe, operand) => {
        print("existential_metatype ")
        print(tpe)
        print(", ")
        print(operand)
      }
      case SILOperator.objcProtocol(protocolDecl, tpe) => {
        print("objc_protocol ")
        print(protocolDecl)
        print(" : ")
        print(tpe)
      }

        // *** AGGREGATE TYPES ***


      case SILOperator.retainValue(operand) => {
        print("retain_value ")
        print(operand)
      }
      case SILOperator.retainValueAddr(operand) => {
        print("retain_value_addr ")
        print(operand)
      }
      case SILOperator.unmanagedRetainValue(operand) => {
        print("unmanaged_retain_value ")
        print(operand)
      }
      case SILOperator.copyValue(operand) => {
        print("copy_value ")
        print(operand)
      }
      case SILOperator.strongCopyUnmanagedValue(operand) => {
        print("strong_copy_unmanaged_value ")
        print(operand)
      }
      case SILOperator.releaseValue(operand) => {
        print("release_value ")
        print(operand)
      }
      case SILOperator.releaseValueAddr(operand) => {
        print("release_value_addr ")
        print(operand)
      }
      case SILOperator.unmanagedReleaseValue(operand) => {
        print("unmanaged_release_value ")
        print(operand)
      }
      case SILOperator.destroyValue(operand) => {
        print("destroy_value ")
        print(operand)
      }
      case SILOperator.autoreleaseValue(operand) => {
        print("autorelease_value ")
        print(operand)
      }
      case SILOperator.unmanagedAutoreleaseValue(operand) => {
        print("unmanaged_autorelease_value ")
        print(operand)
      }
      case SILOperator.tuple(elements) => {
        print("tuple ")
        print(elements)
      }
      case SILOperator.tupleExtract(operand, declRef) => {
        print("tuple_extract ")
        print(operand)
        print(", ")
        literal(declRef)
      }
      case SILOperator.tupleElementAddr(operand, declRef) => {
        print("tuple_element_addr ")
        print(operand)
        print(", ")
        literal(declRef)
      }
      case SILOperator.destructureTuple(operand) => {
        print("destructure_tuple ")
        print(operand)
      }
      case SILOperator.struct(tpe, operands) => {
        print("struct ")
        print(tpe)
        print(whenEmpty = true, " (", operands, ", ", ")", (o: SILOperand) => print(o))
      }
      case SILOperator.structExtract(operand, declRef) => {
        print("struct_extract ")
        print(operand)
        print(", ")
        print(declRef)
      }
      case SILOperator.structElementAddr(operand, declRef) => {
        print("struct_element_addr ")
        print(operand)
        print(", ")
        print(declRef)
      }
      case SILOperator.objct(tpe, operands, tailElems) => {
        print("object ")
        print(tpe)
        print(whenEmpty = true, " (", operands, ", ", "", (o: SILOperand) => print(o))
        if (tailElems.length > 0) {
          if (operands.length > 0) {
            print(", ")
          }
          print("[tail_elems] ")
        }
        print(whenEmpty = true, "", tailElems, ", ", ")", (o: SILOperand) => print(o))
      }
      case SILOperator.refElementAddr(immutable, operand, declRef) => {
        print("ref_element_addr ")
        if (immutable) print("[immutable] ")
        print(operand)
        print(", ")
        print(declRef)
      }
      case SILOperator.refTailAddr(immutable, operand, tpe) => {
        print("ref_tail_addr ")
        if (immutable) print("[immutable] ")
        print(operand)
        print(", ")
        print(tpe)
      }

        // *** ENUMS ***

      case SILOperator.enm(tpe, declRef, operand: Option[SILOperand]) => {
        print("enum ")
        print(tpe)
        print(", ")
        print(declRef)
        if (operand.nonEmpty) {
          print(", ")
          print(operand.get)
        }
      }
      case SILOperator.uncheckedEnumData(operand, declRef) => {
        print("unchecked_enum_data ")
        print(operand)
        print(", ")
        print(declRef)
      }
      case SILOperator.initEnumDataAddr(operand, declRef) => {
        print("init_enum_data_addr ")
        print(operand)
        print(", ")
        print(declRef)
      }
      case SILOperator.injectEnumAddr(operand, declRef) => {
        print("inject_enum_addr ")
        print(operand)
        print(", ")
        print(declRef)
      }
      case SILOperator.uncheckedTakeEnumDataAddr(operand, declRef) => {
        print("unchecked_take_enum_data_addr ")
        print(operand)
        print(", ")
        print(declRef)
      }
      case SILOperator.selectEnum(operand, cases, tpe) => {
        print("select_enum ")
        print(operand)
        print(whenEmpty = false, "", cases, "", "", (c: SILSwitchEnumCase) => print(c))
        print(" : ")
        print(tpe)
      }
      case SILOperator.selectEnumAddr(operand, cases, tpe) => {
        print("select_enum_addr ")
        print(operand)
        print(whenEmpty = false, "", cases, "", "", (c: SILSwitchEnumCase) => print(c))
        print(" : ")
        print(tpe)
      }

        // *** PROTOCOL AND PROTOCOL COMPOSITION TYPES ***

      case SILOperator.initExistentialAddr(operand, tpe) => {
        print("init_existential_addr ")
        print(operand)
        print(", ")
        print(tpe)
      }
      case SILOperator.deinitExistentialAddr(operand) => {
        print("deinit_existential_addr ")
        print(operand)
      }
      case SILOperator.openExistentialAddr(access, operand, tpe) => {
        print("open_existential_addr ")
        print(access)
        print(" ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.initExistentialRef(operand, tpeC, tpeP) => {
        print("init_existential_ref ")
        print(operand)
        print(" : ")
        print(tpeC)
        print(", ")
        print(tpeP)
      }
      case SILOperator.openExistentialRef(operand, tpe) => {
        print("open_existential_ref ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.initExistentialMetatype(operand, tpe) => {
        print("init_existential_metatype ")
        print(operand)
        print(", ")
        print(tpe)
      }
      case SILOperator.openExistentialMetatype(operand, tpe) => {
        print("open_existential_metatype ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.allocExistentialBox(tpeP, tpeT) => {
        print("alloc_existential_box ")
        print(tpeP)
        print(", ")
        print(tpeT)
      }
      case SILOperator.projectExistentialBox(tpe, operand) => {
        print("project_existential_box ")
        print(tpe)
        print(" in ")
        print(operand)
      }
      case SILOperator.openExistentialBox(operand, tpe) => {
        print("open_existential_box ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.deallocExistentialBox(operand, tpe) => {
        print("dealloc_existential_box ")
        print(operand)
        print(", ")
        print(tpe)
      }

        // *** BLOCKS ***

      case SILOperator.projectBlockStorage(operand) => {
        print("project_block_storage ")
        print(operand)
      }
      case SILOperator.initBlockStorageHeader(operand, invoke, tpe) => {
        print("init_block_storage_header ")
        print(operand)
        print(", invoke ")
        print(invoke)
        print(", type ")
        print(tpe)
      }

        // *** UNCHECKED CONVERSIONS ***

      case SILOperator.upcast(operand, tpe) => {
        print("upcast ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.addressToPointer(operand, tpe) => {
        print("address_to_pointer ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.pointerToAddress(operand, strict, tpe) => {
        print("pointer_to_address ")
        print(operand)
        print(" to ")
        print( "[strict] ", strict)
        print(tpe)
      }
      case SILOperator.uncheckedRefCast(operand, tpe) => {
        print("unchecked_ref_cast ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.uncheckedRefCastAddr(fromTpe, fromOperand, toTpe, toOperand) => {
        print("unchecked_ref_cast_addr ")
        naked(fromTpe)
        print(" in ")
        print(fromOperand)
        print(" to ")
        naked(toTpe)
        print(" in ")
        print(toOperand)
      }
      case SILOperator.uncheckedAddrCast(operand, tpe) => {
        print("unchecked_addr_cast ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.uncheckedTrivialBitCast(operand, tpe) => {
        print("unchecked_trivial_bit_cast ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.uncheckedBitwiseCast(operand, tpe) => {
        print("unchecked_bitwise_cast ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.uncheckedOwnershipConversion(operand, from, to) => {
        print("unchecked_ownership_conversion ")
        print(operand)
        print(", ")
        print(from)
        print(" to ")
        print(to)
      }
      case SILOperator.refToRawPointer(operand, tpe) => {
        print("ref_to_raw_pointer ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.rawPointerToRef(operand, tpe) => {
        print("raw_pointer_to_ref ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.refToUnowned(operand, tpe) => {
        print("ref_to_unowned ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.unownedToRef(operand, tpe) => {
        print("unowned_to_ref ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.refToUnmanaged(operand, tpe) => {
        print("ref_to_unmanaged ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.unmanagedToRef(operand, tpe) => {
        print("unmanaged_to_ref ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.convertFunction(operand, withoutActuallyEscaping, tpe) => {
        print("convert_function ")
        print(operand)
        print(" to ")
        print( "[without_actually_escaping] ", withoutActuallyEscaping)
        print(tpe)
      }
      case SILOperator.convertEscapeToNoescape(notGuaranteed, escaped, operand, tpe) => {
        print("convert_escape_to_noescape ")
        print( "[not_guaranteed] ", notGuaranteed)
        print( "[escaped] ", escaped)
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.classifyBridgeObject(operand) => {
        print("classify_bridge_object ")
        print(operand)
      }
      case SILOperator.valueToBridgeObject(operand) => {
        print("value_to_bridge_object ")
        print(operand)
      }
      case SILOperator.refToBridgeObject(operand1, operand2) => {
        print("ref_to_bridge_object ")
        print(operand1)
        print(", ")
        print(operand2)
      }
      case SILOperator.bridgeObjectToRef(operand, tpe) => {
        print("bridge_object_to_ref ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.bridgeObjectToWord(operand, tpe) => {
        print("bridge_object_to_word ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.thinToThickFunction(operand, tpe) => {
        print("thin_to_thick_function ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.thickToObjcMetatype(operand, tpe) => {
        print("thick_to_objc_metatype ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.objcToThickMetatype(operand, tpe) => {
        print("objc_to_thick_metatype ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.objcMetatypeToObject(operand, tpe) => {
        print("objc_metatype_to_object ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case SILOperator.objcExistentialMetatypeToObject(operand, tpe) => {
        print("objc_existential_metatype_to_object ")
        print(operand)
        print(" to ")
        print(tpe)
      }

        // *** CHECKED CONVERSIONS ***

      case SILOperator.unconditionalCheckedCast(operand, tpe) => {
        print("unconditional_checked_cast ")
        print(operand)
        print(" to ")
        naked(tpe)
      }
      case SILOperator.unconditionalCheckedCastAddr(fromTpe, fromOperand, toType, toOperand) => {
        print("unconditional_checked_cast_addr ")
        naked(fromTpe)
        print(" in ")
        print(fromOperand)
        print(" to ")
        naked(toType)
        print(" in ")
        print(toOperand)
      }
      case SILOperator.selectValue(operand, cases, tpe) => {
        print("select_value ")
        print(operand)
        print(whenEmpty = false, "", cases, "", "", (c: SILSelectValueCase) => print(c))
        print(" : ")
        print(tpe)
      }

        // *** OTHER ***

      case SILOperator.keypath(tpe, elements) => {
        print("keypath ")
        print(tpe)
        print(", (")
        // TODO print elements
        print(")")
      }

        // *** RUNTIME FAILURES ***

      case SILOperator.condFail(operand, message) => {
        print("cond_fail ")
        print(operand)
        if (message.nonEmpty) {
          print(", ")
          literal(message.get)
        }
      }
    }
  }

  def print(terminator: SILTerminator): Unit = {
    terminator match {
      case SILTerminator.unreachable => {
        print("unreachable ")
      }
      case SILTerminator.ret(operand) => {
        print("return ")
        print(operand)
      }
      case SILTerminator.thro(operand) => {
        print("throw ")
        print(operand)
      }
      case SILTerminator.yld(operands, resumeLabel, unwindLabel) => {
        print("yield ")
        if (operands.length > 1) {
          print(whenEmpty = false, "(", operands, ", ", ")", (o: SILOperand) => print(o))
        } else {
          print(operands(0))
        }
        print(", resume ")
        print(resumeLabel)
        print(", unwind ")
        print(unwindLabel)
      }
      case SILTerminator.unwind => {
        print("unwind")
      }
      case SILTerminator.br(label, operands) => {
        print("br ")
        print(label)
        print(whenEmpty = false, "(", operands, ", ", ")", (o: SILOperand) => print(o))
      }
      case SILTerminator.condBr(cond, trueLabel, trueOperands, falseLabel, falseOperands) => {
        print("cond_br ")
        print(cond)
        print(", ")
        print(trueLabel)
        print(whenEmpty = false, "(", trueOperands, ", ", ")", (o: SILOperand) => print(o))
        print(", ")
        print(falseLabel)
        print(whenEmpty = false, "(", falseOperands, ", ", ")", (o: SILOperand) => print(o))
      }
      case SILTerminator.switchValue(operand, cases) => {
        print("switch_value ")
        print(operand)
        print(whenEmpty = false, "", cases, "", "", (c: SILSwitchValueCase) => print(c))
      }
      case SILTerminator.switchEnum(operand, cases) => {
        print("switch_enum ")
        print(operand)
        print(whenEmpty = false, "", cases, "", "", (c: SILSwitchEnumCase) => print(c))
      }
      case SILTerminator.switchEnumAddr(operand, cases) => {
        print("switch_enum_addr ")
        print(operand)
        print(whenEmpty = false, "", cases, "", "", (c: SILSwitchEnumCase) => print(c))
      }
      case SILTerminator.dynamicMethodBr(operand, declRef, namedLabel, notNamedLabel) => {
        print("dynamic_method_br ")
        print(operand)
        print(", ")
        print(declRef)
        print(", ")
        print(namedLabel)
        print(", ")
        print(notNamedLabel)
      }
      case SILTerminator.checkedCastBr(exact, operand, tpe, nkd, succeedLabel, failureLabel) => {
        print("checked_cast_br ")
        print("[exact] ", exact)
        print(operand)
        print(" to ")
        print("$", when = nkd)
        naked(tpe)
        print(", ")
        print(succeedLabel)
        print(", ")
        print(failureLabel)
      }
      case SILTerminator.checkedCastAddrBr(kind, fromTpe, fromOperand, toTpe, toOperand, succeedLabel, failureLabel) => {
        print("checked_cast_addr_br ")
        print(kind)
        print(" ")
        naked(fromTpe)
        print(" in ")
        print(fromOperand)
        print(" to ")
        naked(toTpe)
        print(" in ")
        print(toOperand)
        print(", ")
        print(succeedLabel)
        print(", ")
        print(failureLabel)
      }
      case SILTerminator.tryApply(value, substitutions, arguments, tpe, normalLabel, errorLabel) => {
        // NOTE: This is a multi-line instruction.
        print("try_apply ")
        print(value)
        print(whenEmpty = false, "<", substitutions, ", ", ">", (t: SILType) => naked(t))
        print(whenEmpty = true, "(", arguments, ", ", ")", (a: String) => print(a))
        print(" : ")
        print(tpe)
        print(", normal ")
        print(normalLabel)
        print(", error ")
        print(errorLabel)
      }
    }
  }

  def print(globalVariable: SILGlobalVariable): Unit = {
    print("sil_global ")
    print(globalVariable.linkage)
    print("[serialized] ", when = globalVariable.serialized)
    print("[let] ", when = globalVariable.let)
    print(globalVariable.globalName)
    print(" : ")
    print(globalVariable.tpe)
    if (globalVariable.instructions.nonEmpty) {
      print(" = {\n")
      indent()
      globalVariable.instructions.get.foreach(instruction => {
        print(instruction)
        printNewline()
      })
      unindent()
      print("}")
    }
    printNewline();printNewline()
  }

  def print(vTable: SILVTable): Unit = {
    print("sil_vtable ")
    print("[serialized] ", when = vTable.serialized)
    print(vTable.name)
    print(" {")
    if (vTable.entries.nonEmpty) {
      printNewline()
      indent()
      vTable.entries.foreach(entry => {
        print(entry)
        printNewline()
      })
      unindent()
    }
    print("}")
    printNewline()
  }

  def print(vEntry: SILVEntry): Unit = {
    print(vEntry.declRef)
    print(": ")
    if (vEntry.tpe.nonEmpty) { naked(vEntry.tpe.get); print(" ") }
    print(": ", vEntry.tpe.nonEmpty)
    if (vEntry.linkage.nonEmpty) print(vEntry.linkage.get)
    print(vEntry.functionName)
    print(" ", vEntry.kind != SILVTableEntryKind.normal)
    print(vEntry.kind)
    print(" [nonoverridden]", when = vEntry.nonoverridden)
  }

  def print(vTableEntryKind: SILVTableEntryKind): Unit = {
    vTableEntryKind match {
      case SILVTableEntryKind.overide => print("[override]")
      case SILVTableEntryKind.inherited => print("[inherited]")
      case _ =>
    }
  }

  def print(witnessTable: SILWitnessTable): Unit = {
    print("sil_witness_table ")
    print(witnessTable.linkage)
    if(witnessTable.attribute.nonEmpty) {
      print(witnessTable.attribute.get)
      print(" ")
    }
    print(witnessTable.normalProtocolConformance)
    print(" {\n")
    indent()
    witnessTable.entries.foreach(entry => {
      print(entry)
      printNewline()
    })
    unindent()
    print("}\n")
  }

  def print(witnessEntry: SILWitnessEntry): Unit = {
    witnessEntry match {
      case SILWitnessEntry.baseProtocol(identifier, pc) => {
        print("base_protocol ")
        print(identifier)
        print(": ")
        print(pc)
      }
      case SILWitnessEntry.method(declRef, declType, functionName) => {
        print("method ")
        print(declRef)
        print(": ")
        print(declType)
        print(" : ")
        if (functionName.nonEmpty) print(functionName.get) else print("nil")
      }
      case SILWitnessEntry.associatedType(identifier0, identifier1) => {
        print("associated_type ")
        print(identifier0)
        print(": ")
        print(identifier1)
      }
      case SILWitnessEntry.associatedTypeProtocol(identifier/*0, identifier1, pc*/) => {
        print("associated_type_protocol ")
        print(identifier)
        /*print("(")
        print(identifier0)
        print(": ")
        print(identifier1)
        print(")")
        print(": ")
        print(pc)*/
      }
      case SILWitnessEntry.conditionalConformance(identifier) => {
        print("conditional_conformance ")
        print(identifier)
      }
    }
  }

  def print(property: SILProperty): Unit = {
    print("sil_property ")
    print("[serialized] ", when = property.serialized)
    print(property.decl)
    print(property.component)
  }

  def print(normalProtocolConformance: SILNormalProtocolConformance): Unit = {
    naked(normalProtocolConformance.tpe)
    print(": ")
    print(normalProtocolConformance.protocol)
    print(" module ")
    print(normalProtocolConformance.module)
  }

  def print(protocolConformance: SILProtocolConformance): Unit = {
    protocolConformance match {
      case SILProtocolConformance.normal(pc) => print(pc)
      case SILProtocolConformance.inherit(tpe, pc) => {
        naked(tpe)
        print(": ")
        print("inherit ")
        print("(")
        print(pc)
        print(")")
      }
      case SILProtocolConformance.specialize(substitutions, pc) => {
        print("specialize " )
        print(whenEmpty = false, "<", substitutions, ", ", ">", (t: SILType) => naked(t))
        print("( ")
        print(pc)
        print(" )")
      }
      case SILProtocolConformance.dependent => print("dependent")
    }
  }

  def print(access: SILAccess): Unit = {
    access match {
      case SILAccess.deinit => {
        print("deinit")
      }
      case SILAccess.init => {
        print("init")
      }
      case SILAccess.modify => {
        print("modify")
      }
      case SILAccess.read => {
        print("read")
      }
    }
  }

  def print(access: SILAllowedAccess): Unit = {
    access match {
      case SILAllowedAccess.immutable => print("immutable_access")
      case SILAllowedAccess.mutable => print("mutable_access")
    }
  }

  def print(kind: SILCastConsumptionKind): Unit = {
    kind match {
      case SILCastConsumptionKind.takeAlways => print("take_always")
      case SILCastConsumptionKind.takeOnSuccess => print("take_on_success")
      case SILCastConsumptionKind.copyOnSuccess => print("copy_on_success")
    }
  }

  def print(argument: SILArgument): Unit = {
    print(argument.valueName)
    print(" : ")
    print(argument.tpe)
  }

  def print(element: SILKeypathElement): Unit = {
    element match {
      case SILKeypathElement.objc(value) =>
      case SILKeypathElement.root(tpe) =>
      case SILKeypathElement.gettableProperty(tpe) =>
      case SILKeypathElement.storedProperty(decl, tpe) =>
      case SILKeypathElement.settableProperty(tpe) =>
      case SILKeypathElement.id(name, decl, tpe) =>
      case SILKeypathElement.getter(name, tpe) =>
      case SILKeypathElement.setter(name, tpe) =>
      case SILKeypathElement.optionalForce(tpe) =>
      case SILKeypathElement.tupleElement(decl, tpe) =>
      case SILKeypathElement.external(decl) =>
      case SILKeypathElement.optionalChain(tpe) =>
    }
  }

  def print(cse: SILSwitchEnumCase): Unit = {
    print(", ")
    cse match {
      case SILSwitchEnumCase.cs(declRef, result) => {
        print("case ")
        print(declRef)
        print(": ")
        print(result)
      }
      case SILSwitchEnumCase.default(result) => {
        print("default ")
        print(result)
      }
    }
  }

  def print(cse: SILSwitchValueCase): Unit = {
    print(", ")
    cse match {
      case SILSwitchValueCase.cs(value, label) => {
        print("case ")
        print(value)
        print(": ")
        print(label)
      }
      case SILSwitchValueCase.default(label) => {
        print("default ")
        print(label)
      }
    }
  }

  def print(cse: SILSelectValueCase): Unit = {
    print(", ")
    cse match {
      case SILSelectValueCase.cs(value, select) => {
        print("case ")
        print(value)
        print(" : ")
        print(select)
      }
      case SILSelectValueCase.default(select) => {
        print("default ")
        print(select)
      }
    }
  }

  def print(convention: SILConvention): Unit = {
    print("(")
    convention match {
      case SILConvention.c => print("c")
      case SILConvention.method => print("method")
      case SILConvention.thin => print("thin")
      case SILConvention.block => print("block")
      case SILConvention.witnessMethod(tpe) => {
        print("witness_method: ")
        naked(tpe)
      }
      case SILConvention.objc => print("objc_method")
    }
    print(")")
  }

  def print(attribute: SILDebugAttribute): Unit = {
    attribute match {
      case SILDebugAttribute.argno(index) => {
        print("argno ")
        literal(index)
      }
      case SILDebugAttribute.name(name) => {
        print("name ")
        literal(name)
      }
      case SILDebugAttribute.let => {
        print("let")
      }
      case SILDebugAttribute.variable => {
        print("var")
      }
    }
  }

  def print(declKind: SILDeclKind): Unit = {
    declKind match {
      case SILDeclKind.func =>
      case SILDeclKind.allocator => print("!allocator")
      case SILDeclKind.initializer => print("!initializer")
      case SILDeclKind.enumElement => print("!enumelt")
      case SILDeclKind.destroyer => print("!destroyer")
      case SILDeclKind.deallocator => print("!deallocator")
      case SILDeclKind.globalAccessor => print("!globalaccessor")
      case SILDeclKind.defaultArgGenerator(index) => print("!defaultarg" + "." + index)
      case SILDeclKind.storedPropertyInitalizer => print("!propertyinit")
      case SILDeclKind.ivarInitializer => print("!ivarinitializer")
      case SILDeclKind.ivarDestroyer => print("!ivardestroyer")
      case SILDeclKind.propertyWrappingBackingInitializer => print("!backinginit")
    }
  }

  def print(accessorKind: SILAccessorKind): Unit = {
    accessorKind match {
      case SILAccessorKind.get => print("!getter")
      case SILAccessorKind.set => print("!setter")
      case SILAccessorKind.willSet => print("!willSet")
      case SILAccessorKind.didSet => print("!didSet")
      case SILAccessorKind.address => print("!addressor")
      case SILAccessorKind.mutableAddress => print("!mutableAddressor")
      case SILAccessorKind.read => print("!read")
      case SILAccessorKind.modify => print("!modify")
    }
  }

  def print(autoDiff: SILAutoDiff): Unit = {
    autoDiff match {
      case SILAutoDiff.jvp(_) => print("jvp.")
      case SILAutoDiff.vjp(_) => print("vjp.")
    }
    print(autoDiff.paramIndices)
  }

  def print(declSubRef: SILDeclSubRef): Unit = {
    declSubRef match {
      case SILDeclSubRef.part(accessorKind, declKind, level, foreign, autoDiff) => {
        if (accessorKind.nonEmpty) print(accessorKind.get)
        print(declKind)
        if (level.nonEmpty) {
          print(".")
          literal(level.get)
        }
        if (foreign) {
          print(".")
          print("foreign")
        }
        if (autoDiff.nonEmpty) {
          print(".")
          print(autoDiff.get)
        }
      }
      case SILDeclSubRef.lang => print("!foreign")
      case SILDeclSubRef.autoDiff(autoDiff) => { print("!"); print(autoDiff) }
      case SILDeclSubRef.level(level, foreign) => { print("!"); literal(level); print(".foreign", when = foreign) }
    }
  }

  def clearPrint(declRef: SILDeclRef): String = {
    clear()
    print(declRef)
    this.toString
  }

  def print(declRef: SILDeclRef): Unit = {
    print("#")
    print(declRef.name.mkString("."))
    if (declRef.subRef.nonEmpty) print(declRef.subRef.get)
  }

  def print(encoding: SILEncoding): Unit = {
    encoding match {
      case SILEncoding.objcSelector => print("objc_selector")
      case SILEncoding.utf8 => print("utf8")
      case SILEncoding.utf16 => print("utf16")
    }
  }

  def print(enforcement: SILEnforcement): Unit = {
    enforcement match {
      case SILEnforcement.dynamic => print("dynamic")
      case SILEnforcement.static => print("static")
      case SILEnforcement.unknown => print("unknown")
      case SILEnforcement.unsafe => print("unsafe")
    }
  }

  def print(attribute: SILFunctionAttribute): Unit = {
    attribute match {
      case SILFunctionAttribute.canonical => print("[cannonical]")
      case SILFunctionAttribute.differentiable(spec) => {
        print("[differentiable ")
        print(spec)
        print("]")
      }
      case SILFunctionAttribute.dynamicallyReplacable => print("[dynamically_replacable]")
      case SILFunctionAttribute.alwaysInline => print("[always_inline]")
      case SILFunctionAttribute.noInline => print("[noinline]")
      case SILFunctionAttribute.ossa => print("[ossa]")
      case SILFunctionAttribute.serialized => print("[serialized]")
      case SILFunctionAttribute.serializable => print("[serializable]")
      case SILFunctionAttribute.transparent => print("[transparent]")
      case thunk: SILFunctionAttribute.Thunk => {
        thunk match {
          case Thunk.thunk => print("[thunk]")
          case Thunk.signatureOptimized => print("[signature_optimized_thunk]")
          case Thunk.reabstraction => print("[reabstraction_thunk]")
        }
      }
      case SILFunctionAttribute.dynamicReplacement(func) => {
        print("[dynamic_replacement_for ")
        print(func)
        print("]")
      }
      case SILFunctionAttribute.objcReplacement(func) => {
        print("[objc_replacement_for ")
        print(func)
        print("]")
      }
      case SILFunctionAttribute.exactSelfClass => print("[exact_self_class]")
      case SILFunctionAttribute.withoutActuallyEscaping => print("[without_actually_escaping]")
      case purpose: SILFunctionAttribute.FunctionPurpose => {
        purpose match {
          case FunctionPurpose.globalInit => print("[global_init]")
          case FunctionPurpose.lazyGetter => print("[lazy_getter]")
        }
      }
      case SILFunctionAttribute.weakImported => print("[weak_imported]")
      case SILFunctionAttribute.available(version) => {
        print("[available ")
        print(whenEmpty = false, "", version, ".", "", (x: String) => literal(x, naked = true))
        print("]")
      }
      case inlining: SILFunctionAttribute.FunctionInlining => {
        inlining match {
          case FunctionInlining.never => print("[never]")
          case FunctionInlining.always => print("[always]")
        }
      }
      case optimization: SILFunctionAttribute.FunctionOptimization => {
        optimization match {
          case FunctionOptimization.Onone => print("[Onone]")
          case FunctionOptimization.Ospeed => print("[Ospeed]")
          case FunctionOptimization.Osize => print("[Osize]")
        }
      }
      case effects: SILFunctionAttribute.FunctionEffects => {
        effects match {
          case FunctionEffects.readonly => print("[readonly]")
          case FunctionEffects.readnone => print("[readnone]")
          case FunctionEffects.readwrite => print("[readwrite]")
          case FunctionEffects.releasenone => print("[releasenone]")
        }
      }
      case SILFunctionAttribute.semantics(value) => {
        print("[_semantics ")
        literal(value)
        print("]")
      }
      case SILFunctionAttribute.specialize(exported, kind, reqs) => {
        print("[_specialize ")
        if (exported.nonEmpty) {
          print("exported: ")
          print(exported.get)
          print(", ")
        }
        if (kind.nonEmpty) {
          print("kind: ")
          kind.get match {
            case Kind.full => print("full")
            case Kind.partial =>print("partial")
          }
          print(", ")
        }
        print(whenEmpty = true, "where ", reqs, ",", "]", (t: SILTypeRequirement) => print(t))
      }
      case SILFunctionAttribute.clang(value) => {
        print("[clang ")
        print(value)
        print("]")
      }
    }
  }

  def print(linkage: SILLinkage): Unit = {
    linkage match {
      case SILLinkage.hidden => print("hidden ")
      case SILLinkage.hiddenExternal => print("hidden_external ")
      case SILLinkage.priv => print("private ")
      case SILLinkage.privateExternal => print("private_external ")
      case SILLinkage.public => print("")
      case SILLinkage.publicExternal => print("public_external ")
      case SILLinkage.publicNonABI => print("non_abi ")
      case SILLinkage.shared => print("shared ")
      case SILLinkage.sharedExternal => print("shared_external ")
    }
  }

  def print(operand: SILOperand): Unit = {
    print(operand.value)
    print(" : ")
    print(operand.tpe)
  }

  def print(result: SILResult): Unit = {
    if (result.valueNames.length == 1) {
      print(result.valueNames(0))
    } else {
      print(whenEmpty = true, "(", result.valueNames, ", ", ")", (v: String) => print(v))
    }
  }

  def print(sourceInfo: SILSourceInfo): Unit = {
    if (!options.printLocation) {
      return
    }
    // The SIL docs say that scope refs precede locations, but this is
    // not true once you look at the compiler outputs or its source code.
    print(", ", sourceInfo.loc, (l: SILLoc) => print(l))
    print(", ")
    if (sourceInfo.scopeRef.nonEmpty) print(sourceInfo.scopeRef.get)
  }

  def print(scope: SILScope): Unit = {
    print("sil_scope ")
    literal(scope.num)
    print(" { ")
    if (scope.loc.nonEmpty) print(scope.loc.get)
    print(" parent ")
    print(scope.parent)
    if (scope.inlinedAt.nonEmpty) {
      print(" inlined_at ")
      print(scope.inlinedAt.get)
    }
    print(" }")
    printNewline()
  }

  def print(loc: SILLoc): Unit = {
    print("loc ")
    literal(loc.path)
    print(":")
    literal(loc.line)
    print(":")
    literal(loc.column)
  }

  def print(parent: SILScopeParent): Unit = {
    parent match {
      case SILScopeParent.func(name, tpe) => {
        print(name)
        print(" : ")
        print(tpe)
      }
      case SILScopeParent.ref(ref) => literal(ref)
    }
  }

  def print(ref: SILScopeRef): Unit = {
    print("scope ")
    literal(ref.num)
  }

  def print(elements: SILTupleElements): Unit = {
    elements match {
      case SILTupleElements.labeled(tpe, values) => {
        print(tpe)
        print(whenEmpty = true, " (", values, ", ", ")", (v: String) => print(v))
      }
      case SILTupleElements.unlabeled(operands) => {
        print(whenEmpty = true, "(", operands, ", ", ")", (o: SILOperand) => print(o))
      }
    }
  }

  def print(name: SILMangledName): Unit = {
    print("@")
    print(name.mangled)
  }

  def clearPrint(tpe: SILType): String = {
    clear()
    print(tpe)
    this.toString
  }

  def clearNakedPrint(tpe: SILType): String = {
    clear()
    naked(tpe)
    this.toString
  }


  def print(tpe: SILType): Unit = {
    tpe match {
      case SILType.withOwnership(attribute, subtype) => {
        print(attribute)
        print(" ")
        print(subtype)
      }
      case SILType.genericType(_, _, _) => {
        naked(tpe) // No "$"
      }
      case _ => {
        print("$")
        naked(tpe)
      }
    }
  }

  def naked(nakedTpe: SILType): Unit = {
    nakedTpe match {
      case SILType.addressType(tpe) => {
        print("*")
        naked(tpe)
      }
      case SILType.attributedType(attrs, tpe) => {
        print(whenEmpty = true, "", attrs, " ", " ", (t: SILTypeAttribute) => print(t))
        naked(tpe)
      }
      case SILType.coroutineTokenType => {
        print("!CoroutineTokenType!")
      }
      case SILType.functionType(params, optional, throws, result) => {
        print(whenEmpty = true, "(", params, ", ", ")", (t: SILType) => naked(t))
        print("?", when = optional)
        print(" throws", when = throws)
        print(" -> ")
        naked(result)
      }
      case SILType.genericType(params, reqs, tpe) => {
        print(whenEmpty = true, "<", params, ", ", "", (p: String) => print(p))
        print(whenEmpty = false, " where ", reqs, ", ", "", (r: SILTypeRequirement) => print(r))
        print(">")
        // This is a weird corner case of -emit-sil, so we have to go the extra mile.
        if (tpe.isInstanceOf[SILType.genericType]) {
          naked(tpe)
        } else {
          print(" ")
          naked(tpe)
        }
      }
      case SILType.namedType(name) => {
        print(name)
      }
      case SILType.selectType(tpe, name) => {
        naked(tpe)
        print(".")
        print(name)
      }
      case SILType.namedArgType(name, tpe, squareBrackets) => {
        print(name)
        print(" ", when = squareBrackets)
        print(": ")
        naked(tpe)
      }
      case SILType.selfType => {
        print("Self")
      }
      case SILType.selfTypeOptional => {
        print("Self?")
      }
      case SILType.specializedType(tpe, args, optional) => {
        naked(tpe)
        if (tpe.isInstanceOf[SILType.varType]) {
          print(" ")
        }
        print(whenEmpty = true, "<", args, ", ", ">", (t: SILType) => naked(t))
        print("?" * optional)
      }
      case SILType.arrayType(arguments, nakedStyle, optional) => {
        if (nakedStyle) {
          print(whenEmpty = true, "[", arguments, ", ", "]", (t: SILType) => naked(t))
          print("?" * optional)
        } else {
          print("Array")
          print(whenEmpty = true, "<", arguments, ", ", ">", (t: SILType) => naked(t))
        }
      }
      case SILType.tupleType(params, optional, dots) => {
        print(whenEmpty = true, "(", params, ", ", ")", (t: SILType) => naked(t))
        print("?", when = optional)
        print("...", when = dots)
      }
      case SILType.withOwnership(_, _) => {
        throw new Error("<printing>", "Types with ownership should be printed before naked type print!", null)
      }
      case SILType.varType(tpe) => {
        print("{ var ")
        naked(tpe)
        print(" }")
      }
      case SILType.forType(tpe, fr) => {
        naked(tpe)
        print(" for ")
        print(whenEmpty = true, "<", fr, ", ", ">", (t: SILType) => naked(t))
      }
      case SILType.andType(tpe1, tpe2) => {
        naked(tpe1)
        print(" & ")
        naked(tpe2)
      }
      case SILType.dotType(tpes) => {
        print(whenEmpty = true, "(", tpes, ", ", ").Type", (t: SILType) => naked(t))
      }
    }
  }

  def print(attribute: SILTypeAttribute): Unit = {
    attribute match {
      case SILTypeAttribute.pseudoGeneric => print("@pseudogeneric")
      case SILTypeAttribute.calleeGuaranteed => print("@callee_guaranteed")
      case SILTypeAttribute.substituted => print("@substituted")
      case SILTypeAttribute.convention(convention) => {
        print("@convention")
        print(convention)
      }
      case SILTypeAttribute.guaranteed => print("@guaranteed")
      case SILTypeAttribute.inGuaranteed => print("@in_guaranteed")
      case SILTypeAttribute.in => print("@in")
      case SILTypeAttribute.inout => print("@inout")
      case SILTypeAttribute.inoutAliasable => print("@inout_aliasable")
      case SILTypeAttribute.noescape => print("@noescape")
      case SILTypeAttribute.out => print("@out")
      case SILTypeAttribute.unowned => print("@unowned")
      case SILTypeAttribute.unownedInnerPointer => print("@unowned_inner_pointer")
      case SILTypeAttribute.owned => print("@owned")
      case SILTypeAttribute.thick => print("@thick")
      case SILTypeAttribute.thin => print("@thin")
      case SILTypeAttribute.yieldOnce => print("@yield_once")
      case SILTypeAttribute.yields => print("@yields")
      case SILTypeAttribute.error => print("@error")
      case SILTypeAttribute.objcMetatype => print("@objc_metatype")
      case SILTypeAttribute.silWeak => print("@sil_weak")
      case SILTypeAttribute.silUnowned => print("@sil_unowned")
      case SILTypeAttribute.silUnmanaged => print("@sil_unmanaged")
      case SILTypeAttribute.autoreleased => print("@autoreleased")
      case SILTypeAttribute.blockStorage => print("@block_storage")
      case SILTypeAttribute.escaping => print("@escaping")
      case SILTypeAttribute.autoclosure => print("@autoclosure")
      case SILTypeAttribute.opaqueReturnTypeOf(value, num) => {
        print("@_opaqueReturnTypeOf(\"")
        print(value)
        print("\", ")
        print(num)
        print(")")
      }
      case SILTypeAttribute.opened(value) => {
        print("@opened")
        print("(")
        literal(value)
        print(")")
      }
      case SILTypeAttribute.dynamicSelf => print("@dynamic_self")
      case SILTypeAttribute.typeSpecifierInOut => print("inout")
      case SILTypeAttribute.typeSpecifierOwned => print("__owned")
      case SILTypeAttribute.typeSpecifierUnowned => print("__unowned")
    }
  }

  def print(allocAttribute: SILAllocAttribute): Unit = {
    allocAttribute match {
      case SILAllocAttribute.objc => print("[objc]")
      case SILAllocAttribute.stack => print("[stack]")
    }
  }

  def print(muKind: SILMUKind): Unit = {
    muKind match {
      case SILMUKind.varr => print("var")
      case SILMUKind.rootSelf => print("rootself")
      case SILMUKind.crossModuleRootSelf => print("crossmodulerootself")
      case SILMUKind.derivedSelf => print("derivedself")
      case SILMUKind.derivedSelfOnly => print("derivedselfonly")
      case SILMUKind.delegatingSelf => print("delegatingself")
      case SILMUKind.delegatingSelfAllocated => print("delegatingselfallocated")
    }
  }

  def print(requirement: SILTypeRequirement): Unit = {
    requirement match {
      case SILTypeRequirement.conformance(lhs, rhs) => {
        naked(lhs)
        print(" : ")
        naked(rhs)
      }
      case SILTypeRequirement.equality(lhs, rhs) => {
        naked(lhs)
        print(" == ")
        naked(rhs)
      }
    }
  }

  def print(ownership: SILLoadOwnership): Unit = {
    ownership match {
      case SILLoadOwnership.copy => print("[copy]")
      case SILLoadOwnership.take => print("[take]")
      case SILLoadOwnership.trivial => print("[trivial]")
    }
  }

  def print(storeOwnership: SILStoreOwnership): Unit = {
    storeOwnership match {
      case SILStoreOwnership.init => print("[init]")
      case SILStoreOwnership.trivial => print("[trivial]")
      case SILStoreOwnership.assign => print("[assign]")
    }
  }


}