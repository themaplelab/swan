/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.ir.raw

import ca.ualberta.maple.swan.ir.Exceptions.{UnexpectedSILFormatException, UnexpectedSILTypeBehaviourException}
import ca.ualberta.maple.swan.ir.{Position, Type}
import ca.ualberta.maple.swan.parser._

import scala.collection.mutable.ArrayBuffer

object Utils {

  // Type conversions should remove conventions.

  def printer: SILPrinter = {
    new SILPrinter()
  }

  // SIL $T to SWIRL $T
  def SILTypeToType(rootTpe: SILType): Type = {
    if (rootTpe.isInstanceOf[SILType.withOwnership]) {
      new Type(printer.print(rootTpe))
    } else {
      new Type(printer.naked(rootTpe))
    }
  }

  // SIL $T to SWIRL $*T
  def SILTypeToPointerType(rootTpe: SILType): Type = {
    new Type(printer.naked(SILType.addressType(rootTpe)))
  }

  def getFunctionTypeFromType(rootTpe: SILType): SILType.functionType = {
    var curType = rootTpe
    def unexpected(): Nothing = {
      throw new UnexpectedSILFormatException("Unexpected SIL function type")
    }
    // Not all handled types are necessarily expected.
    while (!curType.isInstanceOf[SILType.functionType]) {
      curType match {
        case SILType.addressType(tpe) => curType = tpe
        case SILType.attributedType(_, tpe) => curType = tpe
        case SILType.coroutineTokenType => unexpected()
        case SILType.functionType(_, _, _, _) => unexpected()
        case SILType.genericType(_, _, tpe) => curType = tpe
        case SILType.namedType(_) => unexpected()
        case SILType.selectType(tpe, _) => curType = tpe
        case SILType.namedArgType(_, tpe, _) => curType = tpe
        case SILType.selfType => unexpected()
        case SILType.selfTypeOptional => unexpected()
        case SILType.specializedType(tpe, _, _) => curType = tpe
        case SILType.arrayType(_, _, _) => unexpected()
        case SILType.tupleType(_, _, _) => unexpected()
        case SILType.withOwnership(_, tpe) => curType = tpe
        case SILType.varType(tpe) => curType = tpe
        case _ => unexpected()
      }
    }
    curType.asInstanceOf[SILType.functionType]
  }

  def SILFunctionTypeToReturnType(rootTpe: SILType): Type = {
    val tpe = getFunctionTypeFromType(rootTpe)
    SILTypeToType(tpe.result)
  }

  def SILFunctionTupleTypeToReturnType(rootType: SILType, removeAttributes: Boolean): Array[Type] = {
    val silTypes = {
      val ft = getFunctionTypeFromType(rootType)
      ft.result match {
        case SILType.tupleType(parameters, _, _) => parameters
        case SILType.forType(tpe, _) => tpe.asInstanceOf[SILType.tupleType].parameters
        case _ => throw new UnexpectedSILTypeBehaviourException()
      }
    }
    val types = new Array[Type](silTypes.length)
    silTypes.zipWithIndex.foreach(t => {
      var tpe = t._1
      if (removeAttributes) {
        t._1 match {
          case SILType.attributedType(_, f) => tpe = f
          case _ =>
        }
      }
      types(t._2) = SILTypeToType(tpe)
    })
    types
  }

  def SILFunctionTypeToParamTypes(rootTpe: SILType): Array[Type] = {
    val tpe = getFunctionTypeFromType(rootTpe)
    val params = new ArrayBuffer[Type]()
    tpe.parameters.foreach(t => {
      params.append(SILTypeToType(t))
    })
    params.toArray
  }

  // SIL $*T to SWIRL $T
  @throws[UnexpectedSILTypeBehaviourException]
  def SILPointerTypeToType(tpe: SILType): Type = {
    if (!tpe.isInstanceOf[SILType.addressType]) {
      throw new UnexpectedSILTypeBehaviourException("Expected pointer type: " + print(tpe))
    }
    new Type(printer.naked(tpe.asInstanceOf[SILType.addressType].tpe))
  }

  // SIL "0x3F800000" to double
  def SILFloatStringToDouble(float: String): Double = {
    try { // TODO: Handle problematic cases. e.g., 0xC05C606583E8576D
      val i = java.lang.Long.parseLong(float, 16)
      java.lang.Double.longBitsToDouble(i)
    } catch {
      case _: Throwable => 0.0
    }
  }

  // SIL $(T...), 123 to SWIRL type of the selected element using "123"
  // pointer specifies whether the type is a pointer (tuple_element_addr case)
  @throws[UnexpectedSILTypeBehaviourException]
  @throws[UnexpectedSILFormatException]
  def SILTupleTypeToType(tpe: SILType, index: Int, pointer: Boolean): Type = {
    def getTypeAtIndex(parameters: Array[SILType]): Type = {
      if (parameters.length - 1 < index) {
        throw new UnexpectedSILFormatException("SIL tuple type " + print(tpe) +
          " should have enough parameter types for the index (" + index.toString + ")")
      }
      val param = parameters(index)
      def makeType(tpe: SILType, pointer: Boolean): Type = {
        if (pointer) {
          new Type(printer.naked(SILType.addressType(tpe)))
        } else {
          new Type(printer.naked(parameters(index)))
        }
      }

      makeType(param, pointer)
    }

    tpe match {
      case SILType.addressType(tpe) => {
        tpe match {
          case SILType.tupleType(parameters, _, _) => getTypeAtIndex(parameters)
          case _ => throw new UnexpectedSILTypeBehaviourException("Underlying type should be tuple type")
        }
      }
      case SILType.tupleType(parameters, _, _) => getTypeAtIndex(parameters)
      case _ => throw new UnexpectedSILTypeBehaviourException("Address type or tuple type expected")
    }
  }

  @throws[UnexpectedSILFormatException]
  def SILStructFieldDeclRefToString(declRef: SILDeclRef): String = {
    if (declRef.name.length < 2) { // #T.field[...]
      throw new UnexpectedSILFormatException("Expected struct field decl ref to have at least two components: " + print(declRef))
    }
    declRef.name.last // Assume last is fine for now
  }

  def SILTupleElementsToType(elements: SILTupleElements): Type = {
    elements match {
      case SILTupleElements.labeled(tpe: SILType, _) => {
        SILTypeToType(tpe)
      }
      case SILTupleElements.unlabeled(operands: Array[SILOperand]) => {
        if (operands.isEmpty) {
          return new Type("()")
        }
        val stringBuilder = new StringBuilder()
        stringBuilder.append("(")
        operands.foreach(operand => {
          stringBuilder.append(printer.naked(operand.tpe))
          if (operand != operands.last) {
            stringBuilder.append(", ")
          }
        })
        stringBuilder.append(")")
        new Type(stringBuilder.toString())
      }
    }
  }

  // Note: Source position is lackluster right now (e.g. columns).
  def SILSourceInfoToPosition(info: Option[SILSourceInfo]): Option[Position] = {
    if (info.isEmpty) return None
    val src = info.get
    if (src.loc.isEmpty) return None
    val loc = src.loc.get
    if (loc.path == "<compiler-generated>") {
      None
    } else {
      Some(new Position(loc.path, loc.line, loc.column))
    }
  }

  def print(tpe: SILType): String = {
    printer.naked(tpe)
  }

  def print(declRef: SILDeclRef): String = {
    printer.print(declRef)
  }
}
