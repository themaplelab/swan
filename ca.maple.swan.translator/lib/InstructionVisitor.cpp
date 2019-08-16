//===--- InstructionVisitor.cpp - SIL to CAst Translator -----------------===//
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
/// This file implements the InstructionVisitor class, which inherits
/// the SILInstructionVisitor template class (part of the Swift compiler).
/// The SILInstructionVisitor translates a given SIL
/// (Swift Intermediate Language) Module to CAst (WALA IR).
///
//===---------------------------------------------------------------------===//

#include "InstructionVisitor.h"
#include "swift/AST/Module.h"
#include "swift/AST/Types.h"
#include "swift/Demangling/Demangle.h"
#include "swift/SIL/SILModule.h"
#include <fstream>
#include <memory>

using namespace swan;

//===------------------- MODULE/FUNCTION/BLOCK VISITORS ------------------===//

void InstructionVisitor::visitSILModule(SILModule *M) {
  moduleInfo = std::make_unique<SILModuleInfo>(M->getSwiftModule()->getModuleFilename());
  currentModule = std::make_unique<RootModuleInfo>(Instance->CAst);

  for (SILFunction &F: *M) {
    if (F.empty()) { // Most likely a builtin, so we ignore it.
      continue;
    }

    visitSILFunction(&F);
  }

  Instance->Root = currentModule->make();
}

void InstructionVisitor::visitSILFunction(SILFunction *F) {
  std::string const &demangledFunctionName = Demangle::demangleSymbolAsString(F->getName());
  functionInfo = std::make_unique<SILFunctionInfo>(F->getName(), demangledFunctionName);
  currentFunction = std::make_unique<RootFunctionInfo>(Instance->CAst);
  currentFunction->functionName = demangledFunctionName;

  // TODO: Source information [checking] is not correct!

  // Set function source information.
  unsigned fl = 0, fc = 0, ll = 0, lc = 0;
  // Swift compiler doesn't seem to have a way of getting the specific location of a param.
  if (!F->getLocation().isNull()) {
    SourceManager const &srcMgr = F->getModule().getSourceManager();
    SourceRange const &srcRange = F->getLocation().getSourceRange();
    SourceLoc const &srcStart = srcRange.Start;
    SourceLoc const &srcEnd = srcRange.End;
    if (srcStart.isInvalid() || srcEnd.isInvalid()) {
      llvm::outs() << "WARNING: Source information is invalid for function: " << demangledFunctionName;
    } else {
      auto startLineCol = srcMgr.getLineAndColumn(srcStart);
      fl = startLineCol.first;
      fc = startLineCol.second;
      auto endLineCol = srcMgr.getLineAndColumn(srcEnd);
      ll = endLineCol.first;
      lc = endLineCol.second;
    }
    currentFunction->setFunctionSourceInfo(fl, fc, ll, lc);
  } else {
    llvm::outs() << "WARNING: Source information is null for function: " << demangledFunctionName << "\n";
  }

  // Handle function arguments.
  for (SILArgument *arg: F->getArguments()) {
    if (arg->getDecl() && arg->getDecl()->hasName()) {
      // Currently the arguments do not have a specific position.
      currentFunction->addArgument(addressToString(static_cast<ValueBase*>(arg)), arg->getType().getAsString(),
        fl, fc, fl, fc);
    }
  }

  // Set function result type.
  if (F->getLoweredFunctionType()->getNumResults() == 1) {
    currentFunction->returnType = F->getLoweredFunctionType()->getSingleResult().getSILStorageType().getAsString();
  } else {
    currentFunction->returnType = "MultiResultType"; // TODO: Replace with array of types or something?
  }

  if (SWAN_PRINT) {
    llvm::outs() << "SILFunction: " << "ADDR: " << F << " , NAME: " << demangledFunctionName << "\n";
    llvm::outs() << "<RAW SIL BEGIN> \n\n";
    F->print(llvm::outs(), true);
    llvm::outs() << "\n</RAW SIL END> \n\n";
  }

  // Finally, visit every basic block of the function.
  for (auto &BB: *F) {
    visitSILBasicBlock(&BB);
  }

  currentModule->addFunction(currentFunction.get());
}

