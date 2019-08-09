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
#include "BuiltinFunctions.hpp"
#include "swift/AST/Module.h"
#include "swift/AST/Types.h"
#include "swift/Demangling/Demangle.h"
#include "swift/SIL/SILModule.h"
#include <fstream>
#include <memory>

using namespace swan;

void InstructionVisitor::visitSILModule(SILModule *M) {
  moduleInfo = std::make_unique<SILModuleInfo>(M->getSwiftModule()->getModuleFilename());

  valueTable = std::make_unique<ValueTable>(Instance->CAst);

  for (SILFunction &F: *M) {
    // Make sure it is valid to procede in analyzing this function.
    std::string const &demangledFunctionName = Demangle::demangleSymbolAsString(F.getName());
    if (builtinFunctions.find(demangledFunctionName) != builtinFunctions.end()) {
      continue;
    }
    if (F.empty()) {
      llvm::outs() << "WARNING: Function with empty body: " << Demangle::demangleSymbolAsString(F.getName()) << "\n";
      continue;
    }

    currentEntity = std::make_unique<WALACAstEntityInfo>();
    Instance->createCAstSourcePositionRecorder();
    visitSILFunction(&F);
    if (SWAN_PRINT) {
      currentEntity->print();
    }
    currentEntity->CAstSourcePositionRecorder = Instance->getCurrentCAstSourcePositionRecorder();
    Instance->addCAstEntityInfo(std::move(currentEntity));
  }
}

void InstructionVisitor::visitSILFunction(SILFunction *F) {
  std::string const &demangledFunctionName = Demangle::demangleSymbolAsString(F->getName());
  functionInfo = std::make_unique<SILFunctionInfo>(F->getName(), demangledFunctionName);
  currentEntity->functionName = demangledFunctionName;

  // Set function source information.
  unsigned fl, fc, ll, lc;
  // Swift compiler doesn't seem to have a way of getting the specific location of a param.
  jobject argPosition;
  if (!F->getLocation().isNull()) {
    SourceManager const &srcMgr = F->getModule().getSourceManager();
    SourceRange const &srcRange = F->getLocation().getSourceRange();
    SourceLoc const &srcStart = srcRange.Start;
    SourceLoc const &srcEnd = srcRange.End;
    if (srcStart.isInvalid() || srcEnd.isInvalid()) {
      llvm::outs() << "WARNING: Source information is invalid for function: " << demangledFunctionName;
      currentEntity->functionPosition = Instance->CAst->makeLocation(-1, -1, -1, -1);
      argPosition = currentEntity->functionPosition;
    } else {
      auto startLineCol = srcMgr.getLineAndColumn(srcStart);
      fl = startLineCol.first;
      fc = startLineCol.second;
      auto endLineCol = srcMgr.getLineAndColumn(srcEnd);
      ll = endLineCol.first;
      lc = endLineCol.second;
      currentEntity->functionPosition = Instance->CAst->makeLocation(
        static_cast<int>(fl), static_cast<int>(fc), static_cast<int>(ll), static_cast<int>(lc));
      argPosition = Instance->CAst->makeLocation(
        static_cast<int>(fl), static_cast<int>(fc), static_cast<int>(fl), static_cast<int>(fc));
    }
  } else {
    llvm::outs() << "WARNING: Source information is null for function: " << demangledFunctionName << "\n";
  }

  // Handle function arguments.
  for (SILArgument *arg: F->getArguments()) {
    if (arg->getDecl() && arg->getDecl()->hasName()) {
      valueTable->createAndAddSymbol(static_cast<ValueBase*>(arg), arg->getType().getAsString());
      currentEntity->argumentNames.push_back(addressToString(static_cast<ValueBase*>(arg)));
      currentEntity->argumentPositions.push_back(argPosition);
      currentEntity->argumentTypes.push_back(arg->getType().getAsString());
    }
  }

  // Set function result type.
  if (F->getLoweredFunctionType()->getNumResults() == 1) {
    currentEntity->returnType = F->getLoweredFunctionType()->getSingleResult().getSILStorageType().getAsString();
  } else {
    currentEntity->returnType = "MultiResultType"; // TODO: Replace with array of types or something?
  }

  if (SWAN_PRINT) {
    llvm::outs() << "SILFunction: " << "ADDR: " << F << " , NAME: " << demangledFunctionName << "\n";
    llvm::outs() << "<RAW SIL BEGIN> \n\n";
    F->print(llvm::outs(), true);
    llvm::outs() << "\n</RAW SIL END> \n\n";
  }

  // Finally, visit every basic block of the function.
  blockStmtList.clear();
  for (auto &BB: *F) {
    visitSILBasicBlock(&BB);
  }
}

