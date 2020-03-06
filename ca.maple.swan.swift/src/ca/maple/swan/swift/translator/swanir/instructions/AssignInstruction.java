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

package ca.maple.swan.swift.translator.swanir.instructions;

import ca.maple.swan.swift.translator.swanir.context.InstructionContext;
import ca.maple.swan.swift.translator.swanir.values.ArrayIndexAliasValue;
import ca.maple.swan.swift.translator.swanir.values.FieldAliasValue;
import ca.maple.swan.swift.translator.swanir.values.Value;

public class AssignInstruction extends SWANIRInstruction {

    public final Value to;
    public final Value from;

    public AssignInstruction(String toName, String from, InstructionContext ic) {
        super(ic);
        this.from = ic.valueTable().getPossibleAlias(from);
        this.to = ic.valueTable().getValue(toName);
    }

    public AssignInstruction(String toName, String toType, String from, InstructionContext ic) {
        super(ic);
        Value possibleFrom = ic.valueTable().getPossibleAlias(from);
        if (possibleFrom instanceof FieldAliasValue) {
            // This is a unique case where an assignment is attempted from a value which is an alias.
            // We assume the client wants an explicit copy so the best we can do is a copy.
            this.to = new Value(toName, toType);
            ic.valueTable().add(this.to);
            // Adding an instruction in the constructor of an instruction is not ideal.
            ic.bc.block.addInstruction(new FieldReadInstruction(
                    toName, toType, ((FieldAliasValue) possibleFrom).value.name, ((FieldAliasValue) possibleFrom).field, ic));
            this.from = this.to; // To basically NOP the assign.
        } else if (possibleFrom instanceof ArrayIndexAliasValue) {
            this.to = new Value(toName, toType);
            ic.valueTable().add(this.to);
            ic.bc.block.addInstruction(new ArrayReadInstruction(
                    toName, toType, ((ArrayIndexAliasValue) possibleFrom).value.name, ((ArrayIndexAliasValue) possibleFrom).index, ic));
            this.from = this.to;
        } else {
            this.to = new Value(toName, toType);
            this.from = possibleFrom;
            ic.valueTable().add(this.to);
        }
    }

    @Override
    public void visit(ISWANIRVisitor v) {
        v.visitAssignInstruction(this);
    }

    @Override
    public String toString() {
        return to.simpleName() + " := " + from.simpleName() + this.getComment();
    }
}
