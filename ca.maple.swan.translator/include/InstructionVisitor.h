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

#ifndef SWAN_INSTRUCTIONVISITOR_H
#define SWAN_INSTRUCTIONVISITOR_H

#include "WALAInstance.h"
#include "InfoStructures.hpp"
#include "swift/SIL/ApplySite.h"
#include "swift/SIL/SILVisitor.h"
#include <jni.h>
#include <list>

using namespace swift;

namespace swan {

  class WALAInstance;

  /// Decides whether to print the translation debug info to terminal at runtime.
  static bool SWAN_PRINT = true;
  /// Source information can be annoying/unnecessary for debugging, so there is an option to disable it.
  static bool SWAN_PRINT_SOURCE = true;
  /// Disable printing memory and file information.
  static bool SWAN_PRINT_FILE_AND_MEMORY = false;

  /// This class translates SIL to CAst by using Swift's SILInstructionVisitor which has callbacks, including
  /// ones for every type of SILInstruction. This makes translating simple.
  class InstructionVisitor : public SILInstructionVisitor<InstructionVisitor, jobject> {
  public:
    InstructionVisitor(WALAInstance *Instance) : Instance(Instance) {}

    /// Visit the SILModule of the swift file that holds the SILFunctions.
    void visitSILModule(SILModule *M);

    /// If we do not have a callback handler implemented for a SILInstruction, it will fire this method.
    jobject visitSILInstruction(SILInstruction *I) {
      llvm::outs() << "Instruction not handled: \n\t" << *I << "\n";
      return nullptr;
    }

  private:
    /// Visit the SILFunction that holds the SILBasicBlocks.
    void visitSILFunction(SILFunction *F);
    /// Visit the SILBasicBlock that holds the SILInstructions.
    void visitSILBasicBlock(SILBasicBlock *BB);
    /// Prints the SILInstructionInfo
    void printSILInstructionInfo();

    /// Returns CAstNode with appropriate operator kind.
    jobject getOperatorCAstType(Identifier &Name);

    /// The WALAInstance that holds the resultant CAst.
    WALAInstance *Instance;

    /// Current instruction number.
    unsigned InstructionCounter = 0;

    /// Raw data of current instruction begin translated.
    std::unique_ptr<RootInstructionInfo> currentInstruction;

    /// Raw data of current basic block.
    std::unique_ptr<RootBasicBlockInfo> currentBasicBlock;

    /// Current 'Cast Entity' which contains info necessary to later create the CAstEntity for the current function.
    std::unique_ptr<RootFunctionInfo> currentFunction;

     /// Contains all of the raw data of the module.
    std::unique_ptr<RootModuleInfo> currentModule;

    /// Returns CAstNode with appropriate operator kind.
    jobject getOperatorCAstType(Identifier Name);

    /// Source information about the SILInstruction.
    std::unique_ptr<SILInstructionInfo> instrInfo;
    /// Source information about the SILFunction.
    std::unique_ptr<SILFunctionInfo> functionInfo;
    /// Source information about the SILModule.
    std::unique_ptr<SILModuleInfo> moduleInfo;

  public:

    // SIL INSTRUCTION VISITOR CALLBACKS (TRANSLATE EACH INSTRUCTION TO CAST NODE)

    /// Before visiting the SILInstruction, this callback is fired and here we set up the source info of the instruction.
    void beforeVisit(SILInstruction *I);

    /*******************************************************************************/
    /*                      Allocation and Deallocation                            */
    /*******************************************************************************/

