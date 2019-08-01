//===--- BuiltinFunctions.hpp - Defines builtin functions -----------------==//
//
// This source file is part of the SWAN open source project
//
// Copyright (c) 2019 Maple @ University of Alberta
// All rights reserved. This program and the accompanying materials (unless
// otherwise specified by a license inside of the accompanying material)
// are made available under the terms of the Eclipse Public License v2.0
// which accompanies this distribution, and is available at
// http://www.eclipse.org/legal/epl-v20.html
//
//===---------------------------------------------------------------------===//
///
/// This file defines built in functions in order for the translator to
/// be able to identify built in functions for CONSTANT nodes.
///
//===---------------------------------------------------------------------===//

#ifndef SWAN_BUILTINFUNCTIONS_HPP
#define SWAN_BUILTINFUNCTIONS_HPP

#include <string>
#include <unordered_set>

namespace swan {

static std::unordered_set<std::string> builtinFunctions({
  "Swift.Int.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.Int",
  "Swift.String.init(_builtinStringLiteral: Builtin.RawPointer, utf8CodeUnitCount: Builtin.Word, isASCII: Builtin.Int1) -> Swift.String",
  "Swift.Bool.init(_builtinBooleanLiteral: Builtin.Int1) -> Swift.Bool",
  "default argument 1 of Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()",
  "Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()",
  "default argument 2 of Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()",
  "Swift._allocateUninitializedArray<A>(Builtin.Word) -> (Swift.Array<A>, Builtin.RawPointer)",
  "static Swift.Int.- infix(Swift.Int, Swift.Int) -> Swift.Int",
  "static Swift.Int.+ infix(Swift.Int, Swift.Int) -> Swift.Int"

  // Add more builtin functions here...
});

} // end swan namespace

#endif