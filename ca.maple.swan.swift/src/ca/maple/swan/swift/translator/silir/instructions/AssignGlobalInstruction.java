//===--- AssignGlobalInstruction.java ------------------------------------===//
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

package ca.maple.swan.swift.translator.silir.instructions;

import ca.maple.swan.swift.translator.silir.context.InstructionContext;
import ca.maple.swan.swift.translator.silir.values.Value;

public class AssignGlobalInstruction extends SILIRInstruction {

    public final Value to;
    public final Value from;

    public AssignGlobalInstruction(String toName, String toType, String from, InstructionContext ic) {
        super(ic);
        this.from = ic.globalValueTable().getValue(from);
        this.to = new Value(toName, toType);
        ic.valueTable().add(this.to);
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitAssignGlobalInstruction(this);
    }

    @Override
    public String toString() {
        return to.simpleName() + " := " + from.name + "\n";
    }
}
