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
  InstructionVisitor Visitor(this); // Bool is for enabling translator printing (for debug).
  Visitor.visitSILModule(&SM);
}

void WALAInstance::analyze() {
  // This -emit-sil option is critical as it specifies the action for the frontend,
  // otherwise the compiler will not do anything and complain no action was given.
  // Also, the callbacks required for the translation (hook) will not be triggered
  // without this option.

  // Note that "-o<name>.sil" option will prefix all function names with "<name>.". If you omit this option, it will
  // prefix the function names with the .swift filename. This _shouldn't_ be problematic, but could be in the future
  // especially with multi-file analysis.
  auto Argv = {"", "-emit-silgen", "-oout.sil", "-Onone", File.c_str()};
  swan::Observer observer(this); // create the hook
  SmallVector<const char *, 256> argv(Argv.begin(), Argv.end());

  // Change current working path to allow for relative pathed input files.
  // Regular working dir is swan/ca.maple.swan.analysis, we change it to just swan/.
  char temp[1024];
  std::string currentWorkingPath = getcwd(temp, sizeof(temp)) ? std::string( temp ) : std::string("");
  size_t lastSlashIndex = currentWorkingPath.find_last_of("/"); // TODO: Make this less hardcoded.
  std::string newCurrentWorkingPath = currentWorkingPath.substr(0, lastSlashIndex);
  chdir(newCurrentWorkingPath.c_str());

  // Call Swift compiler frontend.
  performFrontend(llvm::makeArrayRef(argv.data()+1,
                                     argv.data()+argv.size()),
                  argv[0], (void *)(intptr_t)getExecutablePath,
                  &observer);
}

WALAInstance::WALAInstance(JNIEnv *Env, jobject Obj) : JavaEnv(Env), Translator(Obj) {
  TRY(Exception, JavaEnv)
      CAst = new CAstWrapper(JavaEnv, Exception, Translator); // Used for JNI calls.
      // Find the bridge class.
      auto TranslatorClass = JavaEnv->FindClass("ca/maple/swan/swift/translator/SwiftToCAstTranslator");
      THROW_ANY_EXCEPTION(Exception);

      // Get the file to analyze.
      auto GetLocalFile = JavaEnv->GetMethodID(TranslatorClass, "getLocalFile", "()Ljava/lang/String;");
      THROW_ANY_EXCEPTION(Exception);
      auto LocalFile = static_cast<jstring>(JavaEnv->CallObjectMethod(Translator, GetLocalFile, 0));
      THROW_ANY_EXCEPTION(Exception);
      auto LocalFileStr = JavaEnv->GetStringUTFChars(LocalFile, nullptr);
      THROW_ANY_EXCEPTION(Exception);
      File = std::string(LocalFileStr);
      JavaEnv->ReleaseStringUTFChars(LocalFile, LocalFileStr);
      THROW_ANY_EXCEPTION(Exception);
  CATCH()
      // TODO: Report exceptions to user.
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

jobject WALAInstance::getRoot() {
  return Root;
}
