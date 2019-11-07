//===--- ThrowInstruction.java -------------------------------------------===//
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

public class ThrowInstruction extends SILIRInstruction {

    public final Value optionalOperand;

    public ThrowInstruction(InstructionContext ic) {
        super(ic);
        this.optionalOperand = null;
    }

    public ThrowInstruction(String operandName, InstructionContext ic) {
        super(ic);
        this.optionalOperand = ic.valueTable().getValue(operandName);
    }

    @Override
    public String toString() {
        return "throw" + ((optionalOperand != null) ? optionalOperand.simpleName() : "") + "\n";
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitThrowInstruction(this);
    }
}
