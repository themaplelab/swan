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
  valueTable = std::make_unique<ValueTable>(Instance->CAst);

  for (SILFunction &F: *M) {
    // Make sure it is valid to procede in analyzing this function.
    std::string const &demangledFunctionName = Demangle::demangleSymbolAsString(F.getName());

    if (F.empty()) { // Most likely a builtin, so we ignore it.
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
  for (auto &BB: *F) {
    visitSILBasicBlock(&BB);
  }
}

void InstructionVisitor::visitSILBasicBlock(SILBasicBlock *BB) {
  if (SWAN_PRINT) {
    llvm::outs() << "Basic Block: " << BB << "\n";
    llvm::outs() << "Parent SILFunction: " << BB->getParent() << "\n";
  }

  // Clear information from previous basic block. Symbols in the ValueTable
  // are persistent across the module due to scoped access instructions.
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

  // Add all DECL_STMT nodes from the ValueTable to the beginning of the basic block.
  for (jobject declNode: valueTable->getDeclNodes()) {
    nodeList.push_front(declNode);
  }

  // Make a new LABEL_STMT node with the SILBasicBlock # as the name, and add that node as the root
  // of the NodeList tree.
  jobject Node = Instance->CAst->makeConstant(labelBasicBlock(BB).c_str());
  jobject Stmt = Instance->CAst->makeNode(CAstWrapper::LABEL_STMT, Node);
  nodeList.push_front(Stmt);
  // Make a BLOCK_STMT node as the root of the NodeList tree.
  jobject BlockStmt = Instance->CAst->makeNode(CAstWrapper::BLOCK_STMT, Instance->CAst->makeArray(&nodeList));
  currentEntity->basicBlocks.push_back(BlockStmt);
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

/* ============================================================================
 * DESC: Allocates memory, so all we care about is creating a new VAR node
 *       (of correct type) to represent the result, which may be used later.
 */
jobject InstructionVisitor::visitAllocStackInst(AllocStackInst *ASI) {
  std::string type = ASI->getType().getAsString();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [ALLOC TYPE]: " << type << "\n";
  }
  valueTable->createAndAddSymbol(static_cast<ValueBase*>(ASI), type);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Allocates memory, so all we care about is creating a new VAR node
 *       (of correct type) to represent the result, which may be used later.
 */
jobject InstructionVisitor::visitAllocRefInst(AllocRefInst *ARI) {
  std::string type = ARI->getType().getAsString();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [ALLOC TYPE]: " << type << "\n";
  }
  valueTable->createAndAddSymbol(static_cast<ValueBase*>(ARI), type);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Allocates memory, so all we care about is creating a new VAR node
 *       (of correct type) to represent the result, which may be used later.
 */
jobject InstructionVisitor::visitAllocRefDynamicInst(AllocRefDynamicInst *ARDI) {
  std::string type = ARDI->getType().getAsString();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [ALLOC TYPE]: " << type << "\n";
  }
  valueTable->createAndAddSymbol(static_cast<ValueBase*>(ARDI), type);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Allocates memory, so all we care about is creating a new VAR node
 *       (of correct type) to represent the result, which may be used later.
 */
jobject InstructionVisitor::visitAllocBoxInst(AllocBoxInst *ABI){
  std::string type = ABI->getType().getAsString();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [ALLOC TYPE]: " << type << "\n";
  }
  valueTable->createAndAddSymbol(static_cast<ValueBase*>(ABI), type);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Allocates space in the given address.
 */
jobject InstructionVisitor::visitAllocValueBufferInst(AllocValueBufferInst *AVBI) {
  void* src = AVBI->getOperand().getOpaqueValue();
  void* dest = static_cast<ValueBase*>(AVBI);
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(dest), valueTable->get(src));
}

/* ============================================================================
 * DESC: Initializes storage for a global variable. Has no result or value
 *       operand so we don't do anything except print some debug info.
 */
jobject InstructionVisitor::visitAllocGlobalInst(AllocGlobalInst *AGI) {
  SILGlobalVariable *Var = AGI->getReferencedGlobal();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [ALLOC NAME]:" << Demangle::demangleSymbolAsString(Var->getName()) << "\n";
    llvm::outs() << "\t [ALLOC TYPE]:" << Var->getLoweredType().getAsString() << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Deallocates memory.
 */
jobject InstructionVisitor::visitDeallocStackInst(DeallocStackInst *DSI) {
  void* toRemove = DSI->getOperand().getOpaqueValue();
  (valueTable->tryRemove(toRemove))
    ? llvm::outs() << "\t [REMOVED ADDR]: " << toRemove << "\n"
    : llvm::outs() << "\t [NOP]\n";
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Deallocates memory.
 */
jobject InstructionVisitor::visitDeallocBoxInst(DeallocBoxInst *DBI) {
  void* toRemove = DBI->getOperand().getOpaqueValue();
  (valueTable->tryRemove(toRemove))
    ? llvm::outs() << "\t [REMOVED ADDR]: " << toRemove << "\n"
    : llvm::outs() << "\t [NOP]\n";
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Gets an address from @box reference.
 */
jobject InstructionVisitor::visitProjectBoxInst(ProjectBoxInst *PBI) {
  void* src = PBI->getOperand().getOpaqueValue();
  void* dest = static_cast<ValueBase*>(PBI);
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(dest), valueTable->get(src));
}

/* ============================================================================
 * DESC: Deallocates memory.
 */
jobject InstructionVisitor::visitDeallocRefInst(DeallocRefInst *DRI) {
  void* toRemove = DRI->getOperand().getOpaqueValue();
  (valueTable->tryRemove(toRemove))
    ? llvm::outs() << "\t [REMOVED ADDR]: " << toRemove << "\n"
    : llvm::outs() << "\t [NOP]\n";
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Deallocates memory. TODO: What to do about sil-metatype operand?
 */
jobject InstructionVisitor::visitDeallocPartialRefInst(DeallocPartialRefInst *DPRI) {
  void* toRemove = DPRI->getOperand(0).getOpaqueValue();
  (valueTable->tryRemove(toRemove))
    ? llvm::outs() << "\t [REMOVED ADDR]: " << toRemove << "\n"
    : llvm::outs() << "\t [NOP]\n";
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Deallocates memory.
 */
jobject InstructionVisitor::visitDeallocValueBufferInst(DeallocValueBufferInst *DVBI) {
  void* toRemove = DVBI->getOperand().getOpaqueValue();
  (valueTable->tryRemove(toRemove))
    ? llvm::outs() << "\t [REMOVED ADDR]: " << toRemove << "\n"
    : llvm::outs() << "\t [NOP]\n";
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Deallocates memory.
 */
jobject InstructionVisitor::visitProjectValueBufferInst(ProjectValueBufferInst *PVBI) {
  void* src = PVBI->getOperand().getOpaqueValue();
  void* dest = static_cast<ValueBase*>(PVBI);
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(dest), valueTable->get(src));
}

/*******************************************************************************/
/*                        DEBUG INFROMATION                                    */
/*******************************************************************************/

/* ============================================================================
 * DESC: This indicates a value has changed.
 */
jobject InstructionVisitor::visitDebugValueInst(__attribute__((unused)) DebugValueInst *DBI) {
  llvm::outs() << "\t [NOP]\n";
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: This indicates a value has changed. We don't care about this.
 */
jobject InstructionVisitor::visitDebugValueAddrInst(__attribute__((unused)) DebugValueAddrInst *DVAI) {
  llvm::outs() << "\t [NOP]\n";
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                        Accessing Memory                                     */
/*******************************************************************************/

/* ============================================================================
 * DESC: Loads a value from the operand address.
 */
jobject InstructionVisitor::visitLoadInst(LoadInst *LI) {
  void* src = LI->getOperand().getOpaqueValue();
  void* dest = static_cast<ValueBase*>(LI);
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(dest), valueTable->get(src));
}

/* ============================================================================
 * DESC: Stores a value to a memory address. This is just an ASSIGN.
 */
jobject InstructionVisitor::visitStoreInst(StoreInst *SI) {
  void* src = SI->getSrc().getOpaqueValue();
  void* dest = SI->getDest().getOpaqueValue();
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(dest), valueTable->get(src));
}

/* ============================================================================
 * DESC: Similar to borrow, but creates a scope. We don't care about the scope
 *       since our ValueTable is persistent across the whole module.
 */
jobject InstructionVisitor::visitLoadBorrowInst(LoadBorrowInst *LBI) {
  void* src = LBI->getOperand().getOpaqueValue();
  void* dest = static_cast<ValueBase*>(LBI);
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(dest), valueTable->get(src));
}

/* ============================================================================
 * DESC: Ends the borrowed scope. We should remove the associated data.
 */
jobject InstructionVisitor::visitEndBorrowInst(EndBorrowInst *EBI) {
  void* toRemove = EBI->getOperand().getOpaqueValue();
  (valueTable->tryRemove(toRemove))
    ? llvm::outs() << "\t [REMOVED ADDR]: " << toRemove << "\n"
    : llvm::outs() << "\t [NOP]\n";
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Similar to store. This is just an ASSIGN.
 */
jobject InstructionVisitor::visitAssignInst(AssignInst *AI) {
  void* src = AI->getSrc().getOpaqueValue();
  void* dest = AI->getDest().getOpaqueValue();
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(dest), valueTable->get(src));
}

/* ============================================================================
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
  // TODO: Replace with custom operator. (e.g. OP_IS_INIT)
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
 * DESC: Just marks a memory location is unitialized.
 */
jobject InstructionVisitor::visitMarkUninitializedInst(MarkUninitializedInst *MUI) {
  void* src = MUI->getOperand().getOpaqueValue();
  void* dest = static_cast<ValueBase*>(MUI);
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(dest), valueTable->get(src));
}

/* ============================================================================
 * DESC: Similar to mark_unitialized, and all that's different (that we care
 *      about) here is that is has multiple operands.
 */
jobject InstructionVisitor::visitMarkFunctionEscapeInst(MarkFunctionEscapeInst *MFEI) {
  void* dest = MFEI->getResult(0).getOpaqueValue();
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";  }
  for (Operand &op: MFEI->getAllOperands()) {
    void* src = op.get().getOpaqueValue();
    if (SWAN_PRINT) {
      llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    }
    jobject assign = Instance->CAst->makeNode(CAstWrapper::ASSIGN,
      valueTable->get(dest), valueTable->get(src));
    nodeList.push_back(assign);
  }
  if (SWAN_PRINT) {
    llvm::outs() << "\t\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Basically a compilcated (under-the-hood) store.
 */
jobject InstructionVisitor::visitCopyAddrInst(CopyAddrInst *CAI) {
  void* src = CAI->getSrc().getOpaqueValue();
  void* dest = CAI->getDest().getOpaqueValue();
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(dest), valueTable->get(src));
}

/* ============================================================================
 * DESC: Destroys a value in memory at the operand address.
 */
jobject InstructionVisitor::visitDestroyAddrInst(DestroyAddrInst *DAI) {
  void* toRemove = DAI->getOperand().getOpaqueValue();
  (valueTable->tryRemove(toRemove))
    ? llvm::outs() << "\t [REMOVED ADDR]: " << toRemove << "\n"
    : llvm::outs() << "\t [NOP]\n";
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Index a given address (array of values). This is an ASSIGN + OBJECT_REF.
 */
jobject InstructionVisitor::visitIndexAddrInst(IndexAddrInst *IAI) {
  void* baseAddr = IAI->getBase().getOpaqueValue();
  void* indexAddr = IAI->getIndex().getOpaqueValue();
  void* result = static_cast<ValueBase*>(IAI);
  jobject ArrayObj = valueTable->get(baseAddr);
  assert(Instance->CAst->getKind(ArrayObj) == CAstWrapper::OBJECT_LITERAL);
  jobject IndexVar = valueTable->get(indexAddr);
  assert(Instance->CAst->getKind(IndexVar) == CAstWrapper::VAR);
  jobject ArrayRef = Instance->CAst->makeNode(CAstWrapper::OBJECT_REF, ArrayObj, IndexVar);
  valueTable->createAndAddSymbol(result, IAI->getType().getAsString());
  jobject ResultNode = valueTable->get(result);
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [DEST ADDR]: " << result << "\n";
    llvm::outs() << "\t [BASE ADDR]" << baseAddr << "\n";
    llvm::outs() << "\t [INDEX ADDR]" << indexAddr << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN, ResultNode, ArrayRef);
}

/* ============================================================================
 * DESC: Similar to index_addr. We don't care about memory alignment so this is
 *      this same as index_addr as far as we are concerned.
 *      Again, this is an ASSIGN + OBJECT_REF.
 */
jobject InstructionVisitor::visitTailAddrInst(TailAddrInst *TAI) {
  void* baseAddr = TAI->getBase().getOpaqueValue();
  void* indexAddr = TAI->getIndex().getOpaqueValue();
  void* result = static_cast<ValueBase*>(TAI);
  jobject ArrayObj = valueTable->get(baseAddr);
  assert(Instance->CAst->getKind(ArrayObj) == CAstWrapper::OBJECT_LITERAL);
  jobject IndexVar = valueTable->get(indexAddr);
  assert(Instance->CAst->getKind(IndexVar) == CAstWrapper::VAR);
  jobject ArrayRef = Instance->CAst->makeNode(CAstWrapper::OBJECT_REF, ArrayObj, IndexVar);
  valueTable->createAndAddSymbol(result, TAI->getType().getAsString());
  jobject ResultNode = valueTable->get(result);
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [DEST ADDR]: " << result << "\n";
    llvm::outs() << "\t [BASE ADDR]" << baseAddr << "\n";
    llvm::outs() << "\t [INDEX ADDR]" << indexAddr << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN, ResultNode, ArrayRef);
}

/* ============================================================================
 * DESC: Returns a pointer value at a given byte offset. The offset is irrelevant.
 */
jobject InstructionVisitor::visitIndexRawPointerInst(IndexRawPointerInst *IRPI) {
  void* src = IRPI->getOperand(0).getOpaqueValue();
  void* dest = IRPI->getResult(0).getOpaqueValue();
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(dest), valueTable->get(src));
}

/* ============================================================================
 * DESC: Binds memory to a type to hold a given capacity.
 */
jobject InstructionVisitor::visitBindMemoryInst(__attribute__((unused)) BindMemoryInst *BMI) {
  llvm::outs() << "\t [NOP]\n";
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Similar to load_borrow.
 */
jobject InstructionVisitor::visitBeginAccessInst(BeginAccessInst *BAI) {
  void* src = BAI->getOperand().getOpaqueValue();
  void* dest = static_cast<ValueBase*>(BAI);
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(dest), valueTable->get(src));
}

/* ============================================================================
 * DESC: Ends the access. Similar to end_borrow.
 */
jobject InstructionVisitor::visitEndAccessInst(EndAccessInst *EAI) {
  void* toRemove = EAI->getOperand().getOpaqueValue();
  (valueTable->tryRemove(toRemove))
    ? llvm::outs() << "\t [REMOVED ADDR]: " << toRemove << "\n"
    : llvm::outs() << "\t [NOP]\n";
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Slightly more complicated begin_access.
 * TODO: What is the result used for?
 */
jobject InstructionVisitor::visitBeginUnpairedAccessInst(BeginUnpairedAccessInst *BUI) {
  void* source = BUI->getSource().getOpaqueValue();
  void* buffer = BUI->getBuffer().getOpaqueValue();
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SOURCE ADDR]: " << source << "\n";
    llvm::outs() << "\t [BUFFER ADDR]: " << buffer << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(source), valueTable->get(buffer));
}

/* ============================================================================
 * DESC: Similar to end_borrow/end_access. We want to remove associated data.
 * TODO: What is the result used for?
 */
jobject InstructionVisitor::visitEndUnpairedAccessInst(EndUnpairedAccessInst *EUAI) {
  void* toRemove = EUAI->getOperand().getOpaqueValue();
  (valueTable->tryRemove(toRemove))
    ? llvm::outs() << "\t [REMOVED ADDR]: " << toRemove << "\n"
    : llvm::outs() << "\t [NOP]\n";
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                        Reference Counting                                   */
/*******************************************************************************/

/* ============================================================================
 */
jobject InstructionVisitor::visitStrongRetainInst(__attribute__((unused)) StrongRetainInst *SRTI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 */
jobject InstructionVisitor::visitStrongReleaseInst(__attribute__((unused)) StrongReleaseInst *SRLI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 */
jobject InstructionVisitor::visitSetDeallocatingInst(__attribute__((unused)) SetDeallocatingInst *SDI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 */
jobject InstructionVisitor::visitStrongRetainUnownedInst(__attribute__((unused)) StrongRetainUnownedInst *SRUI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 */
jobject InstructionVisitor::visitUnownedRetainInst(__attribute__((unused)) UnownedRetainInst *URTI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 */
jobject InstructionVisitor::visitUnownedReleaseInst(__attribute__((unused)) UnownedReleaseInst *URLI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 */
jobject InstructionVisitor::visitLoadWeakInst(__attribute__((unused)) LoadWeakInst *LWI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Similar to store.
 */
jobject InstructionVisitor::visitStoreWeakInst(StoreWeakInst *SWI) {
  void* src = SWI->getSrc().getOpaqueValue();
  void* dest = SWI->getDest().getOpaqueValue();
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(dest), valueTable->get(src));
}

/* ============================================================================
 * NOTE: No description in SIL.rst so may be incorrect.
 */
jobject InstructionVisitor::visitLoadUnownedInst(LoadUnownedInst *LUI) {
  void* src = LUI->getOperand().getOpaqueValue();
  void* dest = static_cast<ValueBase*>(LUI);
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(dest), valueTable->get(src));
}

/* ============================================================================
 * NOTE: No description in SIL.rst so may be incorrect.
 */
jobject InstructionVisitor::visitStoreUnownedInst(StoreUnownedInst *SUI) {
  void* src = SUI->getSrc().getOpaqueValue();
  void* dest = SUI->getDest().getOpaqueValue();
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(dest), valueTable->get(src));
}

/* ============================================================================
 */
jobject InstructionVisitor::visitFixLifetimeInst(__attribute__((unused)) FixLifetimeInst *FLI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Marks dependency between two operands, but we don't care. We just know
 *       that the result is always equal to the first operand.
 */
jobject InstructionVisitor::visitMarkDependenceInst(MarkDependenceInst *MDI) {
  void* src = MDI->getOperand(0).getOpaqueValue();
  void* dest = static_cast<ValueBase*>(MDI);
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(dest), valueTable->get(src));
}

/* ============================================================================
 * DESC: The result is a boolean based on if the operand is a unique reference.
 */
jobject InstructionVisitor::visitIsUniqueInst(IsUniqueInst *IUI) {
  // TODO: replace with custom operator that checks reference count. (e.g. OP_REF_CNT)
  void* src = IUI->getOperand().getOpaqueValue();
  void* dest = static_cast<ValueBase*>(IUI);
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(dest), valueTable->get(src));
}

/* ============================================================================
 * DESC: Once again, boolean based off operand.
 */
jobject InstructionVisitor::visitIsEscapingClosureInst(IsEscapingClosureInst *IECI) {
  // TODO: replace with custom operator that checks reference count. (e.g. OP_REF_CNT)
  void* src = IECI->getOperand().getOpaqueValue();
  void* dest = static_cast<ValueBase*>(IECI);
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(dest), valueTable->get(src));
}

/* ============================================================================
 * DESC: Copies Obj-C block.
 */
jobject InstructionVisitor::visitCopyBlockInst(CopyBlockInst *CBI) {
  void* src = CBI->getOperand().getOpaqueValue();
  void* dest = static_cast<ValueBase*>(CBI);
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(dest), valueTable->get(src));
}

/* ============================================================================
 * DESC: Similar to copy_block, but also consumes the second operand.
 */
jobject InstructionVisitor::visitCopyBlockWithoutEscapingInst(CopyBlockWithoutEscapingInst *CBWEI) {
  void* src = CBWEI->getOperand(0).getOpaqueValue();
  void* dest = static_cast<ValueBase*>(CBWEI);
  if (SWAN_PRINT) {
    llvm::outs() << "\t Assignment\n";
    llvm::outs() << "\t [SRC ADDR]: " << src<< "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  void* toRemove = CBWEI->getOperand(1).getOpaqueValue();
  (valueTable->tryRemove(toRemove))
    ? llvm::outs() << "\t [REMOVED ADDR]: " << toRemove << "\n"
    : llvm::outs() << "\t [NOP]\n";
  return Instance->CAst->makeNode(CAstWrapper::ASSIGN,
    valueTable->get(dest), valueTable->get(src));
}

/* ============================================================================
 * DESC: Not in SIL.rst. Assume we just need to destroy the data associated
 *       with the operand.
 */
jobject InstructionVisitor::visitEndLifetimeInst(EndLifetimeInst *ELI) {
  void* toRemove = ELI->getOperand().getOpaqueValue();
  (valueTable->tryRemove(toRemove))
    ? llvm::outs() << "\t [REMOVED ADDR]: " << toRemove << "\n"
    : llvm::outs() << "\t [NOP]\n";
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                         Literals                                            */
/*******************************************************************************/

/* ============================================================================
 * DESC: Get a reference to a function and store it in the result. This function
 *       reference can be used in a call or simply be stored to a value.
 */
jobject InstructionVisitor::visitFunctionRefInst(FunctionRefInst *FRI) {
  SILFunction *referencedFunction = FRI->getReferencedFunctionOrNull();
  assert(referencedFunction != nullptr);
  std::string FuncName = Demangle::demangleSymbolAsString(referencedFunction->getName());
  jobject NameNode = Instance->CAst->makeConstant(FuncName.c_str());
  jobject FuncExprNode = Instance->CAst->makeNode(CAstWrapper::FUNCTION_EXPR, NameNode);
  if (SWAN_PRINT) {
    llvm::outs() << "\t [FUNCTION]: " << FuncName << "\n";
  }
  valueTable->addNode(static_cast<ValueBase*>(FRI), FuncExprNode);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Similar to function_ref, but doesn't always have a referenced function.
 */
jobject InstructionVisitor::visitDynamicFunctionRefInst(DynamicFunctionRefInst *DFRI) {
  SILFunction *referencedFunction = DFRI->getReferencedFunctionOrNull();
  std::string FuncName = "UNKNOWN_DNYAMIC_REF";
  if (referencedFunction != nullptr) {
    FuncName = Demangle::demangleSymbolAsString(referencedFunction->getName());
  }
  jobject NameNode = Instance->CAst->makeConstant(FuncName.c_str());
  jobject FuncExprNode = Instance->CAst->makeNode(CAstWrapper::FUNCTION_EXPR, NameNode);
  if (SWAN_PRINT) {
    llvm::outs() << "\t [FUNCTION]: " << FuncName << "\n";
  }
  valueTable->addNode(static_cast<ValueBase*>(DFRI), FuncExprNode);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: References previous implementation of a dynamic_replacement SIL function.
 *       We will treat this one the same as dynamic_function_ref for now.
 */
jobject InstructionVisitor::visitPreviousDynamicFunctionRefInst(PreviousDynamicFunctionRefInst *PDFRI)
{
  SILFunction *referencedFunction = PDFRI->getReferencedFunctionOrNull();
  std::string FuncName = "UNKNOWN_DNYAMIC_REF";
  if (referencedFunction != nullptr) {
    FuncName = Demangle::demangleSymbolAsString(referencedFunction->getName());
  }
  jobject NameNode = Instance->CAst->makeConstant(FuncName.c_str());
  jobject FuncExprNode = Instance->CAst->makeNode(CAstWrapper::FUNCTION_EXPR, NameNode);
  if (SWAN_PRINT) {
    llvm::outs() << "\t [FUNCTION]: " << FuncName << "\n";
  }
  valueTable->addNode(static_cast<ValueBase*>(PDFRI), FuncExprNode);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Creates a reference to a global variable previously allocated by
 *       alloc_global. Here, we can actually create a VAR for it now.
 */
jobject InstructionVisitor::visitGlobalAddrInst(GlobalAddrInst *GAI) {
  SILGlobalVariable *variable = GAI->getReferencedGlobal();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [VAR NAME]:" << Demangle::demangleSymbolAsString(variable->getName()) << "\n";
  }
  valueTable->createAndAddSymbol(static_cast<ValueBase*>(GAI), variable->getLoweredType().getAsString());
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Gets the value of a global variable (as opposed to a reference), but
 *       we will treat it the same as global_addr.
 */
jobject InstructionVisitor::visitGlobalValueInst(GlobalValueInst *GVI) {
  SILGlobalVariable *variable = GVI->getReferencedGlobal();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [VAR NAME]:" << Demangle::demangleSymbolAsString(variable->getName()) << "\n";
  }
  valueTable->createAndAddSymbol(static_cast<ValueBase*>(GVI), variable->getLoweredType().getAsString());
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Integer literal value which simply gets translated to a CONSTANT.
 */
jobject InstructionVisitor::visitIntegerLiteralInst(IntegerLiteralInst *ILI) {
  APInt Value = ILI->getValue();
  jobject Node = nullptr;
  if (Value.isNegative()) {
    if (Value.getMinSignedBits() <= 32) {
      Node = Instance->CAst->makeConstant(static_cast<int>(Value.getSExtValue()));
    } else if (Value.getMinSignedBits() <= 64) {
      Node = Instance->CAst->makeConstant(static_cast<long>(Value.getSExtValue()));
    }
  } else {
    if (Value.getActiveBits() <= 32) {
      Node = Instance->CAst->makeConstant(static_cast<int>(Value.getZExtValue()));
    } else if (Value.getActiveBits() <= 64) {
      Node = Instance->CAst->makeConstant(static_cast<long>(Value.getZExtValue()));
    }
  }
  if (Node != nullptr) {
    if (SWAN_PRINT) {
       llvm::outs() << "\t [VALUE]:" << Value.getZExtValue() << "\n";
    }
    valueTable->addNode(static_cast<ValueBase*>(ILI), Node);
  } else {
    llvm::outs() << "WARNING: Undefined integer_literal behaviour\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Float literal value which simply gets translated to a CONSTANT.
 */
jobject InstructionVisitor::visitFloatLiteralInst(FloatLiteralInst *FLI) {
  APFloat Value = FLI->getValue();
  jobject Node = nullptr;
  if (&Value.getSemantics() == &APFloat::IEEEsingle()) {
    Node = Instance->CAst->makeConstant(Value.convertToFloat());
    if (SWAN_PRINT) {
      llvm::outs() << "\t [VALUE]:" << static_cast<double>(Value.convertToFloat()) << "\n";
    }
  }
  else if (&Value.getSemantics() == &APFloat::IEEEdouble()) {
    Node = Instance->CAst->makeConstant(Value.convertToDouble());
    if (SWAN_PRINT) {
      llvm::outs() << "\t [VALUE]:" << Value.convertToDouble() << "\n";
    }
  }
  else if (Value.isFinite()) {
    SmallVector<char, 128> buf;
    Value.toString(buf);
    jobject BigDecimal = Instance->makeBigDecimal(buf.data(), static_cast<int>(buf.size()));
    Node = Instance->CAst->makeConstant(BigDecimal);
    if (SWAN_PRINT) {
      llvm::outs() << "\t [VALUE]:" << buf << "\n";
    }
  }
  else {
    bool APFLosesInfo;
    Value.convert(APFloat::IEEEdouble(), APFloat::rmNearestTiesToEven, &APFLosesInfo);
    Node = Instance->CAst->makeConstant(Value.convertToDouble());
    if (SWAN_PRINT) {
      llvm::outs() << "\t [VALUE]:" << Value.convertToDouble() << "\n";
    }
  }
  valueTable->addNode(static_cast<ValueBase *>(FLI), Node);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: String literal value which simply gets translated to a CONSTANT.
 */
jobject InstructionVisitor::visitStringLiteralInst(StringLiteralInst *SLI) {
  StringRef Value = SLI->getValue();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [VALUE]: " << Value << "\n";
  }
  valueTable->addNode(static_cast<ValueBase *>(SLI), Instance->CAst->makeConstant((Value.str()).c_str()));
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                         Dynamic Dispatch                                    */
/*******************************************************************************/

/* ============================================================================
 * DESC: Class method function reference. We don't care about the operand
 *       (class object) since we have no notion of such and treat methods
 *       as any other function, but with the first parameter being the class object.
 */
jobject InstructionVisitor::visitClassMethodInst(ClassMethodInst *CMI) {
  if (SWAN_PRINT) {
    llvm::outs() << "\t [CLASS]: " << CMI->getMember().getDecl()->getInterfaceType().getString() << "\n";
  }
  std::string FuncName = Demangle::demangleSymbolAsString(CMI->getMember().mangle());
  jobject NameNode = Instance->CAst->makeConstant(FuncName.c_str());
  jobject FuncExprNode = Instance->CAst->makeNode(CAstWrapper::FUNCTION_EXPR, NameNode);
  if (SWAN_PRINT) {
    llvm::outs() << "\t [FUNCTION]: " << FuncName << "\n";
  }
  valueTable->addNode(static_cast<ValueBase*>(CMI), FuncExprNode);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: It seems we can treat this instruction just like class_method.
 */
jobject InstructionVisitor::visitObjCMethodInst(ObjCMethodInst *AMI) {
  if (SWAN_PRINT) {
    llvm::outs() << "\t [INTERFACE]: " << AMI->getMember().getDecl()->getInterfaceType().getString() << "\n";
  }
  std::string FuncName = Demangle::demangleSymbolAsString(AMI->getMember().mangle());
  jobject NameNode = Instance->CAst->makeConstant(FuncName.c_str());
  jobject FuncExprNode = Instance->CAst->makeNode(CAstWrapper::FUNCTION_EXPR, NameNode);
  if (SWAN_PRINT) {
    llvm::outs() << "\t [OBJC MEMBER]: " << FuncName << "\n";
  }
  valueTable->addNode(static_cast<ValueBase*>(AMI), FuncExprNode);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: It seems we can treat this instruction just like class_method.
 */
jobject InstructionVisitor::visitSuperMethodInst(SuperMethodInst *SMI) {
  if (SWAN_PRINT) {
    llvm::outs() << "\t [CLASS]: " << SMI->getMember().getDecl()->getInterfaceType().getString() << "\n";
  }
  std::string FuncName = Demangle::demangleSymbolAsString(SMI->getMember().mangle());
  jobject NameNode = Instance->CAst->makeConstant(FuncName.c_str());
  jobject FuncExprNode = Instance->CAst->makeNode(CAstWrapper::FUNCTION_EXPR, NameNode);
  if (SWAN_PRINT) {
    llvm::outs() << "\t [FUNCTION]: " << FuncName << "\n";
  }
  valueTable->addNode(static_cast<ValueBase*>(SMI), FuncExprNode);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: It seems we can treat this instruction just like class_method.
 */
jobject InstructionVisitor::visitObjCSuperMethodInst(ObjCSuperMethodInst *ASMI) {
  if (SWAN_PRINT) {
    llvm::outs() << "\t [INTERFACE]: " << ASMI->getMember().getDecl()->getInterfaceType().getString() << "\n";
  }
  std::string FuncName = Demangle::demangleSymbolAsString(ASMI->getMember().mangle());
  jobject NameNode = Instance->CAst->makeConstant(FuncName.c_str());
  jobject FuncExprNode = Instance->CAst->makeNode(CAstWrapper::FUNCTION_EXPR, NameNode);
  if (SWAN_PRINT) {
    llvm::outs() << "\t [OBJC MEMBER]: " << FuncName << "\n";
  }
  valueTable->addNode(static_cast<ValueBase*>(ASMI), FuncExprNode);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: It seems we can treat this instruction just like class_method.
 */
jobject InstructionVisitor::visitWitnessMethodInst(WitnessMethodInst *WMI) {
  if (SWAN_PRINT) {
    llvm::outs() << "\t [PROTOCOL]: " << WMI->getMember().getDecl()->getInterfaceType().getString() << "\n";
  }
  std::string FuncName = Demangle::demangleSymbolAsString(WMI->getMember().mangle());
  jobject NameNode = Instance->CAst->makeConstant(FuncName.c_str());
  jobject FuncExprNode = Instance->CAst->makeNode(CAstWrapper::FUNCTION_EXPR, NameNode);
  if (SWAN_PRINT) {
    llvm::outs() << "\t [MEMBER]: " << FuncName << "\n";
  }
  valueTable->addNode(static_cast<ValueBase*>(WMI), FuncExprNode);
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

/* ============================================================================
 * DESC: Reference to metatype. Theoretically, the operand shouldn't matter
 *       because the compiler will know the instruction's result's type.
 */
jobject InstructionVisitor::visitMetatypeInst(MetatypeInst *MI) {
  std::string MetatypeName = MI->getType().getAsString();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [METATYPE]: " << MetatypeName << "\n";
  }
  jobject Node = Instance->CAst->makeConstant(MetatypeName.c_str());
  valueTable->addNode(static_cast<ValueBase*>(MI), Node);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: It seems we can treat this instruction the same way as metatype.
 */
jobject InstructionVisitor::visitValueMetatypeInst(ValueMetatypeInst *VMI) {
  std::string MetatypeName = VMI->getType().getAsString();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [METATYPE]: " << MetatypeName << "\n";
  }
  jobject Node = Instance->CAst->makeConstant(MetatypeName.c_str());
  valueTable->addNode(static_cast<ValueBase*>(VMI), Node);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: It seems we can treat this instruction the same way as metatype.
 */
jobject InstructionVisitor::visitExistentialMetatypeInst(ExistentialMetatypeInst *EMI) {
  std::string MetatypeName = EMI->getType().getAsString();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [METATYPE]: " << MetatypeName << "\n";
  }
  jobject Node = Instance->CAst->makeConstant(MetatypeName.c_str());
  valueTable->addNode(static_cast<ValueBase*>(EMI), Node);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: No description in SIL.rst, but lets just treat it the same as the
 *       others for now.
 */
jobject InstructionVisitor::visitObjCProtocolInst(ObjCProtocolInst *OPI) {
  std::string MetatypeName = OPI->getType().getAsString();
  if (SWAN_PRINT) {
    llvm::outs() << "\t [METATYPE]: " << MetatypeName << "\n";
  }
  jobject Node = Instance->CAst->makeConstant(MetatypeName.c_str());
  valueTable->addNode(static_cast<ValueBase*>(OPI), Node);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                          Aggregate Types                                    */
/*******************************************************************************/

/* ============================================================================
 * DESC: Retains a loadable value, which is a nop for us.
 */
jobject InstructionVisitor::visitRetainValueInst(__attribute__((unused)) RetainValueInst *RVI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Retains a loadable value inside given address, which is a nop for us.
 */
jobject InstructionVisitor::visitRetainValueAddrInst(__attribute__((unused)) RetainValueAddrInst *RVAI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Similar to retain_value - nop.
 */
jobject InstructionVisitor::visitUnmanagedRetainValueInst(__attribute__((unused)) UnmanagedRetainValueInst *URVI) {
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Copies loadable value. Therefore, it is not just a direct ASSIGN.
 */
jobject InstructionVisitor::visitCopyValueInst(CopyValueInst *CVI) {
  void* src = CVI->getOperand().getOpaqueValue();
  void* dest = static_cast<ValueBase*>(CVI);
  if (SWAN_PRINT) {
    llvm::outs() << "\t Copy\n";
    llvm::outs() << "\t [SRC ADDR]: " << src << "\n";
    llvm::outs() << "\t [DEST ADDR]: " << dest << "\n";
  }
  valueTable->copySymbol(src, dest);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Destroys a loadable value.
 */
jobject InstructionVisitor::visitReleaseValueInst(ReleaseValueInst *REVI) {
  void* toRemove = REVI->getOperand().getOpaqueValue();
  (valueTable->tryRemove(toRemove))
    ? llvm::outs() << "\t [REMOVED ADDR]: " << toRemove << "\n"
    : llvm::outs() << "\t [NOP]\n";
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Destroys a loadable value inside given address.
 */
jobject InstructionVisitor::visitReleaseValueAddrInst(ReleaseValueAddrInst *REVAI) {
  void* toRemove = REVAI->getOperand().getOpaqueValue();
  (valueTable->tryRemove(toRemove))
    ? llvm::outs() << "\t [REMOVED ADDR]: " << toRemove << "\n"
    : llvm::outs() << "\t [NOP]\n";
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Destroys a loadable value.
 */
jobject InstructionVisitor::visitUnmanagedReleaseValueInst(UnmanagedReleaseValueInst *UREVI) {
  void* toRemove = UREVI->getOperand().getOpaqueValue();
  (valueTable->tryRemove(toRemove))
    ? llvm::outs() << "\t [REMOVED ADDR]: " << toRemove << "\n"
    : llvm::outs() << "\t [NOP]\n";
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: Destroys a loadable value.
 */
jobject InstructionVisitor::visitDestroyValueInst(DestroyValueInst *DVI) {
  void* toRemove = DVI->getOperand().getOpaqueValue();
  (valueTable->tryRemove(toRemove))
    ? llvm::outs() << "\t [REMOVED ADDR]: " << toRemove << "\n"
    : llvm::outs() << "\t [NOP]\n";
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/* ============================================================================
 * DESC: No description in SIL.rst, but we will treat it the same as the others.
 */
jobject InstructionVisitor::visitAutoreleaseValueInst(AutoreleaseValueInst *AREVI) {
  void* toRemove = AREVI->getOperand().getOpaqueValue();
  (valueTable->tryRemove(toRemove))
    ? llvm::outs() << "\t [REMOVED ADDR]: " << toRemove << "\n"
    : llvm::outs() << "\t [NOP]\n";
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
