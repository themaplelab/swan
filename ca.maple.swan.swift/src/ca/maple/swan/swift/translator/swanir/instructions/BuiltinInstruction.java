//===--- BuiltinInstruction.java -----------------------------------------===//
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
import ca.maple.swan.swift.translator.swanir.values.BuiltinFunctionRefValue;

public class BuiltinInstruction extends SWANIRInstruction {

    public final String functionName;

    public final BuiltinFunctionRefValue value;

    public BuiltinInstruction(String name, String resultName, String resultType, InstructionContext ic) {
        super(ic);
        this.functionName = name;
        this.value = new BuiltinFunctionRefValue(resultName, resultType, name);
        ic.valueTable().add(this.value);
    }

    @Override
    public void visit(ISWANIRVisitor v) {
        v.visitBuiltinInstruction(this);
    }

    @Override
    public String toString() {
        return value.simpleName() + " := builtin " + functionName + this.getComment();
    }
}
