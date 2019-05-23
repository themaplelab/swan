/******************************************************************************
 * Copyright (c) 2019 Maple @ University of Alberta
 * All rights reserved. This program and the accompanying materials (unless
 * otherwise specified by a license inside of the accompanying material)
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *****************************************************************************/

 // SEE HEADER FILE FOR DOCUMENTATION

 // TODO: Add thorough documentation to code.

#include <swift/SIL/SILModule.h>
#include <swift/AST/Module.h>

#include "BasicBlockLabeller.h"
#include "SILWalaInstructionVisitor.h"
#include "swift/Demangling/Demangle.h"
#include "CAstWrapper.h"

#include <iostream>
#include <fstream>

using namespace swift_wala;


// Gets the sourcefile, start line/col, end line/col, and writes it to the InstrInfo
// that is passed in.
// TODO: check lastBuffer vs. buffer to see if start and end are in the same file
void SILWalaInstructionVisitor::updateInstrSourceInfo(SILInstruction *I) {
  SourceManager &srcMgr = I->getModule().getSourceManager();

  // Get file-line-col information for the source
  SILLocation debugLoc = I->getDebugLocation().getLocation();
  SILLocation::DebugLoc debugInfo = debugLoc.decodeDebugLoc(srcMgr);

  instrInfo->Filename = debugInfo.Filename;

  if (I->getLoc().isNull()) {
    instrInfo->srcType = -1;
  } else {

    SourceRange srcRange = I->getLoc().getSourceRange();
    SourceLoc srcStart = srcRange.Start;
    SourceLoc srcEnd = srcRange.End;

    if (srcStart.isValid()) {

      instrInfo->srcType = 0;

      auto startLineCol = srcMgr.getLineAndColumn(srcStart);
      instrInfo->startLine = startLineCol.first;
      instrInfo->startCol = startLineCol.second;

    } else {
      instrInfo->startLine = debugInfo.Line;
      instrInfo->startCol = debugInfo.Column;
    }

    if (srcEnd.isValid()) {

      auto endLineCol = srcMgr.getLineAndColumn(srcEnd);
      instrInfo->endLine = endLineCol.first;
      instrInfo->endCol = endLineCol.second;
    } else {
      instrInfo->srcType = 1;
    }
  }
}

void SILWalaInstructionVisitor::beforeVisit(SILInstruction *I) {
  instrInfo = std::make_shared<InstrInfo>();

  updateInstrSourceInfo(I);

  instrInfo->num = InstructionCount++;
  instrInfo->memBehavior = I->getMemoryBehavior();
  instrInfo->relBehavior = I->getReleasingBehavior();

  //FixMe: replace with weak pointer.
  instrInfo->modInfo = moduleInfo.get();
  instrInfo->funcInfo = functionInfo.get();
  instrInfo->instrKind = I->getKind();

  std::vector<SILValue> vals;
  for (const auto &op: I->getAllOperands()) {
    vals.push_back(op.get());
  }
  instrInfo->ops = llvm::ArrayRef<SILValue>(vals);
  perInstruction();

  /*
  char* od = std::getenv("SIL_INSTRUCTION_OUTPUT");
  std::std::string OutputDir(od);
  std::stringstream ss;
  ss << OutputDir << "SIL_INSTRUCTION_OUTPUT.txt";
  std::ofstream outfile;
  outfile.open(ss);
  outfile << getSILInstructionName(I->getKind());
  outfile.close();
  */
  if (Print) {
    llvm::outs() << "<< " << getSILInstructionName(I->getKind()) << " >>\n";
  }
}

void SILWalaInstructionVisitor::visitSILBasicBlock(SILBasicBlock *BB) {
  if (Print) {
    llvm::outs() << "Basic Block: ";
    llvm::outs() << BB << "\n";
    llvm::outs() << "SILFunctions: " << BB->getParent() << "\n";
  }
  InstructionCount = 0;
  NodeMap.clear();
  NodeList.clear();

  for (auto &I: *BB) {
    auto Node = visit(&I);
    if (Node != nullptr) {
      NodeList.push_back(Node);
    }
  }

  if (NodeList.size() > 0) {
    jobject Node = Instance->CAst->makeConstant(BasicBlockLabeller::label(BB).c_str());
    jobject Stmt = Instance->CAst->makeNode(CAstWrapper::LABEL_STMT, Node, NodeList.front());
    NodeList.pop_front();
    NodeList.push_front(Stmt);
    jobject BlockStmt = Instance->CAst->makeNode(CAstWrapper::BLOCK_STMT, Instance->CAst->makeArray(&NodeList));
    BlockStmtList.push_back(BlockStmt);
    Instance->CAstNodes.push_back(BlockStmt);
  }
}

void SILWalaInstructionVisitor::visitSILFunction(SILFunction *F) {
  functionInfo =
      std::make_shared<FunctionInfo>(F->getName(), Demangle::demangleSymbolAsString(F->getName()));
  BlockStmtList.clear();
  if (Print) {
    llvm::outs() << "SILFunction: ";
    llvm::outs() << F << "\n";
    F->print(llvm::outs(), true);
  }

  for (auto &BB: *F) {
    visitSILBasicBlock(&BB);
  }

  if (Print) {
    for (auto &Stmt: BlockStmtList) {
      Instance->print(Stmt);
    }
  }

}

void SILWalaInstructionVisitor::visitModule(SILModule *M) {
  moduleInfo = std::make_shared<ModuleInfo>(M->getSwiftModule()->getModuleFilename());
  if (moduleInfo->sourcefile.empty()) {
    moduleInfo->sourcefile = "N/A";
  }
  for (auto &F: *M) {
    visitSILFunction(&F);
  }
}

// Actions to take on a per-instruction basis.  InstrInfo contains all the relevant info
// for the current instruction in the iteration.
void SILWalaInstructionVisitor::perInstruction() {
  if (Print) {
    Instance->print(Instance->CAst->makeLocation(
        instrInfo->startLine, instrInfo->startCol,
        instrInfo->endLine, instrInfo->endCol)
    );
  }


  if (Print) {
    llvm::outs() << "\t [INSTR] #" << instrInfo->num;
    llvm::outs() << ", [OPNUM] " << instrInfo->id << "\n";
    llvm::outs() << "\t --> File: " << instrInfo->Filename << "\n";

    // Has no location information
    if (instrInfo->srcType == -1) {
      llvm::outs() << "\t **** No source information. \n";

      // Has only start information
    } else {
      llvm::outs() << "\t ++++ Start - Line " << instrInfo->startLine << ":"
                   << instrInfo->startCol << "\n";
    }

    // Has end information
    if (instrInfo->srcType == 0) {
      llvm::outs() << "\t ---- End - Line " << instrInfo->endLine;
      llvm::outs() << ":" << instrInfo->endCol << "\n";
    }

    // Memory Behavior
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

    // Releasing Behavior
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

    // Show operands, if they exist
    for (auto op = instrInfo->ops.begin(); op != instrInfo->ops.end(); ++op) {
      llvm::outs() << "\t\t [OPER]: " << *op;
    }

    llvm::outs() << "\n";
  }
}

jobject SILWalaInstructionVisitor::findAndRemoveCAstNode(void *Key) {
  jobject node = nullptr;
  if (SymbolTable.has(Key)) {
    // this is a variable
    jobject name = Instance->CAst->makeConstant(SymbolTable.get(Key).c_str());
    node = Instance->CAst->makeNode(CAstWrapper::VAR, name);
  } else if (NodeMap.find(Key) != NodeMap.end()) {
    // find
    node = NodeMap.at(Key);
    // remove
    auto it = std::find(NodeList.begin(), NodeList.end(), node);
    if (it != NodeList.end()) {
      NodeList.erase(it);
    }
  }
  return node;
}

jobject SILWalaInstructionVisitor::getOperatorCAstType(Identifier Name) {
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
    // return CAstWrapper::OP_REL_AND;
    return nullptr; // && and || are handled separatedly because they involve short circuits
  } else if (Name.is("|")) {
    return CAstWrapper::OP_BIT_OR;
  } else if (Name.is("||")) {
    // return CAstWrapper::OP_REL_OR;
    return nullptr; // && and || are handled separatedly because they involve short circuits
  } else if (Name.is("^")) {
    return CAstWrapper::OP_BIT_XOR;
  } else {
    return nullptr;
  }
}

/*******************************************************************************/
/*                         ALLOCATION AND DEALLOCATION                         */
/*******************************************************************************/

