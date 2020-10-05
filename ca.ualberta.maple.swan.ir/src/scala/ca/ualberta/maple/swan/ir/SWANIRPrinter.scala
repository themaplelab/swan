/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.ir

import scala.collection.mutable.ArrayBuffer

class SWANIRPrinter extends Printer {

  // Options easier on the eyes for debugging
  // Tests are not guaranteed to pass with these options on!
  val DONT_PRINT_POSITION = false
  val ARBITRARY_TYPE_NAMES = false
  var ARBITRARY_TYPE_NAME_COUNTER = new ArrayBuffer[String]()
  // Maybe add option here for function names (they can be quite long).

  def print(module: Module): String = {
    print("swanir_stage raw")
    print("\n\n")
    module.imports.foreach(imprt => {
      print("import ")
      print(imprt)
      print("\n")
    })
    print("\n")
    module.functions.foreach(function => {
      print(function)
      print("\n")
    })
    this.toString
  }

  def print(function: Function): String = {
    print("func ")
    if (function.attribute.nonEmpty) print(function.attribute.get)
    print("@`")
    print(function.name)
    print("`")
    print(" : ")
    print(function.tpe)
    print(whenEmpty = false, " {\n", function.blocks.toArray, "\n", "}", (block: Block) => print(block))
    print("\n")
    this.toString
  }

  def print(block: Block): Unit = {
    print(block.blockRef)
    print(whenEmpty = false, "(", block.arguments, ", ", ")", (arg: Argument) => print(arg))
    print(":")
    indent()
    block.operators.foreach(op => {
      print("\n")
      print(op)
    })
    print("\n")
    // if statements only while terminators are still WIP
    if (block.terminator != null) print(block.terminator)
    if (block.terminator != null) print("\n")
    unindent()
  }

  def print(argument: Argument): Unit = {
    print(argument.name)
    print(" : ")
    print(argument.tpe)
  }

  def print(inst: InstructionDef): String = {
    inst match {
      case InstructionDef.operator(operatorDef) => print(operatorDef)
      case InstructionDef.terminator(terminatorDef) => print(terminatorDef)
    }
    this.toString
  }

  def print(op: OperatorDef): String = {
    print(op.operator)
    print(op.position, (pos: Position) => print(pos))
    this.toString
  }

  def print(term: TerminatorDef): String = {
    print(term.terminator)
    print(term.position, (pos: Position) => print(pos))
    this.toString
  }

  def print(operator: Operator): Unit = {
    operator match {
      case result: WithResult =>
        printResult(result.value)
      case _ =>
    }
    operator match {
      case Operator.newGlobal(name) => {
        print("new_global ")
        printGlobal(name)
      }
      case Operator.neww(result) => {
        print("new ")
        print(result.tpe)
        return // don't print , $T
      }
      case Operator.assignGlobal(_, name) => {
        print("assign_global ")
        printGlobal(name)
      }
      case Operator.assign(_, from) => {
        print("assign ")
        print(from)
      }
      case Operator.literal(_, lit) => {
        print("literal ")
        lit match {
          case Literal.string(value) => print("[string] #"); literal(value)
          case Literal.int(value) => print("[int] #"); literal(value)
          case Literal.float(value) => print("[float] #"); literal(value)
        }
      }
      case Operator.builtinRef(_, declRef, name) => {
        print("builtin_ref ")
        print("[decl] ", when = declRef)
        literal(name)
      }
      case Operator.functionRef(_, names) => {
        print("function_ref ")
        print("@")
        names.foreach(name => {
          print('`')
          print(name)
          print('`')
          if (name != names.last) {
            print(" or ")
          }
        })
      }
      case Operator.apply(_, functionRef, arguments) => {
        print("apply ")
        print(functionRef)
        print(whenEmpty = true, "(", arguments, ", ", ")", (arg: SymbolRef) => print(arg))
      }
      case Operator.applyCoroutine(functionRef, arguments) => {
        // TODO
      }
      case Operator.arrayRead(_, alias, arr) => {
        print("array_read ")
        print("[alias] ", when = alias)
        print(arr)
      }
      case Operator.arrayWrite(value, arr) => {
        print("array_write ")
        print(value)
        print(" to ")
        print(arr)
      }
      case Operator.fieldRead(_, alias, obj, field) =>
        print("field_read ")
        print("[alias] ", when = alias)
        print(obj)
        print(", ")
        print(field)
      case Operator.fieldWrite(value, obj, field) =>
        print("field_write ")
        print(value)
        print(" to ")
        print(obj)
        print(", ")
        print(field)
      case Operator.unaryOp(_, operation, operand) =>
        print("unary_op ")
        print(operation)
        print(" ")
        print(operand)
      case Operator.binaryOp(result, operation, lhs, rhs) => {
        print("binary_op ")
        print(lhs)
        print(" ")
        print(operation)
        print(" ")
        print(rhs)
      }
      case Operator.condFail(value) => {
        print("cond_fail ")
        print(value)
      }
      case Operator.switchEnumAssign(result, switchOn, cases, default) => {
        print("switch_enum_assign ")
        print(switchOn)
        print(whenEmpty = false, ", ", cases, ", ", "", (c : EnumAssignCase) => print(c))
        if (default.nonEmpty) { print(", default "); print(default.get) }
      }
      case Operator.pointerRead(result, pointer) => {
        print("pointer_read ")
        print(pointer)
      }
      case Operator.pointerWrite(value, pointer) => {
        print("pointer_write ")
        print(value)
        print(" to ")
        print(pointer)
      }
      case Operator.abortCoroutine(value) => {
        print("abort_coroutine ")
        print(value)
      }
      case Operator.endCoroutine(value) => {
        print("end_coroutine ")
        print(value)
      }
      case Operator.unhandledApply(_, name, arguments) => {
        print("unhandled_apply @`")
        print(name)
        print("`")
        print(whenEmpty = true, "(", arguments, ", ", ")", (arg: SymbolRef) => print(arg))
      }
      case _: WithResult =>
    }
    operator match {
      case result: WithResult => {
        print(", ")
        print(result.value.tpe)
      }
      case _ =>
    }
  }

