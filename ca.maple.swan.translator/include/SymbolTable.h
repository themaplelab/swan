//===--- SymbolTable.h - Symbol table data structure ---------------------===//
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
/// This file defines the data structure (a wrapped unordered_map) used by
/// the SILWalaInstructionVisitor as a utility for keeping track of
/// allocation sites (mapped to a name for the symbol).
///
//===---------------------------------------------------------------------===//

#ifndef SWAN_SYMBOLTABLE_H
#define SWAN_SYMBOLTABLE_H

#include <string>
#include <unordered_map>

namespace swan {

/// This class is used as a data structure utility by the
/// SILWalaInstructionVisitor to keep track of allocation sites.
class SymbolTable {
public:
  bool has(void* key);
  std::string get(void* key);
  void insert(void* key, const std::string& name = std::string(""));
  void duplicate(void* key, const std::string& name);
  bool remove(void* key);
private:
  std::unordered_map<void*, std::string> table;
};

} // end swan namespace

#endif // SWAN_SYMBOLTABLE_H