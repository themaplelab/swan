//
// Created by leo on 01/05/18.
//
#include "swift-wala/WALASupport/WALAInstance.h"

#include "llvm/Support/raw_ostream.h"
#include "swift/SIL/SILModule.h"
#include "swift/AST/Module.h"
#include "swift/FrontendTool/FrontendTool.h"
#include "swift/Frontend/Frontend.h"
#include "swift-wala/WALASupport/SILWalaInstructionVisitor.h"

using namespace swift_wala;

namespace {
struct Observer : public FrontendObserver {
  std::shared_ptr<WALAInstance> Instance;
  Observer(std::shared_ptr<WALAInstance> Instance) : Instance(Instance) {};

  void performedSILGeneration(SILModule &Module) override {
  }
};
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
  createModule(SM.getSwiftModule()->getModuleFilename());

  SILWalaInstructionVisitor Visitor(shared_from_this(), true);
  Visitor.visitModule(&SM);
}

void WALAInstance::analyze() {
  auto Argc = 3;
  auto Argv = {"", "-emit-llvm", File.c_str()};

  Observer observer(shared_from_this());
  SmallVector<const char *, 256> argv;
  llvm::SpecificBumpPtrAllocator<char> ArgAllocator;
  std::error_code EC = llvm::sys::Process::GetArgumentVector(argv, llvm::ArrayRef<const char *>(Argv, Argc),
                                                             ArgAllocator);
  if (EC) {
    llvm::errs() << "error: couldn't get arguments: " << EC.message() << "\n";
  }

  Observer observer;
  return performFrontend(llvm::makeArrayRef(argv.data()+1,
                                            argv.data()+argv.size()),
                         argv[0], (void *)(intptr_t)getExecutablePath,
                         &observer);
}