void InstructionVisitor::visitSILBasicBlock(SILBasicBlock *BB) {
  if (SWAN_PRINT) {
    llvm::outs() << "Basic Block: " << BB << "\n";
    llvm::outs() << "Parent SILFunction: " << BB->getParent() << "\n";
  }

  // Clear information from previous basic block.
  InstructionCounter = 0;
  currentBasicBlock = std::make_unique<RootBasicBlockInfo>(Instance->CAst);

  // Visit every instruction of the basic block.
  for (auto &I: *BB) {
    currentInstruction = std::make_unique<RootInstructionInfo>(Instance->CAst);
    auto Node = visit(&I);
    if (Node != nullptr) {
      if (Instance->CAst->getKind(Node) == CAstWrapper::EMPTY)
      {
        continue;
      } else {
        currentInstruction->instructionName = getSILInstructionName(I.getKind());
        currentInstruction->setInstructionSourceInfo(instrInfo->startLine, instrInfo->startCol,
          instrInfo->endLine, instrInfo->endCol);
        currentBasicBlock->addInstruction(currentInstruction.get());
      }
    }
  }

  currentFunction->addBlock(currentBasicBlock.get());
}

//===----------- INSTRUCTION SOURCE INFORMATION PROCESSING ---------------===//

void InstructionVisitor::beforeVisit(SILInstruction *I) {
  // Set instruction source information.
  instrInfo = std::make_unique<SILInstructionInfo>();
  SourceManager &srcMgr = I->getModule().getSourceManager();
  SILLocation const &debugLoc = I->getDebugLocation().getLocation();
  SILLocation::DebugLoc const &debugInfo = debugLoc.decodeDebugLoc(srcMgr);
  // Set filename.
  instrInfo->Filename = debugInfo.Filename;
  // Set position.
  if (!I->getLoc().isNull()) {
    SourceRange const &srcRange = I->getLoc().getSourceRange();
    SourceLoc const &srcStart = srcRange.Start;
    SourceLoc const &srcEnd = srcRange.End;

    if (srcStart.isInvalid() && srcEnd.isInvalid()) {
      llvm::outs() << "\t NOTE: Source information is invalid\n";
    } else {
      if (srcStart.isValid()) {
        instrInfo->srcType = SILSourceType::STARTONLY;
        auto startLineCol = srcMgr.getLineAndColumn(srcStart);
        instrInfo->startLine = startLineCol.first;
        instrInfo->startCol = startLineCol.second;
      }
      if (srcEnd.isValid()) {
        auto endLineCol = srcMgr.getLineAndColumn(srcEnd);
        instrInfo->endLine = endLineCol.first;
        instrInfo->endCol = endLineCol.second;
        instrInfo->srcType = SILSourceType::FULL;
      }
    }
  }
  // Set memory behaviour.
  instrInfo->memBehavior = I->getMemoryBehavior();
  instrInfo->relBehavior = I->getReleasingBehavior();

  // Set other properties.
  instrInfo->num = InstructionCounter++;
  instrInfo->modInfo = moduleInfo.get();
  instrInfo->funcInfo = functionInfo.get();
  instrInfo->instrKind = I->getKind();

  // Set instruction operands.
  std::vector<void *> vals;
  for (const auto &op: I->getAllOperands()) {
    vals.push_back(op.get().getOpaqueValue());
  }
  instrInfo->ops = llvm::ArrayRef<void *>(vals);

  if (SWAN_PRINT) {
    if (SWAN_PRINT_SOURCE) {
      llvm::outs() << "\t\t [VALUE BASE]: " << I << "\n";
      printSILInstructionInfo();
    }
    llvm::outs() << "<< " << getSILInstructionName(I->getKind()) << " >>\n";
  }
}