void InstructionVisitor::visitSILBasicBlock(SILBasicBlock *BB) {
  if (SWAN_PRINT) {
    llvm::outs() << "Basic Block: " << BB << "\n";
    llvm::outs() << "Parent SILFunction: " << BB->getParent() << "\n";
  }

  // Clear information from previous basic block.
  InstructionCounter = 0;
  valueTable->clearNodes();
  nodeList.clear();

  // Visit every instruction of the basic block.
  for (auto &I: *BB) {
    auto Node = visit(&I);
    if (Node != nullptr) {
      if (!SWAN_ADD_EMPTY_NODES && (Instance->CAst->getKind(Node) == CAstWrapper::EMPTY))
      {
        continue;
      } else {
        nodeList.push_back(Node);
        Instance->addSourceInfo(Node, instrInfo.get());
      }
    }
  }

  // Make a new LABEL_STMT node with the SILBasicBlock # as the name, and add that node as the root
  // of the NodeList tree.
  jobject Node = Instance->CAst->makeConstant(labelBasicBlock(BB).c_str());
  jobject Stmt = Instance->CAst->makeNode(CAstWrapper::LABEL_STMT, Node);
  nodeList.push_front(Stmt);
  // Make a BLOCK_STMT node as the root of the NodeList tree.
  jobject BlockStmt = Instance->CAst->makeNode(CAstWrapper::BLOCK_STMT, Instance->CAst->makeArray(&nodeList));
  blockStmtList.push_back(BlockStmt);
  currentEntity->basicBlocks.push_back(BlockStmt);
}

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
      llvm::outs() << "\t [VALUE BASE]: " << I << "\n";
      printSILInstructionInfo();
    }
    llvm::outs() << "<< " << getSILInstructionName(I->getKind()) << " >>\n";
  }
}

void InstructionVisitor::printSILInstructionInfo() {
  llvm::outs() << "\t [INSTR] #" << instrInfo->num;
  llvm::outs() << ", [OPNUM] " << instrInfo->id << "\n";
  if (SWAN_PRINT_FILE_AND_MEMORY) {
    llvm::outs() << "\t --> File: " << instrInfo->Filename << "\n";
    if (instrInfo->srcType == SILSourceType::INVALID) {
      llvm::outs() << "\t **** No source information. \n";
    } else { // Has at least start information.
      llvm::outs() << "\t ++++ Start - Line " << instrInfo->startLine << ":"
                   << instrInfo->startCol << "\n";
    }
    // Has end information.
    if (instrInfo->srcType == SILSourceType::FULL) {
      llvm::outs() << "\t ---- End - Line " << instrInfo->endLine;
      llvm::outs() << ":" << instrInfo->endCol << "\n";
    }
    // Memory Behavior.
    switch (instrInfo->memBehavior) {
      case SILInstruction::MemoryBehavior::None: {
        break;
      }
      case SILInstruction::MemoryBehavior::MayRead: {
        llvm::outs() << "\t +++ [MEM-R]: May read from memory. \n";
        break;
      }
      case SILInstruction::MemoryBehavior::MayWrite: {
        llvm::outs() << "\t +++ [MEM-W]: May write to memory. \n";
        break;
      }
      case SILInstruction::MemoryBehavior::MayReadWrite: {
        llvm::outs() << "\t +++ [MEM-RW]: May read or write memory. \n";
        break;
      }
      case SILInstruction::MemoryBehavior::MayHaveSideEffects: {
        llvm::outs() << "\t +++ [MEM-F]: May have side effects. \n";
      }
    }
    // Releasing Behavior.
    switch (instrInfo->relBehavior) {
      case SILInstruction::ReleasingBehavior::DoesNotRelease: {
        llvm::outs() << "\t [REL]: Does not release memory. \n";
        break;
      }
      case SILInstruction::ReleasingBehavior::MayRelease: {
        llvm::outs() << "\t [REL]: May release memory. \n";
        break;
      }
    }
  }
  // Show operands, if they exist.
  for (void * const &op : instrInfo->ops) {
    llvm::outs() << "\t [OPER]: " << op << "\n";
  }
}

