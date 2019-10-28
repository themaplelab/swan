//===--- ImplicitCopyInstruction.java ------------------------------------===//
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
import ca.maple.swan.swift.translator.silir.printing.ValueNameSimplifier;
import ca.maple.swan.swift.translator.silir.values.Value;

public class ImplicitCopyInstruction extends SILIRInstruction {

    private final String to;
    private final Value from;

    public ImplicitCopyInstruction(String to, String from, InstructionContext ic) {
        super(ic);
        this.from = ic.valueTable().getPossibleAlias(from);
        ic.valueTable().copy(to, from);
        this.to = to;
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitImplicitCopyInstruction(this);
    }

    @Override
    public String toString() {
        return ValueNameSimplifier.get(to) + " := " + from.simpleName() + "\n";
    }

    @Override
    public boolean isExplicit() {
        return false;
    }
}
