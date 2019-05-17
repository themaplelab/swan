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
 /// Extend the swiftc FrontendObserver and take advantage of its virtual
 /// virtual methods to hook into the compiler. We pass this Observer to the
 /// swiftc frontend using performFrontend in WALAInstance. Before the
 /// compiler is about to compile the SIL Module, it will notify the Observer
 /// using the configuredCompiler(...) method. Here we grab the SIL Module,
 /// analyze it, and give it back to the compiler (since unique_ptr is used).
 ///
 /// The hook is simple enough that in can be inside only a header file.
 //----------------------------------------------------------------------------/

#pragma once

#include "WALAInstance.h"

#include <CAstWrapper.h>
#include <launch.h>

#include "llvm/Support/raw_ostream.h"
#include "swift/SIL/SILModule.h"
#include "swift/AST/Module.h"
#include "swift/FrontendTool/FrontendTool.h"
#include "swift/Frontend/Frontend.h"
#include "SILWalaInstructionVisitor.h"
#include "swift/SIL/SILValue.h"
#include "llvm/Support/FileSystem.h"
#include "llvm/Support/Process.h"
#include "llvm/Support/Path.h"

namespace swift_wala {
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
      Module = Instance->analyzeSILModule(std::move(Module));
      // reset so compiler can use SIL Module after
      CompilerInstance.setSILModule(std::move(Module));
    }
  }
};

std::string getExecutablePath(const char *FirstArg) {
  auto *P = (void *)(intptr_t)getExecutablePath;
  return llvm::sys::fs::getMainExecutable(FirstArg, P);
}

} // end swift_wala namespace
