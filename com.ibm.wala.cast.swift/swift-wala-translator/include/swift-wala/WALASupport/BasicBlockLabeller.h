/******************************************************************************
 * Copyright (c) 2019 Maple @ University of Alberta
 * All rights reserved. This program and the accompanying materials (unless
 * otherwise specified by a license inside of the accompanying material)
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *    Mark Mroz - initial implementation
 *    Daniil Tiganov (Github: tiganov) - documentation, QC changes
 *****************************************************************************/

 //----------------------------------------------------------------------------/
 /// DESCRIPTION
 /// Returns the ID of a given SIL basic block.
 /// Used by SILWALAInstructionVisitor.
 //----------------------------------------------------------------------------/

#pragma once

#include <string>
#include "swift/SIL/SILBasicBlock.h"

using std::string;

namespace swift_wala {

class BasicBlockLabeller {
public:
  static string label(swift::SILBasicBlock* basicBlock);
};

} // end swift_wala namespace
