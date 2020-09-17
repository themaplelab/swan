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

import ca.ualberta.maple.swan.parser.{SILArgument, SILDeclRef, SILOperand, SILResult, SILSourceInfo, SILTupleElements, SILType}

object Utils {

  // ************* TYPE CONVERSION *************

  // Type conversions should remove conventions.

  // SIL $T to SWANIR $T
  def SILTypeToType(tpe: SILType): Type = {
    new Type(tpe.toString) // TODO: Temp
  }

  // SIL $T to SWANIR $*T
  def SILTypeToPointerType(tpe: SILType): Type = {
    new Type(tpe.toString) // TODO: Temp
  }

  // SIL $*T to SWANIR $T
  def SILPointerTypeToType(tpe: SILType): Type = {
    new Type(tpe.toString) // TODO: Temp
  }

  // SIL "0x3F800000" to float
  def SILFloatStringToFloat(float: String): Float = {
    return 0 // TODO: Temp
  }

  // SIL $(T...), 123 to SWANIR type of the selected element using "123"
  // pointer specifies whether the type is a pointer (tuple_element_addr case)
  def SILTupleTypeToType(tupleType: SILType, index: Int, pointer: Boolean): Type = {
    new Type(tupleType.toString) // TODO: Temp
  }

  def SILStructFieldDeclRefToString(declRef: SILDeclRef): String = {
    "" // TODO: Temp
  }

  def SILTupleElementsToType(elements: SILTupleElements): Type = {
    elements match {
      case SILTupleElements.labeled(tpe: SILType, values: Array[String]) => {
        SILTypeToType(tpe)
      }
      case SILTupleElements.unlabeled(operands: Array[SILOperand]) => {
        new Type("") // TODO: Temp
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

}