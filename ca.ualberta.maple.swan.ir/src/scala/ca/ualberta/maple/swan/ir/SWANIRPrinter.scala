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

class SWANIRPrinter extends Printer {

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
    // print(block.terminator)
    // print("\n")
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
        print("binary_op ")
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

  }

  def print(functionAttribute: FunctionAttribute): Unit = {
    functionAttribute match {
      case FunctionAttribute.global_init => print("[global_init] ")
      case FunctionAttribute.coroutine => print("[coroutine] ")
      case FunctionAttribute.stub => print("[stub] ")
      case FunctionAttribute.model => print("[model] ")
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
    print(tpe.name)
    print('`')
  }

  def print(pos: Position): Unit = {
    print(", loc ")
    print(pos.path)
    print(":")
    literal(pos.line)
    print(":")
    literal(pos.col)
  }
}
