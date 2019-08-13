//===--- Structures.hpp - Information across translation -----------------===//
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
/// This file defines various Info structures used by the translator and
/// WALAInstance to keep track of information across functions, blocks, etc.
///
//===---------------------------------------------------------------------===//

#ifndef SWAN_STRUCTURES_HPP
#define SWAN_STRUCTURES_HPP

#include "CAstWrapper.h"
#include "swift/SIL/ApplySite.h"
#include "swift/SIL/SILVisitor.h"
#include <jni.h>
#include <unordered_map>
#include <unordered_set>

namespace swan {

enum SILSourceType {INVALID, STARTONLY, FULL};

struct SILModuleInfo;
struct SILFunctionInfo;
struct SILInstructionInfo;
struct WALACAstEntityInfo;
class ValueTable;

/// SILModuleInfo is used for storing source information into the CAst.
struct SILModuleInfo {
  explicit SILModuleInfo(llvm::StringRef const &sourcefile) : sourcefile(sourcefile) {}
  llvm::StringRef const sourcefile;
};

/// SILFunctionInfo is used for storing source information into the CAst.
struct SILFunctionInfo {
  SILFunctionInfo(llvm::StringRef const &name, llvm::StringRef const &demangled) : name(name), demangled(demangled) {}
  llvm::StringRef const name;
  llvm::StringRef const demangled;
  short srcType = SILSourceType::INVALID;
};

/// SILInstructionInfo is used for storing source information into the CAst.
struct SILInstructionInfo {
  unsigned num;
  swift::SILPrintContext::ID id;
  swift::SILInstructionKind instrKind;
  swift::SILInstruction::MemoryBehavior memBehavior;
  swift::SILInstruction::ReleasingBehavior relBehavior;
  short srcType = SILSourceType::INVALID;
  std::string Filename;
  unsigned startLine;
  unsigned startCol;
  unsigned endLine;
  unsigned endCol;
  llvm::ArrayRef<void *> ops;
  SILModuleInfo *modInfo;
  SILFunctionInfo *funcInfo;
};

/// WALACAstEntityInfo is used to later build the CAstEntities. The translator populates this struct to avoid
/// traversing the whole AST later to build the scopedEntities map and create the CAstControlFlowMap.
struct WALACAstEntityInfo {
  std::string functionName; // This should be "main" for the SCRIPT_ENTITY.
  std::vector<jobject> basicBlocks; // Every basic block belonging to this entity.
  std::string returnType; // Return type of the function as a string. e.g. "@convention(thin) (Int) -> Int"
  std::vector<std::string> argumentTypes; // Vector of argument type names of the function.
  std::vector<std::string> argumentNames; // Vector of argument names corresponding to those referenced in the AST.
  jobject CAstSourcePositionRecorder; // Maps CAstNodes to source information.
  jobject functionPosition = nullptr; // The Position of the function (for `getNamePosition()`).
  std::vector<jobject> argumentPositions; // The Positions of the function arguments (for `getPosition(int arg)`);

