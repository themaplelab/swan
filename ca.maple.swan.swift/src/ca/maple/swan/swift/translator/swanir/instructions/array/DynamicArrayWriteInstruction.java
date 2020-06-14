//===--- DynamicArrayWriteInstruction.java -------------------------------===//
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

package ca.maple.swan.swift.translator.swanir.instructions.array;

import ca.maple.swan.swift.translator.swanir.context.InstructionContext;
import ca.maple.swan.swift.translator.swanir.instructions.ISWANIRVisitor;
import ca.maple.swan.swift.translator.swanir.instructions.SWANIRInstruction;
import ca.maple.swan.swift.translator.swanir.values.Value;

public class DynamicArrayWriteInstruction extends SWANIRInstruction {

    public final Value base;

    public final Value index;

    public final Value operand;

    public DynamicArrayWriteInstruction(String base, String operand, String index, InstructionContext ic) {
        super(ic);
        this.base = ic.valueTable().getPossibleAlias(base);
        this.operand = ic.valueTable().getValue(operand);
        this.index = ic.valueTable().getValue(index);
    }

    @Override
    public void visit(ISWANIRVisitor v) {
        v.visitDynamicArrayWriteInstruction(this);
    }

    @Override
    public String toString() {
        return base.simpleName() + "[" + this.index + "]" + " := " + operand.simpleName() + this.getComment();
    }
}
