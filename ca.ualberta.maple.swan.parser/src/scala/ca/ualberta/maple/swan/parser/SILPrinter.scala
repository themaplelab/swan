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

  def print(module: Module): Unit = {
    module.functions.foreach(f => {
      print(f)
      print("\n\n")
    })
  }

  def print(function: Function): Unit = {
    print("sil ")
    print(function.linkage)
    print(whenEmpty = false, "", function.attributes, " ", " ", (attribute: FunctionAttribute) => print(attribute))
    print("@")
    print(function.name)
    print(" : ")
    print(function.tpe)
    print(whenEmpty = false, " {\n", function.blocks, "\n", "}", (block: Block) => print(block))
  }

  def print(block: Block): Unit = {
    print(block.identifier)
    print(whenEmpty = false, "(", block.arguments, ", ", ")", (arg: Argument) => print(arg))
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

  def print(operatorDef: OperatorDef): Unit = {
    print(operatorDef.result, " = ", (r: Result) => {
      print(r)
    })
    print(operatorDef.operator)
    print(operatorDef.sourceInfo, (si: SourceInfo) => print(si))
    System.out.println()
  }

  def print(terminatorDef: TerminatorDef): Unit = {
    print(terminatorDef.terminator)
    print(terminatorDef.sourceInfo, (si: SourceInfo) => print(si))
    System.out.println()
  }

  def print(op: Operator): Unit = {
    op match {
      case Operator.allocStack(tpe, attributes) => {
        print("alloc_stack ")
        print(tpe)
        print(whenEmpty = false, ", ", attributes, ", ", "", (a: DebugAttribute) => print(a))
      }
      case Operator.allocBox(tpe, attributes) => {
        print("alloc_box ")
        print(tpe)
        print(whenEmpty = false, ", ", attributes, ", ", "", (a: DebugAttribute) => print(a))
      }
      case Operator.allocGlobal(name) => {
        print("alloc_global ")
        print("@")
        print(name)
      }
      case Operator.apply(nothrow, value, substitutions, arguments, tpe) => {
        print("apply ")
        print( "[nothrow] ", nothrow)
        print(value)
        print(whenEmpty = false, "<", substitutions, ", ", ">", (t: Type) => naked(t))
        print(whenEmpty = true, "(", arguments, ", ", ")", (a: String) => print(a))
        print(" : ")
        print(tpe)
      }
      case Operator.beginAccess(access, enforcement, noNestedConflict, builtin, operand) => {
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
      case Operator.beginApply(nothrow, value, substitutions, arguments, tpe) => {
        print("begin_apply ")
        print( "[nothrow] ", nothrow)
        print(value)
        print(whenEmpty = false, "<", substitutions, ", ", ">", (t: Type) => naked(t))
        print(whenEmpty = true, "(", arguments, ", ", ")", (a: String) => print(a))
        print(" : ")
        print(tpe)
      }
      case Operator.beginBorrow(operand) => {
        print("begin_borrow ")
        print(operand)
      }
      case Operator.builtin(name, operands, tpe) => {
        print("builtin ")
        literal(name)
        print(whenEmpty = true, "(", operands, ", ", ")", (o: Operand) => print(o))
        print(" : ")
        print(tpe)
      }
      case Operator.condFail(operand, message) => {
        print("cond_fail ")
        print(operand)
        if (message.nonEmpty) {
          print(", ")
          literal(message.get)
        }
      }
      case Operator.convertEscapeToNoescape(notGuaranteed, escaped, operand, tpe) => {
        print("convert_escape_to_noescape ")
        print( "[not_guaranteed] ", notGuaranteed)
        print( "[escaped] ", escaped)
        print(operand)
        print(" to ")
        print(tpe)
      }
      case Operator.convertFunction(operand, withoutActuallyEscaping, tpe) => {
        print("convert_function ")
        print(operand)
        print(" to ")
        print( "[without_actually_escaping] ", withoutActuallyEscaping)
        print(tpe)
      }
      case Operator.copyAddr(take, value, initialization, operand) => {
        print("copy_addr ")
        print( "[take] ", take)
        print(value)
        print(" to ")
        print( "[initialization] ", initialization)
        print(operand)
      }
      case Operator.copyValue(operand) => {
        print("copy_value ")
        print(operand)
      }
      case Operator.deallocStack(operand) => {
        print("dealloc_stack ")
        print(operand)
      }
      case Operator.deallocBox(operand) => {
        print("dealloc_box ")
        // NOTE: Not sure if this is correct
        // dealloc_box %0 : $@box T
        print(operand)
      }
      case Operator.projectBox(operand) => {
        print("project_box ")
        print(operand)
      }
      case Operator.debugValue(operand, attributes) => {
        print("debug_value ")
        print(operand)
        print(whenEmpty = false, ", ", attributes, ", ", "", (a: DebugAttribute) => print(a))
      }
      case Operator.debugValueAddr(operand, attributes) => {
        print("debug_value_addr ")
        print(operand)
        print(whenEmpty = false, ", ", attributes, ", ", "", (a: DebugAttribute) => print(a))
      }
      case Operator.destroyAddr(operand) => {
        print("destroy_addr ")
        print(operand)
      }
      case Operator.destroyValue(operand) => {
        print("destroy_value ")
        print(operand)
      }
      case Operator.destructureTuple(operand) => {
        print("destructure_tuple ")
        print(operand)
      }
      case Operator.endAccess(abort, operand) => {
        print("end_access ")
        print( "[abort] ", abort)
        print(operand)
      }
      case Operator.endApply(value) => {
        print("end_apply ")
        print(value)
      }
      case Operator.abortApply(value) => {
        print("abort_apply ")
        print(value)
      }
      case Operator.endBorrow(operand) => {
        print("end_borrow ")
        print(operand)
      }
      case Operator.enum(tpe, declRef, operand: Option[Operand]) => {
        print("enum ")
        print(tpe)
        print(", ")
        print(declRef)
        if (operand.nonEmpty) {
          print(", ")
          print(operand.get)
        }
      }
      case Operator.floatLiteral(tpe, value) => {
        print("float_literal ")
        print(tpe)
        print(", 0x")
        print(value)
      }
      case Operator.functionRef(name, tpe) => {
        print("function_ref ")
        print("@")
        print(name)
        print(" : ")
        print(tpe)
      }
      case Operator.globalAddr(name, tpe) => {
        print("global_addr ")
        print("@")
        print(name)
        print(" : ")
        print(tpe)
      }
      case Operator.indexAddr(addr, index) => {
        print("index_addr ")
        print(addr)
        print(", ")
        print(index)
      }
      case Operator.integerLiteral(tpe, value) => {
        print("integer_literal ")
        print(tpe)
        print(", ")
        literal(value)
      }
      case Operator.load(kind: Option[LoadOwnership], operand) => {
        print("load ")
        if (kind.nonEmpty) {
          print(kind.get)
          print(" ")
        }
        print(operand)
      }
      case Operator.markDependence(operand, on) => {
        print("mark_dependence ")
        print(operand)
        print(" on ")
        print(on)
      }
      case Operator.metatype(tpe) => {
        print("metatype ")
        print(tpe)
      }
      case Operator.partialApply(calleeGuaranteed, onStack, value, substitutions, arguments, tpe) => {
        print("partial_apply ")
        print( "[callee_guaranteed] ", calleeGuaranteed)
        print( "[on_stack] ", onStack)
        print(value)
        print(whenEmpty = false, "<", substitutions, ", ", ">", (t: Type) => naked(t))
        print(whenEmpty = true, "(", arguments, ", ", ")", (a: String) => print(a))
        print(" : ")
        print(tpe)
      }
      case Operator.pointerToAddress(operand, strict, tpe) => {
        print("pointer_to_address ")
        print(operand)
        print(" to ")
        print( "[strict] ", strict)
        print(tpe)
      }
      case Operator.releaseValue(operand) => {
        print("release_value ")
        print(operand)
      }
      case Operator.retainValue(operand) => {
        print("retain_value ")
        print(operand)
      }
      case Operator.selectEnum(operand, cases, tpe) => {
        print("select_enum ")
        print(operand)
        print(whenEmpty = false, "", cases, "", "", (c: Case) => print(c))
        print(" : ")
        print(tpe)
      }
      case Operator.store(value, kind: Option[StoreOwnership], operand) => {
        print("store ")
        print(value)
        print(" to ")
        if (kind.nonEmpty) {
          print(kind.get)
          print(" ")
        }
        print(operand)
      }
      case Operator.stringLiteral(encoding, value) => {
        print("string_literal ")
        print(encoding)
        print(" ")
        literal(value)
      }
      case Operator.strongRelease(operand) => {
        print("strong_release ")
        print(operand)
      }
      case Operator.strongRetain(operand) => {
        print("strong_retain ")
        print(operand)
      }
      case Operator.struct(tpe, operands) => {
        print("struct ")
        print(tpe)
        print(whenEmpty = true, " (", operands, ", ", ")", (o: Operand) => print(o))
      }
      case Operator.structElementAddr(operand, declRef) => {
        print("struct_element_addr ")
        print(operand)
        print(", ")
        print(declRef)
      }
      case Operator.structExtract(operand, declRef) => {
        print("struct_extract ")
        print(operand)
        print(", ")
        print(declRef)
      }
      case Operator.thinToThickFunction(operand, tpe) => {
        print("thin_to_thick_function ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case Operator.tuple(elements) => {
        print("tuple ")
        print(elements)
      }
      case Operator.tupleExtract(operand, declRef) => {
        print("tuple_extract ")
        print(operand)
        print(", ")
        literal(declRef)
      }
      case Operator.unknown(name) => {
        print(name)
        print(" <?>")
      }
      case Operator.witnessMethod(archeType, declRef, declType, tpe) => {
        print("witness_method ")
        print(archeType)
        print(", ")
        print(declRef)
        print(" : ")
        naked(declType)
        print(" : ")
        print(tpe)
      }
      case Operator.initExistentialMetatype(operand, tpe) => {
        print("init_existential_metatype ")
        print(operand)
        print(", ")
        print(tpe)
      }
      case Operator.openExistentialMetatype(operand, tpe) => {
        print("open_existential_metatype ")
        print(operand)
        print(" to ")
        print(tpe)
      }
      case Operator.allocExistentialBox(tpeP, tpeT) => {
        print("alloc_existential_box ")
        print(tpeP)
        print(", ")
        print(tpeT)
      }
    }
  }

  def print(terminator: Terminator): Unit = {
    terminator match {
      case Terminator.br(label, operands) => {
        print("br ")
        print(label)
        print(whenEmpty = false, "(", operands, ", ", ")", (o: Operand) => print(o))
      }
      case Terminator.condBr(cond, trueLabel, trueOperands, falseLabel, falseOperands) => {
        print("cond_br ")
        print(cond)
        print(", ")
        print(trueLabel)
        print(whenEmpty = false, "(", trueOperands, ", ", ")", (o: Operand) => print(o))
        print(", ")
        print(falseLabel)
        print(whenEmpty = false, "(", falseOperands, ", ", ")", (o: Operand) => print(o))
      }
      case Terminator.ret(operand) => {
        print("return ")
        print(operand)
      }
      case Terminator.switchEnum(operand, cases) => {
        print("switch_enum ")
        print(operand)
        print(whenEmpty = false, "", cases, "", "", (c: Case) => print(c))
      }
      case Terminator.switchEnumAddr(operand, cases) => {
        print("switch_enum_addr ")
        print(operand)
        print(whenEmpty = false, "", cases, "", "", (c: Case) => print(c))
      }
      case Terminator.unknown(name) => {
        print(name)
        print(" <?>")
      }
      case Terminator.unreachable => {
        print("unreachable")
      }
    }
  }

  def print(access: Access): Unit = {
    access match {
      case Access.deinit => {
        print("deinit")
      }
      case Access.init => {
        print("init")
      }
      case Access.modify => {
        print("modify")
      }
      case Access.read => {
        print("read")
      }
    }
  }

  def print(argument: Argument): Unit = {
    print(argument.valueName)
    print(" : ")
    print(argument.tpe)
  }

  def print(cse: Case): Unit = {
    print(", ")
    cse match {
      case Case.cs(declRef, result) => {
        print("case ")
        print(declRef)
        print(": ")
        print(result)
      }
      case Case.default(result) => {
        print("default ")
        print(result)
      }
    }
  }

  def print(convention: Convention): Unit = {
    print("(")
    convention match {
      case Convention.c => print("c")
      case Convention.method => print("method")
      case Convention.thin => print("thin")
      case Convention.block => print("block")
      case Convention.witnessMethod(tpe) => {
        print("witness_method: ")
        naked(tpe)
      }
    }
    print(")")
  }

  def print(attribute: DebugAttribute): Unit = {
    attribute match {
      case DebugAttribute.argno(index) => {
        print("argno ")
        literal(index)
      }
      case DebugAttribute.name(name) => {
        print("name ")
        literal(name)
      }
      case DebugAttribute.let => {
        print("let")
      }
      case DebugAttribute.variable => {
        print("var")
      }
    }
  }

  def print(declKind: DeclKind): Unit = {
    declKind match {
      case DeclKind.allocator => print("allocator")
      case DeclKind.deallocator => print("deallocator")
      case DeclKind.destroyer => print("destroyer")
      case DeclKind.enumElement => print("enumelt")
      case DeclKind.getter => print("getter")
      case DeclKind.globalAccessor => print("globalaccessor")
      case DeclKind.initializer => print("initializer")
      case DeclKind.ivarDestroyer => print("ivardestroyer")
      case DeclKind.ivarInitializer => print("ivarinitializer")
      case DeclKind.setter => print("setter")
    }
  }

  def print(declRef: DeclRef): Unit = {
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

  def print(encoding: Encoding): Unit = {
    encoding match {
      case Encoding.objcSelector => print("objcSelector")
      case Encoding.utf8 => print("utf8")
      case Encoding.utf16 => print("utf16")
    }
  }

  def print(enforcement: Enforcement): Unit = {
    enforcement match {
      case Enforcement.dynamic => print("dynamic")
      case Enforcement.static => print("static")
      case Enforcement.unknown => print("unknown")
      case Enforcement.unsafe => print("unsafe")
    }
  }

  def print(attribute: FunctionAttribute): Unit = {
    attribute match {
      case FunctionAttribute.alwaysInline => print("[always_inline]")
      case FunctionAttribute.differentiable(spec) => {
        print("[differentiable ")
        print(spec)
        print("]")
      }
      case FunctionAttribute.dynamicallyReplacable => print("[dynamically_replacable]")
      case FunctionAttribute.noInline => print("[noinline]")
      case FunctionAttribute.noncanonical(NoncanonicalFunctionAttribute.ownershipSSA) => print("[ossa]")
      case FunctionAttribute.readonly => print("[readonly]")
      case FunctionAttribute.semantics(value) => {
        print("[_semantics ")
        literal(value)
        print("]")
      }
      case FunctionAttribute.serialized => print("[serialized]")
      case FunctionAttribute.thunk => print("[thunk]")
      case FunctionAttribute.transparent => print("[transparent]")
    }
  }

  def print(linkage: Linkage): Unit = {
    linkage match {
      case Linkage.hidden => print("hidden ")
      case Linkage.hiddenExternal => print("hidden_external ")
      case Linkage.priv => print("private ")
      case Linkage.privateExternal => print("private_external ")
      case Linkage.public => print("")
      case Linkage.publicExternal => print("public_external ")
      case Linkage.publicNonABI => print("non_abi ")
      case Linkage.shared => print("shared ")
      case Linkage.sharedExternal => print("shared_external ")
    }
  }

  def print(loc: Loc): Unit = {
    print("loc ")
    literal(loc.path)
    print(":")
    literal(loc.line)
    print(":")
    literal(loc.column)
  }

  def print(operand: Operand): Unit = {
    print(operand.value)
    print(" : ")
    print(operand.tpe)
  }

  def print(result: Result): Unit = {
    if (result.valueNames.length == 1) {
      print(result.valueNames(0))
    } else {
      print(whenEmpty = true, "(", result.valueNames, ", ", ")", (v: String) => print(v))
    }
  }

  def print(sourceInfo: SourceInfo): Unit = {
    // The SIL docs say that scope refs precede locations, but this is
    // not true once you look at the compiler outputs or its source code.
    print(", ", sourceInfo.loc, (l: Loc) => print(l))
    print(", scope ", sourceInfo.scopeRef, (ref: Int) => literal(ref))
  }

  def print(elements: TupleElements): Unit = {
    elements match {
      case TupleElements.labeled(tpe, values) => {
        print(tpe)
        print(whenEmpty = true, " (", values, ", ", ")", (v: String) => print(v))
      }
      case TupleElements.unlabeled(operands) => {
        print(whenEmpty = true, "(", operands, ", ", ")", (o: Operand) => print(o))
      }
    }
  }

  def print(tpe: Type): Unit = {
    tpe match {
      case Type.withOwnership(attribute, subtype) => {
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

  def naked(tpe: Type): Unit = {
    tpe match {
      case Type.addressType(tpe) => {
        print("*")
        naked(tpe)
      }
      case Type.attributedType(attrs, tpe) => {
        print(whenEmpty = true, "", attrs, " ", " ", (t: TypeAttribute) => print(t))
        naked(tpe)
      }
      case Type.coroutineTokenType => {
        print("!CoroutineTokenType!")
      }
      case Type.functionType(params, result) => {
        print(whenEmpty = true, "(", params, ", ", ")", (t: Type) => naked(t))
        print(" -> ")
        naked(result)
      }
      case Type.genericType(params, reqs, tpe) => {
        print(whenEmpty = true, "<", params, ", ", "", (p: String) => print(p))
        print(whenEmpty = false, " where ", reqs, ", ", "", (r: TypeRequirement) => print(r))
        print(">")
        // This is a weird corner case of -emit-sil, so we have to go the extra mile.
        if (tpe.isInstanceOf[Type.genericType]) {
          naked(tpe)
        } else {
          print(" ")
          naked(tpe)
        }
      }
      case Type.namedType(name) => {
        print(name)
      }
      case Type.selectType(tpe, name) => {
        naked(tpe)
        print(".")
        print(name)
      }
      case Type.selfType => {
        print("Self")
      }
      case Type.specializedType(tpe, args) => {
        naked(tpe)
        print(whenEmpty = true, "<", args, ", ", ">", (t: Type) => naked(t))
      }
      case Type.tupleType(params) => {
        print(whenEmpty = true, "(", params, ", ", ")", (t: Type) => naked(t))
      }
      case Type.withOwnership(_, _) => {
        // Note: "fatalError" in Swift, but I think exception is okay here.
        ExceptionReporter.report(new Exception("Types with ownership should be printed before naked type print!"))
      }
    }
  }

  def print(attribute: TypeAttribute): Unit = {
    attribute match {
      case TypeAttribute.calleeGuaranteed => print("@callee_guaranteed")
      case TypeAttribute.convention(convention) => {
        print("@convention")
        print(convention)
      }
      case TypeAttribute.guaranteed => print("@guaranteed")
      case TypeAttribute.inGuaranteed => print("@in_guaranteed")
      case TypeAttribute.in => print("@in")
      case TypeAttribute.inout => print("@inout")
      case TypeAttribute.noescape => print("@noescape")
      case TypeAttribute.out => print("@out")
      case TypeAttribute.owned => print("@owned")
      case TypeAttribute.thick => print("@thick")
      case TypeAttribute.thin => print("@thin")
      case TypeAttribute.yieldOnce => print("@yield_once")
      case TypeAttribute.yields => print("@yields")
      case TypeAttribute.error => print("@error")
      case TypeAttribute.objcMetatype => print("@objc_metatype")
    }
  }

  def print(requirement: TypeRequirement): Unit = {
    requirement match {
      case TypeRequirement.conformance(lhs, rhs) => {
        naked(lhs)
        print(" : ")
        naked(rhs)
      }
      case TypeRequirement.equality(lhs, rhs) => {
        naked(lhs)
        print(" == ")
        naked(rhs)
      }
    }
  }

  def print(ownership: LoadOwnership): Unit = {
    ownership match {
      case LoadOwnership.copy => print("[copy]")
      case LoadOwnership.take => print("[take]")
      case LoadOwnership.trivial => print("[trivial]")
    }
  }

  def print(storeOwnership: StoreOwnership): Unit = {
    storeOwnership match {
      case StoreOwnership.init => print("[init]")
      case StoreOwnership.trivial => print("[trivial]")
    }
  }


}