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

package ca.ualberta.maple.swan.ir.canonical

import ca.ualberta.maple.swan.ir.{AssignType, CanFunction, CanOperatorDef, FieldWriteAttribute, Module, Operator, Symbol, SymbolRef, SymbolTableEntry, Type}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/*
 * These mutations are specific to analysis and are therefore
 * separate from SWIRLPass.
 */
class Mutations(val function: CanFunction, val module: Module) {

  val intermediateSymbols: mutable.HashMap[String, Integer] = new mutable.HashMap()

  def doMutations(): Unit = {
    locationManager()
    pointerManager()
    // ...
  }

  private def makeSymbolRef(f: CanFunction, ref: String): SymbolRef = {
    val symbols = f.refTable.symbols
    if (symbols.contains(ref)) {
      symbols.put(ref, symbols(ref))
      symbols(ref)
    } else {
      val symbolRef = new SymbolRef(ref)
      symbols.put(ref, symbolRef)
      symbolRef
    }
  }

  private def generateSymbolName(f: CanFunction, value: String): SymbolRef = {
    if (!intermediateSymbols.contains(value)) {
      intermediateSymbols.put(value, 0)
    }
    val ret = value + "m" + intermediateSymbols(value).toString
    intermediateSymbols(value) = intermediateSymbols(value) + 1
    makeSymbolRef(f, ret)
  }

  private def locationManager(): Unit = {
    function.blocks.foreach(b => {
      var opIdx = 0
      while (opIdx < b.operators.length) {
        val opDef = b.operators(opIdx)
        opDef.operator match {
          case apply: Operator.apply => {
            function.symbolTable(apply.functionRef.name) match {
              case SymbolTableEntry.operator(_, operator) => {
                operator match {
                  case Operator.builtinRef(res, name) => {
                    if (name == "#CLLocationManager.activityType!setter.foreign") {
                      val fieldReadRet = new Symbol(generateSymbolName(function, res.ref.name), new Type("Builtin.RawPointer"))
                      val readType = new CanOperatorDef(Operator.fieldRead(fieldReadRet, None, apply.arguments(0), "type"), None)
                      function.symbolTable.putOp(fieldReadRet, readType.operator)
                      val funcRefRet = new Symbol(generateSymbolName(function, res.ref.name), new Type("Any"))
                      val funcRef = new CanOperatorDef(Operator.functionRef(funcRefRet, "SWAN.CLLocationManager.setActivityType"), None)
                      function.symbolTable.putOp(funcRefRet, funcRef.operator)
                      val args = ArrayBuffer(apply.arguments(1), fieldReadRet.ref)
                      val newApply = new CanOperatorDef(Operator.apply(apply.result, funcRefRet.ref, args, None), opDef.position)
                      function.symbolTable.replace(apply.result.ref.name, SymbolTableEntry.operator(apply.result, newApply.operator))
                      b.operators.remove(opIdx)
                      b.operators.insert(opIdx, newApply)
                      b.operators.insert(opIdx, funcRef)
                      b.operators.insert(opIdx, readType)
                    } else if (name == "#CLLocationManager.distanceFilter!setter.foreign") {
                      val fieldReadRet = new Symbol(generateSymbolName(function, res.ref.name), new Type("Builtin.FPIEEE64"))
                      val readDouble = new CanOperatorDef(Operator.fieldRead(fieldReadRet, None, apply.arguments(0), "_value"), None)
                      function.symbolTable.putOp(fieldReadRet, readDouble.operator)
                      val funcRefRet = new Symbol(generateSymbolName(function, res.ref.name), new Type("Any"))
                      val funcRef = new CanOperatorDef(Operator.functionRef(funcRefRet, "SWAN.CLLocationManager.setDistanceFilter"), None)
                      function.symbolTable.putOp(funcRefRet, funcRef.operator)
                      val args = ArrayBuffer(apply.arguments(1), fieldReadRet.ref)
                      val newApply = new CanOperatorDef(Operator.apply(apply.result, funcRefRet.ref, args, None), opDef.position)
                      function.symbolTable.replace(apply.result.ref.name, SymbolTableEntry.operator(apply.result, newApply.operator))
                      b.operators.remove(opIdx)
                      b.operators.insert(opIdx, newApply)
                      b.operators.insert(opIdx, funcRef)
                      b.operators.insert(opIdx, readDouble)
                    } else if (name == "#CLLocationManager.desiredAccuracy!setter.foreign") {
                      val funcRefRet = new Symbol(generateSymbolName(function, res.ref.name), new Type("Any"))
                      val funcRef = new CanOperatorDef(Operator.functionRef(funcRefRet, "SWAN.CLLocationManager.setDesiredAccuracy"), None)
                      function.symbolTable.putOp(funcRefRet, funcRef.operator)
                      val args = ArrayBuffer(apply.arguments(1), apply.arguments(0))
                      val newApply = new CanOperatorDef(Operator.apply(apply.result, funcRefRet.ref, args, None), opDef.position)
                      function.symbolTable.replace(apply.result.ref.name, SymbolTableEntry.operator(apply.result, newApply.operator))
                      b.operators.remove(opIdx)
                      b.operators.insert(opIdx, newApply)
                      b.operators.insert(opIdx, funcRef)
                    }
                  }
                  case _ =>
                }
              }
              case _ =>
            }
          }
          case _ =>
        }
        opIdx += 1
      }
    })
  }