void InstructionVisitor::printSILInstructionInfo() {
  llvm::outs() << "\t\t [INSTR] #" << instrInfo->num;
  llvm::outs() << ", [OPNUM] " << instrInfo->id << "\n";
  if (SWAN_PRINT_FILE_AND_MEMORY) {
    llvm::outs() << "\t\t --> File: " << instrInfo->Filename << "\n";
    if (instrInfo->srcType == SILSourceType::INVALID) {
      llvm::outs() << "\t\t **** No source information. \n";
    } else { // Has at least start information.
      llvm::outs() << "\t\t\t ++++ Start - Line " << instrInfo->startLine << ":"
                   << instrInfo->startCol << "\n";
    }
    // Has end information.
    if (instrInfo->srcType == SILSourceType::FULL) {
      llvm::outs() << "\t\t\t ---- End - Line " << instrInfo->endLine;
      llvm::outs() << ":" << instrInfo->endCol << "\n";
    }
    // Memory Behavior.
    switch (instrInfo->memBehavior) {
      case SILInstruction::MemoryBehavior::None: {
        break;
      }
      case SILInstruction::MemoryBehavior::MayRead: {
        llvm::outs() << "\t\t +++ [MEM-R]: May read from memory. \n";
        break;
      }
      case SILInstruction::MemoryBehavior::MayWrite: {
        llvm::outs() << "\t\t +++ [MEM-W]: May write to memory. \n";
        break;
      }
      case SILInstruction::MemoryBehavior::MayReadWrite: {
        llvm::outs() << "\t\t +++ [MEM-RW]: May read or write memory. \n";
        break;
      }
      case SILInstruction::MemoryBehavior::MayHaveSideEffects: {
        llvm::outs() << "\t\t +++ [MEM-F]: May have side effects. \n";
      }
    }
    // Releasing Behavior.
    switch (instrInfo->relBehavior) {
      case SILInstruction::ReleasingBehavior::DoesNotRelease: {
        llvm::outs() << "\t\t [REL]: Does not release memory. \n";
        break;
      }
      case SILInstruction::ReleasingBehavior::MayRelease: {
        llvm::outs() << "\t\t [REL]: May release memory. \n";
        break;
      }
    }
  }
  // Show operands, if they exist.
  for (void * const &op : instrInfo->ops) {
    llvm::outs() << "\t\t [OPER]: " << op << "\n";
  }
}

//===------------------------- UTLITY FUNCTIONS ----------------------------===//

jobject InstructionVisitor::getOperatorCAstType(Identifier &Name) {
  if (Name.is("==")) {
    return CAstWrapper::OP_EQ;
  } else if (Name.is("!=")) {
    return CAstWrapper::OP_NE;
  } else if (Name.is("+")) {
    return CAstWrapper::OP_ADD;
  } else if (Name.is("/")) {
    return CAstWrapper::OP_DIV;
  } else if (Name.is("<<")) {
    return CAstWrapper::OP_LSH;
  } else if (Name.is("*")) {
    return CAstWrapper::OP_MUL;
  } else if (Name.is(">>")) {
    return CAstWrapper::OP_RSH;
  } else if (Name.is("-")) {
    return CAstWrapper::OP_SUB;
  } else if (Name.is(">=")) {
    return CAstWrapper::OP_GE;
  } else if (Name.is(">")) {
    return CAstWrapper::OP_GT;
  } else if (Name.is("<=")) {
    return CAstWrapper::OP_LE;
  } else if (Name.is("<")) {
    return CAstWrapper::OP_LT;
  } else if (Name.is("!")) {
    return CAstWrapper::OP_NOT;
  } else if (Name.is("~")) {
    return CAstWrapper::OP_BITNOT;
  } else if (Name.is("&")) {
    return CAstWrapper::OP_BIT_AND;
  } else if (Name.is("&&")) {
    // TODO: Why is this not handled?
    // OLD: return CAstWrapper::OP_REL_AND;
    return nullptr; // OLD: && and || are handled separately because they involve short circuits
  } else if (Name.is("|")) {
    return CAstWrapper::OP_BIT_OR;
  } else if (Name.is("||")) {
    // TODO: Why is this not handled?
    // OLD: return CAstWrapper::OP_REL_OR;
    return nullptr; // OLD: && and || are handled separatedly because they involve short circuits
  } else if (Name.is("^")) {
    return CAstWrapper::OP_BIT_XOR;
  } else {
    llvm::outs() << "WARNING: Unhandled operator: " << Name << " detected! \n";
    return nullptr;
  }
}

