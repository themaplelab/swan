/******************************************************************************
 * Copyright (c) 2019 Maple @ University of Alberta
 * All rights reserved. This program and the accompanying materials (unless
 * otherwise specified by a license inside of the accompanying material)
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *****************************************************************************/

 // SEE HEADER FILE FOR DOCUMENTATION

#include "swift-wala/WALASupport/BasicBlockLabeller.h"

using namespace swift_wala;

string BasicBlockLabeller::label(swift::SILBasicBlock* basicBlock) {
  return (string("BLOCK #") + std::to_string(basicBlock->getDebugID()));
}