    jobject visitAllocStackInst(AllocStackInst *ASI);
    jobject visitAllocRefInst(AllocRefInst *ARI);
    jobject visitAllocRefDynamicInst(AllocRefDynamicInst *ARDI);
    jobject visitAllocBoxInst(AllocBoxInst *ABI);
    jobject visitAllocValueBufferInst(AllocValueBufferInst *AVBI);
    jobject visitAllocGlobalInst(AllocGlobalInst *AGI);
    jobject visitDeallocStackInst(DeallocStackInst *DSI);
    jobject visitDeallocBoxInst(DeallocBoxInst *DBI);
    jobject visitProjectBoxInst(ProjectBoxInst *PBI);
    jobject visitDeallocRefInst(DeallocRefInst *DRI);
    jobject visitDeallocPartialRefInst(DeallocPartialRefInst *DPRI);
    jobject visitDeallocValueBufferInst(DeallocValueBufferInst *DVBI);
    jobject visitProjectValueBufferInst(ProjectValueBufferInst *PVBI);

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
    jobject visitLoadBorrowInst(LoadBorrowInst *LBI);
    jobject visitBeginBorrowInst(BeginBorrowInst *BBI);
    jobject visitEndBorrowInst(EndBorrowInst *EBI);
    jobject visitAssignInst(AssignInst *AI);
    jobject visitAssignByWrapperInst(AssignByWrapperInst *ABWI);
    jobject visitMarkUninitializedInst(MarkUninitializedInst *MUI);
    jobject visitMarkFunctionEscapeInst(MarkFunctionEscapeInst *MFEI);
    jobject visitCopyAddrInst(CopyAddrInst *CAI);
    jobject visitDestroyAddrInst(DestroyAddrInst *DAI);
    jobject visitIndexAddrInst(IndexAddrInst *IAI);
    jobject visitTailAddrInst(TailAddrInst *TAI);
    jobject visitIndexRawPointerInst(IndexRawPointerInst *IRPI);
    jobject visitBindMemoryInst(BindMemoryInst *BMI);
    jobject visitBeginAccessInst(BeginAccessInst *BAI);
    jobject visitEndAccessInst(EndAccessInst *EAI);
    jobject visitBeginUnpairedAccessInst(BeginUnpairedAccessInst *BUI);
    jobject visitEndUnpairedAccessInst(EndUnpairedAccessInst *EUAI);

    /*******************************************************************************/
    /*                        Reference Counting                                   */
    /*******************************************************************************/

    jobject visitStrongRetainInst(StrongRetainInst *SRTI);
    jobject visitStrongReleaseInst(StrongReleaseInst *SRLI);
    jobject visitSetDeallocatingInst(SetDeallocatingInst *SDI);
    jobject visitStrongRetainUnownedInst(StrongRetainUnownedInst *SRUI);
    jobject visitUnownedRetainInst(UnownedRetainInst *URTI);
    jobject visitUnownedReleaseInst(UnownedReleaseInst *URLI);
    jobject visitLoadWeakInst(LoadWeakInst *LWI);
    jobject visitStoreWeakInst(StoreWeakInst *SWI);
    jobject visitLoadUnownedInst(LoadUnownedInst *LUI);
    jobject visitStoreUnownedInst(StoreUnownedInst *SUI);
    jobject visitFixLifetimeInst(FixLifetimeInst *FLI);
    jobject visitEndLifetimeInst(EndLifetimeInst *ELI);
    jobject visitMarkDependenceInst(MarkDependenceInst *MDI);
    jobject visitIsUniqueInst(IsUniqueInst *IUI);
    jobject visitIsEscapingClosureInst(IsEscapingClosureInst *IECI);
    jobject visitCopyBlockInst(CopyBlockInst *CBI);
    jobject visitCopyBlockWithoutEscapingInst(CopyBlockWithoutEscapingInst *CBWEI);
    // TODO? builtin "unsafeGuaranteed" & builtin "unsafeGuaranteedEnd"

    /*******************************************************************************/
    /*                         Literals                                            */
    /*******************************************************************************/