jobject SILWalaInstructionVisitor::visitAllocStackInst(AllocStackInst *ASI) {
  Optional<SILDebugVariable> Info = ASI->getVarInfo();
  unsigned ArgNo = Info->ArgNo;

  if (auto *Decl = ASI->getDecl()) {
    StringRef varName = Decl->getNameStr();
    if (Print) {
      llvm::outs() << "\t [ARG]#" << ArgNo << ": " << varName << "\n";
    }
    SymbolTable.insert(static_cast<ValueBase *>(ASI), varName);
  }
  else {
    // temporary allocation when referencing self.
    if (Print) {
      llvm::outs() << "\t [ARG]#" << ArgNo << ": " << "self" << "\n";
    }
    SymbolTable.insert(static_cast<ValueBase *>(ASI), "self");
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitAllocBoxInst(AllocBoxInst *ABI) {
  Optional<SILDebugVariable> Info = ABI->getVarInfo();
  unsigned ArgNo = Info->ArgNo;

  if (auto *Decl = ABI->getDecl()) {
    StringRef varName = Decl->getNameStr();
    if (Print) {
      llvm::outs() << "\t [ARG]#" << ArgNo << ": " << varName << "\n";
    }
    SymbolTable.insert(static_cast<ValueBase *>(ABI), varName);
  }
  else {
    // temporary allocation when referencing self.
    if (Print) {
      llvm::outs() << "\t [ARG]#" << ArgNo << ": " << "self" << "\n";
    }
    SymbolTable.insert(static_cast<ValueBase *>(ABI), "self");
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitAllocRefInst(AllocRefInst *ARI) {
  std::string RefTypeName = ARI->getType().getAsString();

  if (Print) {
    llvm::outs() << "\t [VAR TYPE]: " << RefTypeName << "\n";
  }

  SymbolTable.insert(static_cast<ValueBase *>(ARI), RefTypeName);

  ArrayRef<SILType> Types = ARI->getTailAllocatedTypes();
  ArrayRef<Operand> Counts = ARI->getTailAllocatedCounts();

  for (unsigned Idx = 0, NumTypes = Types.size(); Idx < NumTypes; ++Idx) {
    SILValue OperandValue = Counts[Idx].get();
    std::string OperandTypeName = Types[Idx].getAsString();

    if (Print) {
      llvm::outs() << "\t [OPERAND]: " << OperandValue.getOpaqueValue() << " [TYPE]: " << OperandTypeName << "\n";
    }

    SymbolTable.insert(static_cast<ValueBase *>(OperandValue.getOpaqueValue()), OperandTypeName);
  }
  return  Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitAllocGlobalInst(AllocGlobalInst *AGI) {
  SILGlobalVariable *Var = AGI->getReferencedGlobal();
  StringRef Name = Var->getName();
  SILType Type = Var->getLoweredType();
  if (Print) {
    llvm::outs() << "\t [VAR NAME]:" << Name << "\n";
    llvm::outs() << "\t [VAR TYPE]:" << Type << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitDeallocStackInst(DeallocStackInst *DSI) {
  if (Print) {
    for (auto &OP : DSI->getAllOperands()) {
      llvm::outs() << "\t [OPERAND]: " << OP.get() << "\n";
      llvm::outs() << "\t [ADDR]: " << OP.get().getOpaqueValue() << "\n";
    }
  }
  return  Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitDeallocBoxInst(DeallocBoxInst *DBI) {
  for (auto &OP : DBI->getAllOperands()) {
    if (Print) {
      llvm::outs() << "\t [OPERAND]: " << OP.get() << "\n";
      llvm::outs() << "\t [BOX]: " << OP.get().getOpaqueValue() << "\n";
    }
    SymbolTable.remove(OP.get().getOpaqueValue());
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitDeallocRefInst(DeallocRefInst *DRI) {
    for (auto &OP : DRI->getAllOperands()) {
    if (Print) {
      llvm::outs() << "\t [OPERAND]: " << OP.get() << "\n";
      llvm::outs() << "\t [REF]: " << OP.get().getOpaqueValue() << "\n";
    }
    SymbolTable.remove(OP.get().getOpaqueValue());
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitProjectBoxInst(ProjectBoxInst *PBI) {
  if (SymbolTable.has(PBI->getOperand().getOpaqueValue())) {
    // This is a variable
    // NOTE: Apple documentation states: This instruction has undefined behavior if the box is not currently allocated
    //       (link: https://github.com/apple/swift/blob/master/docs/SIL.rst#project_box) so there is no need to allocate
    //       it if it is not currently in the Symbol Table
    SymbolTable.duplicate(static_cast<ValueBase *>(PBI), SymbolTable.get(PBI->getOperand().getOpaqueValue()).c_str());
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitAllocValueBufferInst(AllocValueBufferInst *AVBI) {
  SILValue ValueBuffer = AVBI->getOperand();
  std::string ValueTypeName = AVBI->getValueType().getAsString();

  if (Print) {
    llvm::outs() << "\t [VALUE BUFFER]: " << ValueBuffer.getOpaqueValue() << "\n";
    llvm::outs() << "\t [VALUE TYPE]: " << ValueTypeName << "\n";
  }

  SymbolTable.insert(static_cast<ValueBase *>(ValueBuffer.getOpaqueValue()), ValueTypeName);
  return  Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitDeallocValueBufferInst(DeallocValueBufferInst *DVBI) {
  SILValue BufferValue = DVBI->getOperand();
  std::string ValueTypeName = DVBI->getValueType().getAsString();

  if (Print) {
    llvm::outs() << "\t [BUFFER ADDR]: " << BufferValue.getOpaqueValue() << "\n";
    llvm::outs() << "\t [VALUE TYPE]: " << ValueTypeName << "\n";
  }

  SymbolTable.remove(BufferValue.getOpaqueValue());
  return  Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitProjectValueBufferInst(ProjectValueBufferInst *PVBI) {
  SILValue BufferValue = PVBI->getOperand();
  std::string ValueTypeName = PVBI->getValueType().getAsString();

  if (Print) {
    llvm::outs() << "\t [BUFFER ADDR]: " << BufferValue.getOpaqueValue() << "\n";
    llvm::outs() << "\t [VALUE TYPE]: " << ValueTypeName << "\n";
  }

  // NOTE: Apple documentation states: This instruction has undefined behavior if the value buffer is not currently allocated
  //       (link: https://github.com/apple/swift/blob/master/docs/SIL.rst#project-value-buffer) so there is no need to allocate
  //       it if it is not currently in the Symbol Table
  if (SymbolTable.has(BufferValue.getOpaqueValue())) {
    SymbolTable.duplicate(static_cast<ValueBase *>(PVBI), SymbolTable.get(BufferValue.getOpaqueValue()).c_str());
  }

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                        DEBUG INFROMATION                                    */
/*******************************************************************************/

jobject SILWalaInstructionVisitor::visitDebugValueInst(DebugValueInst *DBI) {
  Optional<SILDebugVariable> Info = DBI->getVarInfo();
  unsigned ArgNo = Info->ArgNo;

  if (Print) {
    llvm::outs() << "[ARGNO]: " << ArgNo << "\n";
  }

  VarDecl *Decl = DBI->getDecl();

  if (Decl) {
    std::string VarName = Decl->getNameStr();
    if (VarName.length() == 0) {
      llvm::outs() << "\t DebugValue empty name \n";
      return Instance->CAst->makeNode(CAstWrapper::EMPTY);
    }
    SILValue Val = DBI->getOperand();
    if (!Val) {
      if (Print) {
        llvm::outs() << "\t Operand is null\n";
      }
      return Instance->CAst->makeNode(CAstWrapper::EMPTY);
    }

    void *Addr = Val.getOpaqueValue();
    if (Addr) {
      SymbolTable.insert(Addr, VarName);
      llvm::outs() << "\t [ADDR OF OPERAND]:" << Addr << "\n";
    }
    else {
      if (Print) {
        llvm::outs() << "\t Operand OpaqueValue is null\n";
      }
      return Instance->CAst->makeNode(CAstWrapper::EMPTY);
    }
  }
  else {
    if (Print) {
      llvm::outs() << "\t Decl not found\n";
    }
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitDebugValueAddrInst(DebugValueAddrInst *DVAI) {
  Optional<SILDebugVariable> DebugVar = DVAI->getVarInfo();

  if (Print) {
    llvm::outs() << "\t [ARGNO]: " << DebugVar->ArgNo << "\n";
  }

  VarDecl *Decl = DVAI->getDecl();

  if (Decl) {
    std::string VarName = Decl->getNameStr();
    if (Print) {
      llvm::outs() << "\t [DECL NAME]: " << VarName << "\n";
    }

    SILValue Operand = DVAI->getOperand();
    if (Operand) {
      void *Addr = Operand.getOpaqueValue();
      if (Print) {
        llvm::outs() << "\t [ADDR OF OPERAND]: " << Addr << "\n";
      }

      SymbolTable.insert(Addr, VarName);

    } else {
      if (Print) {
        llvm::outs() << "\t OPERAND IS NULL" << "\n";
      }
      return Instance->CAst->makeNode(CAstWrapper::EMPTY);;
    }

  } else {
      if (Print) {
        llvm::outs() << "\t DECL IS NULL" << "\n";
      }
    return Instance->CAst->makeNode(CAstWrapper::EMPTY);
  }

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                                ACCESSING MEMORY                             */
/*******************************************************************************/

jobject SILWalaInstructionVisitor::visitLoadInst(LoadInst *LI) {
  SILValue LoadOperand = LI->getOperand();

  if (Print) {
    llvm::outs() << "\t [NAME]:" << LoadOperand.getOpaqueValue() << "\n";
  }

  jobject Node = findAndRemoveCAstNode(LoadOperand.getOpaqueValue());

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(LI), Node));

  return Node;
}

jobject SILWalaInstructionVisitor::visitStoreInst(StoreInst *SI) {
  // Cast the instr to access methods
  SILValue Src = SI->getSrc();
  SILValue Dest = SI->getDest();
  if (Print) {
    llvm::outs() << "\t [SRC ADDR]: " << Src.getOpaqueValue() << "\n";
    llvm::outs() << "\t [DEST ADDR]: " << Dest.getOpaqueValue() << "\n";
  }

  jobject Node = Instance->CAst->makeNode(CAstWrapper::EMPTY);
  if (SymbolTable.has(Dest.getOpaqueValue())) {
    jobject Var = findAndRemoveCAstNode(Dest.getOpaqueValue());
    Node = Instance->CAst->makeNode(CAstWrapper::ASSIGN, Var, findAndRemoveCAstNode(Src.getOpaqueValue()));
  }

  // sometimes SIL creates temporary memory on the stack
  // the following code represents the correspondence between the origial value and the new temporary location
  if (NodeMap.find(Src.getOpaqueValue()) != NodeMap.end()) {
    NodeMap.insert(std::make_pair(Dest.getOpaqueValue(), NodeMap.at(Src.getOpaqueValue())));
  }
  return Node;
}

jobject SILWalaInstructionVisitor::visitBeginBorrowInst(BeginBorrowInst *BBI) {
  if (Print) {
    llvm::outs() << "\t [BBI]:" << BBI << "\n";
    llvm::outs() << "\t [OPERAND]:" << BBI->getOperand() << "\n";
    llvm::outs() << "\t [OPERAND ADDR]:" << BBI->getOperand().getOpaqueValue() << "\n";
  }
  jobject Node = findAndRemoveCAstNode(BBI->getOperand().getOpaqueValue());
  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(BBI), Node));
  return Node;
}

jobject SILWalaInstructionVisitor::visitLoadBorrowInst(LoadBorrowInst *LBI) {
  if (Print) {
    llvm::outs() << "\t [NAME]: " << LBI->getOperand() << "\n";
    llvm::outs() << "\t [ADDR]: " << LBI->getOperand().getOpaqueValue() << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*
 * The semantics of this instruction has now changed in Swift 5.0.
 * An EndBorrowInst is now guaranteed to be paired with a BeginBorrowInst.
 * Therefore, I suggest that we here just return an empty CAst node,
 * and do the bookkeeping in visitBeginBorrowInst().
 *
 * TODO: why do we even need to erase the borrowed value? It should be sufficient
 * to check in visitEndBorrowInst that there is a matching EndBorrowInst for the
 * visited BeginBorrowInst.
 */
jobject SILWalaInstructionVisitor::visitEndBorrowInst(EndBorrowInst *EBI) {
  if (Print) {
//    llvm::outs() << "\t [BORROWED VALUE]: " << EBI->getBorrowedValue() << "\n";
//    llvm::outs() << "\t [BORROWED VALUE ADDR]: " << EBI->getBorrowedValue().getOpaqueValue() << "\n";
    llvm::outs() << "\t [ORRIGINAL VALUE]: " << EBI->getSingleOriginalValue() << "\n";
    llvm::outs() << "\t [ORRIGINAL VALUE ADDR]: " << EBI->getSingleOriginalValue().getOpaqueValue() << "\n";
  }
//  if (NodeMap.find(EBI->getBorrowedValue()) != NodeMap.end()) {
//    if (Print) {
//      llvm::outs() << "\t borrowed value found in NodeMap, remove from NodeMap\n";
//    }
//    NodeMap.erase(EBI->getBorrowedValue());
//  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}


jobject SILWalaInstructionVisitor::visitAssignInst(AssignInst *AI) {

  if (Print) {
    llvm::outs() << "\t [SOURCE]: " << AI->getSrc().getOpaqueValue() << "\n";
    llvm::outs() << "\t [DEST]: " << AI->getDest().getOpaqueValue() << "\n";
  }
  jobject Dest = findAndRemoveCAstNode(AI->getDest().getOpaqueValue());
  jobject Expr = findAndRemoveCAstNode(AI->getSrc().getOpaqueValue());

  jobject Node = Instance->CAst->makeNode(CAstWrapper::ASSIGN, Dest, Expr);
  NodeMap.insert(std::make_pair(AI, Node));
  return Node;
}

jobject SILWalaInstructionVisitor::visitStoreBorrowInst(StoreBorrowInst *SBI) {
  SILValue SourceBorrowed = SBI->getSrc();
  SILValue DestBorrowed = SBI->getDest();

  if (Print) {
    llvm::outs() << "\t [SOURCE BORROWED ADDR]: " << SourceBorrowed.getOpaqueValue() << "\n";
    llvm::outs() << "\t [DEST BORROWED ADDR]: " << DestBorrowed.getOpaqueValue() << "\n";
  }

  jobject Dest = findAndRemoveCAstNode(SourceBorrowed.getOpaqueValue());
  jobject Source = findAndRemoveCAstNode(DestBorrowed.getOpaqueValue());

  jobject Node = Instance->CAst->makeNode(CAstWrapper::ASSIGN, Dest, Source);
  NodeMap.insert(std::make_pair(SBI, Node));
  return Node;
}

jobject SILWalaInstructionVisitor::visitMarkUninitializedInst(MarkUninitializedInst *MUI) {
  // This instruction just marks the uninitialized memory locations
  // So from the perspective of Wala no operations is going on here
  // We would just return an empty node here
  SILValue AddressToBeMarked = MUI->getOperand();
  std::string KindOfMark("");
  switch(MUI->getKind()){
    case 0: { KindOfMark = "Var"; } break;
    case 1: { KindOfMark = "RootSelf"; } break;
    case 2: { KindOfMark = "CrossModuleRootSelf"; } break;
    case 3: { KindOfMark = "DerivedSelf"; } break;
    case 4: { KindOfMark = "DerivedSelfOnly"; } break;
    case 5: { KindOfMark = "DelegatingSelf"; } break;
    case 6: { KindOfMark = "DelegatingSelfAllocating"; } break;
  }
  if (Print) {
    llvm::outs() << "\t [MARK]: " << AddressToBeMarked.getOpaqueValue() << " AS " << KindOfMark << "\n";
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitMarkFunctionEscapeInst(MarkFunctionEscapeInst *MFEI){
  for(Operand &MFEOperand : MFEI->getAllOperands()){
    unsigned OperandNumber = MFEOperand.getOperandNumber();
    SILValue OperandValue = MFEOperand.get();
    if (Print) {
      llvm::outs() << "\t [OPERAND NO]: " << OperandNumber << " [VALUE]: " << OperandValue.getOpaqueValue() << "\n";
    }
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitCopyAddrInst(CopyAddrInst *CAI) {

  SILValue Source = CAI->getSrc();
  SILValue Dest = CAI->getDest();

  if (Print) {
    llvm::outs() << "\t [SOURCE ADDR]: " << Source.getOpaqueValue() << "\n";
    llvm::outs() << "\t [DEST ADDR]: " << Dest.getOpaqueValue() << "\n";
  }

  jobject NewVar = findAndRemoveCAstNode(Source.getOpaqueValue());
  jobject OldVar = findAndRemoveCAstNode(Dest.getOpaqueValue());

  jobject Node = Instance->CAst->makeNode(CAstWrapper::ASSIGN, NewVar, OldVar);
  NodeMap.insert(std::make_pair(CAI, Node));

  return Node;
}

jobject SILWalaInstructionVisitor::visitDestroyAddrInst(DestroyAddrInst *DAI) {

    SILValue DestroyAddr = DAI->getOperand();

  if (Print) {
      llvm::outs() << "\t [ADDR TO DESTROY]: " << DestroyAddr.getOpaqueValue() << "\n";
  }

  findAndRemoveCAstNode(DestroyAddr.getOpaqueValue());
  SymbolTable.remove(DestroyAddr.getOpaqueValue());

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitIndexAddrInst(IndexAddrInst *IAI) {
  SILValue base = IAI->getBase();
  SILValue idx = IAI->getIndex();

  if(Print){
    llvm::outs() << "\t [BASE ADDR]" << base.getOpaqueValue() << "\n";
    llvm::outs() << "\t [INDEX ADDR]" << idx.getOpaqueValue() << "\n";
  }

  jobject BaseNode = findAndRemoveCAstNode(base.getOpaqueValue());
  jobject IndexNode = findAndRemoveCAstNode(idx.getOpaqueValue());

  jobject Node = Instance->CAst->makeNode(CAstWrapper::EMPTY);
  Node = Instance->CAst->makeNode(CAstWrapper::ARRAY_REF, BaseNode , IndexNode);

  if(Node != nullptr) {
    NodeMap.insert(std::make_pair(static_cast<ValueBase *>(IAI), Node));
    return Node;
  }

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitTailAddrInst(TailAddrInst *TAI) {
  SILValue BaseVale = TAI->getBase();
  SILValue IndexValue = TAI->getIndex();
  SILType  ResultType = TAI->getTailType();

  if(Print){
    llvm::outs() << "\t [BASE ADDR]" << BaseVale.getOpaqueValue() << "\n";
    llvm::outs() << "\t [INDEX ADDR]" << IndexValue.getOpaqueValue() << "\n";
    llvm::outs() << "\t [RESULT TYPE]" << ResultType.getAsString() << "\n";
  }

  jobject BaseNode = findAndRemoveCAstNode(BaseVale.getOpaqueValue());
  jobject IndexNode = findAndRemoveCAstNode(IndexValue.getOpaqueValue());

  jobject Node = Instance->CAst->makeNode(CAstWrapper::ARRAY_REF, BaseNode, IndexNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(TAI), Node));
  return Node;
}

jobject SILWalaInstructionVisitor::visitBeginAccessInst(BeginAccessInst *BAI) {
  if (Print) {
    llvm::outs() << "\t [OPERAND ADDR]:" << (BAI->getSource()).getOpaqueValue() << "\n";
  }
  jobject Var = findAndRemoveCAstNode(BAI->getSource().getOpaqueValue());
  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(BAI), Var));
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitEndAccessInst(EndAccessInst *EAI) {
  if (Print) {
    llvm::outs() << "\t [BEGIN ACCESS]: " << EAI->getBeginAccess() << "\n";
  }
  ValueBase *key = static_cast<ValueBase *>(EAI->getBeginAccess());
  if (NodeMap.find(key) != NodeMap.end()) {
    if (Print) {
      llvm::outs() << "\t borrowed value found in NodeMap, remove from NodeMap\n";
    }
    NodeMap.erase(key);
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitBeginUnpairedAccessInst(BeginUnpairedAccessInst *BUI) {

  SILValue SourceValue = BUI->getSource();
  SILValue BufferValue = BUI->getBuffer();

  if (Print) {
    llvm::outs() << "\t [OPERAND]: " << SourceValue.getOpaqueValue() << "\n";
    llvm::outs() << "\t [BUFFER]: " << SourceValue.getOpaqueValue() << "\n";
  }

  jobject SourceNode = findAndRemoveCAstNode(SourceValue.getOpaqueValue());

  NodeMap.insert(std::make_pair(BufferValue.getOpaqueValue(), SourceNode));

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitEndUnpairedAccessInst(EndUnpairedAccessInst *EUAI) {
  SILValue BufferValue = EUAI->getBuffer();

  if (Print) {
    llvm::outs() << "\t [BUFFER]: " << BufferValue << "\n";
    llvm::outs() << "\t [BUFFER ADDR]: " << BufferValue.getOpaqueValue() << "\n";
  }

  findAndRemoveCAstNode(BufferValue.getOpaqueValue());

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

/*******************************************************************************/
/*                              REFERENCE COUNTING                             */
/*******************************************************************************/

/*jobject SILWalaInstructionVisitor::visitStrongUnpinInst(StrongUnpinInst *SUI) {

  SILValue UnpinOperand = SUI->getOperand();

  if (Print) {
    llvm::outs() << "\t [VALUE]: " << UnpinOperand.getOpaqueValue() << "\n";
    llvm::outs() << "\t [NODE]: " << findAndRemoveCAstNode(UnpinOperand.getOpaqueValue()) << "\n";
  }

  if (NodeMap.find(UnpinOperand) != NodeMap.end()) {
    if (Print) {
      llvm::outs() << "\t pinned value found in NodeMap, remove from NodeMap\n";
    }
    NodeMap.erase(UnpinOperand);
  } else {
    SymbolTable.remove(UnpinOperand.getOpaqueValue());
  }

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}*/

jobject SILWalaInstructionVisitor::visitEndLifetimeInst(EndLifetimeInst *ELI) {
  SILValue EndLifetimeOperand = ELI->getOperand();
  if (Print) {
    llvm::outs() << "\t [VALUE]: " << EndLifetimeOperand.getOpaqueValue() << "\n";
    llvm::outs() << "\t [NODE]: " << findAndRemoveCAstNode(EndLifetimeOperand.getOpaqueValue()) << "\n";
  }

  if (NodeMap.find(EndLifetimeOperand) != NodeMap.end()) {
    NodeMap.erase(EndLifetimeOperand);
  } else {
    SymbolTable.remove(EndLifetimeOperand.getOpaqueValue());
  }

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitMarkDependenceInst(MarkDependenceInst *MDI) {

  SILValue DependentValue = MDI->getValue();
  SILValue BaseValue = MDI->getBase();

  if (Print) {
    llvm::outs() << "\t [MarkDependence]: " << static_cast<ValueBase *>(MDI) << "\n";
    llvm::outs() << "\t [VALUE]: " << DependentValue.getOpaqueValue() << "\n";
    llvm::outs() << "\t validity depends on" << "\n";
    llvm::outs() << "\t [BASE]: " << BaseValue.getOpaqueValue() << "\n";
  }

  jobject DependentNode = findAndRemoveCAstNode(DependentValue.getOpaqueValue());
  jobject BaseNode = findAndRemoveCAstNode(BaseValue.getOpaqueValue());

  jobject Node = Instance->CAst->makeNode(CAstWrapper::PRIMITIVE, BaseNode, DependentNode );

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(MDI), Node));

  return Node;
}

/*jobject SILWalaInstructionVisitor::visitStrongPinInst(StrongPinInst *SPI) {

  SILValue PinOperand = SPI->getOperand();

  if (Print) {
    llvm::outs() << "\t [StrongPinInst]: " << static_cast<ValueBase *>(SPI) << "\n";
    llvm::outs() << "\t [OPERAND ADDR]: " << PinOperand.getOpaqueValue() << "\n";
  }

  jobject PinNode = findAndRemoveCAstNode(PinOperand.getOpaqueValue());

  jobject Node = Instance->CAst->makeNode(CAstWrapper::PRIMITIVE, PinNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(SPI), Node));

  return Node;
}*/

/*******************************************************************************/
/*                                  LITERALS                                   */
/*******************************************************************************/

jobject SILWalaInstructionVisitor::visitFunctionRefInst(FunctionRefInst *FRI) {
  // Cast the instr to access methods
  std::string FuncName = Demangle::demangleSymbolAsString(FRI->getReferencedFunction()->getName());
  jobject NameNode = Instance->CAst->makeConstant(FuncName.c_str());
  jobject FuncExprNode = Instance->CAst->makeNode(CAstWrapper::FUNCTION_EXPR, NameNode);

  if (Print) {
    llvm::outs() << "\t [FUNCTION]: " << FuncName << "\n";
  }

  NodeMap.insert(std::make_pair(FRI->getReferencedFunction(), FuncExprNode));
  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(FRI), FuncExprNode));
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitGlobalAddrInst(GlobalAddrInst *GAI) {
  SILGlobalVariable *variable = GAI->getReferencedGlobal();
  StringRef Name = variable->getName();
  if (Print) {
    llvm::outs() << "\t [VAR NAME]:" << Name << "\n";
  }
  SymbolTable.insert(static_cast<ValueBase *>(GAI), Name);
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitIntegerLiteralInst(IntegerLiteralInst *ILI) {
  APInt Value = ILI->getValue();
  jobject Node = Instance->CAst->makeNode(CAstWrapper::EMPTY);
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
    NodeMap.insert(std::make_pair(static_cast<ValueBase *>(ILI), Node));
  }
  return Node;
}


jobject SILWalaInstructionVisitor::visitFloatLiteralInst(FloatLiteralInst *FLI) {
  jobject Node = Instance->CAst->makeNode(CAstWrapper::EMPTY);
  APFloat Value = FLI->getValue();

  if (&Value.getSemantics() == &APFloat::IEEEsingle()) {
    // To Float
    Node = Instance->CAst->makeConstant(Value.convertToFloat());
  }
  else if (&Value.getSemantics() == &APFloat::IEEEdouble()) {
    // To Double
    Node = Instance->CAst->makeConstant(Value.convertToDouble());
  }
  else if (Value.isFinite()) {
    // To BigDecimal
    SmallVector<char, 128> buf;
    Value.toString(buf);
    jobject BigDecimal = Instance->makeBigDecimal(buf.data(), buf.size());
    Node = Instance->CAst->makeConstant(BigDecimal);
  }
  else {
    // Infinity or NaN, convert to double
    // as BigDecimal constructor cannot accept strings of these
    bool APFLosesInfo;
    Value.convert(APFloat::IEEEdouble(), APFloat::rmNearestTiesToEven, &APFLosesInfo);
    Node = Instance->CAst->makeConstant(Value.convertToDouble());
  }
  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(FLI), Node));
  return Node;
}

/*jobject SILWalaInstructionVisitor::visitConstStringLiteralInst(ConstStringLiteralInst *CSLI) {
  // Value: the string data for the literal, in UTF-8.
  StringRef Value = CSLI->getValue();
  if (Print) {
    llvm::outs() << "\t [VALUE] " << Value << "\n";
  }

  // Encoding: the desired encoding of the text.
  std::string E;
  switch (CSLI->getEncoding()) {
  case ConstStringLiteralInst::Encoding::UTF8: {
    E = "UTF8";
    break;
  }
  case ConstStringLiteralInst::Encoding::UTF16: {
    E = "UTF16";
    break;
  }
  }
  if (Print) {
    llvm::outs() << "\t [ENCODING]: " << E << "\n";
  }

  // Count: encoding-based length of the string literal in code units.
  uint64_t Count = CSLI->getCodeUnitCount();
  if (Print) {
    llvm::outs() << "\t [COUNT]: " << Count << "\n";
  }

  // Call WALA in Java
  jobject C = Instance->CAst->makeConstant((Value.str()).c_str());

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(CSLI), C));

  return C;
}*/

jobject SILWalaInstructionVisitor::visitStringLiteralInst(StringLiteralInst *SLI) {
  // Value: the string data for the literal, in UTF-8.
  StringRef Value = SLI->getValue();

  if (Print) {
    llvm::outs() << "\t [VALUE]: " << Value << "\n";
  }

  // Encoding: the desired encoding of the text.
  std::string encoding;
  switch (SLI->getEncoding()) {
    case StringLiteralInst::Encoding::UTF8: {
      encoding = "UTF8";
      break;
    }
    case StringLiteralInst::Encoding::UTF16: {
      encoding = "UTF16";
      break;
    }
    case StringLiteralInst::Encoding::ObjCSelector: {
      encoding = "ObjCSelector";
      break;
    }
  }

  if (Print) {
    llvm::outs() << "\t [ENCODING]: " << encoding << "\n";
  }

  // Count: encoding-based length of the string literal in code units.
  uint64_t codeUnitCount = SLI->getCodeUnitCount();

  if (Print) {
    llvm::outs() << "\t [CODEUNITCOUNT]: " << codeUnitCount << "\n";
  }

  // Call WALA in Java
  jobject walaConstant = Instance->CAst->makeConstant((Value.str()).c_str());
  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(SLI), walaConstant));
  return walaConstant;
}

/*******************************************************************************/
/*                               DYNAMIC DISPATCH                              */
/*******************************************************************************/

jobject SILWalaInstructionVisitor::visitClassMethodInst(ClassMethodInst *CMI) {
  SILValue ClassOperand = CMI->getOperand();
  SILDeclRef MemberFunc = CMI->getMember();

  std::string MemberFuncName = Demangle::demangleSymbolAsString(MemberFunc.mangle());

  if (Print) {
    llvm::outs() << "\t [CLASS]: " << CMI->getMember().getDecl()->getInterfaceType().getString() << "\n";
    llvm::outs() << "\t [MEMBER]: " << MemberFuncName << "\n";
  }

  jobject ClassNode = findAndRemoveCAstNode(ClassOperand.getOpaqueValue());

  jobject MemberNameNode = Instance->CAst->makeConstant(MemberFuncName.c_str());
  jobject FuncNode = Instance->CAst->makeNode(CAstWrapper::FUNCTION_EXPR, MemberNameNode);

  jobject Node = Instance->CAst->makeNode(CAstWrapper::OBJECT_REF, ClassNode, FuncNode );

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(CMI), Node));

  return Node;
}

jobject SILWalaInstructionVisitor::visitObjCMethodInst(ObjCMethodInst *AMI) {
  SILValue InterfaceOperand = AMI->getOperand();
  SILDeclRef MemberFunc = AMI->getMember();

  std::string MemberFuncName = Demangle::demangleSymbolAsString(MemberFunc.mangle());

  if (Print) {
    llvm::outs() << "\t [INTERFACE]: " << AMI->getMember().getDecl()->getInterfaceType().getString() << "\n";
    llvm::outs() << "\t [OBJC MEMBER]: " << MemberFuncName << "\n";
  }

  jobject InterfaceNode = findAndRemoveCAstNode(InterfaceOperand.getOpaqueValue());

  jobject MemberNameNode = Instance->CAst->makeConstant(MemberFuncName.c_str());
  jobject FuncNode = Instance->CAst->makeNode(CAstWrapper::FUNCTION_EXPR, MemberNameNode);

  jobject Node = Instance->CAst->makeNode(CAstWrapper::OBJECT_REF, InterfaceNode, FuncNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(AMI), Node));

  return Node;
}

jobject SILWalaInstructionVisitor::visitSuperMethodInst(SuperMethodInst *SMI) {

  SILValue ClassOperand = SMI->getOperand();
  SILDeclRef MemberFunc = SMI->getMember();

  std::string MemberFuncName = Demangle::demangleSymbolAsString(MemberFunc.mangle());

  if (Print) {
    llvm::outs() << "\t [SuperMethodInst]: " << static_cast<ValueBase *>(SMI) << "\n";
    llvm::outs() << "\t [SUPER CLASS]: " << SMI->getMember().getDecl()->getInterfaceType().getString() << "\n";
    llvm::outs() << "\t [MEMBER]: " << MemberFuncName << "\n";
  }

  jobject ClassNode = findAndRemoveCAstNode(ClassOperand.getOpaqueValue());

  jobject MemberNameNode = Instance->CAst->makeConstant(MemberFuncName.c_str());
  jobject FuncNode = Instance->CAst->makeNode(CAstWrapper::FUNCTION_EXPR, MemberNameNode);

  jobject Node = Instance->CAst->makeNode(CAstWrapper::OBJECT_REF, ClassNode, FuncNode );

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(SMI), Node));

  return Node;
}

jobject SILWalaInstructionVisitor::visitWitnessMethodInst(WitnessMethodInst *WMI) {

  ProtocolDecl *Protocol = WMI->getLookupProtocol();
  SILDeclRef MemberFunc = WMI->getMember();

  std::string ProtocolName = Protocol->getNameStr();
  std::string MemberFuncName = Demangle::demangleSymbolAsString(MemberFunc.mangle());

  if (Print) {
    llvm::outs() << "\t [PROTOCOL]: " << ProtocolName << "\n";
    llvm::outs() << "\t [MEMBER]: " << MemberFuncName << "\n";
  }

  jobject ProtocolNode = Instance->CAst->makeConstant(ProtocolName.c_str());

  jobject MemberNameNode = Instance->CAst->makeConstant(MemberFuncName.c_str());
  jobject FuncNode = Instance->CAst->makeNode(CAstWrapper::FUNCTION_EXPR, MemberNameNode);

  jobject Node = Instance->CAst->makeNode(CAstWrapper::OBJECT_REF, ProtocolNode , FuncNode );

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(WMI), Node));

  return Node;
}

/*******************************************************************************/
/*                              FUNCTION APPLICATION                           */
/*******************************************************************************/

jobject SILWalaInstructionVisitor::visitApplyInst(ApplyInst *AI) {
  if (auto Node = visitApplySite(AI)) {
    NodeMap.insert(std::make_pair(static_cast<ValueBase *>(AI), Node)); // insert the node into the hash map
    return Node;
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitBeginApplyInst(BeginApplyInst *BAI) {
  if (auto Node = visitApplySite(BAI)) {
    NodeMap.insert(std::make_pair(BAI, Node)); // insert the node into the hash map
    return Node;
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitEndApplyInst(EndApplyInst *EAI) {
  if (Print) {
    llvm::outs() << "[BEGIN APPLY]: " << EAI->getBeginApply() << "\n";
  }

  findAndRemoveCAstNode(EAI->getBeginApply());

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitAbortApplyInst(AbortApplyInst *AAI) {
  if (Print) {
    llvm::outs() << "[BEGIN APPLY]: " << AAI->getBeginApply() << "\n";
  }

  findAndRemoveCAstNode(AAI->getBeginApply());

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitPartialApplyInst(PartialApplyInst *PAI) {
  if (auto Node = visitApplySite(PAI)) {
    NodeMap.insert(std::make_pair(static_cast<ValueBase *>(PAI), Node)); // insert the node into the hash map
    return Node;
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitApplySite(ApplySite Apply) {
  jobject Node = Instance->CAst->makeNode(CAstWrapper::EMPTY); // the CAst node to be created
  auto *Callee = Apply.getReferencedFunction();

  if (!Callee) {
    return Instance->CAst->makeNode(CAstWrapper::EMPTY);
  }

  auto *FD = Callee->getLocation().getAsASTNode<FuncDecl>();

  if (Print) {
    llvm::outs() << "\t [CALLEE]: " << Demangle::demangleSymbolAsString(Callee->getName()) << "\n";
    for (unsigned I = 0; I < Apply.getNumArguments(); ++I) {
      SILValue V = Apply.getArgument(I);
      llvm::outs() << "\t [ARG] #" << I << ": " << V;
      llvm::outs() << "\t [ADDR] #" << I << ": " << V.getOpaqueValue() << "\n";
    }
  }

  if (FD && (FD->isUnaryOperator() || FD->isBinaryOperator())) {
    Identifier name = FD->getName();
    jobject OperatorNode = getOperatorCAstType(name);
    if (OperatorNode != nullptr) {
      llvm::outs() << "\t Built in operator\n";
      auto GetOperand = [&Apply, this](unsigned int Index) -> jobject {
        if (Index < Apply.getNumArguments()) {
          SILValue Argument = Apply.getArgument(Index);
          return findAndRemoveCAstNode(Argument.getOpaqueValue());
        }
        else return Instance->CAst->makeNode(CAstWrapper::EMPTY);
      };
      if (FD->isUnaryOperator()) {
        Node = Instance->CAst->makeNode(CAstWrapper::UNARY_EXPR, OperatorNode, GetOperand(0));
      } else {
        Node = Instance->CAst->makeNode(CAstWrapper::BINARY_EXPR, OperatorNode, GetOperand(0), GetOperand(1));
      }
      return Node;
    } // otherwise, fall through to the regular funcion call logic
  }

  auto FuncExprNode = findAndRemoveCAstNode(Callee);
  list<jobject> Params;

  for (unsigned i = 0; i < Apply.getNumArguments(); ++i) {
    SILValue Arg = Apply.getArgument(i);
    jobject Child = findAndRemoveCAstNode(Arg.getOpaqueValue());
    if (Child != nullptr) {
      Params.push_back(Child);
    }
  }

  Node = Instance->CAst->makeNode(CAstWrapper::CALL, FuncExprNode, Instance->CAst->makeArray(&Params));
  return Node;
}

jobject SILWalaInstructionVisitor::visitBuiltinInst(BuiltinInst *BI) {

  list<jobject> params;

  std::string FuncName = BI->getName().str();
  if (FuncName.empty()) {
    // cannot get function name, abort
    return Instance->CAst->makeNode(CAstWrapper::EMPTY);
  }

  // To prevent confusion if there is a user defined func with the same name
  FuncName = "Builtin." + FuncName;

  if (Print) {
    llvm::outs() << "Builtin Function Name: " << FuncName << "\n";
  }

  jobject NameNode = Instance->CAst->makeConstant(FuncName.c_str());
  jobject FuncExprNode = Instance->CAst->makeNode(CAstWrapper::FUNCTION_EXPR, NameNode);

  for (const auto &operand : BI->getArguments()) {
    if (Print) {
      llvm::outs() << "\t [OPERAND]: " << operand << "\n";
    }
    jobject child = findAndRemoveCAstNode(operand);
    if (child != nullptr) {
      params.push_back(child);
    }
  }

  jobject Node = Instance->CAst->makeNode(CAstWrapper::CALL, FuncExprNode, Instance->CAst->makeArray(&params));

  return Node;
}

/*******************************************************************************/
/*                                  METATYPES                                  */
/*******************************************************************************/

jobject SILWalaInstructionVisitor::visitMetatypeInst(MetatypeInst *MI) {

  std::string MetatypeName = MI->getType().getAsString();

  if (Print) {
    llvm::outs() << "\t [METATYPE]: " << MetatypeName << "\n";
  }

  jobject NameNode = Instance->CAst->makeConstant(MetatypeName.c_str());
  jobject MetaTypeConstNode = Instance->CAst->makeNode(CAstWrapper::CONSTANT, NameNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(MI), MetaTypeConstNode));

  return MetaTypeConstNode;
}

jobject SILWalaInstructionVisitor::visitValueMetatypeInst(ValueMetatypeInst *VMI) {

  SILValue ValueMetatypeOperand = VMI->getOperand();

  if (Print) {
    llvm::outs() << "\t [METATYPE]: " << VMI->getType().getAsString() << "\n";
  }

  jobject TypeNode = findAndRemoveCAstNode(ValueMetatypeOperand.getOpaqueValue());

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(VMI), TypeNode));

  return TypeNode;
}

/*******************************************************************************/
/*                                AGGREGATE TYPES                              */
/*******************************************************************************/

jobject SILWalaInstructionVisitor::visitCopyValueInst(CopyValueInst *CVI) {

  SILValue CopyOperand = CVI->getOperand();

  if (Print) {
    llvm::outs() << "\t [CopyValueInst]: " << static_cast<ValueBase *>(CVI) << "\n";
    llvm::outs() << "\t [OPERAND ADDR]: " << CopyOperand.getOpaqueValue() << "\n";
  }

  jobject Node = findAndRemoveCAstNode(CVI->getOperand().getOpaqueValue());

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(CVI), Node));
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitDestroyValueInst(DestroyValueInst *DVI) {

  SILValue DestroyValue = DVI->getOperand();

  if (Print) {
      llvm::outs() << "\t [VALUE TO DESTROY]: " << DestroyValue.getOpaqueValue() << "\n";
  }

  findAndRemoveCAstNode(DestroyValue.getOpaqueValue());
  SymbolTable.remove(DestroyValue.getOpaqueValue());

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitTupleInst(TupleInst *TI) {

  list<jobject> Properties;

  jobject TupleIdentifierNode = Instance->CAst->makeConstant("Tuple");

  Properties.push_back(TupleIdentifierNode);

  for (Operand &TupleOperand : TI->getElementOperands()) {

      SILValue Value = TupleOperand.get();
      unsigned ValueNumber = TupleOperand.getOperandNumber();

      jobject OperandNameNode = Instance->CAst->makeConstant(std::to_string(ValueNumber).c_str());
      jobject OperandValueNode = findAndRemoveCAstNode(Value.getOpaqueValue());

      if (Print) {
        llvm::outs() << "\t [OPERAND]: " << ValueNumber << " [VALUE]: "<< Value.getOpaqueValue()  << "\n";
      }

      Properties.push_back(OperandNameNode);
      Properties.push_back(OperandValueNode);
  }

  auto VisitTupleNode = Instance->CAst->makeNode(CAstWrapper::OBJECT_LITERAL, Instance->CAst->makeArray(&Properties));

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(TI), VisitTupleNode));

  return VisitTupleNode;
}

jobject SILWalaInstructionVisitor::visitTupleExtractInst(TupleExtractInst *TEI) {
  // Operand is a tuple type from which some field needs to be extracted.
  // So it must have been created earlier. We just find the corresponding node
  SILValue TupleTypeOperand = TEI->getOperand();
  jobject TupleTypeNode = findAndRemoveCAstNode(TupleTypeOperand.getOpaqueValue());

  // Now we get the element
  const TupleTypeElt &Element = TEI->getTupleType()->getElement(TEI->getFieldNo());

  // We would need two information to create a node resembling this instruction - (object, field)
  // First information, object, is already available as TupleTypeNode
  // Need to find the second information, field.
  // Then would create new node with the field name or field type name
  // Depending on whether field name is available or not
  jobject FieldNode = Instance->CAst->makeNode(CAstWrapper::EMPTY);
  if(Element.hasName()){
    std::string FieldName = Element.getName().str();
    FieldNode = Instance->CAst->makeConstant(FieldName.c_str());
  }
  else{
    std::string FieldTypeName = Element.getType().getString();
    FieldNode = Instance->CAst->makeConstant(FieldTypeName.c_str());
  }

  // Now create a new Wala node of OBJECT_REF type with these two nodes
  jobject TupleExtractNode = Instance->CAst->makeNode(CAstWrapper::OBJECT_REF, TupleTypeNode, FieldNode);

  //And insert the new node to the NodeMap
  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(TEI), TupleExtractNode));

  if (Print) {
    llvm::outs() << "\t [OPERAND ADDR]: " << TupleTypeOperand.getOpaqueValue() << "\n";
    if(Element.hasName()){
      llvm::outs() << "\t [TUPLE FIELD NAME]: " << Element.getName().str() << "\n";
    }
    else{
      llvm::outs() << "\t [TUPLE FIELD TYPE NAME]: " << Element.getType().getString() << "\n";
    }
  }

  return TupleExtractNode;
}

jobject SILWalaInstructionVisitor::visitStructInst(StructInst *SI) {

  list<jobject> Fields;

  StringRef StructName = SI->getStructDecl()->getNameStr();

  jobject DiscriminantNameNode = Instance->CAst->makeConstant(StructName.data());

  if (Print) {
    llvm::outs() << "\t [STRUCT]: " << StructName <<  "\n";
  }


  Fields.push_back(DiscriminantNameNode);

  for (Operand &StructOperand : SI->getElementOperands()) {

      unsigned OperandNumber = StructOperand.getOperandNumber();

      jobject OperandValueNode = findAndRemoveCAstNode(StructOperand.get().getOpaqueValue());
      jobject OperandNameNode = Instance->CAst->makeConstant(std::to_string(OperandNumber).c_str());

      if (Print) {
        llvm::outs() << "\t [OPERAND]: " << OperandNumber << " [VALUE]: "<< OperandValueNode  << "\n";
      }

      Fields.push_back(OperandNameNode);
      Fields.push_back(OperandValueNode);
  }

  auto VisitStructNode = Instance->CAst->makeNode(CAstWrapper::OBJECT_LITERAL, Instance->CAst->makeArray(&Fields));

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(SI), VisitStructNode));

  return VisitStructNode;
}

jobject SILWalaInstructionVisitor::visitStructExtractInst(StructExtractInst *SEI) {

  SILValue StructOperand = SEI->getOperand();

  StructDecl *StructElement = SEI->getStructDecl();
  VarDecl *StructField = SEI->getField();

  jobject ElementNode = findAndRemoveCAstNode(StructOperand.getOpaqueValue());

  if (Print) {
        llvm::outs() << "\t [OPERAND ADDR]: " << StructOperand.getOpaqueValue() << "\n";
        llvm::outs() << "\t [STRUCT]: " << StructElement->getDeclaredType().getString() << "\n";
        llvm::outs() << "\t [STRUCT FIELD]: " << StructField->getNameStr() << "\n";
  }

  std::string FieldName = StructField->getNameStr();
  jobject FieldNameNode = Instance->CAst->makeConstant(FieldName.c_str());
  jobject FieldNode = Instance->CAst->makeNode(CAstWrapper::VAR, FieldNameNode);
  jobject Node = Instance->CAst->makeNode(CAstWrapper::OBJECT_REF, ElementNode , FieldNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(SEI), Node));

  return Node;
}

jobject SILWalaInstructionVisitor::visitTupleElementAddrInst(TupleElementAddrInst *TEAI) {
  SILValue TupleTypeOperand = TEAI->getOperand();
  jobject TupleTypeNode = findAndRemoveCAstNode(TupleTypeOperand.getOpaqueValue());

  const TupleTypeElt &Element = TEAI->getTupleType()->getElement(TEAI->getFieldNo());

  jobject FieldNode = Instance->CAst->makeNode(CAstWrapper::EMPTY);
  if(Element.hasName()){
    std::string FieldName = Element.getName().str();
    FieldNode = Instance->CAst->makeConstant(FieldName.c_str());
  }
  else{
    std::string FieldTypeName = Element.getType().getString();
    FieldNode = Instance->CAst->makeConstant(FieldTypeName.c_str());
  }

  jobject TupleElementAddrNode = Instance->CAst->makeNode(CAstWrapper::OBJECT_REF, TupleTypeNode, FieldNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(TEAI), TupleElementAddrNode));

  if (Print) {
    llvm::outs() << "\t [OPERAND ADDR]: " << TupleTypeOperand.getOpaqueValue() << "\n";
    if(Element.hasName()){
      llvm::outs() << "\t [TUPLE FIELD NAME]: " << Element.getName().str() << "\n";
    }
    else{
      llvm::outs() << "\t [TUPLE FIELD TYPE NAME]: " << Element.getType().getString() << "\n";
    }
  }

  return TupleElementAddrNode;
}

jobject SILWalaInstructionVisitor::visitStructElementAddrInst(StructElementAddrInst *SEAI) {

  SILValue StructOperand = SEAI->getOperand();

  StructDecl *StructElement = SEAI->getStructDecl();
  VarDecl *StructField = SEAI->getField();

  jobject ElementNode = findAndRemoveCAstNode(StructOperand.getOpaqueValue());

  if (Print) {
        llvm::outs() << "\t [OPERAND ADDR]: " << StructOperand.getOpaqueValue() << "\n";
        llvm::outs() << "\t [STRUCT]: " << StructElement->getDeclaredType().getString() << "\n";
        llvm::outs() << "\t [STRUCT FIELD]: " << StructField->getNameStr() << "\n";
  }

  std::string FieldName = StructField->getNameStr();
  jobject FieldNameNode = Instance->CAst->makeConstant(FieldName.c_str());
  jobject FieldNode = Instance->CAst->makeNode(CAstWrapper::VAR, FieldNameNode);

  jobject Node = Instance->CAst->makeNode(CAstWrapper::OBJECT_REF, ElementNode , FieldNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(SEAI), Node));

  return Node;
}

jobject SILWalaInstructionVisitor::visitRefElementAddrInst(RefElementAddrInst *REAI) {

  SILValue ElementOperand = REAI->getOperand();

  ClassDecl *ClassElement = REAI->getClassDecl();
  VarDecl *ClassField = REAI->getField();

  jobject ElementNode = findAndRemoveCAstNode(ElementOperand.getOpaqueValue());

  if (Print) {
        llvm::outs() << "\t [OPERAND]: " << ElementOperand << "\n";
        llvm::outs() << "\t [CLASS]: " << ClassElement->getDeclaredType().getString() << "\n";
        llvm::outs() << "\t [CLASS FIELD]: " << ClassField->getNameStr() << "\n";
  }

  std::string ClassName = ClassField->getNameStr();
  jobject FieldNameNode = Instance->CAst->makeConstant(ClassName.c_str());
  jobject FieldNode = Instance->CAst->makeNode(CAstWrapper::VAR, FieldNameNode);

  // OBJECT_REF takes (CLASS , FIELD)
  auto Node = Instance->CAst->makeNode(CAstWrapper::OBJECT_REF, ElementNode , FieldNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(REAI), Node));

  return Node;
}

jobject SILWalaInstructionVisitor::visitRefTailAddrInst(RefTailAddrInst *RTAI) {
  SILValue ElementOperand = RTAI->getOperand();

  ClassDecl *ClassElement = RTAI->getClassDecl();
  SILType  ResultType = RTAI->getTailType();

  jobject ElementNode = findAndRemoveCAstNode(ElementOperand.getOpaqueValue());

  if (Print) {
    llvm::outs() << "\t [RefTailAddrInst]: " << static_cast<ValueBase *>(RTAI) << "\n";
    llvm::outs() << "\t [OPERAND]: " << ElementOperand << "\n";
    llvm::outs() << "\t [OPERAND ADDR]: " << ElementOperand.getOpaqueValue() << "\n";
    llvm::outs() << "\t [CLASS]: " << ClassElement->getDeclaredType().getString() << "\n";
    llvm::outs() << "\t [RESULT TYPE]: " << ResultType.getAsString() << "\n";
  }

  jobject ResultTypeNameNode = Instance->CAst->makeConstant(ResultType.getAsString().c_str());

  auto Node = Instance->CAst->makeNode(CAstWrapper::OBJECT_REF, ElementNode , ResultTypeNameNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(RTAI), Node));

  return Node;
}

/*******************************************************************************/
/*                                    ENUMS                                    */
/*******************************************************************************/

jobject SILWalaInstructionVisitor::visitEnumInst(EnumInst *EI) {

  list<jobject> Properties;

  StringRef enumName = EI->getElement()->getParentEnum()->getName().str();
  StringRef discriminantName = EI->getElement()->getNameStr();

  jobject DiscriminantNameNode = Instance->CAst->makeConstant(enumName.data());
  jobject DiscriminantValueNode = Instance->CAst->makeConstant(discriminantName.data());

  Properties.push_back(DiscriminantNameNode);
  Properties.push_back(DiscriminantValueNode);

  if (Print) {
    llvm::outs() << "\t [ENUM]: " << enumName <<  "\n";
    llvm::outs() << "\t [CASE]: " << discriminantName <<  "\n";
  }

  for (Operand &EnumOperand : EI->getAllOperands()) {

      unsigned OperandNumber = EnumOperand.getOperandNumber();

      jobject OperandValueNode = findAndRemoveCAstNode(EnumOperand.get().getOpaqueValue());
      jobject OperandNameNode = Instance->CAst->makeConstant(std::to_string(OperandNumber).c_str());

      if (Print) {
        llvm::outs() << "\t [OPERAND]: " << OperandNumber << " [VALUE]: "<< OperandValueNode  << "\n";
      }

      Properties.push_back(OperandNameNode);
      Properties.push_back(OperandValueNode);
  }

  auto VisitEnumNode = Instance->CAst->makeNode(CAstWrapper::OBJECT_LITERAL, Instance->CAst->makeArray(&Properties));

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(EI), VisitEnumNode));

  return VisitEnumNode;
 }

jobject SILWalaInstructionVisitor::visitUncheckedEnumDataInst(UncheckedEnumDataInst *UED) {

  SILValue Value = UED->getOperand();

  if (Print) {
    llvm::outs() << "\t [ENUM]: " << UED->getEnumDecl()->getName() << "\n";
    llvm::outs() << "\t [CASE]: " << UED->getElement()->getNameStr() << "\n";
    llvm::outs() << "\t [OPERAND]: " << Value.getOpaqueValue() << "\n";
  }

  jobject UncheckedEnumData = findAndRemoveCAstNode(Value.getOpaqueValue());
  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(UED), UncheckedEnumData));

  return UncheckedEnumData;
}

jobject SILWalaInstructionVisitor::visitInjectEnumAddrInst(InjectEnumAddrInst *IUAI) {

  // This instruction can be ignored for us.
  // Swift uses 2 pass initialization to initialize a data case but we can ignore this becuase in
  // visitInitEnumDataAddrInst we create the enum as an object_literal and it is fully initialized

  if (Print) {
    llvm::outs() << "\t [OPERAND]: " << IUAI->getOperand() << "\n";
    llvm::outs() << "\t [ELEMNT]: " << IUAI->getElement()->getNameStr() << "\n";
    llvm::outs() << "\t [OPERAND ADDR]: " << IUAI->getOperand().getOpaqueValue() << "\n";
  }

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
 }

jobject SILWalaInstructionVisitor::visitInitEnumDataAddrInst(InitEnumDataAddrInst *UDAI) {

  list<jobject> Properties;

  SILValue EnumOperand = UDAI->getOperand();
  StringRef EnumName = UDAI->getElement()->getParentEnum()->getName().str();

  jobject EnumNameNode = Instance->CAst->makeConstant(EnumName.data());

  Properties.push_back(EnumNameNode);

  StringRef CaseName = UDAI->getElement()->getNameStr().str();
  jobject CaseNameNode = Instance->CAst->makeConstant(CaseName.data());

  jobject CaseNode = findAndRemoveCAstNode(EnumOperand.getOpaqueValue());

  Properties.push_back(CaseNameNode);
  Properties.push_back(CaseNode);

  if (Print) {
    llvm::outs() << "\t [ENUM]: " << EnumName <<  "\n";
    llvm::outs() << "\t [CASE]: " << CaseName <<  "\n";
    llvm::outs() << "\t [CASE NODE]: " << CaseNode <<  "\n";
  }

  jobject InitEnumNode = Instance->CAst->makeNode(CAstWrapper::OBJECT_LITERAL, Instance->CAst->makeArray(&Properties));

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(UDAI), InitEnumNode));

  return InitEnumNode;
}

jobject SILWalaInstructionVisitor::visitUncheckedTakeEnumDataAddrInst(UncheckedTakeEnumDataAddrInst *UDAI) {

  SILValue EnumletOperand = UDAI->getOperand();
  EnumElementDecl *EnumElement = UDAI->getElement();
  EnumDecl *EnumDecl = UDAI->getEnumDecl();

  std::string EnumletName = EnumElement->getName().str();
  std::string EnumName = EnumDecl->getNameStr().str();

  std::string FullEnumCaseName = EnumName + "." + EnumletName;

  if (Print) {
    llvm::outs() << "\t [OPERAND]: " << EnumletOperand << "\n";
    llvm::outs() << "\t [OPERAND ADDR]: " << EnumletOperand.getOpaqueValue() << "\n";
    llvm::outs() << "\t [ENUM]: " << EnumName << "\n";
    llvm::outs() << "\t [ENUMLET]: " << EnumletName << "\n";
  }

  jobject EnumNode = findAndRemoveCAstNode(EnumletOperand.getOpaqueValue());
  jobject CaseNode =  Instance->CAst->makeConstant(FullEnumCaseName.c_str());
  jobject ReferenceNode = Instance->CAst->makeNode(CAstWrapper::OBJECT_REF, EnumNode , CaseNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(UDAI), ReferenceNode));

   return ReferenceNode;
}

jobject SILWalaInstructionVisitor::visitSelectEnumInst(SelectEnumInst *SEI) {

  list<jobject> Children;

  SILValue Cond = SEI->getEnumOperand();

  jobject CondNode = findAndRemoveCAstNode(Cond.getOpaqueValue());
  jobject DescriminatorNameNode = Instance->CAst->makeConstant("DISCRIMINATOR");

  if (Print) {
    llvm::outs() << "\t [COND]: " << Cond << "\n";
    llvm::outs() << "\t [COND NODE]: " << Cond.getOpaqueValue() << "\n";
  }

  Children.push_back(DescriminatorNameNode);
  Children.push_back(CondNode);

  for (unsigned Idx = 0, Num = SEI->getNumCases(); Idx < Num; ++Idx) {
    auto Case = SEI->getCase(Idx);

    EnumElementDecl *CaseDecl = Case.first;
    // SILValue CaseVal = Case.second;

    StringRef EnumName = CaseDecl->getParentEnum()->getName().str();

    for (EnumElementDecl *E : CaseDecl->getParentEnum()->getAllElements()) {

      StringRef CaseName = E->getNameStr();

      SILValue CaseVal = SEI->getCaseResult(E);
      if (auto intLit = dyn_cast<IntegerLiteralInst>(CaseVal)) {

        auto CaseNameString = EnumName.str() + "." + CaseName.str() + ".enumlet!." + intLit->getValue().toString(10, false);

        jobject CaseNameNode = Instance->CAst->makeConstant(CaseNameString.c_str());
        jobject CaseValNode = findAndRemoveCAstNode(CaseVal);

        if (Print) {
          llvm::outs() << "\t [CASE NAME]: " << CaseNameString << "\n";
          llvm::outs() << "\t [CASE VAL]: " << CaseValNode << "\n";
        }

        Children.push_back(CaseNameNode);
        Children.push_back(CaseValNode);

      }
    }
  }

  auto SelectEnumNode = Instance->CAst->makeNode(CAstWrapper::BLOCK_STMT,  Instance->CAst->makeArray(&Children));
  auto SelectNode = Instance->CAst->makeNode(CAstWrapper::SWITCH, CondNode, SelectEnumNode);

  NodeMap.insert(std::make_pair(SEI, SelectNode));

  return SelectNode;
}

/*******************************************************************************/
/*                      PROTOCOL AND PROTOCOL COMPARISON TYPES                 */
/*******************************************************************************/

jobject SILWalaInstructionVisitor::visitInitExistentialAddrInst(InitExistentialAddrInst *IEAI) {
  if (Print) {
    llvm::outs() << "[IEAI] " << IEAI << "\n";
    llvm::outs() << "[OPERAND]: " << IEAI->getOperand() << "\n";
    llvm::outs() << "[FormalConcreteType]: " << IEAI->getFormalConcreteType() << "\n";
  }

  if (SymbolTable.has(IEAI->getOperand().getOpaqueValue())) {
    auto name = "ExistentialAddr of " +
      SymbolTable.get(IEAI->getOperand().getOpaqueValue()) + " -> " +
      IEAI->getFormalConcreteType().getString();
    SymbolTable.insert(static_cast<ValueBase *>(IEAI), name);
    }

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitInitExistentialValueInst(InitExistentialValueInst *IEVI) {
  if (Print) {
    llvm::outs() << "[IEVI]: " << IEVI << "\n";
    llvm::outs() << "[OPERAND]: " << IEVI->getOperand() << "\n";
    llvm::outs() << "[ConcreteType]: " << IEVI->getFormalConcreteType() << "\n";
  }

   if (SymbolTable.has(IEVI->getOperand().getOpaqueValue())) {
    auto name = "ExistentialValue of " +
      SymbolTable.get(IEVI->getOperand().getOpaqueValue()) + " -> " +
      IEVI->getFormalConcreteType().getString();
    SymbolTable.insert(static_cast<ValueBase *>(IEVI), name);
  }

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitDeinitExistentialAddrInst(DeinitExistentialAddrInst *DEAI) {
  if (Print) {
    llvm::outs() << "[OPERAND]: " << DEAI->getOperand() << "\n";
  }

  findAndRemoveCAstNode(DEAI->getOperand().getOpaqueValue());
  SymbolTable.remove(DEAI->getOperand().getOpaqueValue());

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}


jobject SILWalaInstructionVisitor::visitDeinitExistentialValueInst(DeinitExistentialValueInst *DEVI) {
  if (Print) {
    llvm::outs() << "[DEVI]: " << DEVI << "\n";
    llvm::outs() << "[Operand]: " << DEVI->getOperand() << "\n";
  }

  findAndRemoveCAstNode(DEVI->getOperand().getOpaqueValue());
  SymbolTable.remove(DEVI->getOperand().getOpaqueValue());

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitOpenExistentialAddrInst(OpenExistentialAddrInst *OEAI) {
  jobject operandNode = findAndRemoveCAstNode(OEAI->getOperand().getOpaqueValue());
  std::string openedType = OEAI->getType().getAsString();

  if (Print) {
    llvm::outs() << "[OPERAND]: " << OEAI->getOperand() << "\n";
    llvm::outs() << "[EXISTENTIAL TYPE]: " << openedType << "\n";
  }

  jobject openedTypeNode = Instance->CAst->makeConstant(openedType.c_str());
  jobject castNode = Instance->CAst->makeNode(CAstWrapper::CAST, operandNode, openedTypeNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(OEAI), castNode));

  return castNode;
}

jobject SILWalaInstructionVisitor::visitOpenExistentialValueInst(OpenExistentialValueInst *OEVI) {
  jobject operandNode = findAndRemoveCAstNode(OEVI->getOperand().getOpaqueValue());
  std::string openedType = OEVI->getType().getAsString();

  if (Print) {
    llvm::outs() << "[OPERAND]: " << OEVI->getOperand() << "\n";
    llvm::outs() << "[EXISTENTIAL TYPE]: " << openedType << "\n";
  }

  jobject openedTypeNode = Instance->CAst->makeConstant(openedType.c_str());
  jobject castNode = Instance->CAst->makeNode(CAstWrapper::CAST, operandNode, openedTypeNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(OEVI), castNode));

  return castNode;
}


jobject SILWalaInstructionVisitor::visitInitExistentialMetatypeInst(InitExistentialMetatypeInst *IEMI) {
  if (Print) {
    llvm::outs() << "[IEMI]: " << IEMI << "\n";
    llvm::outs() << "[OPERAND]: " << IEMI->getOperand() << "\n";
    llvm::outs() << "[EX-TYPE]: " << IEMI->getType() << "\n";
  }

  if (SymbolTable.has(IEMI->getOperand().getOpaqueValue())) {
    auto name = "ExistentialMetatype of " +
      SymbolTable.get(IEMI->getOperand().getOpaqueValue()) + " -> " +
      IEMI->getType().getAsString();
    SymbolTable.insert(static_cast<ValueBase *>(IEMI), name);
  }

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitOpenExistentialMetatypeInst(OpenExistentialMetatypeInst *OEMI) {
  jobject operandNode = findAndRemoveCAstNode(OEMI->getOperand().getOpaqueValue());
  std::string openedType = OEMI->getType().getAsString();

  if (Print) {
    llvm::outs() << "[OPERAND]: " << OEMI->getOperand() << "\n";
    llvm::outs() << "[EXISTENTIAL TYPE]: " << openedType << "\n";
  }

  jobject openedTypeNode = Instance->CAst->makeConstant(openedType.c_str());
  jobject castNode = Instance->CAst->makeNode(CAstWrapper::CAST, operandNode, openedTypeNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(OEMI), castNode));

  return castNode;
}

jobject SILWalaInstructionVisitor::visitInitExistentialRefInst(InitExistentialRefInst *IERI) {
  if (Print) {
    llvm::outs() << "[IERI]: " << IERI << "\n";
    llvm::outs() << "[OPERAND]: " << IERI->getOperand() << "\n";
    llvm::outs() << "[ConcreteType]: " << IERI->getFormalConcreteType() << "\n";
  }

   if (SymbolTable.has(IERI->getOperand().getOpaqueValue())) {
    auto name = "ExistentialRef of " +
      SymbolTable.get(IERI->getOperand().getOpaqueValue()) + " -> " +
      IERI->getFormalConcreteType().getString();
    SymbolTable.insert(static_cast<ValueBase *>(IERI), name);
  }

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}


jobject SILWalaInstructionVisitor::visitOpenExistentialRefInst(OpenExistentialRefInst *OERI) {
  jobject operandNode = findAndRemoveCAstNode(OERI->getOperand().getOpaqueValue());
  std::string openedType = OERI->getType().getAsString();

  if (Print) {
    llvm::outs() << "[OPERAND]: " << OERI->getOperand() << "\n";
    llvm::outs() << "[EXISTENTIAL TYPE]: " << openedType << "\n";
  }

  jobject openedTypeNode = Instance->CAst->makeConstant(openedType.c_str());
  jobject castNode = Instance->CAst->makeNode(CAstWrapper::CAST, operandNode, openedTypeNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(OERI), castNode));

  return castNode;
}

jobject SILWalaInstructionVisitor::visitAllocExistentialBoxInst(AllocExistentialBoxInst *AEBI) {
    if (Print) {
      llvm::outs() << "\t [AEBI]: " << AEBI << "\n";
      llvm::outs() << "\t [CONTRETE TYPE]: " << AEBI->getFormalConcreteType() << "\n";
      llvm::outs() << "\t [EXISTENTIAL TYPE]: " << AEBI->getExistentialType() << "\n";
    }

    auto name = "ExistentialBox:" +
      AEBI->getFormalConcreteType().getString() + "->" + AEBI->getExistentialType().getAsString();
    SymbolTable.insert(static_cast<ValueBase *>(AEBI), name);

    return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitProjectExistentialBoxInst(ProjectExistentialBoxInst *PEBI) {
  if (Print) {
    llvm::outs() << "\t [PEBI]: " << PEBI << "\n";
    llvm::outs() << "\t [OPERAND]: " << PEBI->getOperand() << "\n";
    llvm::outs() << "\t [OPERAND ADDR]: " << PEBI->getOperand().getOpaqueValue() << "\n";
  }
  // NOTE: Apple documentation states: This instruction has undefined behavior if the box is not currently allocated
  //       (link: https://github.com/apple/swift/blob/master/docs/SIL.rst#project-existential-box so there is no need to allocate
  //       it if it is not currently in the Symbol Table
  if (SymbolTable.has(PEBI->getOperand().getOpaqueValue())) {
    SymbolTable.duplicate(static_cast<ValueBase *>(PEBI), SymbolTable.get(PEBI->getOperand().getOpaqueValue()).c_str());
  }

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitOpenExistentialBoxInst(OpenExistentialBoxInst *OEBI) {
  jobject operandNode = findAndRemoveCAstNode(OEBI->getOperand().getOpaqueValue());
  std::string openedType = OEBI->getType().getAsString();

  if (Print) {
    llvm::outs() << "[OPERAND]: " << OEBI->getOperand() << "\n";
    llvm::outs() << "[EXISTENTIAL TYPE]: " << openedType << "\n";
  }

  jobject openedTypeNode = Instance->CAst->makeConstant(openedType.c_str());
  jobject castNode = Instance->CAst->makeNode(CAstWrapper::CAST, operandNode, openedTypeNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(OEBI), castNode));

  return castNode;
}

jobject SILWalaInstructionVisitor::visitOpenExistentialBoxValueInst(OpenExistentialBoxValueInst *OEBVI) {
  jobject operandNode = findAndRemoveCAstNode(OEBVI->getOperand().getOpaqueValue());
  std::string openedType = OEBVI->getType().getAsString();

  if (Print) {
    llvm::outs() << "[OPERAND]: " << OEBVI->getOperand() << "\n";
    llvm::outs() << "[EXISTENTIAL TYPE]: " << openedType << "\n";
  }

  jobject openedTypeNode = Instance->CAst->makeConstant(openedType.c_str());
  jobject castNode = Instance->CAst->makeNode(CAstWrapper::CAST, operandNode, openedTypeNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(OEBVI), castNode));

  return castNode;
}

jobject SILWalaInstructionVisitor::visitDeallocExistentialBoxInst(DeallocExistentialBoxInst *DEBI) {
  if (Print) {
    llvm::outs() << "\t [OPERAND]: " << DEBI->getOperand() << "\n";
  }

  findAndRemoveCAstNode(DEBI->getOperand().getOpaqueValue());
  SymbolTable.remove(DEBI->getOperand().getOpaqueValue());

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}


/*******************************************************************************/
/*                        BLOCKS                                               */
/*******************************************************************************/

/*******************************************************************************/
/*                  UNCHECKED CONVERSIONS                                      */
/*******************************************************************************/

jobject SILWalaInstructionVisitor::visitUpcastInst(UpcastInst *UI) {

  SILValue ConvertedValue = UI->getConverted();
  std::string CovertedType = UI->getType().getAsString();

  jobject UpcastNode = findAndRemoveCAstNode(ConvertedValue.getOpaqueValue());

  if (Print) {
    llvm::outs() << "\t [CONVERTED ADDR]: " << ConvertedValue.getOpaqueValue() << " [TO]: " << CovertedType << "\n";
    llvm::outs() << "\t [CONVERTED NODE]: " << UpcastNode << "\n";
  }

  jobject ConvertedTypeNode = Instance->CAst->makeConstant(CovertedType.c_str());
  jobject CastedNode = Instance->CAst->makeNode(CAstWrapper::CAST, UpcastNode, ConvertedTypeNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(UI), CastedNode));

  return CastedNode;
}

