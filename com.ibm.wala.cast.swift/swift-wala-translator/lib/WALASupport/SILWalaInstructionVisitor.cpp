#include <swift/SIL/SILModule.h>

#include "swift-wala/WALASupport/SILWalaInstructionVisitor.h"

using namespace swift_wala;

void SILWalaInstructionVisitor::visitModule(SILModule *M) {
  M->dump();
}
