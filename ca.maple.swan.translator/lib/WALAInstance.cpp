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
#include "SILWalaInstructionVisitor.h"
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
  SILWalaInstructionVisitor Visitor(this, true); // Bool is for enabling translator printing (for debug).
  Visitor.visitModule(&SM);
}

void WALAInstance::analyze() {
  // This -emit-sil option is critical as it specifies the action for the frontend,
  // otherwise the compiler will not do anything and complain no action was given.
  // Also, the callbacks required for the translation (hook) will not be triggered
  // without this option.
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
      auto LocalFile = (jstring)(JavaEnv->CallObjectMethod(Translator, GetLocalFile, 0));
      THROW_ANY_EXCEPTION(Exception);
      auto LocalFileStr = JavaEnv->GetStringUTFChars(LocalFile, 0);
      THROW_ANY_EXCEPTION(Exception);
      File = std::string(LocalFileStr);
      JavaEnv->ReleaseStringUTFChars(LocalFile, LocalFileStr);
      THROW_ANY_EXCEPTION(Exception);
  CATCH()
      // TODO: Report exceptions to user.
}

jobject WALAInstance::makeBigDecimal(const char *strData, int strLen) {
  char *safeData = strndup(strData, strLen);
  jobject val = JavaEnv->NewStringUTF(safeData);
  delete safeData;
  jclass bigDecimalCls = JavaEnv->FindClass("java/math/BigDecimal");
  jmethodID bigDecimalInit = JavaEnv->GetMethodID(bigDecimalCls,
    "<init>", "(Ljava/lang/String;)V");
  jobject bigDecimal = JavaEnv->NewObject(bigDecimalCls, bigDecimalInit, val);
  JavaEnv->DeleteLocalRef(val);
  return bigDecimal;
}

jobject WALAInstance::getCAstNodes() {
  jclass java_util_ArrayList = JavaEnv->FindClass("java/util/ArrayList");
  jmethodID java_util_ArrayList_ = JavaEnv->GetMethodID(java_util_ArrayList, "<init>", "(I)V");
  jmethodID java_util_ArrayList_add = JavaEnv->GetMethodID(java_util_ArrayList, "add", "(Ljava/lang/Object;)Z");

  auto result = JavaEnv->NewObject(java_util_ArrayList, java_util_ArrayList_, CAstNodes.size());

  for (jobject decl: CAstNodes) {
    JavaEnv->CallBooleanMethod(result, java_util_ArrayList_add, decl);
  }

  return result;
}

void WALAInstance::addCAstEntityInfo(std::unique_ptr<CAstEntityInfo> entity) {
  castEntities.push_back(std::move(entity));
}

jobject WALAInstance::getCAstNodesOfEntityInfo(const std::vector<jobject> &nodes) {
  jclass ArrayList = JavaEnv->FindClass("java/util/ArrayList");
  jmethodID ArrayListConstructor = JavaEnv->GetMethodID(ArrayList, "<init>", "(I)V");
  jmethodID ArrayListAdd = JavaEnv->GetMethodID(ArrayList, "add", "(Ljava/lang/Object;)Z");

  auto ArrayListCastNode = JavaEnv->NewObject(ArrayList, ArrayListConstructor, nodes.size());

  for (jobject node: nodes) {
    JavaEnv->CallBooleanMethod(ArrayListCastNode, ArrayListAdd, node);
  }

  return ArrayListCastNode;
}

jobject WALAInstance::getArgumentTypesOfEntityInfo(const std::vector<std::string> &argumentTypes) {
  jclass ArrayList = JavaEnv->FindClass("java/util/ArrayList");
  jmethodID ArrayListConstructor = JavaEnv->GetMethodID(ArrayList, "<init>", "(I)V");
  jmethodID ArrayListAdd = JavaEnv->GetMethodID(ArrayList, "add", "(Ljava/lang/Object;)Z");

  auto ArrayListArguments = JavaEnv->NewObject(ArrayList, ArrayListConstructor, argumentTypes.size());

  for (std::string type: argumentTypes) {
      JavaEnv->CallBooleanMethod(ArrayListArguments, ArrayListAdd, JavaEnv->NewStringUTF(type.c_str()));
  }

  return ArrayListArguments;
}

jobject WALAInstance::getCAstEntityInfo() {
  TRY(Exception, JavaEnv)
    // Create ArrayList<CAstEntityInfo>
    jclass ArrayList = JavaEnv->FindClass("java/util/ArrayList");
    THROW_ANY_EXCEPTION(Exception);
    jmethodID ArrayListConstructor = JavaEnv->GetMethodID(ArrayList, "<init>", "(I)V");
    THROW_ANY_EXCEPTION(Exception);
    jmethodID ArrayListAdd = JavaEnv->GetMethodID(ArrayList, "add", "(Ljava/lang/Object;)Z");

    auto ArrayListCAstEntityInfo = JavaEnv->NewObject(ArrayList, ArrayListConstructor, castEntities.size());

    // Add every CAstEntityInfo to ArrayList
    for (auto &info : castEntities) {
      auto CAstEntityInfoClass = JavaEnv->FindClass("ca/maple/swan/swift/tree/CAstEntityInfo");

      THROW_ANY_EXCEPTION(Exception);
      jmethodID CAstEntityInfoConstructor = JavaEnv->GetMethodID(CAstEntityInfoClass, "<init>",
        "(Ljava/lang/String;Ljava/util/ArrayList;Ljava/util/ArrayList;Ljava/util/ArrayList;Ljava/lang/String;Ljava/util/ArrayList;Ljava/util/ArrayList;)V");
      THROW_ANY_EXCEPTION(Exception);

      // get CAstEntityInfo constructor arguments
      jstring FunctionName = JavaEnv->NewStringUTF(info->functionName.c_str());
      THROW_ANY_EXCEPTION(Exception);
      jobject BasicBlocks = getCAstNodesOfEntityInfo(info->basicBlocks);
      THROW_ANY_EXCEPTION(Exception);
      jobject CallNodes = getCAstNodesOfEntityInfo(info->callNodes);
      THROW_ANY_EXCEPTION(Exception);
      jobject CFNodes = getCAstNodesOfEntityInfo(info->cfNodes);
      THROW_ANY_EXCEPTION(Exception);
      jstring ReturnType = JavaEnv->NewStringUTF(info->returnType.c_str());
      THROW_ANY_EXCEPTION(Exception);
      jobject ArgumentTypes = getArgumentTypesOfEntityInfo(info->argumentTypes);
      THROW_ANY_EXCEPTION(Exception);
      jobject ArgumentNames = getArgumentTypesOfEntityInfo(info->argumentNames);
      THROW_ANY_EXCEPTION(Exception);

      // create the CAstEntity object and add it to the ArrayList
      auto CAstEntityInfoObject = JavaEnv->NewObject(CAstEntityInfoClass, CAstEntityInfoConstructor,
        FunctionName, BasicBlocks, CallNodes, CFNodes, ReturnType, ArgumentTypes, ArgumentNames);
      JavaEnv->CallBooleanMethod(ArrayListCAstEntityInfo, ArrayListAdd, CAstEntityInfoObject);
    }
    return ArrayListCAstEntityInfo;
  CATCH()
    // TODO: Control may reach end of non-void function.
}