jobject SILWalaInstructionVisitor::visitAddressToPointerInst(AddressToPointerInst *ATPI) {

  SILValue ConvertedValue = ATPI->getConverted();
  std::string CovertedType = ATPI->getType().getAsString();

  jobject ConvertedNode = findAndRemoveCAstNode(ConvertedValue.getOpaqueValue());

  if (Print) {
    llvm::outs() << "\t [CONVERTED ADDR]: " << ConvertedValue.getOpaqueValue() << " [TO]: " << CovertedType << "\n";
    llvm::outs() << "\t [CONVERTED NODE]: " << ConvertedNode << "\n";
  }

  jobject ConvertedTypeNode = Instance->CAst->makeConstant(CovertedType.c_str());
  jobject RawPointerCastNode = Instance->CAst->makeNode(CAstWrapper::CAST, ConvertedNode, ConvertedTypeNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(ATPI), RawPointerCastNode));
  return RawPointerCastNode;
}

jobject SILWalaInstructionVisitor::visitPointerToAddressInst(PointerToAddressInst *PTAI) {
  // Getting the value to be converted
  SILValue ValueToBeConverted = PTAI->getConverted();
  // Now getting the type of address to be converted into
  std::string TypeToBeConvertedInto = PTAI->getType().getAsString();

  // Conversion means it is a "CAST" instruction. CAST type node in Wala takes two other nodes.
  // One is a node corresponding to the value to be converted
  // Another is a node corresponding to the type to be converted into

  // Firstly finding the node corresponding to the value to be converted
  jobject ToBeConvertedNode = findAndRemoveCAstNode(ValueToBeConverted.getOpaqueValue());
  // Then creating the node corresponding to the type to be converted into
  jobject TypeNode = Instance->CAst->makeConstant(TypeToBeConvertedInto.c_str());
  // Then creating a new node of CAST type with these two nodes
  jobject ConversionNode = Instance->CAst->makeNode(CAstWrapper::CAST, ToBeConvertedNode, TypeNode);
  // Finally inserting into the NodeMap for future reference
  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(PTAI), ConversionNode));

  if (Print) {
    llvm::outs() << "\t" << ValueToBeConverted.getOpaqueValue() << " [TO BE CONVERTED INTO]: " << TypeToBeConvertedInto << " [TYPE ADDRESS] " << "\n";
  }

  return ConversionNode;
}

