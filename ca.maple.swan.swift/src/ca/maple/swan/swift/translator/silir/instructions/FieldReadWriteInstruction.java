//===--- FieldReadWriteInstruction.java ----------------------------------===//
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
import ca.maple.swan.swift.translator.silir.values.Value;


public class FieldReadWriteInstruction extends SILIRInstruction {

    public final Value resultValue;

    public final String resultField;

    public final Value operandValue;

    public final String operandField;

    public final Value dynamicOperandField;

    public final boolean operandIsDynamic;

    public FieldReadWriteInstruction(String resultName, String resultField,
                                     String operandName, String operandField,
                                     boolean operandIsDynamic, InstructionContext ic) {
        super(ic);
        this.resultValue = ic.valueTable().getValue(resultName);
        this.resultField = resultField;
        this.operandValue = ic.valueTable().getValue(operandName);
        this.operandIsDynamic = operandIsDynamic;
        if (operandIsDynamic) {
            this.operandField = "N/A";
            this.dynamicOperandField = ic.valueTable().getValue(operandField);
        } else {
            this.operandField = operandField;
            this.dynamicOperandField = null;
        }
    }

    public FieldReadWriteInstruction(String resultName, String resultField,
                                     String operandName, String operandField, InstructionContext ic) {
        this (resultName, resultField, operandName, operandField, false, ic);
    }

    @Override
    public String toString() {
        return this.resultValue.simpleName() + "." + resultField + " := " + operandValue.simpleName() + "." +
                (operandIsDynamic ? this.dynamicOperandField.simpleName() : operandField) +
                this.getComment();
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitFieldReadWriteInstruction(this);
    }
}
