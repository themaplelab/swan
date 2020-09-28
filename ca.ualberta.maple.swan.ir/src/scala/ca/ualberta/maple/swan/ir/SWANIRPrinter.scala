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
    print(whenEmpty = false, " {\n", function.blocks, "\n", "}", (block: Block) => print(block))
    print("\n")
    this.toString
  }

  def print(block: Block): Unit = {
    print(block.label)
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
      case Operator.newGlobal(name) => {
        print("new_global ")
        printGlobal(name)
      }
      case Operator.neww(result) => {
        printResult(result)
        print("new ")
        print(result.tpe)
      }
      case Operator.assignGlobal(result, name) => {
        printResult(result)
        print("assign_global ")
        printGlobal(name)
        print(", ")
        print(result.tpe)
      }
      case Operator.assign(result, from) => {
        printResult(result)
        print("assign ")
        print(from)
        print(", ")
        print(result.tpe)
      }
      case Operator.literal(result, lit) => {
        printResult(result)
        print("literal ")
        lit match {
          case Literal.string(value) => print("[string] #"); literal(value)
          case Literal.int(value) => print("[int] #"); literal(value)
          case Literal.float(value) => print("[float] #"); literal(value)
        }
        print(", ")
        print(result.tpe)
      }
      case Operator.builtinRef(result, declRef, name) => {
        printResult(result)
        print("builtin_ref ")
        print("[decl] ", when = declRef)
        literal(name)
        print(", ")
        print(result.tpe)
      }
      case Operator.functionRef(result, names) => {
        printResult(result)
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
        print(", ")
        print(result.tpe)
      }
      case Operator.apply(result, functionRef, arguments) => {
        printResult(result)
        print("apply ")
        print(functionRef)
        print(whenEmpty = true, "(", arguments, ", ", ")", (arg: String) => print(arg))
        print(", ")
        print(result.tpe)
      }
      case Operator.applyCoroutine(functionRef, arguments) => {
        // TODO
      }
      case Operator.arrayRead(result, alias, arr) => {
        printResult(result)
        print("array_read ")
        print("[alias] ", when = alias)
        print(arr)
        print(", ")
        print(result.tpe)
      }
      case Operator.arrayWrite(value, arr) => {
        print("array_write ")
        print(value)
        print(" to ")
        print(arr)
      }
      case Operator.fieldRead(result, alias, obj, field) =>
        printResult(result)
        print("field_read ")
        print("[alias] ", when = alias)
        print(obj)
        print(", ")
        print(field)
        print(", ")
        print(result.tpe)
      case Operator.fieldWrite(value, obj, field) =>
        print("field_write ")
        print(value)
        print(" to ")
        print(obj)
        print(", ")
        print(field)
      case Operator.unaryOp(result, operation, operand) =>
        printResult(result)
        print("unary_op ")
        print(operation)
        print(" ")
        print(operand)
        print(", ")
        print(result.tpe)
      case Operator.binaryOp(result, operation, lhs, rhs) => {
        printResult(result)
        print("binary_op ")
        print(lhs)
        print(" ")
        print(operation)
        print(" ")
        print(rhs)
        print(", ")
        print(result.tpe)
      }
      case Operator.condFail(value) => {
        print("cond_fail ")
        print(value)
      }
      case Operator.switchEnumAssign(result, switchOn, cases, default) => {
        printResult(result)
        print("switch_enum_assign ")
        print(switchOn)
        print(whenEmpty = false, ", ", cases, ", ", "", (c : EnumAssignCase) => print(c))
        if (default.nonEmpty) { print(", default "); print(default.get) }
        print(", ")
        print(result.tpe)
      }
      case Operator.pointerRead(result, pointer) => {
        printResult(result)
        print("pointer_read ")
        print(pointer)
        print(", ")
        print(result.tpe)
      }
      case Operator.pointerWrite(value, pointer) => {
        print("pointer_write ")
        print(value)
        print(" to ")
        print(pointer)
      }
      case Operator.symbolCopy(from, to) => {
        print("symbol_copy ")
        print(from)
        print(" to ")
        print(to)
      }
      case Operator.abortCoroutine(value) => {
        print("abort_coroutine ")
        print(value)
      }
      case Operator.endCoroutine(value) => {
        print("end_coroutine ")
        print(value)
      }
    }
  }

  def print(terminator: Terminator): Unit = {
    terminator match {
      case Terminator.br(label, args) => {
        print("br ")
        print(label)
        if (args.length > 0) {
          print(whenEmpty = false, "(", args, ", ", ")", (s: String) => print(s))
        }
      }
      case Terminator.condBr(cond, trueLabel, trueArgs, falseLabel, falseArgs) => {
        print("cond_br ")
        print(cond)
        print(", true ")
        print(trueLabel)
        print(whenEmpty = false, "(", trueArgs, ", ", ")", (s: String) => print(s))
        print(", false ")
        print(falseLabel)
        print(whenEmpty = false, "(", falseArgs, ", ", ")", (s: String) => print(s))
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
      case Terminator.unreachable => print("unreachable")
      case Terminator.yld(yields, resumeLabel, unwindLabel) => {
        print("yield ")
        print(whenEmpty = true, "(", yields, ", ", ")", (s: String) => print(s))
        print(", resume ")
        print(resumeLabel)
        print(", unwind ")
        print(unwindLabel)
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

  def printResult(symbol: Symbol): Unit = {
    print(symbol.name)
    print(" = ")
  }

  def printGlobal(name: String): Unit = {
    print("@")
    print(name)
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