    jobject visitFunctionRefInst(FunctionRefInst *FRI);
    jobject visitDynamicFunctionRefInst(DynamicFunctionRefInst *DFRI);
    jobject visitPreviousDynamicFunctionRefInst(PreviousDynamicFunctionRefInst *PDFRI);
    jobject visitGlobalAddrInst(GlobalAddrInst *GAI);
    jobject visitGlobalValueInst(GlobalValueInst *GVI);
    jobject visitIntegerLiteralInst(IntegerLiteralInst *ILI);
    jobject visitFloatLiteralInst(FloatLiteralInst *FLI);
    jobject visitStringLiteralInst(StringLiteralInst *SLI);

    /*******************************************************************************/
    /*                         Dynamic Dispatch                                    */
    /*******************************************************************************/

    jobject visitClassMethodInst(ClassMethodInst *CMI);
    jobject visitObjCMethodInst(ObjCMethodInst *AMI);
    jobject visitSuperMethodInst(SuperMethodInst *SMI);
    jobject visitObjCSuperMethodInst(ObjCSuperMethodInst *ASMI);
    jobject visitWitnessMethodInst(WitnessMethodInst *WMI);

    /*******************************************************************************/
    /*                         Function Application                                */
    /*******************************************************************************/

    jobject visitApplyInst(ApplyInst *AI);
    jobject visitBeginApplyInst(BeginApplyInst *BAI);
    jobject visitAbortApplyInst(AbortApplyInst *AAI);
    jobject visitEndApplyInst(EndApplyInst *EAI);
    jobject visitPartialApplyInst(PartialApplyInst *PAI);
    jobject visitBuiltinInst(BuiltinInst *BI);

    /*******************************************************************************/
    /*                          Metatypes                                          */
    /*******************************************************************************/

    jobject visitMetatypeInst(MetatypeInst *MI);
    jobject visitValueMetatypeInst(ValueMetatypeInst *VMI);
    jobject visitExistentialMetatypeInst(ExistentialMetatypeInst *EMI);
    jobject visitObjCProtocolInst(ObjCProtocolInst *OPI);

    /*******************************************************************************/
    /*                          Aggregate Types                                    */
    /*******************************************************************************/

    jobject visitRetainValueInst(RetainValueInst *RVI);
    jobject visitRetainValueAddrInst(RetainValueAddrInst *RVAI);
    jobject visitUnmanagedRetainValueInst(UnmanagedRetainValueInst *URVI);
    jobject visitCopyValueInst(CopyValueInst *CVI);
    jobject visitReleaseValueInst(ReleaseValueInst *REVI);
    jobject visitReleaseValueAddrInst(ReleaseValueAddrInst *REVAI);
    jobject visitUnmanagedReleaseValueInst(UnmanagedReleaseValueInst *UREVI);
    jobject visitDestroyValueInst(DestroyValueInst *DVI);
    jobject visitAutoreleaseValueInst(AutoreleaseValueInst *AREVI);
    jobject visitTupleInst(TupleInst *TI);
    jobject visitTupleExtractInst(TupleExtractInst *TEI);
    jobject visitTupleElementAddrInst(TupleElementAddrInst *TEAI);
    jobject visitDestructureTupleInst(DestructureTupleInst *DTI);
    jobject visitStructInst(StructInst *SI);
    jobject visitStructExtractInst(StructExtractInst *SEI);
    jobject visitStructElementAddrInst(StructElementAddrInst *SEAI);
    jobject visitDestructureStructInst(DestructureStructInst *DSI);
    jobject visitObjectInst(ObjectInst *OI);
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
    jobject visitThinToThickFunctionInst(ThinToThickFunctionInst *TTFI);
    jobject visitThickToObjCMetatypeInst(ThickToObjCMetatypeInst *TTOMI);
    jobject visitObjCToThickMetatypeInst(ObjCToThickMetatypeInst *OTTMI);
    jobject visitConvertEscapeToNoEscapeInst(ConvertEscapeToNoEscapeInst *CVT);

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

} // end swan namespace

#endif // SWAN_INSTRUCTIONVISITOR_H
