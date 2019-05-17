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
 /// Main code for translating SIL to CAst.
 ///
 /// TODO: Add technical description and code documentation.
 //----------------------------------------------------------------------------/

#pragma once

#include "swift/SIL/SILVisitor.h"
#include "swift/SIL/ApplySite.h"
#include "WALAInstance.h"
#include "SymbolTable.h"

#include <jni.h>
#include <unordered_map>

using namespace swift;


namespace swift_wala {

class WALAInstance;

struct ModuleInfo {
    explicit ModuleInfo(llvm::StringRef sourcefile) : sourcefile(sourcefile) {};
    llvm::StringRef sourcefile;
  };

struct FunctionInfo {
  FunctionInfo(llvm::StringRef name, llvm::StringRef demangled) : name(name), demangled(demangled) {};
  llvm::StringRef name;
  llvm::StringRef demangled;
};

struct InstrInfo {
  unsigned num;
  swift::SILPrintContext::ID id;
  swift::SILInstructionKind instrKind;

  swift::SILInstruction::MemoryBehavior memBehavior;
  swift::SILInstruction::ReleasingBehavior relBehavior;

  short srcType;
  std::string Filename;
  unsigned startLine;
  unsigned startCol;
  unsigned endLine;
  unsigned endCol;

  llvm::ArrayRef<swift::SILValue> ops;
  ModuleInfo *modInfo;
  FunctionInfo *funcInfo;
};

class SILWalaInstructionVisitor : public SILInstructionVisitor<SILWalaInstructionVisitor, jobject> {
public:
  SILWalaInstructionVisitor(WALAInstance *Instance, bool Print) : Instance(Instance), Print(Print) {}

  std::unique_ptr<SILModule> visitModule(std::unique_ptr<SILModule> M);
  void visitSILFunction(SILFunction *F);
  void visitSILBasicBlock(SILBasicBlock *BB);
  void beforeVisit(SILInstruction *I);

  jobject visitSILInstruction(SILInstruction *I) {
    llvm::outs() << "Not handled instruction: \n" << *I << "\n";
    return nullptr;
  }

  /*******************************************************************************/
  /*                      Allocation and Deallocation                            */
  /*******************************************************************************/

  jobject visitAllocStackInst(AllocStackInst *ASI);
  jobject visitAllocBoxInst(AllocBoxInst *ABI);
  jobject visitAllocRefInst(AllocRefInst *ARI);
  jobject visitDeallocStackInst(DeallocStackInst *DSI);
  jobject visitDeallocBoxInst(DeallocBoxInst *DBI);
  jobject visitDeallocRefInst(DeallocRefInst *DRI);
  jobject visitAllocGlobalInst(AllocGlobalInst *AGI);
  jobject visitProjectBoxInst(ProjectBoxInst *PBI);
  jobject visitAllocValueBufferInst(AllocValueBufferInst *AVBI);
  jobject visitProjectValueBufferInst(ProjectValueBufferInst *PVBI);
  jobject visitDeallocValueBufferInst(DeallocValueBufferInst *DVBI);

  /*******************************************************************************/
  /*                        Debug Information                                    */
  /*******************************************************************************/

  jobject visitDebugValueInst(DebugValueInst *DBI);
  jobject visitDebugValueAddrInst(DebugValueAddrInst *DVAI);

  /*******************************************************************************/
  /*                        Accessing Memory                                     */
  /*******************************************************************************/

  jobject visitLoadInst(LoadInst *LI);
  jobject visitStoreInst(StoreInst *SI);
  jobject visitBeginBorrowInst(BeginBorrowInst *BBI);
  jobject visitLoadBorrowInst(LoadBorrowInst *LBI);
  jobject visitEndBorrowInst(EndBorrowInst *EBI);
  jobject visitAssignInst(AssignInst *AI);
  jobject visitStoreBorrowInst(StoreBorrowInst *SBI);
  jobject visitMarkUninitializedInst(MarkUninitializedInst *MUI);
  jobject visitMarkFunctionEscapeInst(MarkFunctionEscapeInst *MFEI);
  jobject visitCopyAddrInst(CopyAddrInst *CAI);
  jobject visitDestroyAddrInst(DestroyAddrInst *DAI);
  jobject visitIndexAddrInst(IndexAddrInst *IAI);
  jobject visitTailAddrInst(TailAddrInst *TAI);
  jobject visitBeginAccessInst(BeginAccessInst *BAI);
  jobject visitEndAccessInst(EndAccessInst *EAI);
  jobject visitBeginUnpairedAccessInst(BeginUnpairedAccessInst *BUI);
  jobject visitEndUnpairedAccessInst(EndUnpairedAccessInst *EUAI);

