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

import ca.ualberta.maple.swan.utils.ExceptionReporter
import ca.ualberta.maple.swan.parser.Error

class SILPrinter extends Printer {

  def print(module: SILModule): Unit = {
    module.functions.foreach(f => {
      print(f)
      print("\n\n")
    })
  }

  def print(function: SILFunction): Unit = {
    print("sil ")
    print(function.linkage)
    print(whenEmpty = false, "", function.attributes, " ", " ", (attribute: SILFunctionAttribute) => print(attribute))
    print("@")
    print(function.name)
    print(" : ")
    print(function.tpe)
    print(whenEmpty = false, " {\n", function.blocks, "\n", "}", (block: SILBlock) => print(block))
  }

  def print(block: SILBlock): Unit = {
    print(block.identifier)
    print(whenEmpty = false, "(", block.arguments, ", ", ")", (arg: SILArgument) => print(arg))
    print(":")
    indent()
    block.operatorDefs.foreach({
      print("\n")
      print
    })
    print("\n")
    print(block.terminatorDef)
    print("\n")
    unindent()
  }

  def print(operatorDef: SILOperatorDef): Unit = {
    print(operatorDef.result, " = ", (r: SILResult) => {
      print(r)
    })
    print(operatorDef.operator)
    print(operatorDef.sourceInfo, (si: SILSourceInfo) => print(si))
    System.out.println()
  }

  def print(terminatorDef: SILTerminatorDef): Unit = {
    print(terminatorDef.terminator)
    print(terminatorDef.sourceInfo, (si: SILSourceInfo) => print(si))
    System.out.println()
  }