jobject SILWalaInstructionVisitor::visitUncheckedRefCastInst(UncheckedRefCastInst *URCI) {

  SILValue ConvertedValue = URCI->getConverted();
  std::string CovertedType = URCI->getType().getAsString();

  jobject UncheckedRefCastNode = findAndRemoveCAstNode(ConvertedValue.getOpaqueValue());

  if (Print) {
    llvm::outs() << "\t [CONVERTED ADDR]: " << ConvertedValue.getOpaqueValue() << " [TO]: " << CovertedType << "\n";
    llvm::outs() << "\t [CONVERTED NODE]: " << UncheckedRefCastNode << "\n";
  }

  jobject ConvertedTypeNode = Instance->CAst->makeConstant(CovertedType.c_str());
  jobject CastedNode = Instance->CAst->makeNode(CAstWrapper::CAST, UncheckedRefCastNode, ConvertedTypeNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(URCI), CastedNode));

  return CastedNode;
}

jobject SILWalaInstructionVisitor::visitUncheckedAddrCastInst(UncheckedAddrCastInst *UACI) {
  SILValue ConvertedValue = UACI->getConverted();
  std::string CovertedType = UACI->getType().getAsString();

  jobject UncheckedAddrCastNode = findAndRemoveCAstNode(ConvertedValue.getOpaqueValue());

  if (Print) {
    llvm::outs() << "\t [UncheckedAddrCastInst]: " << static_cast<ValueBase *>(UACI) << "\n";
    llvm::outs() << "\t [CONVERTED ADDR]: " << ConvertedValue.getOpaqueValue() << " [TO]: " << CovertedType << "\n";
    llvm::outs() << "\t [CONVERTED NODE]: " << UncheckedAddrCastNode << "\n";
  }

  jobject ConvertedTypeNode = Instance->CAst->makeConstant(CovertedType.c_str());
  jobject CastedNode = Instance->CAst->makeNode(CAstWrapper::CAST, UncheckedAddrCastNode, ConvertedTypeNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(UACI), CastedNode));

  return CastedNode;
}

