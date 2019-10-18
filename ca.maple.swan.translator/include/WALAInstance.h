//===--- WALAInstance.h - Class that bridges translator and JNI ----------===//
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
/// This file defines the 'hub' class that calls the Swift compiler frontend,
/// and uses the hook to call the SILWalaInstructionVisitor on the SILModule.
///
//===---------------------------------------------------------------------===//

#ifndef SWAN_WALAINSTANCE_H
#define SWAN_WALAINSTANCE_H

#include "InfoStructures.hpp"
#include <jni.h>
#include <string>
#include <sstream>
#include <cstring>
#include <memory>
#include <vector>
#include "swift/SIL/SILModule.h"

class CAstWrapper;

namespace swan {

/// This class serves as a bridge between the JNI bridge and the
/// SILWalaInstructionVisitor. It is effectively the framework's
/// (C++ side) data and call hub.
class WALAInstance {
private:
  /// JVM.
  JNIEnv *JavaEnv;
  /// Java translator object.
  jobject Translator;
  /// Since even with multi-file compilation we only have a single
  /// SILModule, we have to group SILFunctions by their source file.
  std::unordered_map<std::string, std::list<jobject>> mappedRoots;

public:
  CAstWrapper *CAst; // For handling JNI calls (WALA).

  explicit WALAInstance(JNIEnv* Env, jobject Obj, jobject args);

  /// Converts C++ string to Java BigDecimal, and is used by the
  /// SILWalaInstructionVisitor.
  jobject makeBigDecimal(const char *strData, int strLen);

  /// Used for debugging CAst nodes, as jobjects. Not synchronous with llvm::outs()!
  void printNode(jobject Node);

  /// Starts the analysis, and hooks into the Swift compiler frontend.
  void analyze(const std::list<string> args);

  /// Returns the root nodes containing the information for each file.
  jobject getRoots();

  /// Callback method from the Observer hook. It visits the given SIL module
  /// and will put the result back into the instance.
  void analyzeSILModule(swift::SILModule &SM);

  /// Sets the source URL/filename on the Java side so that locations are valid.
  void setSource(std::string url);

  void addSourceFunction(std::string file, jobject function) {
    mappedRoots[file].push_back(function);
  }
};

} // end swan namespace

#endif // SWAN_WALAINSTANCE_H
