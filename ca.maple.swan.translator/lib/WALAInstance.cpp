//===--- WALAInstance.cpp - Class that bridges translator and JNI --------===//
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
/// This file implements the 'hub' class that calls the Swift compiler
/// frontend, and uses the hook to call the SILWalaInstructionVisitor
/// on the SILModule.
///
//===---------------------------------------------------------------------===//

#include "WALAInstance.h"
#include "SwiftCHook.hpp"
#include "swift/AST/Module.h"
#include "swift/Frontend/Frontend.h"
#include "swift/FrontendTool/FrontendTool.h"
#include "swift/SIL/SILModule.h"
#include "swift/SIL/SILValue.h"
#include "llvm/Support/FileSystem.h"
#include "llvm/Support/Path.h"
#include "llvm/Support/Process.h"
#include "llvm/Support/raw_ostream.h"
#include <CAstWrapper.h>
#include <launch.h>
#include <unistd.h>

using namespace swan;
using namespace llvm;
using namespace swift;

void WALAInstance::printNode(jobject Node) {
  CAst->log(Node);
}

void WALAInstance::analyzeSILModule(SILModule &SM) {
  InstructionVisitor Visitor(this);
  Visitor.visitSILModule(&SM);
}

void WALAInstance::analyze(const std::list<string> args) {
  // Create the hook.
  swan::Observer observer(this);
  // Set up the arguments.
  std::vector<const char *> vec(args.size());
  for (auto it = args.begin(); it != args.end(); ++it) {
    vec.push_back((*it).c_str());
  }
  SmallVector<const char *, 256> argv(vec.begin(), vec.end());
  // Call Swift compiler frontend.
  performFrontend(llvm::makeArrayRef(argv.data()+1,
                                     argv.data()+argv.size()),
                  argv[0], (void *)(intptr_t)getExecutablePath,
                  &observer);
}

WALAInstance::WALAInstance(JNIEnv *Env, jobject Obj) : JavaEnv(Env), Translator(Obj) {
  TRY(Exception, JavaEnv)
      this->CAst = new CAstWrapper(JavaEnv, Exception, Translator);

      jobject test0 = CAst->makeConstant("test0");
      printNode(test0);
  CATCH()
}

jobject WALAInstance::makeBigDecimal(const char *strData, int strLen) {
  char *safeData = strndup(strData, static_cast<size_t>(strLen));
  jobject val = JavaEnv->NewStringUTF(safeData);
  delete safeData;
  jclass bigDecimalCls = JavaEnv->FindClass("java/math/BigDecimal");
  jmethodID bigDecimalInit = JavaEnv->GetMethodID(bigDecimalCls,
    "<init>", "(Ljava/lang/String;)V");
  jobject bigDecimal = JavaEnv->NewObject(bigDecimalCls, bigDecimalInit, val);
  JavaEnv->DeleteLocalRef(val);
  return bigDecimal;
}

jobject WALAInstance::getRoots() {
  jclass java_util_ArrayList = JavaEnv->FindClass("java/util/ArrayList");
  jmethodID java_util_ArrayList_ = JavaEnv->GetMethodID(java_util_ArrayList, "<init>", "(I)V");
  jmethodID java_util_ArrayList_add = JavaEnv->GetMethodID(java_util_ArrayList, "add", "(Ljava/lang/Object;)Z");

  auto result = JavaEnv->NewObject(java_util_ArrayList, java_util_ArrayList_, Roots.size());

  for (jobject node: Roots) {
    JavaEnv->CallBooleanMethod(result, java_util_ArrayList_add, node);
  }

  return result;
}
