//===--- BasicBlockLabeller.cpp - Returns ID of SIL basic block ----------===//
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
/// This file contains the implementation of the method that returns the ID
/// of a given SIL basic block. It is used by SILWalaInstructionVisitor.
///
//===---------------------------------------------------------------------===//

#include "BasicBlockLabeller.h"

using namespace swan;

string BasicBlockLabeller::label(swift::SILBasicBlock* basicBlock) {
  return (string("BLOCK #") + std::to_string(basicBlock->getDebugID()));
}
