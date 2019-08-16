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

struct RootInstructionInfo {
  RootInstructionInfo(CAstWrapper *wrapper) : wrapper(wrapper) {}
  CAstWrapper *wrapper;
  std::string instructionName;
  jobject instructionSourceInfo;
  std::list<jobject> properties;

  void setInstructionSourceInfo(unsigned int fl, unsigned int fc, unsigned int ll, unsigned int lc) {
    instructionSourceInfo = wrapper->makeLocation(static_cast<int>(fl), static_cast<int>(fc),
      static_cast<int>(ll), static_cast<int>(lc));
  }

  void addProperties(std::list<jobject> givenProperties) {
    for (jobject p : givenProperties) {
      properties.push_back(p);
    }
  }

  void addProperty(jobject property) {
    properties.push_back(property);
  }

  jobject make() {
    /*
     *  PRIMITIVE
     *    NAME
     *    JOBJECT <-- LOCATION
     *    PRIMITIVE
     *      ... <-- ANY PROPERTIES
     */
    return wrapper->makeNode(CAstWrapper::PRIMITIVE,
      wrapper->makeConstant(instructionName.c_str()),
      wrapper->makeConstant(instructionSourceInfo),
      wrapper->makeNode(
        CAstWrapper::PRIMITIVE, wrapper->makeArray(&properties)));
  }
};

/// Contains raw basic block information.
struct RootBasicBlockInfo {
  RootBasicBlockInfo(CAstWrapper *wrapper) : wrapper(wrapper) {}
  CAstWrapper *wrapper;
  std::list<jobject> instructions;

  void addInstruction(RootInstructionInfo *instruction) {
    instructions.push_back(instruction->make());
  }

  void addInstruction(jobject instruction) {
    instructions.push_back(instruction);
  }

  jobject make() {
    /*
     *  PRIMITIVE
     *    PRIMTIVE <-- INSTRUCTION
     *    ...
     */
    return wrapper->makeNode(CAstWrapper::PRIMITIVE, wrapper->makeArray(&instructions));
  }
};

/// Contains raw function information.
struct RootFunctionInfo {
  RootFunctionInfo(CAstWrapper *wrapper) : wrapper(wrapper) {}
  CAstWrapper *wrapper;
  std::string functionName;
  std::string returnType;
  jobject functionSourceInfo;
  std::list<jobject> arguments;
  std::list<jobject> blocks;

  void setFunctionSourceInfo(unsigned int fl, unsigned int fc, unsigned int ll, unsigned int lc) {
    functionSourceInfo = wrapper->makeLocation(static_cast<int>(fl), static_cast<int>(fc),
      static_cast<int>(ll), static_cast<int>(lc));
  }

  void addArgument(std::string name, std::string type, unsigned int fl, unsigned int fc, unsigned int ll, unsigned int lc) {
    jobject argumentSourceInfo = wrapper->makeLocation(static_cast<int>(fl), static_cast<int>(fc),
      static_cast<int>(ll), static_cast<int>(lc));
    jobject newArgument = wrapper->makeNode(CAstWrapper::PRIMITIVE,
      wrapper->makeConstant(name.c_str()), wrapper->makeConstant(type.c_str()),
      wrapper->makeConstant(argumentSourceInfo));
    arguments.push_back(newArgument);
  }

  void addBlock(RootBasicBlockInfo *basicBlock) {
    blocks.push_back(basicBlock->make());
  }

  void addBlock(std::list<jobject> instructions) {
    blocks.push_back(wrapper->makeNode(CAstWrapper::PRIMITIVE, wrapper->makeArray(&instructions)));
  }

  jobject make() {
    /*
     *  PRIMITIVE
     *    NAME
     *    TYPE
     *    JOBJECT <-- LOCATION
     *    PRIMITIVE
     *      PRIMTIVE <-- ARGUMENT
     *      ...
     *    PRIMITIVE
     *      PRIMITIVE <-- BLOCK
     *      ...
     */
    return wrapper->makeNode(CAstWrapper::PRIMITIVE,
      wrapper->makeConstant(functionName.c_str()),
      wrapper->makeConstant(returnType.c_str()),
      wrapper->makeConstant(functionSourceInfo),
      wrapper->makeNode(CAstWrapper::PRIMITIVE, wrapper->makeArray(&arguments)),
      wrapper->makeNode(CAstWrapper::PRIMITIVE, wrapper->makeArray(&blocks)));
  }
};

/// Contains all raw function information.
struct RootModuleInfo {
  RootModuleInfo(CAstWrapper *wrapper) : wrapper(wrapper) {}
  CAstWrapper *wrapper;
  std::list<jobject> functions;

  void addFunction(RootFunctionInfo *function) {
    functions.push_back(function->make());
  }

  void addFunction(jobject function) {
    functions.push_back(function);
  }

  jobject make() {
    /*
     *  PRIMITIVE
     *    PRIMTIVE <-- FUNCTION
     *    ...
     */
    return wrapper->makeNode(CAstWrapper::PRIMITIVE, wrapper->makeArray(&functions));
  }
};

inline std::string addressToString(void* const a) {
  char buff[80];
  std::sprintf(buff, "%p", a);
  return buff;
}

} // end swan namespace

#endif // SWAN_STRUCTURES_HPP
