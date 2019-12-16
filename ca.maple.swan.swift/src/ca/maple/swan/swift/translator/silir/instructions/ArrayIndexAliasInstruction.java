//===--- ArrayIndexAliasInstruction.java ---------------------------------===//
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
import ca.maple.swan.swift.translator.silir.values.ArrayIndexAliasValue;
import ca.maple.swan.swift.translator.silir.values.Value;

public class ArrayIndexAliasInstruction extends SILIRInstruction {

    public final Value resultValue;

    public final Value operandValue;

    public final int index;

    public ArrayIndexAliasInstruction(String resultName, String resultType, String operandName, int index, InstructionContext ic) {
        super(ic);
        this.operandValue = ic.valueTable().getValue(operandName);
        this.resultValue = new ArrayIndexAliasValue(resultName, resultType, this.operandValue, index);
        ic.valueTable().add(this.resultValue);
        this.index = index;
        this.setImplicit();
    }

    @Override
    public String toString() {
        return operandValue.simpleName() + "[" + index + "]" + " alias-to " + resultValue.simpleName() + this.getComment();
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitLoadArrayIndexInstruction(this);
    }

}