  /*******************************************************************************/
  /*                        Reference Counting                                   */
  /*******************************************************************************/

  // jobject visitStrongUnpinInst(StrongUnpinInst *SUI);
  jobject visitEndLifetimeInst(EndLifetimeInst *ELI);
  jobject visitMarkDependenceInst(MarkDependenceInst *MDI);
  // jobject visitStrongPinInst(StrongPinInst *SPI);

  /*******************************************************************************/
  /*                         Literals                                            */
  /*******************************************************************************/

  jobject visitFunctionRefInst(FunctionRefInst *FRI);
  jobject visitGlobalAddrInst(GlobalAddrInst *GAI);
  jobject visitIntegerLiteralInst(IntegerLiteralInst *ILI);
  jobject visitFloatLiteralInst(FloatLiteralInst *FLI);
  jobject visitStringLiteralInst(StringLiteralInst *SLI);
  //jobject visitConstStringLiteralInst(ConstStringLiteralInst *CSLI);

  /*******************************************************************************/
  /*                         Dynamic Dispatch                                    */
  /*******************************************************************************/

  jobject visitClassMethodInst(ClassMethodInst *CMI);
  jobject visitObjCMethodInst(ObjCMethodInst *AMI);
  jobject visitSuperMethodInst(SuperMethodInst *SMI);
  jobject visitWitnessMethodInst(WitnessMethodInst *WMI);

  /*******************************************************************************/
  /*                         Function Application                                */
  /*******************************************************************************/

  jobject visitApplyInst(ApplyInst *AI);
  jobject visitBeginApplyInst(BeginApplyInst *BAI);
  jobject visitEndApplyInst(EndApplyInst *EAI);
  jobject visitAbortApplyInst(AbortApplyInst *AAI);
  jobject visitPartialApplyInst(PartialApplyInst *PAI);
  jobject visitBuiltinInst(BuiltinInst *BI);

  /*******************************************************************************/
  /*                          Metatypes                                          */
  /*******************************************************************************/

  jobject visitMetatypeInst(MetatypeInst *MI);
  jobject visitValueMetatypeInst(ValueMetatypeInst *VMI);

  /*******************************************************************************/
  /*                          Aggregate Types                                    */
  /*******************************************************************************/

  jobject visitCopyValueInst(CopyValueInst *CVI);
  jobject visitDestroyValueInst(DestroyValueInst *DVI);
  jobject visitTupleInst(TupleInst *TI);
  jobject visitTupleExtractInst(TupleExtractInst *TEI);
  jobject visitTupleElementAddrInst(TupleElementAddrInst *TEAI);
  jobject visitStructInst(StructInst *SI);
  jobject visitStructExtractInst(StructExtractInst *SEI);
  jobject visitStructElementAddrInst(StructElementAddrInst *SEAI);
  jobject visitRefElementAddrInst(RefElementAddrInst *REAI);
  jobject visitRefTailAddrInst(RefTailAddrInst *RTAI);

  /*******************************************************************************/
  /*                          Enums                                              */
  /*******************************************************************************/

  jobject visitEnumInst(EnumInst *EI);
  jobject visitUncheckedEnumDataInst(UncheckedEnumDataInst *UED);
  jobject visitInjectEnumAddrInst(InjectEnumAddrInst *IUAI);
  jobject visitInitEnumDataAddrInst(InitEnumDataAddrInst *UDAI);
  jobject visitUncheckedTakeEnumDataAddrInst(UncheckedTakeEnumDataAddrInst *UDAI);
  jobject visitSelectEnumInst(SelectEnumInst *SEI);

  /*******************************************************************************/
  /*                          Protocol and Protocol Composition Types            */
  /*******************************************************************************/

  jobject visitInitExistentialAddrInst(InitExistentialAddrInst *IEAI);
  jobject visitDeinitExistentialAddrInst(DeinitExistentialAddrInst *DEAI);
  jobject visitInitExistentialValueInst(InitExistentialValueInst *IEVI);
  jobject visitDeinitExistentialValueInst(DeinitExistentialValueInst *DEVI);
  jobject visitOpenExistentialAddrInst(OpenExistentialAddrInst *OEAI);
  jobject visitOpenExistentialValueInst(OpenExistentialValueInst *OEVI);
  jobject visitInitExistentialMetatypeInst(InitExistentialMetatypeInst *IEMI);
  jobject visitOpenExistentialMetatypeInst(OpenExistentialMetatypeInst *OEMI);
  jobject visitInitExistentialRefInst(InitExistentialRefInst *IERI);
  jobject visitOpenExistentialRefInst(OpenExistentialRefInst *OERI);
  jobject visitAllocExistentialBoxInst(AllocExistentialBoxInst *AEBI);
  jobject visitProjectExistentialBoxInst(ProjectExistentialBoxInst *PEBI);
  jobject visitOpenExistentialBoxInst(OpenExistentialBoxInst *OEBI);
  jobject visitOpenExistentialBoxValueInst(OpenExistentialBoxValueInst *OEBVI);
  jobject visitDeallocExistentialBoxInst(DeallocExistentialBoxInst *DEBI);

