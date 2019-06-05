//===--- SILWalaInstructionVisitor.h - SIL to CAst Translator ------------===//
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
/// This file defines the SILWalaInstructionVisitor class, which inherits
/// the SILInstructionVisitor template class (part of the Swift compiler).
/// The SILInstructionVisitor translates a given SIL (Swift Intermediate
/// Language) Module to CAst (WALA IR).
///
//===---------------------------------------------------------------------===//

// TODO: Class/method Doxygen comments and thorough functionality comments.

#ifndef SWAN_SILWALAINSTRUCTIONVISITOR_H
#define SWAN_SILWALAINSTRUCTIONVISITOR_H

#include "InfoStructures.hpp"
#include "SymbolTable.h"
#include "WALAInstance.h"
#include "swift/SIL/ApplySite.h"
#include "swift/SIL/SILVisitor.h"
#include <jni.h>
#include <list>
#include <unordered_map>

using namespace swift;

namespace swift_wala {

class WALAInstance;

/// This class translates SIL to CAst by using Swift's SILInstructionVisitor which has callbacks, including
/// ones for every type of SILInstruction. This makes translating simple.
class SILWalaInstructionVisitor : public SILInstructionVisitor<SILWalaInstructionVisitor, jobject> {
public:
  SILWalaInstructionVisitor(WALAInstance *Instance, bool Print) : Instance(Instance), Print(Print) {}

  /// Visit the SILModule of the swift file that holds the SILFunctions.
  void visitModule(SILModule *M);
  /// Visit the SILFunction that holds the SILBasicBlocks.
  void visitSILFunction(SILFunction *F);
  /// Visit the SILBasicBlock that holds the SILInstructions.
  void visitSILBasicBlock(SILBasicBlock *BB);
  /// Before visiting the SILInstruction, this callback is fired and here we set up the source info of the instruction.
  void beforeVisit(SILInstruction *I);

  /// If we do not have a callback handler implemented for a SILInstruction, it will fire this method.
  jobject visitSILInstruction(SILInstruction *I) {
    llvm::outs() << "Not handled instruction: \n" << *I << "\n";
    return nullptr;
  }

private:
  /// The WALAInstance that holds the resultant CAst.
  WALAInstance *Instance;
  /// Decides whether to print the translation debug info to terminal at runtime.
  bool Print;

  /// Current 'Cast Entity' being worked on.
  std::shared_ptr<CAstEntityInfo> currentEntity;

  /// Update instrInfo with the given SILInstruction information.
  void updateInstrSourceInfo(SILInstruction *I);
  /// Called by beforeVisit. Prints debug info about the instrInfo (in our case). It can also handle any other
  /// housekeeping that needs to be done at every instruction if needed.
  void perInstruction();

  /// Import call that all instructions directly pertaining to function calls lead to. It will create a
  /// CAstNode that will be mapped to the function site (CAstEntity).
  jobject visitApplySite(ApplySite Apply);
  /// TODO: What is this used for exactly?
  jobject findAndRemoveCAstNode(void *Key);
  /// Returns CAstNode with appropriate operator kind.
  jobject getOperatorCAstType(Identifier Name);

  unsigned int InstructionCount = 0;

  /// Source information about the SILInstruction.
  std::shared_ptr<InstrInfo> instrInfo;
  /// Source information about the SILFunction.
  std::shared_ptr<FunctionInfo> functionInfo;
  /// Source information about the SILModule.
  std::shared_ptr<ModuleInfo> moduleInfo;

  /// TODO: Why is this needed?
  SymbolTable SymbolTable;

  /// Map of Instr* (various SIL instruction types) to the CAstNode representing it.
  /// Scoped to the current SILBasicBlock.
  std::unordered_map<void *, jobject> NodeMap;
  /// List of CAstNodes in the current SILBasicBlock.
  std::list<jobject> NodeList;
  /// List of BLOCK_STMT CAstNodes that hold the AST for the SILBasicBlock.
  /// Scoped to the current SILFunction.
  std::list<jobject> BlockStmtList;

public:
  // SIL INSTRUCTION VISITOR CALLBACKS (TRANSLATE EACH INSTRUCTION TO CAST NODE)

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
  jobject visitDestructureTupleInst(DestructureTupleInst *DTI);
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

};

} // end swift_wala namespace

#endif // SWAN_SILWALAINSTRUCTIONVISITOR_H