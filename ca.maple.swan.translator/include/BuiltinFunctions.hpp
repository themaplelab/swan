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
  "Swift.Int.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.Int"
  // Add more builtin functions here...
});

} // end swan namespace

#endif