//===--- IWALASWANIRVisitor.java ----------------------------------------===//
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

package ca.maple.swan.swift.translator.swanir.instructions;

/*
 * Visitor for instructions pertaining to WALA translation.
 */

import ca.maple.swan.swift.translator.swanir.instructions.spds.DynamicApplyInstruction;
import ca.maple.swan.swift.translator.swanir.instructions.spds.RuleInstruction;

public abstract class IWALASWANIRVisitor extends ISWANIRVisitor {

    @Override
    public void visitDynamicApplyInstruction(DynamicApplyInstruction instruction) {

    }

    @Override
    public void visitRuleInstruction(RuleInstruction instruction) {

    }
}
