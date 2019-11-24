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

    private String comment = "";

    protected SILIRInstruction(InstructionContext ic) {
        this.ic = ic;
    }

    @Override
    public abstract String toString();

    public abstract void visit(ISILIRVisitor v);

    public boolean isExplicit() {
        return true;
    }

    public void setComment(String s) {
        this.comment = s;
    }

    public String getComment() {
        return this.comment.equals("") ? "\n" : "    // " + this.comment + "\n";
    }
}
