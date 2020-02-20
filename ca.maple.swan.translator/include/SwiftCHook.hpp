//===--- SwiftCHook.h - Hook into Swift Compiler -------------------------===//
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
/// This file contains the hook that is needed to grab SILModules for the
/// Swift compiler. It extends the compiler's FrontendObserver class, which
/// the compiler's frontend has callbacks to. Some of these callbacks pass
/// the SILModule we need to translate. The hook is simple enough that it
/// can be inside only a header file.
///
//===---------------------------------------------------------------------===//

#ifndef SWAN_SWIFTCHOOK_H
#define SWAN_SWIFTCHOOK_H

#include "InstructionVisitor.h"
#include "WALAInstance.h"
#include "llvm/Support/FileSystem.h"
#include "llvm/Support/Path.h"
#include "llvm/Support/Process.h"
#include "llvm/Support/raw_ostream.h"
#include "swift/AST/Module.h"
#include "swift/Frontend/Frontend.h"
#include "swift/FrontendTool/FrontendTool.h"
#include "swift/SIL/SILModule.h"
#include "swift/SIL/SILValue.h"
#include <CAstWrapper.h>
#include <launch.h>

using namespace swift;

namespace swan {

/// Observer class implements virtual callback methods (that the Frontend
/// calls) of the FrontendObserver, so that desired objects (such as the
/// SILModule) can be grabbed from the compiler during compilation.
struct Observer : public FrontendObserver {
  WALAInstance *Instance;
  Observer(WALAInstance *Instance) : Instance(Instance) {};

  /// This callback method is called by the compiler frontend once
  /// the compiler invocation is fully configured.
  void parsedArgs(CompilerInvocation &Invocation) override {
  }

  /// This callback method is called by the compiler frontend once the
  /// SILModule is generated.
  void performedSILGeneration(SILModule &Module) override {
    Instance->analyzeSILModule(Module);
  }
};

/// This method is copied from the `apple/swift` repository since it was
/// only available in tools/driver/driver.cpp. It returns the full path
/// of a given filename.
std::string getExecutablePath(const char *FirstArg) {
  auto *P = (void *)(intptr_t)getExecutablePath;
  return llvm::sys::fs::getMainExecutable(FirstArg, P);
}

} // end swan namespace

#endif // SWAN_SWIFTCHOOK_H