//===-------------------SPECIFIC INSTRUCTION VISITORS ----------------------===//

/*
 * Instructions are categorized by category and class. X-Y* where X is the
 * category identifier and Y* is the class identifier.
 *
 * Categories:
 *
 * DIRECT (D):
 *  - An instruction that is directly translated (returns a non-EMPTY CAstNode).
 *
 * INDIRECT (I):
 *  - An instruction that is not directly translated, but generates a node that
 *    is added to the ValueTable to be used later. Visit function returns EMPTY.
 *
 * INTERNAL (C):
 *  - An internal change that occurs to the translator.
 *
 * IGNORED (X):
 *  - An instruction that is completely ignored. Nothing is added to the
 *    ValueTable and EMPTY is returned. We might still print debug info.
 *
 * Classes:
 *
 * MIXED (M):
 *  - Instruction which has mixed functionality.
 *
 * DATA TRANSFER (DT):
 *  - Data is transferred in some way from the operand(s) to the result.
 *
 * DATA ENTRYPOINT (DE):
 *  - No operand.
 *  - Generates a result.
 *
 * SEVERED DATA ENTRYPOINT (SDE)
 *  - Operand(s) are ignored.
 *  - Generates a result.
 *
 * DATA COPY (DC):
 *  - Result references the same data as the operand in the ValueTable.
 *
 * DATA DESTROY (DD):
 *  - Data is removed from the ValueTable.
 *
 * Note: "operand" here means an actual value or "register" (not a type, for instance).
 *
 * An example instruction specification may look like "D-DE" which would mean
 * it is a DIRECT and DATA ENTRYPOINT instruction.
 */

/*******************************************************************************/
/*                         ALLOCATION AND DEALLOCATION                         */
/*******************************************************************************/

/* ============================================================================
 * TYPE: I-DE
 * DESC: Allocates memory, so all we care about is creating a new VAR node
 *       (of correct type) to represent the result, which may be used later.
 */
jobject InstructionVisitor::visitAllocStackInst(AllocStackInst *ASI) {
  std::string type = ASI->getType().getAsString();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [SIL TYPE]: " << type << "\n";
  }
  valueTable->createAndAddSymbol(static_cast<ValueBase*>(ASI), type);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 *  TYPE: I-DE
 * DESC: Allocates memory, so all we care about is creating a new VAR node
 *       (of correct type) to represent the result, which may be used later.
 */
jobject InstructionVisitor::visitAllocRefInst(AllocRefInst *ARI) {
  std::string type = ARI->getType().getAsString();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [SIL TYPE]: " << type << "\n";
  }
  valueTable->createAndAddSymbol(static_cast<ValueBase*>(ARI), type);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 *  TYPE: I-DE
 * DESC: Allocates memory, so all we care about is creating a new VAR node
 *       (of correct type) to represent the result, which may be used later.
 */
jobject InstructionVisitor::visitAllocRefDynamicInst(AllocRefDynamicInst *ARDI) {
  std::string type = ARDI->getType().getAsString();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [SIL TYPE]: " << type << "\n";
  }
  valueTable->createAndAddSymbol(static_cast<ValueBase*>(ARDI), type);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: I-DE
 * DESC: Allocates memory, so all we care about is creating a new VAR node
 *       (of correct type) to represent the result, which may be used later.
 */
jobject InstructionVisitor::visitAllocBoxInst(AllocBoxInst *ABI){
  std::string type = ABI->getType().getAsString();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [SIL TYPE]: " << type << "\n";
  }
  valueTable->createAndAddSymbol(static_cast<ValueBase*>(ABI), type);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: I-DC
 * DESC: Allocates space in the given address.
 */
