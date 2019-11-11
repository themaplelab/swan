//===--- AssignInstruction.java ------------------------------------------===//
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
import ca.maple.swan.swift.translator.silir.values.FieldAliasValue;
import ca.maple.swan.swift.translator.silir.values.Value;

public class AssignInstruction extends SILIRInstruction {

    public final Value to;
    public final Value from;

    public AssignInstruction(String toName, String from, InstructionContext ic) {
        super(ic);
        this.from = ic.valueTable().getValue(from);
        this.to = ic.valueTable().getValue(toName);
    }

    public AssignInstruction(String toName, String toType, String from, InstructionContext ic) {
        super(ic);
        this.from = ic.valueTable().getPossibleAlias(from);
        if (this.from instanceof FieldAliasValue) {
            System.err.println("Just checking if this ever occurs in practice");
            this.to = new FieldAliasValue(this.from.name, this.from.type,
                    ((FieldAliasValue) this.from).value, ((FieldAliasValue) this.from).field);
        } else {
            this.to = new Value(toName, toType);
        }
        ic.valueTable().add(this.to);
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitAssignInstruction(this);
    }

    @Override
    public String toString() {
        return to.simpleName() + " := " + from.simpleName() + "\n";
    }
}