  /*******************************************************************************/
  /*                          Blocks                                             */
  /*******************************************************************************/

  /*******************************************************************************/
  /*                          Unchecked Conversions                              */
  /*******************************************************************************/

  jobject visitUpcastInst(UpcastInst *UI);
  jobject visitAddressToPointerInst(AddressToPointerInst *ATPI);
  jobject visitPointerToAddressInst(PointerToAddressInst *PTAI);
  jobject visitUncheckedRefCastInst(UncheckedRefCastInst *URCI);
  jobject visitUncheckedAddrCastInst(UncheckedAddrCastInst *UACI);
  jobject visitUncheckedTrivialBitCastInst(UncheckedTrivialBitCastInst *BI);
  jobject visitUncheckedOwnershipConversionInst(UncheckedOwnershipConversionInst *UOCI);
  jobject visitRefToRawPointerInst(RefToRawPointerInst *CI);
  jobject visitRawPointerToRefInst(RawPointerToRefInst *CI);
  jobject visitUnmanagedToRefInst(UnmanagedToRefInst *CI);
  jobject visitConvertFunctionInst(ConvertFunctionInst *CFI);
  jobject visitThinFunctionToPointerInst(ThinFunctionToPointerInst *TFPI);
  jobject visitPointerToThinFunctionInst(PointerToThinFunctionInst *CI);
  // jobject visitClassifyBridgeObjectInst(ClassifyBridgeObjectInst *CBOI);
  // jobject visitBridgeObjectToRefInst(BridgeObjectToRefInst *I);
  jobject visitThinToThickFunctionInst(ThinToThickFunctionInst *TTFI);
  jobject visitThickToObjCMetatypeInst(ThickToObjCMetatypeInst *TTOMI);
  jobject visitObjCToThickMetatypeInst(ObjCToThickMetatypeInst *OTTMI);

  /*******************************************************************************/
  /*                          Checked Conversions                                */
  /*******************************************************************************/

  jobject visitUnconditionalCheckedCastAddrInst(UnconditionalCheckedCastAddrInst *CI);

  /*******************************************************************************/
  /*                          Runtime Failures                                   */
  /*******************************************************************************/

  jobject visitCondFailInst(CondFailInst *FI);

  /*******************************************************************************/
  /*                           Terminators                                       */
  /*******************************************************************************/

  jobject visitUnreachableInst(UnreachableInst *UI);
  jobject visitReturnInst(ReturnInst *RI);
  jobject visitThrowInst(ThrowInst *TI);
  jobject visitYieldInst(YieldInst *YI);
  jobject visitUnwindInst(UnwindInst *UI);
  jobject visitBranchInst(BranchInst *BI);
  jobject visitCondBranchInst(CondBranchInst *CBI);
  jobject visitSwitchValueInst(SwitchValueInst *SVI);
  jobject visitSelectValueInst(SelectValueInst *SVI);
  jobject visitSwitchEnumInst(SwitchEnumInst *SWI);
  jobject visitSwitchEnumAddrInst(SwitchEnumAddrInst *SEAI);
  jobject visitCheckedCastBranchInst(CheckedCastBranchInst *CI);
  jobject visitCheckedCastAddrBranchInst(CheckedCastAddrBranchInst *CI);
  jobject visitTryApplyInst(TryApplyInst *TAI);

private:
  WALAInstance *Instance;
  bool Print;

  void updateInstrSourceInfo(SILInstruction *I);
  void perInstruction();
  jobject visitApplySite(ApplySite Apply);
  jobject findAndRemoveCAstNode(void *Key);
  jobject getOperatorCAstType(Identifier Name);

  unsigned int InstructionCount = 0;

  std::shared_ptr<InstrInfo> instrInfo;
  std::shared_ptr<FunctionInfo> functionInfo;
  std::shared_ptr<ModuleInfo> moduleInfo;

  SymbolTable SymbolTable;
  std::unordered_map<void *, jobject> NodeMap;
  std::list<jobject> NodeList;
  std::list<jobject> BlockStmtList;

};

} // end swift_wala namespace
