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

import org.jgrapht.Graph
import org.jgrapht.graph.DefaultEdge

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class SWIRLPrinterOptions {
  var printLocation = false
  var printLocationPaths = true
  var useArbitraryTypeNames = false
  var printCFG = false
  var genLocationMap = false // expensive

  // only for canonical (meant for SPDS debugging)
  var printLineNumber = false
  var cgDebugInfo: Option[SWIRLPrinterOptions.CallGraphDebugInfo] = None
  var queryReachedInfo: Option[mutable.HashSet[CanOperatorDef]] = None

  def printLocation(b: Boolean): SWIRLPrinterOptions = {
    printLocation = b
    this
  }
  def printLocationPaths(b: Boolean): SWIRLPrinterOptions = {
    printLocationPaths = b
    this
  }
  def useArbitraryTypeNames(b: Boolean): SWIRLPrinterOptions = {
    useArbitraryTypeNames = b
    this
  }
  def printCFGWhenCanonical(b: Boolean): SWIRLPrinterOptions = {
    printCFG = b
    this
  }
  def genLocationMap(b: Boolean): SWIRLPrinterOptions = {
    genLocationMap = b
    this
  }
  def printLineNumber(b: Boolean): SWIRLPrinterOptions = {
    printLineNumber = b
    this
  }
  def cgDebugInfo(info: SWIRLPrinterOptions.CallGraphDebugInfo): SWIRLPrinterOptions = {
    cgDebugInfo = Some(info)
    this
  }
  def queryReachedInfo(info: mutable.HashSet[CanOperatorDef]): SWIRLPrinterOptions = {
    queryReachedInfo = Some(info)
    this
  }
}

class SWIRLPrinter extends Printer {

  val ARBITRARY_TYPE_NAME_COUNTER = new ArrayBuffer[String]()

  var canModule: CanModule = _ // needed global for printing line numbers

  var options = new SWIRLPrinterOptions()

  val locMap: mutable.HashMap[Object, (Int, Int)] =
    new mutable.HashMap[Object, (Int, Int)]()

  def print(mg: ModuleGroup, opts: SWIRLPrinterOptions): String = {
    options = opts
    this.canModule = new CanModule(mg.functions, None, mg.silMap, null)
    print("swirl_stage canonical")
    printNewline();printNewline()
    mg.toString.split("\n").foreach(s => {
      print("// " + s + "\n")
    })
    printNewline()
    canModule.functions.foreach(function => {
      print(function)
      printNewline()
    })
    if (options.genLocationMap) mg.swirlSourceMap = Some(locMap)
    this.toString
  }

  def print(module: Module, opts: SWIRLPrinterOptions): String = {
    options = opts
    print("swirl_stage raw")
    printNewline();printNewline()
    module.functions.foreach(function => {
      print(function)
      printNewline()
    })
    this.toString
  }

  def print(canModule: CanModule, opts: SWIRLPrinterOptions): String = {
    options = opts
    this.canModule = canModule
    print("swirl_stage canonical")
    printNewline();printNewline()
    canModule.functions.foreach(function => {
      print(function)
      printNewline()
    })
    this.toString
  }

  def print(function: Function): Unit = {
    if (options.genLocationMap) locMap.put(function, (line, getCol))
    print("func ")
    if (function.attribute.nonEmpty) print(function.attribute.get)
    print("@`")
    print(function.name)
    print("`")
    print(" : ")
    print(function.returnTpe)
    print(" : ")
    print(function.fullTpe)
    print(whenEmpty = false, " {\n", function.blocks, "\n", "}", (block: Block) => print(block))
    printNewline()
  }

