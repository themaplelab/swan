//===--- SWANIRInstruction.java ------------------------------------------===//
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

import ca.maple.swan.swift.translator.swanir.context.InstructionContext;

public abstract class SWANIRInstruction {

    public final InstructionContext ic;

    private int lineNumber = 0;

    private String comment = "";

    protected SWANIRInstruction(InstructionContext ic) {
        this.ic = ic;
    }

    @Override
    public abstract String toString();

    public abstract void visit(ISWANIRVisitor v);

    public void setComment(String s) {
        this.comment = s;
    }

    public void setLineNumber(int n) {
        this.lineNumber = n;
    }

    public int getLineNumber() {
        return this.lineNumber;
    }

    public String getComment() {
        return this.comment.equals("") ? "\n" : "    // " + this.comment + "\n";
    }
}
