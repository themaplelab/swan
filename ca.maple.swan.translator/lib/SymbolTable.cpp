//===--- SymbolTable.cpp - Symbol table data structure -------------------===//
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
/// This file implements the data structure (a wrapped unordered_map) used by
/// the SILWalaInstructionVisitor as a utility.
///
//===---------------------------------------------------------------------===//

#include "SymbolTable.h"
#include <cassert>
#include <cstdio>

using namespace swan;

bool SymbolTable::has(void* key) {
  return nameTable.find(key) != nameTable.end();
}

bool SymbolTable::hasType(void* key) {
  return typeTable.find(key) != typeTable.end();
}

std::string SymbolTable::get(void* key) {
  assert(has(key) && "NameTable - Invalid key requested!");
  return nameTable.at(key);
}

std::string SymbolTable::getType(void* key) {
  assert(has(key) && "TypeTable - Invalid key requested!");
  return typeTable.at(key);
}

void SymbolTable::insert(void* key, const std::string& type, const std::string& name) {
  char buff[80]; // Enough for 64 bit address.
  std::sprintf(buff, "%p", key);
  std::string varName;
  if (name != "") {
    varName = name + "_" + buff;
  } else {
    varName = buff;
  }
  nameTable.insert(std::make_pair(key, varName));
  typeTable.insert(std::make_pair(key, type));
}

void SymbolTable::duplicate(void* key, const std::string& type, const std::string& name) {
  nameTable.insert(std::make_pair(key, name));
  typeTable.insert(std::make_pair(key, type));
}

bool SymbolTable::remove(void* key) {
  if (has(key)) {
    nameTable.erase(key);
    typeTable.erase(key);
    return true;
  }
	return false;
}
