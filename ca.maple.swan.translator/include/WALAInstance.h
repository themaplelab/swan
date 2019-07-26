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

namespace swan {

/// This class serves as a bridge between the JNI bridge and the
/// SILWalaInstructionVisitor. It is effectively the framework's
/// (C++ side) data and call hub.
class WALAInstance {
private:
  JNIEnv *JavaEnv; // JVM.
  jobject Translator; // Java translator object.
  std::string File; // Swift file to analyze.
  std::vector<std::unique_ptr<CAstEntityInfo>> castEntities; // Entity info needed to make the CAstEntities on the Java side.
  jobject CurrentCAstSourcePositionRecorder = nullptr;

public:
  CAstWrapper *CAst; // For handling JNI calls (WALA).
  std::vector<jobject> CAstNodes; // Translated nodes (CAst Blocks).

  explicit WALAInstance(JNIEnv* Env, jobject Obj);

  /// Converts C++ string to Java BigDecimal, and is used by the
  /// SILWalaInstructionVisitor.
  jobject makeBigDecimal(const char *strData, int strLen);

  /// Returns copy of translated nodes as a jobject (ArrayList<CastNode>).
  jobject getCAstNodes();

  /// Used for debugging CAst nodes, as jobjects. Not synchronous with llvm::outs()!
  void printNode(jobject Node);

  /// Starts the analysis, and hooks into the Swift compiler frontend.
  void analyze();

  /// Callback method from the Observer hook. It visits the given SIL module
  /// and will put the result back into the instance.
  void analyzeSILModule(swift::SILModule &SM);

  /// Add the translated entity to the instance to later pass to the Java side.
  void addCAstEntityInfo(std::unique_ptr<CAstEntityInfo> entity);

  /// Returns ArrayList<CAstEntityInfo> as jobject.
  jobject getCAstEntityInfo();

  /// Converts C++ std::vector<jobject> to Java ArrayList.
  jobject vectorToArrayList(const std::vector<jobject> &v);

  /// Used to turn the std::vector<std::string> of argument types to ArrayList<String> as jobject.
  jobject getArgumentTypesOfEntityInfo(const std::vector<std::string> &argumentTypes);

  /// Converts a given C++ map to a Java LinkedHashMap and returns it as jobject.
  jobject mapToLinkedHashMap(const std::map<jobject, std::string> &map);

  /// Creates a CAstSourcePositionRecorder object and returns it as jobject. Every CAstEntity needs one.
  void createCAstSourcePositionRecorder();

  /// Calls setPosition on the CurrentCAstSourcePositionRecorder using the given info and CAstNode.
  void addSourceInfo(jobject CAstNode, std::shared_ptr<InstrInfo> instrInfo);

  /// Returns the current source position recorder (presumably to add it to the currentEntity).
  jobject getCurrentCAstSourcePositionRecorder();

  /// Used to keep track of the currentBlock index so we know when to add the basic block arguments to the
  /// entity argument names. There is probably a better way to do this such as looking up the basic block
  /// the instruction lies in, but this is good enough for now.
  unsigned int currentBlock = 0;
};

} // end swan namespace

#endif // SWAN_WALAINSTANCE_H