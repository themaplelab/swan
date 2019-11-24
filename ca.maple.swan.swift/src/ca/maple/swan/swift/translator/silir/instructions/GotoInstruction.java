//===--- GotoInstruction.java --------------------------------------------===//
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

import ca.maple.swan.swift.translator.silir.BasicBlock;
import ca.maple.swan.swift.translator.silir.context.InstructionContext;

public class GotoInstruction extends SILIRInstruction {

    public BasicBlock bb;

    public GotoInstruction(BasicBlock bb, InstructionContext ic) {
        super(ic);
        this.bb = bb;
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitGotoInstruction(this);
    }

    @Override
    public String toString() {
        if (bb == null) {
            return "goto bb UNKNOWN" + this.getComment();
        }
        return "goto bb" + bb.getNumber() + this.getComment();
    }

}
