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

import ca.ualberta.maple.swan.parser.{SILArgument, SILDeclRef, SILOperand, SILPrinter, SILResult, SILSourceInfo, SILTupleElements, SILType}

object Utils {

  // ************* TYPE CONVERSION *************

  // Type conversions should remove conventions.

  def printer: SILPrinter = {
    new SILPrinter()
  }

  // SIL $T to SWANIR $T
  def SILTypeToType(rootTpe: SILType): Type = {
    new Type(printer.naked(rootTpe))
  }

  // SIL $T to SWANIR $*T
  def SILTypeToPointerType(rootTpe: SILType): Type = {
    new Type(printer.naked(SILType.addressType(rootTpe)))
  }

  // SIL $*T to SWANIR $T
  def SILPointerTypeToType(tpe: SILType): Type = {
    assert(tpe.isInstanceOf[SILType.addressType])
    new Type(printer.naked(tpe.asInstanceOf[SILType.addressType].tpe))
  }

  // SIL "0x3F800000" to float
  def SILFloatStringToFloat(float: String): Float = {
    return 0 // TODO: Temp
  }

  // SIL $(T...), 123 to SWANIR type of the selected element using "123"
  // pointer specifies whether the type is a pointer (tuple_element_addr case)
  def SILTupleTypeToType(tpe: SILType, index: Int, pointer: Boolean): Type = {
    def getTypeAtIndex(parameters: Array[SILType]): Type = {
      assert(parameters.length - 1 >= index)
      val param = parameters(index)
      def makeType(tpe: SILType, pointer: Boolean): Type = {
        if (pointer) {
          new Type(printer.naked(SILType.addressType(tpe)))
        } else {
          new Type(printer.naked(parameters(index)))
        }
      }
      param match {
        case SILType.namedArgType(_, tpe) => return makeType(tpe, pointer)
        case _ => return makeType(param, pointer)
      }
    }
    tpe match {
      case SILType.addressType(tpe) => {
        tpe match {
          case SILType.tupleType(parameters, _) => return getTypeAtIndex(parameters)
          case _ => assert(false, "Underlying type should be tuple type"); null
        }
      }
      case SILType.tupleType(parameters, _) => return getTypeAtIndex(parameters)
      case _ => assert(false, "Address type or tuple type expected"); null
    }
  }

  def SILStructFieldDeclRefToString(declRef: SILDeclRef): String = {
    assert(declRef.name.length >= 2)
    declRef.name.last // Assume last is fine for now
  }

  def SILTupleElementsToType(elements: SILTupleElements): Type = {
    elements match {
      case SILTupleElements.labeled(tpe: SILType, values: Array[String]) => {
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

  def SILArgumentToArgument(arg: SILArgument): Argument = {
    new Argument(arg.valueName, SILTypeToType(arg.tpe))
  }

  // Note: Source position is lackluster right now (e.g. columns).
  def SILSourceInfoToPosition(info: Option[SILSourceInfo]): Option[Position] = {
    if (info.isEmpty) return None
    val src = info.get
    if (src.loc.isEmpty) return None
    val loc = src.loc.get
    Some(new Position(loc.path, loc.line, loc.column))
  }

  def print(tpe: SILType): String = {
    printer.naked(tpe);
  }

}