jobject InstructionVisitor::visitAllocValueBufferInst(AllocValueBufferInst *AVBI) {
  valueTable->duplicate(AVBI->getOperand().getOpaqueValue(), static_cast<ValueBase*>(AVBI));
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: X
 * DESC: Initializes storage for a global variable. Has no result or value
 * operand so we don't do anything except print some debug info.
 */
jobject InstructionVisitor::visitAllocGlobalInst(AllocGlobalInst *AGI) {
  SILGlobalVariable *Var = AGI->getReferencedGlobal();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [VAR NAME]:" << Demangle::demangleSymbolAsString(Var->getName()) << "\n";
    llvm::outs() << "\t [VAR TYPE]:" << Var->getLoweredType().getAsString() << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: X
 * DESC: Deallocates memory. We don't care about this.
 */
jobject InstructionVisitor::visitDeallocStackInst(__attribute__((unused)) DeallocStackInst *DSI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: X
 * DESC: Deallocates memory. We don't care about this.
 */
jobject InstructionVisitor::visitDeallocBoxInst(__attribute__((unused)) DeallocBoxInst *DBI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: I-DC
 * DESC: Gets an address from @box reference. We treat references and addresses
 *       as the same thing.
 */
jobject InstructionVisitor::visitProjectBoxInst(ProjectBoxInst *PBI) {
  valueTable->duplicate(PBI->getOperand().getOpaqueValue(), static_cast<ValueBase*>(PBI));
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: X
 * DESC: Deallocates memory. We don't care about this.
 */
jobject InstructionVisitor::visitDeallocRefInst(__attribute__((unused)) DeallocRefInst *DRI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: X
 * DESC: Deallocates memory. We don't care about this.
 */
jobject InstructionVisitor::visitDeallocPartialRefInst(__attribute__((unused)) DeallocPartialRefInst *DPRI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: X
 * DESC: Deallocates memory. We don't care about this.
 */
jobject InstructionVisitor::visitDeallocValueBufferInst(__attribute__((unused)) DeallocValueBufferInst *DVBI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: X
 * DESC: Deallocates memory. We don't care about this.
 */
jobject InstructionVisitor::visitProjectValueBufferInst(__attribute__((unused)) ProjectValueBufferInst *PVBI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                        DEBUG INFROMATION                                    */
/*******************************************************************************/

/* ============================================================================
 * TYPE: X
 * DESC: This indicates a value has changed. We don't care about this.
 */
jobject InstructionVisitor::visitDebugValueInst(__attribute__((unused)) DebugValueInst *DBI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: X
 * DESC: This indicates a value has changed. We don't care about this.
 */
jobject InstructionVisitor::visitDebugValueAddrInst(__attribute__((unused)) DebugValueAddrInst *DVAI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                        Accessing Memory                                     */
/*******************************************************************************/

/* ============================================================================
 * TYPE: I-DC
 * DESC: Loads a value from the operand address.
 */
jobject InstructionVisitor::visitLoadInst(LoadInst *LI) {
  valueTable->duplicate(LI->getOperand().getOpaqueValue(), static_cast<ValueBase*>(LI));
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: D-DT
 * DESC: Stores a value to a memory address. This is just an ASSIGN.
 */
jobject InstructionVisitor::visitStoreInst(StoreInst *SI) {
  void* src = SI->getSrc().getOpaqueValue();
  void* dest = SI->getDest().getOpaqueValue();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(src), valueTable->get(dest));
}

/* ============================================================================
 * TYPE: I-DC
 * DESC: Similar to borrow, but creates a scope. We don't care about the scope
 *       since our ValueTable is persistent across the whole module.
 */
jobject InstructionVisitor::visitLoadBorrowInst(LoadBorrowInst *LBI) {
  valueTable->duplicate(LBI->getOperand().getOpaqueValue(), static_cast<ValueBase*>(LBI));
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: C-DD
 * DESC: Ends the borrowed scope. We should remove the associated data.
 */
jobject InstructionVisitor::visitEndBorrowInst(EndBorrowInst *EBI) {
  valueTable->tryRemove(EBI->getOperand().getOpaqueValue());
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: D-DT
 * DESC: Similar to store. This is just an ASSIGN.
 */
jobject InstructionVisitor::visitAssignInst(AssignInst *AI) {
  void* src = AI->getSrc().getOpaqueValue();
  void* dest = AI->getDest().getOpaqueValue();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(src), valueTable->get(dest));
}

/* ============================================================================
 * TYPE: D-M
 * DESC: Delegates an assignment with a conditional functional call. The condition
 *       here is implicit in the SIL, so we just make an arbitrary condition.
 */
jobject InstructionVisitor::visitAssignByWrapperInst(AssignByWrapperInst *ABWI) {
  jobject initFunc = valueTable->get(ABWI->getInitializer().getOpaqueValue());
  assert(Instance->CAst->getKind(initFunc) == CAstWrapper::FUNCTION_EXPR);
  jobject setFunc = valueTable->get(ABWI->getSetter().getOpaqueValue());
  assert(Instance->CAst->getKind(setFunc) == CAstWrapper::FUNCTION_EXPR);
  jobject param = valueTable->get(ABWI->getOperand(0).getOpaqueValue());
  jobject dest = valueTable->get(ABWI->getOperand(1).getOpaqueValue());
  jobject initCall = Instance->CAst->makeNode(CAstWrapper::CALL, initFunc, DO_NODE, param);
  jobject setCall = Instance->CAst->makeNode(CAstWrapper::CALL, setFunc, DO_NODE, param);
  jobject initAssign = Instance->CAst->makeNode(CAstWrapper::ASSIGN, dest, initCall);
  jobject setAssign = Instance->CAst->makeNode(CAstWrapper::ASSIGN, dest, setCall);
  jobject arbCondition = Instance->CAst->makeConstant("initOrSet");
  if (SWAN_PRINT) {
    llvm::outs() << "\t [SRC ADDR]: " << ABWI->getOperand(0).getOpaqueValue() << "\n";
    llvm::outs() << "\t [DEST ADDR]: " << ABWI->getOperand(1).getOpaqueValue() << "\n";
    llvm::outs() << "\t [INIT FUNC]: " << ABWI->getOperand(2).getOpaqueValue() << "\n";
    llvm::outs() << "\t [SET FUNC]: " << ABWI->getOperand(3).getOpaqueValue() << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::IF_STMT, arbCondition, initAssign, setAssign);
}

/* ============================================================================
 * TYPE: I-DC
 * DESC: Just marks a memory location is unitialized.
 */
jobject InstructionVisitor::visitMarkUninitializedInst(MarkUninitializedInst *MUI) {
  valueTable->duplicate(MUI->getOperand().getOpaqueValue(), static_cast<ValueBase*>(MUI));
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: I-DC
 * DESC: Similar to mark_unitialized, and all that's different (that we care
 *      about) here is that is has multiple operands.
 */
jobject InstructionVisitor::visitMarkFunctionEscapeInst(MarkFunctionEscapeInst *MFEI) {
  for (Operand &op: MFEI->getAllOperands()) {
      valueTable->duplicate(op.get().getOpaqueValue(), MFEI->getResult(0).getOpaqueValue());
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: D-DT
 * DESC: Basically a compilcated (under-the-hood) store.
 */
jobject InstructionVisitor::visitCopyAddrInst(CopyAddrInst *CAI) {
  void* src = CAI->getSrc().getOpaqueValue();
  void* dest = CAI->getDest().getOpaqueValue();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(src), valueTable->get(dest));
}

/* ============================================================================
 * TYPE: C-DD
 * DESC: Destroys a valuein memory at the operand address. We should probably
 *       remove any data associated with this address from the ValueTable.
 */
jobject InstructionVisitor::visitDestroyAddrInst(DestroyAddrInst *DAI) {
  void* operand = DAI->getOperand().getOpaqueValue();
  if (SWAN_PRINT) {
      llvm::outs() << "\t [ADDR TO DESTROY]: " << operand << "\n";
  }
  valueTable->tryRemove(operand);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: D-M
 * DESC: Index a given address (array of values). This is an ASSIGN + ARRAY_REF.
 */
jobject InstructionVisitor::visitIndexAddrInst(IndexAddrInst *IAI) {
  void* baseAddr = IAI->getBase().getOpaqueValue();
  void* indexAddr = IAI->getIndex().getOpaqueValue();
  jobject ArrayObj = valueTable->get(baseAddr);
  assert(Instance->CAst->getKind(ArrayObj) == CAstWrapper::ARRAY_LITERAL);
  jobject IndexVar = valueTable->get(indexAddr);
  assert(Instance->CAst->getKind(IndexVar) == CAstWrapper::VAR);
  jobject ArrayRef = Instance->CAst->makeNode(CAstWrapper::ARRAY_REF, ArrayObj, IndexVar);
  valueTable->createAndAddSymbol(IAI->getOperand(0), IAI->getType().getAsString());
  jobject ResultNode = valueTable->get(IAI->getOperand(0));
  if (SWAN_PRINT) {
    llvm::outs() << "\t [BASE ADDR]" << baseAddr << "\n";
    llvm::outs() << "\t [INDEX ADDR]" << indexAddr << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN, ResultNode, ArrayRef);
}

/* ============================================================================
 * TYPE: D-M
 * DESC: Similar to index_addr. We don't care about memory alignment so this is
 *      this same as index_addr as far as we are concerned.
 *      Again, this is an ASSIGN + ARRAY_REF.
 */
jobject InstructionVisitor::visitTailAddrInst(TailAddrInst *TAI) {
  void* baseAddr = TAI->getBase().getOpaqueValue();
  void* indexAddr = TAI->getIndex().getOpaqueValue();
  jobject ArrayObj = valueTable->get(baseAddr);
  assert(Instance->CAst->getKind(ArrayObj) == CAstWrapper::ARRAY_LITERAL);
  jobject IndexVar = valueTable->get(indexAddr);
  assert(Instance->CAst->getKind(IndexVar) == CAstWrapper::VAR);
  jobject ArrayRef = Instance->CAst->makeNode(CAstWrapper::ARRAY_REF, ArrayObj, IndexVar);
  valueTable->createAndAddSymbol(TAI->getOperand(0), TAI->getType().getAsString());
  jobject ResultNode = valueTable->get(TAI->getOperand(0));
  if (SWAN_PRINT) {
    llvm::outs() << "\t [BASE ADDR]" << baseAddr << "\n";
    llvm::outs() << "\t [INDEX ADDR]" << indexAddr << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN, ResultNode, ArrayRef);
}

/* ============================================================================
 * TYPE: I-DC
 * DESC: Returns a pointer value at a given byte offset. We don't care about
 *       this offset.
 */
jobject InstructionVisitor::visitIndexRawPointerInst(IndexRawPointerInst *IRPI) {
  valueTable->duplicate(IRPI->getOperand(0).getOpaqueValue(), IRPI->getResult(0).getOpaqueValue());
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: X
 * DESC: Binds memory to a type to hold a given capacity. We don't care about this.
 */
jobject InstructionVisitor::visitBindMemoryInst(__attribute__((unused)) BindMemoryInst *BMI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: I-DC
 * DESC: Similar to load_borrow.
 */
jobject InstructionVisitor::visitBeginAccessInst(BeginAccessInst *BAI) {
  valueTable->duplicate(BAI->getOperand().getOpaqueValue(), static_cast<ValueBase*>(BAI));
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: C-DD
 * DESC: Ends the access. Similar to end_borrow.
 */
jobject InstructionVisitor::visitEndAccessInst(EndAccessInst *EAI) {
  valueTable->tryRemove(EAI->getOperand().getOpaqueValue());
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: I-DC
 * DESC: Slightly more complicated begin_access.
 * TODO: What is the result used for?
 */
jobject InstructionVisitor::visitBeginUnpairedAccessInst(BeginUnpairedAccessInst *BUI) {
  void* source = BUI->getSource().getOpaqueValue();
  void* buffer = BUI->getBuffer().getOpaqueValue();
  valueTable->duplicate(source, buffer);
  if (SWAN_PRINT) {
    llvm::outs() << "\t [ACCESSING]: " << source << "\n";
    llvm::outs() << "\t [BUFFER ID]: " << buffer << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: C-DD
 * DESC: Similar to end_borrow/end_access. We want to remove associated data.
 * TODO: What is the result used for?
 */
jobject InstructionVisitor::visitEndUnpairedAccessInst(EndUnpairedAccessInst *EUAI) {
  valueTable->tryRemove(EUAI->getOperand().getOpaqueValue());
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                        Reference Counting                                   */
/*******************************************************************************/

/* ============================================================================
 * TYPE: X
 */
jobject InstructionVisitor::visitStrongRetainInst(__attribute__((unused)) StrongRetainInst *SRTI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: X
 */
jobject InstructionVisitor::visitStrongReleaseInst(__attribute__((unused)) StrongReleaseInst *SRLI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: X
 */
jobject InstructionVisitor::visitSetDeallocatingInst(__attribute__((unused)) SetDeallocatingInst *SDI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: X
 */
jobject InstructionVisitor::visitStrongRetainUnownedInst(__attribute__((unused)) StrongRetainUnownedInst *SRUI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: X
 */
jobject InstructionVisitor::visitUnownedRetainInst(__attribute__((unused)) UnownedRetainInst *URTI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: X
 */
jobject InstructionVisitor::visitUnownedReleaseInst(__attribute__((unused)) UnownedReleaseInst *URLI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: X
 */
jobject InstructionVisitor::visitLoadWeakInst(__attribute__((unused)) LoadWeakInst *LWI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: D-DT
 * DESC: Similar to store.
 */
jobject InstructionVisitor::visitStoreWeakInst(StoreWeakInst *SWI) {
  void* src = SWI->getSrc().getOpaqueValue();
  void* dest = SWI->getDest().getOpaqueValue();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(src), valueTable->get(dest));
}

/* ============================================================================
 * TYPE: X
 * NOTE: No description in SIL.rst so may be incorrect.
 */
jobject InstructionVisitor::visitLoadUnownedInst(__attribute__((unused)) LoadUnownedInst *LUI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: X
 * NOTE: No description in SIL.rst so may be incorrect.
 */
jobject InstructionVisitor::visitStoreUnownedInst(StoreUnownedInst *SUI) {
  void* src = SUI->getSrc().getOpaqueValue();
  void* dest = SUI->getDest().getOpaqueValue();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(src), valueTable->get(dest));
}

/* ============================================================================
 * TYPE: X
 */
jobject InstructionVisitor::visitFixLifetimeInst(__attribute__((unused)) FixLifetimeInst *FLI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: I-DC
 * DESC: Marks dependency between two operands, but we don't care. We just know
 *       that the result is always equal to the first operand. So we duplicate.
 */
jobject InstructionVisitor::visitMarkDependenceInst(MarkDependenceInst *MDI) {
  valueTable->duplicate(MDI->getOperand(0).getOpaqueValue(), static_cast<ValueBase*>(MDI));
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: I-DC
 * DESC: The result is a boolean based on if the operand is a unique reference.
 *       As far as data flow analysis is concerned, and because the boolean is
 *       directly dependent on the operand, the result is equal to the operand.
 */
jobject InstructionVisitor::visitIsUniqueInst(IsUniqueInst *IUI) {
  valueTable->duplicate(IUI->getOperand().getOpaqueValue(), static_cast<ValueBase*>(IUI));
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: I-DC
 * DESC: Once again, boolean based off operand. Duplicate.
 */
jobject InstructionVisitor::visitIsEscapingClosureInst(IsEscapingClosureInst *IECI) {
  valueTable->duplicate(IECI->getOperand().getOpaqueValue(), static_cast<ValueBase*>(IECI));
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: I-DC
 * DESC: Copies Obj-C block. Duplicate.
 */
jobject InstructionVisitor::visitCopyBlockInst(CopyBlockInst *CBI) {
  valueTable->duplicate(CBI->getOperand().getOpaqueValue(), static_cast<ValueBase*>(CBI));
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: I-M
 * DESC: Similar to copy_block, but also consumes the second operand.
 */
jobject InstructionVisitor::visitCopyBlockWithoutEscapingInst(CopyBlockWithoutEscapingInst *CBWEI) {
  valueTable->duplicate(CBWEI->getOperand(0).getOpaqueValue(), static_cast<ValueBase*>(CBWEI));
  valueTable->tryRemove(CBWEI->getOperand(1).getOpaqueValue());
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * TYPE: C-DD
 * DESC: Not in SIL.rst. Assume we just need to destroy the data associated
 *       with the operand.
 */
jobject InstructionVisitor::visitEndLifetimeInst(EndLifetimeInst *ELI) {
  valueTable->tryRemove(ELI->getOperand().getOpaqueValue());
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                         Literals                                            */
/*******************************************************************************/

jobject InstructionVisitor::visitFunctionRefInst(FunctionRefInst *FRI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitGlobalAddrInst(GlobalAddrInst *GAI) {
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

jobject InstructionVisitor::visitEndApplyInst(EndApplyInst *EAI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitAbortApplyInst(AbortApplyInst *AAI) {
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

/*******************************************************************************/
/*                          Aggregate Types                                    */
/*******************************************************************************/

jobject InstructionVisitor::visitCopyValueInst(CopyValueInst *CVI) {
  // TODO: UNIMPLEMENTED
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject InstructionVisitor::visitDestroyValueInst(DestroyValueInst *DVI) {
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