  /** Does a simple escape analysis to convert pointers to variables. */
  private def pointerManager(): Unit = {
    val allNewws = mutable.HashSet.empty[Symbol]
    val pointers = mutable.HashSet.empty[SymbolRef]
    val escaped = mutable.HashSet.empty[SymbolRef]
    function.blocks.foreach{ b =>
      b.operators.foreach{ canOperatorDef => canOperatorDef.operator match {
        case o@Operator.neww(result, allocType) => {
          allNewws.add(o.value)
        }
        case Operator.fieldRead(result, alias, obj, field, pointer) if pointer =>
          pointers.add(obj)
        case Operator.fieldWrite(value, obj, field, Some(FieldWriteAttribute.pointer)) =>
          escaped.add(value)
          pointers.add(obj)
        case Operator.fieldWrite(value, obj, field, Some(FieldWriteAttribute.weakPointer)) =>
          escaped.add(value)
          pointers.add(obj)
        case Operator.fieldWrite(value, obj, field, attr) =>
          escaped.add(value)
        case o: Operator.condFail =>
        case o: Operator.assign =>
          escaped.add(o.from)
        case Operator.fieldRead(result, alias, obj, field, pointer) =>
        case o: Operator.literal =>
        case o: Operator.dynamicRef =>
        case o: Operator.builtinRef =>
        case o: Operator.functionRef =>
        case Operator.apply(result, functionRef, args, functionType) =>
          args.foreach(escaped.add)
        case o: Operator.singletonRead =>
        case Operator.singletonWrite(value, tpe, field) =>
          escaped.add(value)
        case _ =>
          throw new RuntimeException("Unexpected Operator in escape analysis")
      }
      }
    }
    val unescapedPointers = allNewws.filter(s => pointers.contains(s.ref) && !escaped.contains(s.ref))
    val unescapedPointerRefs = mutable.HashMap.empty[SymbolRef,Symbol]
    unescapedPointers.foreach(s => unescapedPointerRefs.addOne(s.ref,s))
    function.blocks.foreach(b => {
      var opIdx = 0
      while (opIdx < b.operators.length) {
        val opDef = b.operators(opIdx)
        opDef.operator match {
          case Operator.fieldRead(result, alias, obj, field, pointer) if (pointer && unescapedPointerRefs.contains(obj)) =>
            val newAssign = new CanOperatorDef(Operator.assign(result,obj,Some(AssignType.PointerRead())), opDef.position)
            b.operators.remove(opIdx)
            b.operators.insert(opIdx, newAssign)
          case Operator.fieldWrite(value, obj, field, Some(FieldWriteAttribute.pointer)) if (unescapedPointerRefs.contains(obj)) =>
            val newAssign = new CanOperatorDef(Operator.assign(unescapedPointerRefs(obj),value,Some(AssignType.PointerWrite())), opDef.position)
            b.operators.remove(opIdx)
            b.operators.insert(opIdx, newAssign)
          case Operator.fieldWrite(value, obj, field, Some(FieldWriteAttribute.weakPointer)) if (unescapedPointerRefs.contains(obj)) =>
            val newAssign = new CanOperatorDef(Operator.assign(unescapedPointerRefs(obj),value,Some(AssignType.PointerWrite())), opDef.position)
            b.operators.remove(opIdx)
            b.operators.insert(opIdx, newAssign)
          case _ =>
        }
        opIdx += 1
      }
    })
  }

  // ...

}
