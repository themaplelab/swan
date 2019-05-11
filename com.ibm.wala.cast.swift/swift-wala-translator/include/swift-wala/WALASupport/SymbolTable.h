/******************************************************************************
 * Copyright (c) 2019 Maple @ University of Alberta
 * All rights reserved. This program and the accompanying materials (unless
 * otherwise specified by a license inside of the accompanying material)
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *    Mark Mroz - initial implementation
 *    Daniil Tiganov (Github: tiganov) - documentation, QC changes
 *****************************************************************************/

 //----------------------------------------------------------------------------/
 /// DESCRIPTION
 /// SymbolTable is an interface for an unordered_map used as a utility
 /// by the SILWalaInstructionVisitor.
 //----------------------------------------------------------------------------/

#pragma once

#include <string>
#include <unordered_map>

using std::string;
using std::unordered_map;

namespace swift_wala {

class SymbolTable {
public:
  bool has(void* key);
  string get(void* key);
  void insert(void* key, const string& name);
  void duplicate(void* key, const string& name);
  bool remove(void* key);
private:
  unordered_map<void*, string> table;
};

} // end swift_wala namespace