  def print(terminator: Terminator): Unit = {
    terminator match {
      case Terminator.br(to, args) => {
        print("br ")
        print(to)
        if (args.length > 0) {
          print(whenEmpty = false, "(", args, ", ", ")", (s: SymbolRef) => print(s))
        }
      }
      case Terminator.condBr(cond, trueBlock, trueArgs, falseBlock, falseArgs) => {
        print("cond_br ")
        print(cond)
        print(", true ")
        print(trueBlock)
        print(whenEmpty = false, "(", trueArgs, ", ", ")", (s: SymbolRef) => print(s))
        print(", false ")
        print(falseBlock)
        print(whenEmpty = false, "(", falseArgs, ", ", ")", (s: SymbolRef) => print(s))
      }
      case Terminator.switch(switchOn, cases, default) => {
        print("switch ")
        print(switchOn)
        print(whenEmpty = false, ", ", cases, ", ", "", (c: SwitchCase) => print(c))
        if (default.nonEmpty) { print(", default "); print(default.get); }
      }
      case Terminator.switchEnum(switchOn, cases, default) => {
        print("switch_enum ")
        print(switchOn)
        print(whenEmpty = false, ", ", cases, ", ", "", (c: SwitchEnumCase) => print(c))
        if (default.nonEmpty) { print(", default "); print(default.get); }
      }
      case Terminator.ret(value) => {
        print("return ")
        print(value)
      }
      case Terminator.thro(value) => {
        print("throw ")
        print(value)
      }
      case Terminator.tryApply(functionRef, arguments, normal, error) => {
        print("try_apply ")
        print(functionRef)
        print(whenEmpty = true, "(", arguments, ", ", ")", (arg: SymbolRef) => print(arg))
        print(", normal ")
        print(normal)
        print(", error ")
        print(error)
      }
      case Terminator.unreachable => print("unreachable")
      case Terminator.yld(yields, resume, unwind) => {
        print("yield ")
        print(whenEmpty = true, "(", yields, ", ", ")", (s: SymbolRef) => print(s))
        print(", resume ")
        print(resume)
        print(", unwind ")
        print(unwind)
      }
      case Terminator.unwind => print("unwind")
    }
  }

  def print(cse: EnumAssignCase): Unit = {
    print("case \"")
    print(cse.decl)
    print("\" : ")
    print(cse.value)
  }

  def print(cse: SwitchCase): Unit = {
    print("case ")
    print(cse.value)
    print(" : ")
    print(cse.destination)
  }

  def print(cse: SwitchEnumCase): Unit = {
    print("case ")
    print(cse.decl)
    print(" : ")
    print(cse.destination)
  }

  def print(functionAttribute: FunctionAttribute): Unit = {
    functionAttribute match {
      case FunctionAttribute.global_init => print("[global_init] ")
      case FunctionAttribute.coroutine => print("[coroutine] ")
      case FunctionAttribute.stub => print("[stub] ")
      case FunctionAttribute.model => print("[model] ")
    }
  }

  def print(unaryOperation: UnaryOperation): Unit = {
    unaryOperation match {
      case UnaryOperation.arbitrary => print("[arb]")
    }
  }

  def print(binaryOperation: BinaryOperation): Unit = {
    binaryOperation match {
      case BinaryOperation.arbitrary => print("[arb]")
    }
  }

  def print(symbolRef: SymbolRef): Unit = {
    print(symbolRef.name)
  }

  def print(blockRef: BlockRef): Unit = {
    print(blockRef.label)
  }

  def printResult(symbol: Symbol): Unit = {
    print(symbol.ref)
    print(" = ")
  }

  def printGlobal(name: String): Unit = {
    print("@")
    print('`')
    print(name)
    print('`')
  }

  def print(tpe: Type): Unit = {
    print("$")
    print('`')
    if (ARBITRARY_TYPE_NAMES) {
      val num = {
        if (!ARBITRARY_TYPE_NAME_COUNTER.contains(tpe.name)) {
          ARBITRARY_TYPE_NAME_COUNTER.append(tpe.name)
        }
        ARBITRARY_TYPE_NAME_COUNTER.indexOf(tpe.name)
      }
      print("T" + num)
    } else {
      print(tpe.name)
    }
    print('`')
  }

  def print(pos: Position): Unit = {
    if (DONT_PRINT_POSITION) {
      return
    }
    print(", loc ")
    print(pos.path)
    print(":")
    literal(pos.line)
    print(":")
    literal(pos.col)
  }
}
