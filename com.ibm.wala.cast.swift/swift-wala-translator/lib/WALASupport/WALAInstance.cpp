//
// Created by leo on 01/05/18.
//
#include "swift-wala/WALASupport/WALAInstance.h"

#include <CAstWrapper.h>
#include <launch.h>

#include "llvm/Support/raw_ostream.h"
#include "swift/SIL/SILModule.h"
#include "swift/AST/Module.h"
#include "swift/FrontendTool/FrontendTool.h"
#include "swift/Frontend/Frontend.h"
#include "swift-wala/WALASupport/SILWalaInstructionVisitor.h"
#include "swift/SIL/SILValue.h"
#include "llvm/Support/FileSystem.h"
#include "llvm/Support/Process.h"
#include "llvm/Support/Path.h"

using namespace swift_wala;
using namespace llvm;
using namespace swift;

namespace {
struct Observer : public FrontendObserver {
  WALAInstance *Instance;
  Observer(WALAInstance *Instance) : Instance(Instance) {};

  void parsedArgs(CompilerInvocation &Invocation) override {
    llvm::SmallString<128> LibPath(std::getenv("WALA_PATH_TO_SWIFT_BUILD"));
    llvm::sys::path::append(LibPath, "lib", "swift");
    Invocation.setRuntimeResourcePath(LibPath.str());
  }

  void configuredCompiler(CompilerInstance &CompilerInstance) override {
    if (auto Module = CompilerInstance.takeSILModule())
    {
      Instance->analyzeSILModule(Module);
    }
  }
};

std::string getExecutablePath(const char *FirstArg) {
  auto *P = (void *)(intptr_t)getExecutablePath;
  return llvm::sys::fs::getMainExecutable(FirstArg, P);
}
}

void WALAInstance::print(jobject Object) {
  auto ObjClass = JavaEnv->FindClass("java/lang/Object");
  auto ToString = JavaEnv->GetMethodID(ObjClass, "toString", "()Ljava/lang/String;");
  auto Message = (jstring) JavaEnv->CallObjectMethod(ObjClass, ToString);

  jboolean Result = false;
  const char *Text = JavaEnv->GetStringUTFChars(Message, &Result);

  outs() << "WALA: " << Text << "\n";
  JavaEnv->ReleaseStringUTFChars(Message, Text);
}

void WALAInstance::analyzeSILModule(SILModule &SM) {
  SILWalaInstructionVisitor Visitor(this, true);
  Visitor.visitModule(&SM);
}

void WALAInstance::analyze() {
  auto Argv = {"", "-emit-sil", File.c_str()};

  ::Observer observer(this);
  auto argv_ = Argv.begin();
  auto argc_ = Argv.end();
  SmallVector<const char *, 256> argv(&argv_[0], &argv_[argc_]);

  performFrontend(llvm::makeArrayRef(argv.data()+1,
                                     argv.data()+argv.size()),
                  argv[0], (void *)(intptr_t)getExecutablePath,
                  &observer);
}

WALAInstance::WALAInstance(JNIEnv *Env, jobject Obj) : JavaEnv(Env), XLator(Obj) {
  TRY(Exception, JavaEnv)
      CAst = new CAstWrapper(JavaEnv, Exception, XLator);
      auto XLatorClass = JavaEnv->FindClass("com/ibm/wala/cast/swift/SwiftToCAstTranslator");
      THROW_ANY_EXCEPTION(Exception);
      auto GetLocalFile = JavaEnv->GetMethodID(XLatorClass, "getLocalFile", "()Ljava/lang/String;");
      THROW_ANY_EXCEPTION(Exception);
      auto LocalFile = (jstring)(JavaEnv->CallObjectMethod(XLator, GetLocalFile, 0));
      THROW_ANY_EXCEPTION(Exception);
      auto LocalFileStr = JavaEnv->GetStringUTFChars(LocalFile, 0);
      THROW_ANY_EXCEPTION(Exception);
      File = std::string(LocalFileStr);
      JavaEnv->ReleaseStringUTFChars(LocalFile, LocalFileStr);
      THROW_ANY_EXCEPTION(Exception);
  CATCH()
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
  jclass java_util_ArrayList      = JavaEnv->FindClass("java/util/ArrayList");
  jmethodID java_util_ArrayList_  = JavaEnv->GetMethodID(java_util_ArrayList, "<init>", "(I)V");
  jmethodID java_util_ArrayList_add         = JavaEnv->GetMethodID(java_util_ArrayList, "add", "(Ljava/lang/Object;)Z");

  auto result = JavaEnv->NewObject(java_util_ArrayList, java_util_ArrayList_, CAstNodes.size());

  for (jobject decl: CAstNodes) {
    JavaEnv->CallBooleanMethod(result, java_util_ArrayList_add, decl);
  }

  return result;
}