jobject SILWalaInstructionVisitor::visitUncheckedTrivialBitCastInst(UncheckedTrivialBitCastInst *BI) {
  SILValue ConvertedValue = BI->getConverted();
  std::string CovertedType = BI->getType().getAsString();

  jobject UncheckedAddrCastNode = findAndRemoveCAstNode(ConvertedValue.getOpaqueValue());

  if (Print) {
    llvm::outs() << "\t [UncheckedAddrCastInst]: " << static_cast<ValueBase *>(BI) << "\n";
    llvm::outs() << "\t [CONVERTED ADDR]: " << ConvertedValue.getOpaqueValue() << " [TO]: " << CovertedType << "\n";
    llvm::outs() << "\t [CONVERTED NODE]: " << UncheckedAddrCastNode << "\n";
  }

  jobject ConvertedTypeNode = Instance->CAst->makeConstant(CovertedType.c_str());
  jobject CastedNode = Instance->CAst->makeNode(CAstWrapper::CAST, UncheckedAddrCastNode, ConvertedTypeNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(BI), CastedNode));

  return CastedNode;
}

jobject SILWalaInstructionVisitor::visitUncheckedOwnershipConversionInst(UncheckedOwnershipConversionInst *UOCI) {

  SILValue ConversionOperand = UOCI->getOperand();
  std::string ConversionType = UOCI->getType().getAsString();

  jobject ConversionNode = findAndRemoveCAstNode(ConversionOperand.getOpaqueValue());

  if (Print) {
    llvm::outs() << "\t [CONVERTED ADDR]: " << ConversionOperand.getOpaqueValue() << "\n";
    llvm::outs() << "\t [CONVERTED NODE]: " << ConversionNode << "\n";
  }

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(UOCI), ConversionNode));
  return ConversionNode;
}

