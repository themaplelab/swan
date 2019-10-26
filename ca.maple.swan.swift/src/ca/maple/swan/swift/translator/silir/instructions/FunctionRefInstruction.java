//===--- FunctionRefInstruction.java -------------------------------------===//
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

import ca.maple.swan.swift.translator.silir.Function;
import ca.maple.swan.swift.translator.silir.context.InstructionContext;
import ca.maple.swan.swift.translator.silir.values.FunctionRefValue;

public class FunctionRefInstruction extends SILIRInstruction {

    public final FunctionRefValue value;

    public FunctionRefInstruction(String resultName, String resultType, Function f, InstructionContext ic) {
        super(ic);
        FunctionRefValue refValue = new FunctionRefValue(resultName, resultType, f);
        ic.valueTable().add(refValue);
        this.value = refValue;
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitFunctionRefInstruction(this);
    }

    @Override
    public String toString() {
        return value.simpleName() + " := func_ref " +
                ((value.getFunction() != null) ? value.getFunction().getName() : "?")
                + "\n";
    }
}
