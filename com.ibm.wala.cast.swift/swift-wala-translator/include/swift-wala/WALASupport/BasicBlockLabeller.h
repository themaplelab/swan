#ifndef SWIFT_BASICBLOCKLABELLER_H
#define SWIFT_BASICBLOCKLABELLER_H

#include <string>
#include "swift/SIL/SILBasicBlock.h"

using std::string;

namespace swift_wala {

class BasicBlockLabeller {
public:
  static string label(swift::SILBasicBlock* basicBlock);
};

} // end namespace swift

#endif

