//===--- NewArrayTupleInstruction.java -----------------------------------===//
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
import ca.maple.swan.swift.translator.swanir.values.ArrayTupleValue;
import ca.maple.swan.swift.translator.swanir.values.Value;

public class NewArrayTupleInstruction extends SWANIRInstruction {

    public final Value value;

    public NewArrayTupleInstruction(String name, String type, InstructionContext ic) {
        super(ic);
        this.value = new ArrayTupleValue(name, type);
        ic.valueTable().add(this.value);
    }

    @Override
    public void visit(ISWANIRVisitor v) {
        v.visitNewArrayTupleInst(this);
    }

    @Override
    public String toString() {
        return this.value.simpleName() + " := new " + this.value.type + this.getComment();
    }
}