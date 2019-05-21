/******************************************************************************
 * Copyright (c) 2019 Maple @ University of Alberta
 * All rights reserved. This program and the accompanying materials (unless
 * otherwise specified by a license inside of the accompanying material)
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *****************************************************************************/

 //----------------------------------------------------------------------------/
 /// DESCRIPTION
 /// WALAInstance is the hub class that calls the Swift compiler frontend,
 /// calls the SILWALAInstructionVisitor on each SIL module, which translates
 /// the SIL to CAst and puts the CAst result back into the WALAInstance.
 //----------------------------------------------------------------------------/

#pragma once

#include <jni.h>
#include <string>
#include <sstream>
#include <cstring>
#include <memory>
#include <vector>

#include "swift/SIL/SILModule.h"

class CAstWrapper;

namespace swift_wala {

class WALAInstance {
private:
  JNIEnv *JavaEnv; // JVM
  jobject Translator; // swift-wala translator
  std::string File; // Swift file to analyze

public:
  CAstWrapper *CAst; // for handling JNI calls
  std::vector<jobject> CAstNodes; // translated nodes

  explicit WALAInstance(JNIEnv* Env, jobject Obj);

  // convert C++ string to Java BigDecimal, used in instruction visitor
  jobject makeBigDecimal(const char *strData, int strLen);
  // return copy of translated nodes as a jobject (ArrayList of CAst ndoes)
  jobject getCAstNodes();
  // for debugging
  void print(jobject Object);
  // method to start the analysis, hooks into the Swift compiler frontend
  void analyze();
  // visits the given SIL module and will put the result back into the instance
  void analyzeSILModule(swift::SILModule &SM);
};

} // end swift_wala namespace
