//===--- FieldAliasInstruction.java --------------------------------------===//
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

public class FieldAliasInstruction extends SILIRInstruction {

    public final Value resultValue;

    public final Value operandValue;

    public final String field;

    public FieldAliasInstruction(String resultName, String resultType, String operandName, String operandField, InstructionContext ic) {
        super(ic);
        this.operandValue = ic.valueTable().getValue(operandName);
        this.resultValue = new FieldAliasValue(resultName, resultType, this.operandValue, operandField);
        ic.valueTable().add(this.resultValue);
        this.field = operandField;
    }

    @Override
    public String toString() {
        return operandValue.simpleName() + "." + field + " alias-to " + resultValue.simpleName() + "\n";
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitFieldAliasInstruction(this);
    }

    @Override
    public boolean isExplicit() {
        return false;
    }
}
