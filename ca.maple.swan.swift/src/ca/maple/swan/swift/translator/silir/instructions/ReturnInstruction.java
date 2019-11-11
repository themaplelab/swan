//===--- ReturnInstruction.java ------------------------------------------===//
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

public class ReturnInstruction extends SILIRInstruction {

    private final Value returnVal; // MAY BE NULL;

    public ReturnInstruction(String returnVal, InstructionContext ic) {
        super(ic);
        this.returnVal = ic.valueTable().has(returnVal) ? ic.valueTable().getValue(returnVal) : null;
    }

    public ReturnInstruction(InstructionContext ic) {
        this(null, ic);
    }

    public Value getReturnVal() {
        return this.returnVal;
    }

    public boolean hasReturnVal() {
        return this.returnVal != null;
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitReturnInstruction(this);
    }

    @Override
    public String toString() {
        return "return " +
                ((returnVal != null)
                    ? returnVal.simpleName()
                    : "")
                + "\n";
    }
}