jobject SILWalaInstructionVisitor::visitRefToRawPointerInst(RefToRawPointerInst *CI) {
  SILValue ConvertedValue = CI->getConverted();
  std::string CovertedType = CI->getType().getAsString();

  jobject ConvertedNode = findAndRemoveCAstNode(ConvertedValue.getOpaqueValue());

  if (Print) {
    llvm::outs() << "\t [RefToRawPointerInst]: " << static_cast<ValueBase *>(CI) << "\n";
    llvm::outs() << "\t [CONVERTED ADDR]: " << ConvertedValue.getOpaqueValue() << " [TO]: " << CovertedType << "\n";
    llvm::outs() << "\t [CONVERTED NODE]: " << ConvertedNode << "\n";
  }

  jobject ConvertedTypeNode = Instance->CAst->makeConstant(CovertedType.c_str());
  jobject CastedNode = Instance->CAst->makeNode(CAstWrapper::CAST, ConvertedNode, ConvertedTypeNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(CI), CastedNode));
  return CastedNode;
}

jobject SILWalaInstructionVisitor::visitRawPointerToRefInst(RawPointerToRefInst *CI) {

  SILValue ValueToBeConverted = CI->getConverted();
  std::string TypeToBeConvertedInto = CI->getType().getAsString();

  if (Print) {
    llvm::outs() << "\t [RawPointerToRef]: " << static_cast<ValueBase *>(CI) << "\n";
    llvm::outs() << "\t " << ValueToBeConverted.getOpaqueValue() << " [TO BE CONVERTED INTO]: " << TypeToBeConvertedInto << "\n";
  }

  jobject ToBeConvertedNode = findAndRemoveCAstNode(ValueToBeConverted.getOpaqueValue());

  jobject TypeNode = Instance->CAst->makeConstant(TypeToBeConvertedInto.c_str());

  jobject ConversionNode = Instance->CAst->makeNode(CAstWrapper::CAST, ToBeConvertedNode, TypeNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(CI), ConversionNode));

  return ConversionNode;
}

