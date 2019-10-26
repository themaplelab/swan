//===--- YieldInstruction.java -------------------------------------------===//
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

import java.util.ArrayList;

public class YieldInstruction extends SILIRInstruction {

    // TODO ADD BRANCHING (THIS IS A TEMPORARY IMPLEMENTATION)

    private final ArrayList<Value> values;

    public YieldInstruction(ArrayList<String> yieldValues, InstructionContext ic) {
        super(ic);
        this.values = new ArrayList<>();
        for (String s : yieldValues) {
            this.values.add(ic.valueTable().getValue(s));
        }
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitYieldInstruction(this);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("yield ");
        for (Value v : values) {
            s.append(v.simpleName());
            s.append(" ");
        }
        s.append("\n");
        return s.toString();
    }
}