  def print(function: CanFunction): Unit = {
    if (options.printCFG) {
      // T0D0: slow?
      val cfg = canModule.functions.find(p => p == function).get.cfg
      print(function, cfg)
    }
    if (options.genLocationMap) locMap.put(function, (line, getCol))
    if (options.printLineNumber) printLineNumber()
    if (options.cgDebugInfo.nonEmpty) {
       if (options.cgDebugInfo.get.entries.contains(function)) {
         print("// ENTRY"); printNewline()
       } else if (options.cgDebugInfo.get.dead.contains(function)) {
         print("// DEAD"); printNewline()
       }
       else {
         val from = mutable.HashSet.empty[String]
         options.cgDebugInfo.get.edges.foreach{ case (op, allTo) =>
           allTo.foreach{ case (m,to) =>
             if (to == function.name) {
               from.add(m)
             }
           }
         }
         from.foreach{m => print("// "); print(m); printNewline()}
       }
    }
    print("func ")
    if (function.isLibrary) print("[lib] ")
    if (function.attribute.nonEmpty) print(function.attribute.get)
    print("@`")
    print(function.name)
    print("`")
    print(whenEmpty = false, "(", function.arguments, ", ", ")", (arg: Argument) => {
      if (options.genLocationMap) locMap.put(arg, (line, getCol))
      print(arg)
    })
    print(" : ")
    print(function.returnTpe)
    print(" : ")
    print(function.fullTpe)
    print(whenEmpty = false, " {\n", function.blocks, "\n", "}", (block: CanBlock) => print(block))
    printNewline()
    if (options.genLocationMap) {
      function.symbolTable.foreach(entry => {
        entry._2 match {
          case SymbolTableEntry.operator(symbol, operator) => {
            if (locMap.contains(operator)) {
              locMap.put(symbol, locMap(operator))
            }
          }
          case _ =>
        }
      })
    }
  }

  def print(function: CanFunction, cfg: Graph[CanBlock, DefaultEdge]): Unit = {
    function.blocks.foreach(b => {
      // TODO: Remove check when DDG and CFG is serialized
      if (cfg.containsVertex(b)) {
        print(b.blockRef.label + " -> ")
        val it = cfg.outgoingEdgesOf(b).iterator()
        while (it.hasNext) {
          print(cfg.getEdgeTarget(it.next()).blockRef.label)
          if (it.hasNext) {
            print(", ")
          }
        }
        printNewline()
      }
    })
  }

  def print(block: Block): Unit = {
    if (options.genLocationMap) locMap.put(block, (line, getCol))
    print(block.blockRef)
    print(whenEmpty = false, "(", block.arguments, ", ", ")", (arg: Argument) => print(arg))
    print(":")
    indent()
    block.operators.foreach(op => {
      printNewline()
      print(op)
    })
    printNewline()
    // if statements only while terminators are still WIP
    if (block.terminator != null) print(block.terminator)
    if (block.terminator != null) printNewline()
    unindent()
  }

  def print(block: CanBlock): Unit = {
    if (options.genLocationMap) locMap.put(block, (line, getCol))
    print(block.blockRef)
    print(":")
    indent()
    block.operators.foreach(op => {
      printNewline()
      print(op)
    })
    printNewline()
    // if statements only while terminators are still WIP
    if (block.terminator != null) print(block.terminator)
    if (block.terminator != null) printNewline()
    unindent()
  }

  def print(argument: Argument): Unit = {
    print(argument.ref.name)
    print(" : ")
    print(argument.tpe)
  }

  def print(inst: RawInstructionDef): Unit = {
    inst match {
      case RawInstructionDef.operator(operatorDef) => print(operatorDef)
      case RawInstructionDef.terminator(terminatorDef) => print(terminatorDef)
    }
  }

  def print(inst: CanInstructionDef): Unit = {
    inst match {
      case CanInstructionDef.operator(operatorDef) => print(operatorDef)
      case CanInstructionDef.terminator(terminatorDef) => print(terminatorDef)
    }
  }

  def print(op: RawOperatorDef): Unit = {
    if (options.genLocationMap) locMap.put(op, (line, getCol))
    print(op.operator.asInstanceOf[Operator])
    print(op.position, (pos: Position) => print(pos))
  }