//===-------------------SPECIFIC INSTRUCTION VISITORS ----------------------===//

/*******************************************************************************/
/*                         ALLOCATION AND DEALLOCATION                         */
/*******************************************************************************/

jobject InstructionVisitor::visitAllocStackInst(AllocStackInst *ASI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitAllocRefInst(AllocRefInst *ARI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitAllocRefDynamicInst(AllocRefDynamicInst *ARDI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitAllocBoxInst(AllocBoxInst *ABI){
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitAllocValueBufferInst(AllocValueBufferInst *AVBI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitAllocGlobalInst(AllocGlobalInst *AGI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitDeallocStackInst(DeallocStackInst *DSI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitDeallocBoxInst(DeallocBoxInst *DBI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitProjectBoxInst(ProjectBoxInst *PBI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitDeallocRefInst(DeallocRefInst *DRI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitDeallocPartialRefInst(DeallocPartialRefInst *DPRI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitDeallocValueBufferInst(DeallocValueBufferInst *DVBI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitProjectValueBufferInst(ProjectValueBufferInst *PVBI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                        DEBUG INFROMATION                                    */
/*******************************************************************************/

jobject InstructionVisitor::visitDebugValueInst(DebugValueInst *DBI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitDebugValueAddrInst(DebugValueAddrInst *DVAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                        Accessing Memory                                     */
/*******************************************************************************/

jobject InstructionVisitor::visitLoadInst(LoadInst *LI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitStoreInst(StoreInst *SI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitLoadBorrowInst(LoadBorrowInst *LBI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitBeginBorrowInst(BeginBorrowInst *BBI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitEndBorrowInst(EndBorrowInst *EBI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitAssignInst(AssignInst *AI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitAssignByWrapperInst(AssignByWrapperInst *ABWI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitMarkUninitializedInst(MarkUninitializedInst *MUI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitMarkFunctionEscapeInst(MarkFunctionEscapeInst *MFEI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitCopyAddrInst(CopyAddrInst *CAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitDestroyAddrInst(DestroyAddrInst *DAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitIndexAddrInst(IndexAddrInst *IAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitTailAddrInst(TailAddrInst *TAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitIndexRawPointerInst(IndexRawPointerInst *IRPI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitBindMemoryInst(BindMemoryInst *BMI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitBeginAccessInst(BeginAccessInst *BAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitEndAccessInst(EndAccessInst *EAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitBeginUnpairedAccessInst(BeginUnpairedAccessInst *BUI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitEndUnpairedAccessInst(EndUnpairedAccessInst *EUAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                        Reference Counting                                   */
/*******************************************************************************/

jobject InstructionVisitor::visitStrongRetainInst(StrongRetainInst *SRTI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitStrongReleaseInst(StrongReleaseInst *SRLI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitSetDeallocatingInst(SetDeallocatingInst *SDI)  {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitStrongRetainUnownedInst(StrongRetainUnownedInst *SRUI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitUnownedRetainInst(UnownedRetainInst *URTI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitUnownedReleaseInst(UnownedReleaseInst *URLI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitLoadWeakInst(LoadWeakInst *LWI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitStoreWeakInst(StoreWeakInst *SWI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitLoadUnownedInst(LoadUnownedInst *LUI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitStoreUnownedInst(StoreUnownedInst *SUI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitFixLifetimeInst(FixLifetimeInst *FLI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitEndLifetimeInst(EndLifetimeInst *ELI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitMarkDependenceInst(MarkDependenceInst *MDI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitIsUniqueInst(IsUniqueInst *IUI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitIsEscapingClosureInst(IsEscapingClosureInst *IECI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitCopyBlockInst(CopyBlockInst *CBI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitCopyBlockWithoutEscapingInst(CopyBlockWithoutEscapingInst *CBWEI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                         Literals                                            */
/*******************************************************************************/

jobject InstructionVisitor::visitFunctionRefInst(FunctionRefInst *FRI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitDynamicFunctionRefInst(DynamicFunctionRefInst *DFRI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitPreviousDynamicFunctionRefInst(PreviousDynamicFunctionRefInst *PDFRI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitGlobalAddrInst(GlobalAddrInst *GAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitGlobalValueInst(GlobalValueInst *GVI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitIntegerLiteralInst(IntegerLiteralInst *ILI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitFloatLiteralInst(FloatLiteralInst *FLI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitStringLiteralInst(StringLiteralInst *SLI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                         Dynamic Dispatch                                    */
/*******************************************************************************/

jobject InstructionVisitor::visitClassMethodInst(ClassMethodInst *CMI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitObjCMethodInst(ObjCMethodInst *AMI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitSuperMethodInst(SuperMethodInst *SMI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitObjCSuperMethodInst(ObjCSuperMethodInst *ASMI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitWitnessMethodInst(WitnessMethodInst *WMI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                         Function Application                                */
/*******************************************************************************/

jobject InstructionVisitor::visitApplyInst(ApplyInst *AI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitBeginApplyInst(BeginApplyInst *BAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitAbortApplyInst(AbortApplyInst *AAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitEndApplyInst(EndApplyInst *EAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitPartialApplyInst(PartialApplyInst *PAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitBuiltinInst(BuiltinInst *BI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                          Metatypes                                          */
/*******************************************************************************/

jobject InstructionVisitor::visitMetatypeInst(MetatypeInst *MI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitValueMetatypeInst(ValueMetatypeInst *VMI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitExistentialMetatypeInst(ExistentialMetatypeInst *EMI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitObjCProtocolInst(ObjCProtocolInst *OPI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                          Aggregate Types                                    */
/*******************************************************************************/

jobject InstructionVisitor::visitRetainValueInst(RetainValueInst *RVI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitRetainValueAddrInst(RetainValueAddrInst *RVAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitUnmanagedRetainValueInst(UnmanagedRetainValueInst *URVI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitCopyValueInst(CopyValueInst *CVI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitReleaseValueInst(ReleaseValueInst *REVI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitReleaseValueAddrInst(ReleaseValueAddrInst *REVAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitUnmanagedReleaseValueInst(UnmanagedReleaseValueInst *UREVI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitDestroyValueInst(DestroyValueInst *DVI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitAutoreleaseValueInst(AutoreleaseValueInst *AREVI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitTupleInst(TupleInst *TI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitTupleExtractInst(TupleExtractInst *TEI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitTupleElementAddrInst(TupleElementAddrInst *TEAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitDestructureTupleInst(DestructureTupleInst *DTI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitStructInst(StructInst *SI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitStructExtractInst(StructExtractInst *SEI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitStructElementAddrInst(StructElementAddrInst *SEAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitDestructureStructInst(DestructureStructInst *DSI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitObjectInst(ObjectInst *OI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitRefElementAddrInst(RefElementAddrInst *REAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitRefTailAddrInst(RefTailAddrInst *RTAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                          Enums                                              */
/*******************************************************************************/

jobject InstructionVisitor::visitEnumInst(EnumInst *EI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitUncheckedEnumDataInst(UncheckedEnumDataInst *UED) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitInjectEnumAddrInst(InjectEnumAddrInst *IUAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitInitEnumDataAddrInst(InitEnumDataAddrInst *UDAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitUncheckedTakeEnumDataAddrInst(UncheckedTakeEnumDataAddrInst *UDAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitSelectEnumInst(SelectEnumInst *SEI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                          Protocol and Protocol Composition Types            */
/*******************************************************************************/

jobject InstructionVisitor::visitInitExistentialAddrInst(InitExistentialAddrInst *IEAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitDeinitExistentialAddrInst(DeinitExistentialAddrInst *DEAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitInitExistentialValueInst(InitExistentialValueInst *IEVI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitDeinitExistentialValueInst(DeinitExistentialValueInst *DEVI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitOpenExistentialAddrInst(OpenExistentialAddrInst *OEAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitOpenExistentialValueInst(OpenExistentialValueInst *OEVI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitInitExistentialMetatypeInst(InitExistentialMetatypeInst *IEMI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitOpenExistentialMetatypeInst(OpenExistentialMetatypeInst *OEMI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitInitExistentialRefInst(InitExistentialRefInst *IERI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitOpenExistentialRefInst(OpenExistentialRefInst *OERI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitAllocExistentialBoxInst(AllocExistentialBoxInst *AEBI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitProjectExistentialBoxInst(ProjectExistentialBoxInst *PEBI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitOpenExistentialBoxInst(OpenExistentialBoxInst *OEBI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitOpenExistentialBoxValueInst(OpenExistentialBoxValueInst *OEBVI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitDeallocExistentialBoxInst(DeallocExistentialBoxInst *DEBI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                          Blocks                                             */
/*******************************************************************************/

/*******************************************************************************/
/*                          Unchecked Conversions                              */
/*******************************************************************************/

jobject InstructionVisitor::visitUpcastInst(UpcastInst *UI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitAddressToPointerInst(AddressToPointerInst *ATPI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitPointerToAddressInst(PointerToAddressInst *PTAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitUncheckedRefCastInst(UncheckedRefCastInst *URCI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitUncheckedAddrCastInst(UncheckedAddrCastInst *UACI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitUncheckedTrivialBitCastInst(UncheckedTrivialBitCastInst *BI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitUncheckedOwnershipConversionInst(UncheckedOwnershipConversionInst *UOCI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitRefToRawPointerInst(RefToRawPointerInst *CI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitRawPointerToRefInst(RawPointerToRefInst *CI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitUnmanagedToRefInst(UnmanagedToRefInst *CI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitConvertFunctionInst(ConvertFunctionInst *CFI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitThinFunctionToPointerInst(ThinFunctionToPointerInst *TFPI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitPointerToThinFunctionInst(PointerToThinFunctionInst *CI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitThinToThickFunctionInst(ThinToThickFunctionInst *TTFI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitThickToObjCMetatypeInst(ThickToObjCMetatypeInst *TTOMI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitObjCToThickMetatypeInst(ObjCToThickMetatypeInst *OTTMI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitConvertEscapeToNoEscapeInst(ConvertEscapeToNoEscapeInst *CVT) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                          Checked Conversions                                */
/*******************************************************************************/

jobject InstructionVisitor::visitUnconditionalCheckedCastAddrInst(UnconditionalCheckedCastAddrInst *CI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                          Runtime Failures                                   */
/*******************************************************************************/

jobject InstructionVisitor::visitCondFailInst(CondFailInst *FI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                           Terminators                                       */
/*******************************************************************************/

jobject InstructionVisitor::visitUnreachableInst(UnreachableInst *UI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitReturnInst(ReturnInst *RI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitThrowInst(ThrowInst *TI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitYieldInst(YieldInst *YI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitUnwindInst(UnwindInst *UI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitBranchInst(BranchInst *BI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitCondBranchInst(CondBranchInst *CBI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitSwitchValueInst(SwitchValueInst *SVI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitSelectValueInst(SelectValueInst *SVI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitSwitchEnumInst(SwitchEnumInst *SWI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitSwitchEnumAddrInst(SwitchEnumAddrInst *SEAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitCheckedCastBranchInst(CheckedCastBranchInst *CI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitCheckedCastAddrBranchInst(CheckedCastAddrBranchInst *CI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitTryApplyInst(TryApplyInst *TAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}