  void print() {
    llvm::outs() << "\n\n" << "-*- CAST ENTITY INFO -*-" << "\n";
    llvm::outs() << "\tFUNCTION NAME: " << functionName << "\n";
    // If we print the blocks using CAstWrapper, they won't print where expected to the terminal.
    // There is probably a way to solve this but is not necessary for now.
    llvm::outs() << "\t# OF BASIC BLOCKS: " << basicBlocks.size() << "\n";
    llvm::outs() << "\tRETURN TYPE: " << returnType << "\n";
    for (auto argType: argumentTypes) {
      llvm::outs() << "\tARGUMENT TYPE: " << argType << "\n";
    }
    for (auto argName: argumentNames) {
      llvm::outs() << "\tARGUMENT NAME: " << argName << "\n";
    }
    llvm::outs() << "=*= CAST ENTITY INFO =*=" << "\n\n";
  }
};

inline std::string labelBasicBlock(swift::SILBasicBlock* const basicBlock) {
    return (std::string("BLOCK #") + std::to_string(basicBlock->getDebugID()));
  }

inline std::string addressToString(void* const a) {
  char buff[80];
  std::sprintf(buff, "%p", a);
  return buff;
}

class ValueTable {
public:
  ValueTable(CAstWrapper *wrapper) : wrapper(wrapper) {}
  bool has(void* key) const {
    return ((symbols.find(key) != symbols.end()) || (nodes.find(key) != nodes.end()));
  }
  jobject get(void* const key) {
    if (isSymbol(key)) {
      return std::get<0>(symbols.at(key));
    } else if (isNode(key)) {
      return nodes.at(key);
    } else {
      createAndAddSymbol(key, "Unimplemented");
      llvm::outs() << "\t DEBUG: Requested key (" << key << ") not found.\n";
      return get(key);
      // llvm::outs() << "\t ERROR: Requested key (" << key << ") not found. Fatal, exiting...";
      // exit(1);
    }
  }
  void createAndAddSymbol(void* const key, std::string const &type) {
    jobject Var = wrapper->makeNode(CAstWrapper::VAR, wrapper->makeConstant(addressToString(key).c_str()));
    addSymbol(key, Var, type);
  }
  void addSymbol(void* const key, jobject const symbol, std::string const &type) {
    if (symbols.find(key) == symbols.end()) {
      symbols.insert({key, std::make_tuple(symbol, type)});
      declNodes.push_front(wrapper->makeNode(CAstWrapper::DECL_STMT,
        wrapper->makeConstant(addressToString(key).c_str()), wrapper->makeConstant(type.c_str())));
    } else {
      llvm::outs() << "\t WARNING: Attempted to re-add symbol to ValueTable: " << key << "\n";
    }
  }
  void duplicate(void* const source, void* const target) {
    if (symbols.find(source) != symbols.end()) {
      symbols.insert({target, symbols.at(source)});
    } else {
      /* DEBUG */
      createAndAddSymbol(source, "Unimplemented");
      symbols.insert({target, symbols.at(source)});
      // llvm::outs() << "\t ERROR: Requested key (" << source << ") not found while duplicating. Fatal, exiting...";
      // exit(1);
    }
  }
  void addNode(void* const key, jobject const node) {
    if (nodes.find(key) == nodes.end()) {
      nodes.insert({key, node});
    } else {
      llvm::outs() << "\t WARNING: Attempted to re-add node to ValueTable: " << key << "\n";
    }
  }
  void clearNodes() {
    nodes.clear();
  }
  void clearSymbols() {
    symbols.clear();
  }
  void clearDeclNodes() {
    declNodes.clear();
  }
  void remove(void* const key) {
    if (isSymbol(key)) {
      symbols.erase(symbols.find(key));
    } else if (isNode(key)) {
      nodes.erase(nodes.find(key));
    } else {
      llvm::outs() << "\t WARNING: Attempted to remove key (" << key << ") which does not exist.";
    }
  }
  bool tryRemove(void* const key) {
    if (isSymbol(key)) {
      symbols.erase(symbols.find(key));
      return true;
    } else if (isNode(key)) {
      nodes.erase(nodes.find(key));
      return true;
    }
    return false;
  }
  void copySymbol(void* const src, void* const dest) {
    /* DEBUG */ // assert(has(src));
    if (!has(src)) {
      createAndAddSymbol(src, "Unimplemented");
    }
    jobject Var = wrapper->makeNode(CAstWrapper::VAR, wrapper->getNthChild(get(src), 0));
    addSymbol(dest, Var, std::get<1>(symbols.at(src)));
  }
  bool isSymbol(void* const key) const {
    return symbols.find(key) != symbols.end();
  }
  bool isNode(void* const key) const {
    return nodes.find(key) != nodes.end();
  }
  std::list<jobject> getDeclNodes() const {
    return declNodes;
  }
private:
  CAstWrapper *wrapper;
  std::unordered_map<void*, std::tuple<jobject, std::string>> symbols;
  std::unordered_map<void*, jobject> nodes;
  std::list<jobject> declNodes;
};


} // end swan namespace

#endif // SWAN_STRUCTURES_HPP
