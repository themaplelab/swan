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

#include <jni.h>
#include <string>
#include <sstream>
#include <cstring>
#include <memory>
#include <vector>

#include "InfoStructures.hpp"
#include "swift/SIL/SILModule.h"

class CAstWrapper;

namespace swift_wala {

/// This class serves as a bridge between the JNI bridge and the
/// SILWalaInstructionVisitor. It is effectively the framework's
/// (C++ side) data and call hub.
class WALAInstance {
private:
  JNIEnv *JavaEnv; // JVM.
  jobject Translator; // Java translator object.
  std::string File; // Swift file to analyze.
  std::vector<std::unique_ptr<CAstEntityInfo>> castEntities; // Entity info needed to make the CAstEntities on the Java side.

public:
  CAstWrapper *CAst; // For handling JNI calls (WALA).
  std::vector<jobject> CAstNodes; // Translated nodes (CAst Blocks).

  explicit WALAInstance(JNIEnv* Env, jobject Obj);

  /// Converts C++ string to Java BigDecimal, and is used by the
  /// SILWalaInstructionVisitor.
  jobject makeBigDecimal(const char *strData, int strLen);

  /// Returns copy of translated nodes as a jobject (ArrayList<CastNode>).
  jobject getCAstNodes();

  /// Used for debugging jobjects.
  void print(jobject Object);

  /// Used for debugging CAst nodes, as jobjects. Not synchronous with llvm::outs()!
  void printNode(jobject Node);

  /// Starts the analysis, and hooks into the Swift compiler frontend.
  void analyze();

  /// Callback method from the Observer hook. It visits the given SIL module
  /// and will put the result back into the instance.
  void analyzeSILModule(swift::SILModule &SM);

  /// Add the translated entity to the instance to later pass to the Java side.
  void addCAstEntityInfo(std::unique_ptr<CAstEntityInfo> entity);

  /// Returns ArrayList<CAstEntityInfo> as jobject
  jobject getCAstEntityInfo();

  /// Used to turn fields of CAstEntityInfo into ArrayList<CAstNode> as jobject
  jobject getCAstNodesOfEntityInfo(const std::vector<jobject> &nodes);
};

} // end swift_wala namespace

#endif // SWAN_WALAINSTANCE_H