//===--- RuleInstruction.java --------------------------------------------===//
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

package ca.maple.swan.swift.translator.swanir.instructions.spds;

import ca.maple.swan.swift.translator.swanir.context.InstructionContext;
import ca.maple.swan.swift.translator.swanir.instructions.ISWANIRVisitor;
import ca.maple.swan.swift.translator.swanir.instructions.SWANIRInstruction;

/*
 * This instruction is here to specify a rule.
 * TODO: Have a way of representing all of the different rules and a way to load the data for them.
 */

public class RuleInstruction extends SWANIRInstruction {

    RuleInstruction(InstructionContext ic) {
        super(ic);
    }

    @Override
    public String toString() {
        // TODO
        return null;
    }

    @Override
    public void visit(ISWANIRVisitor v) {
        v.visitRuleInstruction(this);
    }
}