  def print(op: CanOperatorDef): Unit = {
    if (options.queryReachedInfo.nonEmpty && options.queryReachedInfo.get.contains(op)) {
      print("// REACHED")
      printNewline()
    }
    if (options.genLocationMap) {
      locMap.put(op, (line, getCol))
      locMap.put(op.operator, locMap(op))
    }
    if (options.printLineNumber) printLineNumber()
    print(op.operator.asInstanceOf[Operator])
    print(op.position, (pos: Position) => print(pos))
  }

  def print(term: RawTerminatorDef): Unit = {
    if (options.genLocationMap) locMap.put(term, (line, getCol))
    print(term.terminator.asInstanceOf[Terminator])
    print(term.position, (pos: Position) => print(pos))
  }

  def print(term: CanTerminatorDef): Unit = {
    if (options.genLocationMap) {
      locMap.put(term, (line, getCol))
      locMap.put(term.terminator, locMap(term))
    }
    if (options.printLineNumber) printLineNumber()
    print(term.terminator.asInstanceOf[Terminator])
    print(term.position, (pos: Position) => print(pos))
  }

  def print(operator: Operator): Unit = {
    if (options.cgDebugInfo.nonEmpty) {
      val d = options.cgDebugInfo.get
      d.edges.get(operator.asInstanceOf[CanOperator]) match {
        case Some(value) => {
          value.foreach{ case (_,f) => {
            print("// " + f)
            printNewline()
          }}
        }
        case None =>
      }
    }
    operator match {
      case result: WithResult =>
        printResult(result.value)
      case _ =>
    }
    operator match {
      case Operator.neww(result, allocType) => {
        print("new ")
        print(allocType)
        print(", ")
        print(result.tpe)
        return // don't print , $T
      }
      case Operator.assign(_, from, assignType) => {
        print("assign ")
        print(from)
        assignType match {
          case Some(value) => {
            value match {
              case AssignType.BBArg() =>
                print(" [bb arg]")
              case AssignType.PointerRead() =>
                print(" [pointer read]")
              case AssignType.PointerWrite() =>
                print(" [pointer write]")
            }
          }
          case None =>
        }
      }
      case Operator.literal(_, lit) => {
        print("literal ")
        lit match {
          case Literal.string(value) => print("[string] "); literal(value)
          case Literal.int(value) => print("[int] "); literal(value)
          case Literal.float(value) => print("[float] "); literal(value)
        }
      }
      case Operator.dynamicRef(_, obj, index) => {
        print("dynamic_ref ")
        print(obj)
        print(", ")
        print("@`")
        print(index)
        print("`")
      }
      case Operator.builtinRef(_, name) => {
        print("builtin_ref ")
        print("@`")
        print(name)
        print("`")
      }
      case Operator.functionRef(_, name) => {
        print("function_ref ")
        print("@`")
        print(name)
        print('`')
      }
      case Operator.apply(_, functionRef, arguments, functionType) => {
        // TODO: update documentation
        print("apply ")
        print(functionRef)
        print(whenEmpty = true, "(", arguments, ", ", ")", (arg: SymbolRef) => print(arg))
        if (functionType.nonEmpty) {
          print(", func_tpe: ")
          print(functionType.get)
        }
      }
      case Operator.singletonRead(_, tpe, field) => {
        print("singleton_read `")
        print(field)
        print("` from ")
        print(tpe)
      }
      case Operator.singletonWrite(value, tpe, field) => {
        print("singleton_write ")
        print(value)
        print(" to `")
        print(field)
        print("` in ")
        print(tpe)
      }
      case Operator.fieldRead(_, alias, obj, field, pointer) =>
        print("field_read ")
        if (alias.nonEmpty && (canModule == null)) {
          print("[alias ")
          print(alias.get)
          print("] ")
        }
        print("[pointer] ", when = pointer)
        print(obj)
        print(", ")
        print(field)
      case Operator.fieldWrite(value, obj, field, attr) =>
        print("field_write ")
        if (attr.nonEmpty) print(attr.get)
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
      case Operator.binaryOp(_, operation, lhs, rhs) => {
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
      case Operator.switchEnumAssign(_, switchOn, cases, default) => {
        print("switch_enum_assign ")
        print(switchOn)
        print(whenEmpty = false, ", ", cases, ", ", "", (c : EnumAssignCase) => print(c))
        if (default.nonEmpty) { print(", default "); print(default.get) }
      }
      case Operator.switchValueAssign(_, switchOn, cases, default) => {
        print("switch_value_assign ")
        print(switchOn)
        print(whenEmpty = false, ", ", cases, ", ", "", (c : ValueAssignCase) => print(c))
        if (default.nonEmpty) { print(", default "); print(default.get) }
      }
      case Operator.pointerRead(_, pointer) => {
        print("pointer_read ")
        print(pointer)
      }
      case Operator.pointerWrite(value, pointer, weak) => {
        print("pointer_write ")
        print("[weak] ", when = weak)
        print(value)
        print(" to ")
        print(pointer)
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
        if (args.nonEmpty) {
          print(whenEmpty = false, "(", args, ", ", ")", (s: SymbolRef) => print(s))
        }
      }
      case Terminator.br_can(to) => {
        print("br ")
        print(to)
      }
      case Terminator.brIf(cond, target, args) => {
        print("br_if ")
        print(cond)
        print(", ")
        print(target)
        print(whenEmpty = false, "(", args, ", ", ")", (s: SymbolRef) => print(s))
      }
      case Terminator.brIf_can(cond, target) => {
        print("br_if ")
        print(cond)
        print(", ")
        print(target)
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
      case Terminator.unreachable => print("unreachable")
      case Terminator.yld(yields, resume, unwind) => {
        print("yield ")
        print(whenEmpty = true, "(", yields, ", ", ")", (s: SymbolRef) => print(s))
        print(", resume ")
        print(resume)
        print(", unwind ")
        print(unwind)
      }
    }
  }

  def print(attr: FieldWriteAttribute): Unit = {
    print("[")
    attr match {
      case FieldWriteAttribute.pointer => print("pointer")
      case FieldWriteAttribute.weakPointer => print("weak_pointer")
      case FieldWriteAttribute.weak => print("weak")
    }
    print("] ")
  }

  def print(cse: EnumAssignCase): Unit = {
    print("case \"")
    print(cse.decl)
    print("\" : ")
    print(cse.value)
  }

  def print(cse: ValueAssignCase): Unit = {
    print("case ")
    print(cse.value)
    print(" : ")
    print(cse.select)
  }

  def print(cse: SwitchCase): Unit = {
    print("case ")
    print(cse.value)
    print(" : ")
    print(cse.destination)
  }

  def print(cse: SwitchEnumCase): Unit = {
    print("case \"")
    print(cse.decl)
    print("\" : ")
    print(cse.destination)
  }

  def print(functionAttribute: FunctionAttribute): Unit = {
    functionAttribute match {
      case FunctionAttribute.coroutine => print("[coroutine] ")
      case FunctionAttribute.stub => print("[stub] ")
      case FunctionAttribute.model => print("[model] ")
      case FunctionAttribute.modelOverride => print("[model_override] ")
      case FunctionAttribute.linked => print("[linked] ")
    }
  }

  def print(unaryOperation: UnaryOperation): Unit = {
    unaryOperation match {
      case UnaryOperation.arbitrary => print("[arb]")
    }
  }

  def print(binaryOperation: BinaryOperation): Unit = {
    binaryOperation match {
      case BinaryOperation.regular => print("[reg]")
      case BinaryOperation.arbitrary => print("[arb]")
      case BinaryOperation.equals => print("[eq]")
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
    if (options.useArbitraryTypeNames) {
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
    if (options.printLocation) {
      print(", loc ")
      if (options.printLocationPaths) {
        print("\"")
        print(pos.path)
        print("\":")
      }
      literal(pos.line)
      print(":")
      literal(pos.col)
    }
  }
}

object SWIRLPrinterOptions {

  class CallGraphDebugInfo() {
    val edges = new mutable.HashMap[CanOperator, mutable.HashSet[(String,String)]]
    val entries = new mutable.HashSet[CanFunction]
    val dead = new mutable.HashSet[CanFunction]
  }

}