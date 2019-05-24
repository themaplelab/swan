//===--- BasicBlockLabeller.h - Returns ID of SIL basic block ------------===//
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
/// This file contains the declaration of the method that returns the ID
/// of a given SIL basic block. It is used by SILWalaInstructionVisitor.
///
//===---------------------------------------------------------------------===//

#ifndef SWAN_BASICBLOCKLABELLER_H
#define SWAN_BASICBLOCKLABELLER_H

#include "swift/SIL/SILBasicBlock.h"
#include <string>

using std::string;

namespace swift_wala {

/// This class contains a single static method that returns the block # of
/// the given SILBasicBlock.
class BasicBlockLabeller {
public:
    /// This returns the block # of the given SILBasicBlock.
  static string label(swift::SILBasicBlock* basicBlock);
};

} // end swift_wala namespace

#endif // SWAN_BASICBLOCKLABELLER_H