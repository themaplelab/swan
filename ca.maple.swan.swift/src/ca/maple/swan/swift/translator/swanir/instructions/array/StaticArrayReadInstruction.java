//===--- StaticArrayReadInstruction.java ---------------------------------===//
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

package ca.maple.swan.swift.translator.swanir.instructions.array;

import ca.maple.swan.swift.translator.swanir.context.InstructionContext;
import ca.maple.swan.swift.translator.swanir.instructions.ISWANIRVisitor;
import ca.maple.swan.swift.translator.swanir.instructions.SWANIRInstruction;
import ca.maple.swan.swift.translator.swanir.values.Value;

public class StaticArrayReadInstruction extends SWANIRInstruction {

    public final Value result;

    public final int index;

    public final Value operandBase;


    public StaticArrayReadInstruction(String resultName, String resultType, String operand, int index, InstructionContext ic) {
        super(ic);
        this.result = new Value(resultName, resultType);
        ic.valueTable().add(this.result);
        this.operandBase = ic.valueTable().getValue(operand);
        this.index = index;
    }

    @Override
    public void visit(ISWANIRVisitor v) {
        v.visitStaticArrayReadInstruction(this);
    }

    @Override
    public String toString() {
        return result.simpleName() + " := " + operandBase.simpleName() +
                "[" + index + "]" + this.getComment();
    }
}