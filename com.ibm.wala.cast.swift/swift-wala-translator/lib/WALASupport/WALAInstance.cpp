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
#include "llvm/Support/FileSystem.h"
#include "llvm/Support/Process.h"

using namespace swift_wala;
using namespace llvm;
using namespace swift;

namespace {
struct Observer : public FrontendObserver {
  WALAInstance *Instance;
  Observer(WALAInstance *Instance) : Instance(Instance) {};

  void performedSILGeneration(SILModule &Module) override {
    Instance->analyzeSILModule(Module);
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
  auto Argv = {"", "-emit-llvm", File.c_str()};

  ::Observer observer(this);
  SmallVector<const char *, 256> argv;
  llvm::SpecificBumpPtrAllocator<char> ArgAllocator;
  std::error_code EC = llvm::sys::Process::GetArgumentVector(argv,
                                                             llvm::ArrayRef<const char *>(Argv.begin(), Argv.end()),
                                                             ArgAllocator);
  if (EC) {
    llvm::errs() << "error: couldn't get arguments: " << EC.message() << "\n";
  }
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