jobject SILWalaInstructionVisitor::visitUnmanagedToRefInst(UnmanagedToRefInst *CI) {

  SILValue ConvertedValue = CI->getConverted();
  std::string CovertedType = CI->getType().getAsString();

  jobject ConvertedNode = findAndRemoveCAstNode(ConvertedValue.getOpaqueValue());

  if (Print) {
    llvm::outs() << "\t [UnmanagedToRefInst]: " << static_cast<ValueBase *>(CI) << "\n";
    llvm::outs() << "\t [CONVERTED ADDR]: " << ConvertedValue.getOpaqueValue() << " [TO]: " << CovertedType << "\n";
  }

  jobject ConvertedTypeNode = Instance->CAst->makeConstant(CovertedType.c_str());
  jobject CastedNode = Instance->CAst->makeNode(CAstWrapper::CAST, ConvertedNode, ConvertedTypeNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(CI), CastedNode));
  return CastedNode;
}

jobject SILWalaInstructionVisitor::visitConvertFunctionInst(ConvertFunctionInst *CFI) {

  SILValue ConvertedValue = CFI->getConverted();
  std::string CovertedType = CFI->getType().getAsString();

  jobject ConvertedFunctionNode = findAndRemoveCAstNode(ConvertedValue.getOpaqueValue());

  if (Print) {
    llvm::outs() << "\t [CONVERTED ADDR]: " << ConvertedValue.getOpaqueValue()  << " [TO]: " << CovertedType << "\n";
    llvm::outs() << "\t [CONVERTED NODE]: " << ConvertedFunctionNode << "\n";
  }

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(CFI), ConvertedFunctionNode));
  return ConvertedFunctionNode;
}

jobject SILWalaInstructionVisitor::visitThinFunctionToPointerInst(ThinFunctionToPointerInst *TFPI) {

   SILValue ConvertedFunction = TFPI->getConverted();
   std::string CovertedType = TFPI->getType().getAsString();

   jobject FunctionPointerNode = findAndRemoveCAstNode(ConvertedFunction.getOpaqueValue());

   if (Print) {
     llvm::outs() << "\t [FUNCTION ADDR]: " << ConvertedFunction.getOpaqueValue() << " [TO]: " << CovertedType << "\n";
     llvm::outs() << "\t [FUNCTION NODE]: " << FunctionPointerNode << "\n";
   }

   NodeMap.insert(std::make_pair(static_cast<ValueBase *>(TFPI), FunctionPointerNode));
   return FunctionPointerNode;
}

jobject SILWalaInstructionVisitor::visitPointerToThinFunctionInst(PointerToThinFunctionInst *CI) {

  SILValue ConvertedValue = CI->getConverted();
  std::string CovertedType = CI->getType().getAsString();

  jobject ConvertedNode = findAndRemoveCAstNode(ConvertedValue.getOpaqueValue());

  if (Print) {
    llvm::outs() << "\t [PointerToThinFunctionInst]: " << static_cast<ValueBase *>(CI) << "\n";
    llvm::outs() << "\t [CONVERTED ADDR]: " << ConvertedValue.getOpaqueValue() << " [TO]: " << CovertedType << "\n";
  }

  jobject ConvertedTypeNode = Instance->CAst->makeConstant(CovertedType.c_str());
  jobject CastedNode = Instance->CAst->makeNode(CAstWrapper::CAST, ConvertedNode, ConvertedTypeNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(CI), CastedNode));
  return CastedNode;
}

// jobject SILWalaInstructionVisitor::visitClassifyBridgeObjectInst(ClassifyBridgeObjectInst *CBOI) {
//   SILValue BridgeObjectOperand = CBOI->getOperand();

//   if (Print) {
//     llvm::outs() << "\t [ClassifyBridgeObjectInst]: " << static_cast<ValueBase *>(CBOI) << "\n";
//     llvm::outs() << "\t [OPERAND ADDR]: " << BridgeObjectOperand.getOpaqueValue() << "\n";
//   }

//   jobject BridgeObjectNode = findAndRemoveCAstNode(BridgeObjectOperand.getOpaqueValue());

//   NodeMap.insert(std::make_pair(static_cast<ValueBase *>(CBOI), BridgeObjectNode));
//   return BridgeObjectNode;
// }

// jobject SILWalaInstructionVisitor::visitBridgeObjectToRefInst(BridgeObjectToRefInst *I) {

//   SILValue ConvertedValue = I->getConverted();
//   std::string CovertedType = I->getType().getAsString();

//   jobject ConvertedNode = findAndRemoveCAstNode(ConvertedValue.getOpaqueValue());

//   if (Print) {
//     llvm::outs() << "\t [BridgeObjectToRefInst]: " << static_cast<ValueBase *>(I) << "\n";
//     llvm::outs() << "\t [CONVERTED ADDR]: " << ConvertedValue.getOpaqueValue() << " [TO]: " << CovertedType << "\n";
//   }

//   jobject ConvertedTypeNode = Instance->CAst->makeConstant(CovertedType.c_str());
//   jobject CastedNode = Instance->CAst->makeNode(CAstWrapper::CAST, ConvertedNode, ConvertedTypeNode);

//   NodeMap.insert(std::make_pair(static_cast<ValueBase *>(I), CastedNode));
//   return CastedNode;
// }

jobject SILWalaInstructionVisitor::visitThinToThickFunctionInst(ThinToThickFunctionInst *TTFI) {
  // Cast the instr to access methods

  SILValue CalleeValue = TTFI->getCallee();
  std::string CovertedType = TTFI->getType().getAsString();

  jobject FuncRefNode = findAndRemoveCAstNode(CalleeValue.getOpaqueValue());

  if (Print) {
    llvm::outs() << "\t [CALLEE ADDR]: " << CalleeValue.getOpaqueValue()  << " [TO]: " << CovertedType << "\n";
    llvm::outs() << "\t [CALLEE NODE]: " << FuncRefNode << "\n";
  }

  // cast in CASt
  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(TTFI), FuncRefNode));
  return FuncRefNode;
}


jobject SILWalaInstructionVisitor::visitThickToObjCMetatypeInst(ThickToObjCMetatypeInst *TTOCI) {

  SILValue ConvertedValue = TTOCI->getConverted();
  std::string CovertedType = TTOCI->getType().getAsString();

  jobject ConvertedNode = findAndRemoveCAstNode(ConvertedValue.getOpaqueValue());

  if (Print) {
    llvm::outs() << "\t [ThickToObjCMetatypeInst]: " << static_cast<ValueBase *>(TTOCI) << "\n";
    llvm::outs() << "\t [CONVERTED ADDR]: " << ConvertedValue.getOpaqueValue() << " [TO]: " << CovertedType << "\n";
  }

  jobject ConvertedTypeNode = Instance->CAst->makeConstant(CovertedType.c_str());
  jobject CastedNode = Instance->CAst->makeNode(CAstWrapper::CAST, ConvertedNode, ConvertedTypeNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(TTOCI), CastedNode));
  return CastedNode;
}

jobject SILWalaInstructionVisitor::visitObjCToThickMetatypeInst(ObjCToThickMetatypeInst *OTTMI) {

  SILValue ConvertedValue = OTTMI->getConverted();
  std::string CovertedType = OTTMI->getType().getAsString();

  jobject ConvertedNode = findAndRemoveCAstNode(ConvertedValue.getOpaqueValue());

  if (Print) {
    llvm::outs() << "\t [ObjCToThickMetatypeInst]: " << static_cast<ValueBase *>(OTTMI) << "\n";
    llvm::outs() << "\t [CONVERTED ADDR]: " << ConvertedValue.getOpaqueValue() << " [TO]: " << CovertedType << "\n";
  }

  jobject ConvertedTypeNode = Instance->CAst->makeConstant(CovertedType.c_str());
  jobject CastedNode = Instance->CAst->makeNode(CAstWrapper::CAST, ConvertedNode, ConvertedTypeNode);

  NodeMap.insert(std::make_pair(static_cast<ValueBase *>(OTTMI), CastedNode));
  return CastedNode;
}

/*******************************************************************************/
/*                   CHECKED CONVERSIONS                                       */
/*******************************************************************************/

jobject SILWalaInstructionVisitor::visitUnconditionalCheckedCastAddrInst(UnconditionalCheckedCastAddrInst *CI) {
  SILValue SrcValue = CI->getSrc();
  SILValue DestValue = CI->getDest();

  if (Print) {
    llvm::outs() << "\t [UnconditionalCheckedCastAddrInst]: " << CI << "\n";
    llvm::outs() << "\t [CONVERT]: " << CI->getSourceType().getString() << " " << SrcValue.getOpaqueValue();
    llvm::outs() << " [TO]: " << CI->getTargetType().getString() << " " << DestValue.getOpaqueValue() << "\n";
  }

  jobject SrcNode = findAndRemoveCAstNode(SrcValue.getOpaqueValue());
  jobject ConvertedTypeNode = Instance->CAst->makeConstant(CI->getTargetType().getString().c_str());

  jobject ConversionNode = Instance->CAst->makeNode(CAstWrapper::CAST, SrcNode, ConvertedTypeNode);

  NodeMap.insert(std::make_pair(DestValue.getOpaqueValue(), ConversionNode));
  return ConversionNode;
}

/*******************************************************************************/
/*                   RUNTIME FAILURES                                          */
/*******************************************************************************/

jobject SILWalaInstructionVisitor::visitCondFailInst(CondFailInst *FI) {

  SILValue CondOperand = FI->getOperand();

  if (Print) {
    llvm::outs() << "\t [OPERAND]: " << CondOperand << "\n";
    llvm::outs() << "\t [OPERAND ADDR]: " << CondOperand.getOpaqueValue() << "\n";
  }

  jobject Node = Instance->CAst->makeNode(CAstWrapper::ASSERT, findAndRemoveCAstNode(CondOperand.getOpaqueValue()));

  NodeMap.insert(std::make_pair(FI, Node));

  return Node;
}

/*******************************************************************************/
/*                      TERMINATORS                                            */
/*******************************************************************************/