  def print(op: SILOperator): Unit = {
    op match {
      case SILOperator.allocStack(tpe, attributes) => {
        print("alloc_stack ")
        print(tpe)
        print(whenEmpty = false, ", ", attributes, ", ", "", (a: SILDebugAttribute) => print(a))
      }
      case SILOperator.allocBox(tpe, attributes) => {
        print("alloc_box ")
        print(tpe)
        print(whenEmpty = false, ", ", attributes, ", ", "", (a: SILDebugAttribute) => print(a))
      }
      case SILOperator.allocGlobal(name) => {
        print("alloc_global ")
        print("@")
        print(name)
      }
      case SILOperator.apply(nothrow, value, substitutions, arguments, tpe) => {
        print("apply ")
        print( "[nothrow] ", nothrow)
        print(value)
        print(whenEmpty = false, "<", substitutions, ", ", ">", (t: SILType) => naked(t))
        print(whenEmpty = true, "(", arguments, ", ", ")", (a: String) => print(a))
        print(" : ")
        print(tpe)
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
      case SILOperator.beginApply(nothrow, value, substitutions, arguments, tpe) => {
        print("begin_apply ")
        print( "[nothrow] ", nothrow)
        print(value)
        print(whenEmpty = false, "<", substitutions, ", ", ">", (t: SILType) => naked(t))
        print(whenEmpty = true, "(", arguments, ", ", ")", (a: String) => print(a))
        print(" : ")
        print(tpe)
      }
      case SILOperator.beginBorrow(operand) => {
        print("begin_borrow ")
        print(operand)
      }
      case SILOperator.builtin(name, operands, tpe) => {
        print("builtin ")
        literal(name)
        print(whenEmpty = true, "(", operands, ", ", ")", (o: SILOperand) => print(o))
        print(" : ")
        print(tpe)
      }
      case SILOperator.condFail(operand, message) => {
        print("cond_fail ")
        print(operand)
        if (message.nonEmpty) {
          print(", ")
          literal(message.get)
        }
      }
      case SILOperator.convertEscapeToNoescape(notGuaranteed, escaped, operand, tpe) => {
        print("convert_escape_to_noescape ")
        print( "[not_guaranteed] ", notGuaranteed)
        print( "[escaped] ", escaped)
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
      case SILOperator.copyAddr(take, value, initialization, operand) => {
        print("copy_addr ")
        print( "[take] ", take)
        print(value)
        print(" to ")
        print( "[initialization] ", initialization)
        print(operand)
      }
      case SILOperator.copyValue(operand) => {
        print("copy_value ")
        print(operand)
      }
      case SILOperator.deallocStack(operand) => {
        print("dealloc_stack ")
        print(operand)
      }
      case SILOperator.deallocBox(operand) => {
        print("dealloc_box ")
        // NOTE: Not sure if this is correct
        // dealloc_box %0 : $@box T
        print(operand)
      }
      case SILOperator.projectBox(operand) => {
        print("project_box ")
        print(operand)
      }
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
      case SILOperator.destroyAddr(operand) => {
        print("destroy_addr ")
        print(operand)
      }
      case SILOperator.destroyValue(operand) => {
        print("destroy_value ")
        print(operand)
      }
      case SILOperator.destructureTuple(operand) => {
        print("destructure_tuple ")
        print(operand)
      }
      case SILOperator.endAccess(abort, operand) => {
        print("end_access ")
        print( "[abort] ", abort)
        print(operand)
      }
      case SILOperator.endApply(value) => {
        print("end_apply ")
        print(value)
      }
      case SILOperator.abortApply(value) => {
        print("abort_apply ")
        print(value)
      }
      case SILOperator.endBorrow(operand) => {
        print("end_borrow ")
        print(operand)
      }
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
      case SILOperator.floatLiteral(tpe, value) => {
        print("float_literal ")
        print(tpe)
        print(", 0x")
        print(value)
      }
      case SILOperator.functionRef(name, tpe) => {
        print("function_ref ")
        print("@")
        print(name)
        print(" : ")
        print(tpe)
      }
      case SILOperator.globalAddr(name, tpe) => {
        print("global_addr ")
        print("@")
        print(name)
        print(" : ")
        print(tpe)
      }
      case SILOperator.indexAddr(addr, index) => {
        print("index_addr ")
        print(addr)
        print(", ")
        print(index)
      }
      case SILOperator.integerLiteral(tpe, value) => {
        print("integer_literal ")
        print(tpe)
        print(", ")
        literal(value)
      }
      case SILOperator.load(kind: Option[SILLoadOwnership], operand) => {
        print("load ")
        if (kind.nonEmpty) {
          print(kind.get)
          print(" ")
        }
        print(operand)
      }
      case SILOperator.loadWeak(take: Boolean, operand) => {
        print("load_weak ")
        print("[take]", take)
        print(operand)
      }
      case SILOperator.storeWeak(value, initialization, operand) => {
        print("store_weak ")
        print(value)
        print(" to ")
        print("[initialization] ", initialization)
        print(operand)
      }
      case SILOperator.markDependence(operand, on) => {
        print("mark_dependence ")
        print(operand)
        print(" on ")
        print(on)
      }
      case SILOperator.metatype(tpe) => {
        print("metatype ")
        print(tpe)
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
      case SILOperator.pointerToAddress(operand, strict, tpe) => {
        print("pointer_to_address ")
        print(operand)
        print(" to ")
        print( "[strict] ", strict)
        print(tpe)
      }
      case SILOperator.releaseValue(operand) => {
        print("release_value ")
        print(operand)
      }
      case SILOperator.retainValue(operand) => {
        print("retain_value ")
        print(operand)
      }
      case SILOperator.selectEnum(operand, cases, tpe) => {
        print("select_enum ")
        print(operand)
        print(whenEmpty = false, "", cases, "", "", (c: SILCase) => print(c))
        print(" : ")
        print(tpe)
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
      case SILOperator.stringLiteral(encoding, value) => {
        print("string_literal ")
        print(encoding)
        print(" ")
        literal(value)
      }
      case SILOperator.strongRelease(operand) => {
        print("strong_release ")
        print(operand)
      }
      case SILOperator.strongRetain(operand) => {
        print("strong_retain ")
        print(operand)
      }
      case SILOperator.struct(tpe, operands) => {
        print("struct ")
        print(tpe)
        print(whenEmpty = true, " (", operands, ", ", ")", (o: SILOperand) => print(o))
      }
      case SILOperator.structElementAddr(operand, declRef) => {
        print("struct_element_addr ")
        print(operand)
        print(", ")
        print(declRef)
      }
      case SILOperator.structExtract(operand, declRef) => {
        print("struct_extract ")
        print(operand)
        print(", ")
        print(declRef)
      }
      case SILOperator.thinToThickFunction(operand, tpe) => {
        print("thin_to_thick_function ")
        print(operand)
        print(" to ")
        print(tpe)
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
      case SILOperator.unknown(name) => {
        print(name)
        print(" <?>")
      }
      case SILOperator.witnessMethod(archeType, declRef, declType, tpe) => {
        print("witness_method ")
        print(archeType)
        print(", ")
        print(declRef)
        print(" : ")
        naked(declType)
        print(" : ")
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
    }
  }

  def print(terminator: SILTerminator): Unit = {
    terminator match {
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
      case SILTerminator.ret(operand) => {
        print("return ")
        print(operand)
      }
      case SILTerminator.thro(operand) => {
        print("throw ")
        print(operand)
      }
      case SILTerminator.unwind => {
        print("unwind ")
      }
      case SILTerminator.switchEnum(operand, cases) => {
        print("switch_enum ")
        print(operand)
        print(whenEmpty = false, "", cases, "", "", (c: SILCase) => print(c))
      }
      case SILTerminator.switchEnumAddr(operand, cases) => {
        print("switch_enum_addr ")
        print(operand)
        print(whenEmpty = false, "", cases, "", "", (c: SILCase) => print(c))
      }
      case SILTerminator.unknown(name) => {
        print(name)
        print(" <?>")
      }
      case SILTerminator.unreachable => {
        print("unreachable ")
      }
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

  def print(argument: SILArgument): Unit = {
    print(argument.valueName)
    print(" : ")
    print(argument.tpe)
  }

  def print(cse: SILCase): Unit = {
    print(", ")
    cse match {
      case SILCase.cs(declRef, result) => {
        print("case ")
        print(declRef)
        print(": ")
        print(result)
      }
      case SILCase.default(result) => {
        print("default ")
        print(result)
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
      case SILDeclKind.allocator => print("allocator")
      case SILDeclKind.deallocator => print("deallocator")
      case SILDeclKind.destroyer => print("destroyer")
      case SILDeclKind.enumElement => print("enumelt")
      case SILDeclKind.getter => print("getter")
      case SILDeclKind.globalAccessor => print("globalaccessor")
      case SILDeclKind.initializer => print("initializer")
      case SILDeclKind.ivarDestroyer => print("ivardestroyer")
      case SILDeclKind.ivarInitializer => print("ivarinitializer")
      case SILDeclKind.setter => print("setter")
    }
  }

  def print(declRef: SILDeclRef): Unit = {
    print("#")
    print(declRef.name.mkString("."))
    if (declRef.kind.nonEmpty) {
      print("!")
      print(declRef.kind.get)
    }
    if (declRef.level.nonEmpty) {
      print(if (declRef.kind.isEmpty == true) "!" else ".")
      literal(declRef.level.get)
    }
  }

  def print(encoding: SILEncoding): Unit = {
    encoding match {
      case SILEncoding.objcSelector => print("objcSelector")
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
      case SILFunctionAttribute.alwaysInline => print("[always_inline]")
      case SILFunctionAttribute.differentiable(spec) => {
        print("[differentiable ")
        print(spec)
        print("]")
      }
      case SILFunctionAttribute.dynamicallyReplacable => print("[dynamically_replacable]")
      case SILFunctionAttribute.noInline => print("[noinline]")
      case SILFunctionAttribute.noncanonical(SILNoncanonicalFunctionAttribute.ownershipSSA) => print("[ossa]")
      case SILFunctionAttribute.readonly => print("[readonly]")
      case SILFunctionAttribute.semantics(value) => {
        print("[_semantics ")
        literal(value)
        print("]")
      }
      case SILFunctionAttribute.serialized => print("[serialized]")
      case SILFunctionAttribute.thunk => print("[thunk]")
      case SILFunctionAttribute.transparent => print("[transparent]")
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

  def print(loc: SILLoc): Unit = {
    print("loc ")
    literal(loc.path)
    print(":")
    literal(loc.line)
    print(":")
    literal(loc.column)
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
    // The SIL docs say that scope refs precede locations, but this is
    // not true once you look at the compiler outputs or its source code.
    print(", ", sourceInfo.loc, (l: SILLoc) => print(l))
    print(", scope ", sourceInfo.scopeRef, (ref: Int) => literal(ref))
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

  def print(tpe: SILType): Unit = {
    tpe match {
      case SILType.withOwnership(attribute, subtype) => {
        print(attribute)
        print(" ")
        print(subtype)
      }
      case default => {
        print("$")
        naked(tpe)
      }
    }
  }

  def naked(tpe: SILType): Unit = {
    tpe match {
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
      case SILType.functionType(params, result) => {
        print(whenEmpty = true, "(", params, ", ", ")", (t: SILType) => naked(t))
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
      case SILType.selfType => {
        print("Self")
      }
      case SILType.specializedType(tpe, args) => {
        naked(tpe)
        print(whenEmpty = true, "<", args, ", ", ">", (t: SILType) => naked(t))
      }
      case SILType.tupleType(params) => {
        print(whenEmpty = true, "(", params, ", ", ")", (t: SILType) => naked(t))
      }
      case SILType.withOwnership(_, _) => {
        // Note: "fatalError" in Swift, but I think exception is okay here.
        ExceptionReporter.report(new Exception("Types with ownership should be printed before naked type print!"))
      }
    }
  }

  def print(attribute: SILTypeAttribute): Unit = {
    attribute match {
      case SILTypeAttribute.calleeGuaranteed => print("@callee_guaranteed")
      case SILTypeAttribute.convention(convention) => {
        print("@convention")
        print(convention)
      }
      case SILTypeAttribute.guaranteed => print("@guaranteed")
      case SILTypeAttribute.inGuaranteed => print("@in_guaranteed")
      case SILTypeAttribute.in => print("@in")
      case SILTypeAttribute.inout => print("@inout")
      case SILTypeAttribute.noescape => print("@noescape")
      case SILTypeAttribute.out => print("@out")
      case SILTypeAttribute.owned => print("@owned")
      case SILTypeAttribute.thick => print("@thick")
      case SILTypeAttribute.thin => print("@thin")
      case SILTypeAttribute.yieldOnce => print("@yield_once")
      case SILTypeAttribute.yields => print("@yields")
      case SILTypeAttribute.error => print("@error")
      case SILTypeAttribute.objcMetatype => print("@objc_metatype")
      case SILTypeAttribute.silWeak => print("@sil_weak")
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
    }
  }


}