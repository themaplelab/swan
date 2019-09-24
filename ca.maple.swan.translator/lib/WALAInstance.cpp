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

WALAInstance::WALAInstance(JNIEnv *Env, jobject Obj, jobject args) : JavaEnv(Env), Translator(Obj) {
  TRY(Exception, JavaEnv)
      this->CAst = new CAstWrapper(JavaEnv, Exception, Translator);

      // Convert given ArrayList<String> to std::list<string>.
      // Credit: https://gist.github.com/qiao-tw/6e43fb2311ee3c31752e11a4415deeb1
      auto java_util_ArrayList      = JavaEnv->FindClass("java/util/ArrayList");
      auto java_util_ArrayList_size = JavaEnv->GetMethodID (java_util_ArrayList, "size", "()I");
      auto java_util_ArrayList_get  = JavaEnv->GetMethodID(java_util_ArrayList, "get", "(I)Ljava/lang/Object;");
      jint len = JavaEnv->CallIntMethod(args, java_util_ArrayList_size);
      std::list<std::string> argsList;
      for (jint i=0; i<len; i++) {
        jstring element = static_cast<jstring>(JavaEnv->CallObjectMethod(args, java_util_ArrayList_get, i));
        const char* pchars = JavaEnv->GetStringUTFChars(element, nullptr);
        argsList.push_back(pchars);
        JavaEnv->ReleaseStringUTFChars(element, pchars);
        JavaEnv->DeleteLocalRef(element);
      }
      analyze(argsList);
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
  TRY(Exception, JavaEnv)
    jclass java_util_ArrayList = JavaEnv->FindClass("java/util/ArrayList");
    jmethodID java_util_ArrayList_ = JavaEnv->GetMethodID(java_util_ArrayList, "<init>", "(I)V");
    jmethodID java_util_ArrayList_add = JavaEnv->GetMethodID(java_util_ArrayList, "add", "(Ljava/lang/Object;)Z");

    auto result = JavaEnv->NewObject(java_util_ArrayList, java_util_ArrayList_, mappedRoots.size());

    delete CAst;
    CAst = new CAstWrapper(JavaEnv, Exception, Translator);
    for (auto pair: mappedRoots) {
      jobject Node = CAst->makeNode(
        CAstWrapper::PRIMITIVE,
          CAst->makeConstant(pair.first.c_str()),
          CAst->makeNode(
            CAstWrapper::PRIMITIVE,
              CAst->makeArray(&pair.second)));
      JavaEnv->CallBooleanMethod(result, java_util_ArrayList_add, Node);
    }

    return result;
  CATCH()
}

void WALAInstance::setSource(std::string url) {
  TRY(Exception, JavaEnv)
      auto TranslatorClass = JavaEnv->FindClass("ca/maple/swan/swift/translator/SwiftToCAstTranslator");
      THROW_ANY_EXCEPTION(Exception);
      auto SetSource = JavaEnv->GetMethodID(TranslatorClass, "setSource", "(Ljava/lang/String;)V");
      THROW_ANY_EXCEPTION(Exception);
      JavaEnv->CallVoidMethod(Translator, SetSource, JavaEnv->NewStringUTF(url.c_str()));
      THROW_ANY_EXCEPTION(Exception);
  CATCH()
}
