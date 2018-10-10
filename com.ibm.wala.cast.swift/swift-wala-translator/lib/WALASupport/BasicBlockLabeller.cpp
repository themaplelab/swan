#include "swift-wala/WALASupport/BasicBlockLabeller.h"

using namespace swift_wala;

string BasicBlockLabeller::label(swift::SILBasicBlock* basicBlock) {
  return (string("BLOCK #") + std::to_string(basicBlock->getDebugID()));
}

