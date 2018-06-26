#ifndef SWIFT_SILWALAINSTRUCTIONVISITOR_H
#define SWIFT_SILWALAINSTRUCTIONVISITOR_H

#include "swift/SIL/SILVisitor.h"
#include "swift-wala/WALASupport/WALAInstance.h"

#include <jni.h>
#include <unordered_map>

using namespace swift;


namespace swift_wala {

class WALAInstance;

class SILWalaInstructionVisitor : public SILInstructionVisitor<SILWalaInstructionVisitor, jobject> {
public:
  SILWalaInstructionVisitor(std::shared_ptr<WALAInstance> Instance, bool Print) : Instance(Instance), Print(Print) {}

  void visitSILModule(SILModule *M);
  void visitSILFunction(SILFunction *F);
  void visitSILBasicBlock(SILBasicBlock *BB);
  void visitModule(SILModule *M);
  void beforeVisit(SILInstruction *I);

  jobject visitSILInstruction(SILInstruction *I) {
    llvm::outs() << "Not handled instruction: \n" << *I << "\n";
    return nullptr;
  }

private:
  std::weak_ptr<WALAInstance> Instance;
  bool Print;

};

}

#endif //SWIFT_SILWALAINSTRUCTIONVISITOR_H
