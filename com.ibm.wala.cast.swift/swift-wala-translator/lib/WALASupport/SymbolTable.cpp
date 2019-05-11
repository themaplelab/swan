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
 *****************************************************************************/

 // SEE HEADER FILE FOR DOCUMENTATION

#include "swift-wala/WALASupport/SymbolTable.h"
#include <cstdio>
#include <iostream>
#include <cassert>

using namespace swift_wala;

bool SymbolTable::has(void* key) {
	return table.find(key) != table.end();
}

string SymbolTable::get(void* key) {
	assert(has(key) && "SymbolTable - Invalid key requested!");
	return table.at(key);
}

void SymbolTable::insert(void* key, const string& name) {
	char buff[80]; // enough for 64 bit address
	std::sprintf(buff, "%p", key);
	string varName = name + "_" + buff;
	table.insert(std::make_pair(key, varName));
}

void SymbolTable::duplicate(void* key, const string& name) {
	table.insert(std::make_pair(key, name));
}

bool SymbolTable::remove(void* key) {
	if (has(key)) {
		table.erase(key);
		return true;
	}
	return false;
}
