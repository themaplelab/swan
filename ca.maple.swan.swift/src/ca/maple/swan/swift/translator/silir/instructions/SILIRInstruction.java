//===--- SILIRInstruction.java -------------------------------------------===//
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

public abstract class SILIRInstruction {

    public final InstructionContext ic;

    private int lineNumber = -1;

    private String comment = "";

    protected SILIRInstruction(InstructionContext ic) {
        this.ic = ic;
    }

    private boolean isExplicit = true;

    @Override
    public abstract String toString();

    public abstract void visit(ISILIRVisitor v);

    public boolean isExplicit() {
        return this.isExplicit;
    }

    public void setImplicit() {
        this.isExplicit = false;
    }

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
