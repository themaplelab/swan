#include "swift-wala/WALASupport/SymbolTable.h"
#include <cstdio>

using namespace swift_wala;

bool SymbolTable::has(void* key) {
	return table.find(key) != table.end();
}

string SymbolTable::get(void* key) {
	if (has(key)) {
		return table.at(key);
	} else {
		return "";
	}
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