jobject SILWalaInstructionVisitor::visitUnreachableInst(UnreachableInst *UI) {
  if (Print) {
    if (UI->isBranch()) {
      llvm::outs() << "\t This is a terminator of branch!" << "\n";
    }
    if (UI->isFunctionExiting()) {
      llvm::outs() << "\t This is a terminator of function!" << "\n";
    }
  }
  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitReturnInst(ReturnInst *RI) {
  SILValue RV = RI->getOperand();

  if (Print) {
    llvm::outs() << "\t [OPERAND]: " << RV << "\n";
    llvm::outs() << "\t [ADDR]: " << RV.getOpaqueValue() << "\n";
  }
  jobject Node = nullptr;
  if (RV != nullptr) {
    jobject V = nullptr;
    V = findAndRemoveCAstNode(RV.getOpaqueValue());
    if (V == nullptr) {
      Node = Instance->CAst->makeNode(CAstWrapper::RETURN);
    } else {
      Node = Instance->CAst->makeNode(CAstWrapper::RETURN, V);
    }
    NodeMap.insert(std::make_pair(RI, Node));
  }
  return Node;
}

jobject SILWalaInstructionVisitor::visitThrowInst(ThrowInst *TI) {
  SILValue TV = TI->getOperand();

  if (Print) {
    llvm::outs() << "\t [OPERAND]: " << TV << "\n";
    llvm::outs() << "\t [ADDR]: " << TV.getOpaqueValue() << "\n";
  }

  jobject Node = nullptr;
  if (TV != nullptr) {
    jobject V = nullptr;
    V = findAndRemoveCAstNode(TV.getOpaqueValue());
    if (V == nullptr) {
      Node = Instance->CAst->makeNode(CAstWrapper::THROW);
    } else {
      Node = Instance->CAst->makeNode(CAstWrapper::THROW, V);
    }
    NodeMap.insert(std::make_pair(TI, Node));
  }
  return Node;
}

jobject SILWalaInstructionVisitor::visitYieldInst(YieldInst *YI) {
  SILBasicBlock *ResumeBB = YI->getResumeBB();
  SILBasicBlock *UnwindBB = YI->getUnwindBB();

  if (Print) {
    llvm::outs() << "\t [RESUME BB]: " << ResumeBB << "\n";
    llvm::outs() << "\t [UNWIND BB]: " << UnwindBB << "\n";
  }

  list<jobject> yieldValues;

  for (const auto &value : YI->getYieldedValues()) {
    if (Print) {
      llvm::outs() << "\t [YIELD VALUE]: " << value << "\n";
    }
    jobject child = findAndRemoveCAstNode(value);
    if (child != nullptr) {
      yieldValues.push_back(child);
    }
  }

  jobject ResumeLabelNode = Instance->CAst->makeConstant(BasicBlockLabeller::label(ResumeBB).c_str());
  jobject ResumeGotoNode = Instance->CAst->makeNode(CAstWrapper::GOTO, ResumeLabelNode);

  jobject UnwindLabelNode = Instance->CAst->makeConstant(BasicBlockLabeller::label(UnwindBB).c_str());
  jobject UnwindGotoNode = Instance->CAst->makeNode(CAstWrapper::GOTO, UnwindLabelNode);

  jobject Node = Instance->CAst->makeNode(CAstWrapper::YIELD_STMT, Instance->CAst->makeArray(&yieldValues), ResumeGotoNode, UnwindGotoNode);

  NodeMap.insert(std::make_pair(YI, Node));

  return Node;
}

jobject SILWalaInstructionVisitor::visitUnwindInst(UnwindInst *UI) {
  jobject Node = Instance->CAst->makeNode(CAstWrapper::UNWIND);
  NodeMap.insert(std::make_pair(UI, Node));
  return Node;
}

jobject SILWalaInstructionVisitor::visitBranchInst(BranchInst *BI) {
  // This is an unconditional branch
  jobject GotoNode = Instance->CAst->makeNode(CAstWrapper::EMPTY);

  // Destination block
  int I = 0;
  SILBasicBlock *Dest = BI->getDestBB();
  if (Print) {
    llvm::outs() << "\t [DESTBB]: " << Dest << "\n";
    if (Dest != nullptr) {
      for (auto &Instr : *Dest) {
        llvm::outs() << "\t [INST" << I++ << "]: " << &Instr << "\n";
      }
    }
  }
  if (Dest != nullptr) {
    jobject LabelNode = Instance->CAst->makeConstant(BasicBlockLabeller::label(Dest).c_str());
    GotoNode = Instance->CAst->makeNode(CAstWrapper::GOTO, LabelNode);
  }

  for (unsigned Idx = 0; Idx < BI->getNumArgs(); Idx++) {
    llvm::outs() << "[ADDR]: " << Dest->getArgument(Idx) << "\n";
    jobject Node = findAndRemoveCAstNode(BI->getArg(Idx).getOpaqueValue());
    SymbolTable.insert(Dest->getArgument(Idx), ("argument" + std::to_string(Idx)));

    jobject varName = Instance->CAst->makeConstant(SymbolTable.get(Dest->getArgument(Idx)).c_str());
    jobject var = Instance->CAst->makeNode(CAstWrapper::VAR, varName);
    jobject assign = Instance->CAst->makeNode(CAstWrapper::ASSIGN, var, Node);
    NodeList.push_back(assign);
  }
  return GotoNode;
}

jobject SILWalaInstructionVisitor::visitCondBranchInst(CondBranchInst *CBI) {
  // 1. Condition
  SILValue Cond = CBI->getCondition();
  jobject CondNode = findAndRemoveCAstNode(Cond.getOpaqueValue());
  if (Print) {
    llvm::outs() << "\t [COND]: " << Cond.getOpaqueValue() << "\n";
  }

  // 2. True block
  int I = 0;
  SILBasicBlock *TrueBasicBlock = CBI->getTrueBB();
  jobject TrueGotoNode = Instance->CAst->makeNode(CAstWrapper::EMPTY);
  if (Print) {
    llvm::outs() << "\t [TBB]: " << TrueBasicBlock << "\n";
    if (TrueBasicBlock != nullptr) {
      for (auto &Instr : *TrueBasicBlock) {
        llvm::outs() << "\t [INST" << I++ << "]: " << &Instr << "\n";
      }
    }
  }
  if (TrueBasicBlock != nullptr) {
    jobject LabelNode = Instance->CAst->makeConstant(BasicBlockLabeller::label(TrueBasicBlock).c_str());
    TrueGotoNode = Instance->CAst->makeNode(CAstWrapper::GOTO, LabelNode);
  }

  // 3. False block
  I = 0;
  SILBasicBlock *FalseBasicBlock = CBI->getFalseBB();
  jobject FalseGotoNode = Instance->CAst->makeNode(CAstWrapper::EMPTY);
  if (Print) {
    llvm::outs() << "\t [FBB]: " << FalseBasicBlock << "\n";
    if (FalseBasicBlock != nullptr) {
      for (auto &Instr : *FalseBasicBlock) {
        llvm::outs() << "\t [INST" << I++ << "]: " << &Instr << "\n";
      }
    }
  }
  if (FalseBasicBlock != nullptr) {
    jobject LabelNode = Instance->CAst->makeConstant(BasicBlockLabeller::label(FalseBasicBlock).c_str());
    FalseGotoNode = Instance->CAst->makeNode(CAstWrapper::GOTO, LabelNode);
  }

  // 4. Assemble them into an if-stmt node
  jobject IfStmtNode = Instance->CAst->makeNode(CAstWrapper::EMPTY);
  if (FalseGotoNode != nullptr) { // with else block
    IfStmtNode = Instance->CAst->makeNode(CAstWrapper::IF_STMT, CondNode, TrueGotoNode, FalseGotoNode);
  } else { // without else block
    IfStmtNode = Instance->CAst->makeNode(CAstWrapper::IF_STMT, CondNode, TrueGotoNode);
  }
  NodeMap.insert(std::make_pair(CBI, IfStmtNode));
  return IfStmtNode;
}

jobject SILWalaInstructionVisitor::visitSwitchValueInst(SwitchValueInst *SVI) {

  SILValue Cond = SVI->getOperand();
  jobject CondNode = findAndRemoveCAstNode(Cond.getOpaqueValue());

  if (Print) {
    llvm::outs() << "\t [COND]: " << Cond.getOpaqueValue() << "\n";
  }

  // Make children
  list<jobject> Children;

  for (unsigned Idx = 0, Num = SVI->getNumCases(); Idx < Num; ++Idx) {
    auto Case = SVI->getCase(Idx);

    jobject CaseValNode = findAndRemoveCAstNode(Case.first);
    SILBasicBlock *CaseBasicBlock = Case.second;

    Children.push_back(CaseValNode);

    auto LabelNodeName = BasicBlockLabeller::label(CaseBasicBlock);
    jobject LabelNode = Instance->CAst->makeConstant(LabelNodeName.c_str());
    Children.push_back(LabelNode);

    if (Print) {
      if (SVI->hasDefault() && CaseBasicBlock == SVI->getDefaultBB()) {
        // Default Node.
        llvm::outs() << "\t [DEFAULT]: " << LabelNode << " => " << *CaseBasicBlock << "\n";
      } else {
        // Not Default Node.
        llvm::outs() << "\t [CASE]: VAL = " << CaseValNode << " " << LabelNodeName << " => " << *CaseBasicBlock << "\n";
      }

      int I = 0;
      for (auto &Instr : *CaseBasicBlock) {
        llvm::outs() << "\t [INST" << I++ << "]: " << &Instr << "\n";
      }
    }

    auto GotoCaseNode = Instance->CAst->makeNode(CAstWrapper::GOTO, LabelNode);
    Children.push_back(GotoCaseNode);
  }

  auto SwitchCasesNode = Instance->CAst->makeNode(CAstWrapper::BLOCK_STMT,  Instance->CAst->makeArray(&Children));
  auto SwitchNode = Instance->CAst->makeNode(CAstWrapper::SWITCH, CondNode, SwitchCasesNode);

  NodeMap.insert(std::make_pair(SVI, SwitchCasesNode));

  return SwitchNode;
}

jobject SILWalaInstructionVisitor::visitSelectValueInst(SelectValueInst *SVI) {

  if (Print) {
    llvm::outs() << "\t This should never be reached! Swift does not support this anymore" << "\n";
  }

  return Instance->CAst->makeNode(CAstWrapper::EMPTY);
}

jobject SILWalaInstructionVisitor::visitSwitchEnumInst(SwitchEnumInst *SWI) {

  SILValue Cond = SWI->getOperand();
  jobject CondNode = findAndRemoveCAstNode(Cond.getOpaqueValue());

  if (Print) {
    llvm::outs() << "\t [COND]: " << Cond.getOpaqueValue() << "\n";
  }

  list<jobject> Children;

  for (unsigned Idx = 0, Num = SWI->getNumCases(); Idx < Num; ++Idx) {
    auto Case = SWI->getCase(Idx);
    EnumElementDecl *CaseDecl = Case.first;
    SILBasicBlock *CaseBasicBlock = Case.second;

    auto LabelNodeName = BasicBlockLabeller::label(CaseBasicBlock);
    jobject LabelNode = Instance->CAst->makeConstant(LabelNodeName.c_str());
    Children.push_back(LabelNode);

    if (Print) {
      if (SWI->hasDefault() && CaseBasicBlock == SWI->getDefaultBB()) {
        // Default Node.
        llvm::outs() << "\t [DEFAULT]: " << LabelNode << " => " << *CaseBasicBlock << "\n";
      } else {
        // Not Default Node.
        llvm::outs() << "\t [CASE]: DECL = " << CaseDecl << " " << LabelNodeName << " => " << *CaseBasicBlock << "\n";
      }

      int I = 0;
      for (auto &Instr : *CaseBasicBlock) {
        llvm::outs() << "\t [INST" << I++ << "]: " << &Instr << "\n";
      }
    }

    auto GotoCaseNode = Instance->CAst->makeNode(CAstWrapper::GOTO, LabelNode);
    Children.push_back(GotoCaseNode);
  }

  auto SwitchCasesNode = Instance->CAst->makeNode(CAstWrapper::BLOCK_STMT,  Instance->CAst->makeArray(&Children));
  auto SwitchNode = Instance->CAst->makeNode(CAstWrapper::SWITCH, CondNode, SwitchCasesNode);

  NodeMap.insert(std::make_pair(SWI, SwitchNode));

  return SwitchNode;
}

jobject SILWalaInstructionVisitor::visitSwitchEnumAddrInst(SwitchEnumAddrInst *SEAI) {
  SILValue ConditionOperand = SEAI->getOperand();

  jobject CondNode = findAndRemoveCAstNode(ConditionOperand.getOpaqueValue());

  list<jobject> Children;

  for (unsigned i = 0, e = SEAI->getNumCases(); i < e; ++i) {
      EnumElementDecl *Element;
      SILBasicBlock *DestinationBlock;

      std::tie(Element, DestinationBlock) = SEAI->getCase(i);

      std::string ElementNameString = Element->getNameStr();
      jobject ElementNameNode = Instance->CAst->makeConstant(ElementNameString.c_str());

      if (Print) {
         llvm::outs() << "\t [BASIC BLOCK]: " << DestinationBlock << "\n";
      }

      jobject BlockLabelNodeName = Instance->CAst->makeConstant(BasicBlockLabeller::label(DestinationBlock).c_str());
      jobject GotoNode = Instance->CAst->makeNode(CAstWrapper::GOTO, BlockLabelNodeName);

      Children.push_back(ElementNameNode);
      Children.push_back(GotoNode);
    }

    if (SEAI->hasDefault()) {
      SILBasicBlock *DestinationBlock = SEAI->getDefaultBB();

      std::string ElementNameString = "DEFAULT";
      jobject ElementNameNode = Instance->CAst->makeConstant(ElementNameString.c_str());

      if (Print) {
         llvm::outs() << "\t [DEFAULT BASIC BLOCK]: " << DestinationBlock << "\n";
      }

      jobject BlockLabelNodeName = Instance->CAst->makeConstant(BasicBlockLabeller::label(DestinationBlock).c_str());
      jobject GotoNode = Instance->CAst->makeNode(CAstWrapper::GOTO, BlockLabelNodeName);

      Children.push_back(ElementNameNode);
      Children.push_back(GotoNode);
    }

    jobject EnumNode = Instance->CAst->makeNode(CAstWrapper::BLOCK_STMT,  Instance->CAst->makeArray(&Children));
    jobject SwitchEnumAddrNode = Instance->CAst->makeNode(CAstWrapper::SWITCH, CondNode, EnumNode);

    NodeMap.insert(std::make_pair(SEAI, SwitchEnumAddrNode));

    return SwitchEnumAddrNode;
}

jobject SILWalaInstructionVisitor::visitCheckedCastBranchInst(CheckedCastBranchInst *CI) {
  SILValue CastingOperand = CI->getOperand();
  SILType CastType = CI->getCastType();

  if (Print) {
    llvm::outs() << "\t [CONVERT]: " << CastingOperand.getOpaqueValue() << "\n";
    llvm::outs() << "\t [TO]: " << CastType.getAsString() <<  "\n";
  }

  // 1. Cast statement
  jobject CastingNode = findAndRemoveCAstNode(CastingOperand.getOpaqueValue());
  jobject CastingTypeNode =  Instance->CAst->makeConstant(CastType.getAsString().c_str());

  jobject ConversionNode = Instance->CAst->makeNode(CAstWrapper::CAST, CastingNode, CastingTypeNode);

  // 2. Success block
  SILBasicBlock *SuccessBlock = CI->getSuccessBB();

  if (Print) {
     llvm::outs() << "\t [SUCCESS BASIC BLOCK]: " << SuccessBlock << "\n";
  }

  jobject SuccessBlockNode = Instance->CAst->makeConstant(BasicBlockLabeller::label(SuccessBlock).c_str());
  jobject SuccessGoToNode = Instance->CAst->makeNode(CAstWrapper::GOTO, SuccessBlockNode);

  // 3. False block
  SILBasicBlock *FailureBlock = CI->getFailureBB();

  if (Print) {
     llvm::outs() << "\t [FAILIURE BASIC BLOCK]: " << FailureBlock << "\n";
  }

  jobject FailureBlockNode = Instance->CAst->makeConstant(BasicBlockLabeller::label(FailureBlock).c_str());
  jobject FailureGoToNode = Instance->CAst->makeNode(CAstWrapper::GOTO, FailureBlockNode);

  // 4. Assemble them into an if-stmt node
  jobject StmtNode = Instance->CAst->makeNode(CAstWrapper::IF_STMT, ConversionNode, SuccessGoToNode, FailureGoToNode);

  NodeMap.insert(std::make_pair(CI, StmtNode));
  return StmtNode;
}

jobject SILWalaInstructionVisitor::visitCheckedCastAddrBranchInst(CheckedCastAddrBranchInst *CI) {
  SILValue SrcValue = CI->getSrc();
  SILValue DestValue = CI->getDest();

  if (Print) {
    llvm::outs() << "\t [CONVERT]: " << CI->getSourceType().getString() << " " << SrcValue.getOpaqueValue();
    llvm::outs() << " [TO]: " << CI->getTargetType().getString() << " " << DestValue.getOpaqueValue() << "\n";
  }

  // 1. Cast statement
  jobject SrcNode = findAndRemoveCAstNode(SrcValue.getOpaqueValue());
  jobject DestNode = findAndRemoveCAstNode(DestValue.getOpaqueValue());

  jobject ConversionNode = Instance->CAst->makeNode(CAstWrapper::CAST, SrcNode, DestNode);

  // 2. Success block
  SILBasicBlock *SuccessBlock = CI->getSuccessBB();

  if (Print) {
     llvm::outs() << "\t [SUCCESS BASIC BLOCK]: " << SuccessBlock << "\n";
  }

  jobject SuccessBlockNode = Instance->CAst->makeConstant(BasicBlockLabeller::label(SuccessBlock).c_str());
  jobject SuccessGoToNode = Instance->CAst->makeNode(CAstWrapper::GOTO, SuccessBlockNode);

  // 3. False block
  SILBasicBlock *FailureBlock = CI->getFailureBB();

  if (Print) {
     llvm::outs() << "\t [FAILIURE BASIC BLOCK]: " << FailureBlock << "\n";
  }

  jobject FailureBlockNode = Instance->CAst->makeConstant(BasicBlockLabeller::label(FailureBlock).c_str());
  jobject FailureGoToNode = Instance->CAst->makeNode(CAstWrapper::GOTO, FailureBlockNode);

  // 4. Assemble them into an if-stmt node
  jobject StmtNode = Instance->CAst->makeNode(CAstWrapper::IF_STMT, ConversionNode, SuccessGoToNode, FailureGoToNode);

  NodeMap.insert(std::make_pair(CI, StmtNode));
  return StmtNode;
}

jobject SILWalaInstructionVisitor::visitTryApplyInst(TryApplyInst *TAI) {
  auto Call = visitApplySite(ApplySite(TAI));
  jobject TryFunc = Instance->CAst->makeNode(CAstWrapper::TRY, Call);
  jobject VarName = Instance->CAst->makeConstant("resulf_of_try");
  jobject Var = Instance->CAst->makeNode(CAstWrapper::VAR, VarName);
  jobject Node = Instance->CAst->makeNode(CAstWrapper::ASSIGN, Var, TryFunc);
  NodeMap.insert(std::make_pair(TAI, Node));
  SILBasicBlock *BB = TAI->getNormalBB();
  SymbolTable.insert(BB->getArgument(0), "resulf_of_try"); // insert the node into the hash map
  return Node;
}
