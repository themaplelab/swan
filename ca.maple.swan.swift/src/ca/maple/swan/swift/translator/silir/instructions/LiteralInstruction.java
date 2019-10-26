//===--- LiteralInstruction.java -----------------------------------------===//
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
import ca.maple.swan.swift.translator.silir.values.LiteralValue;

public class LiteralInstruction extends SILIRInstruction {

    public final LiteralValue literal;

    public LiteralInstruction(Object literal, String resultName, String resultType, InstructionContext ic) {
        super(ic);
        this.literal = new LiteralValue(resultName, resultType, literal);
        ic.bc.fc.vt.add(this.literal);
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitLiteralInstruction(this);
    }

    @Override
    public String toString() {
        return literal.simpleName() + " := #" + literal.getLiteral() + "\n";
    }
}
