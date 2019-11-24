//===--- BinaryOperatorInstruction.java ----------------------------------===//
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

public class BinaryOperatorInstruction extends SILIRInstruction {

    public final Value resultValue;

    public final String operator;

    public final Value operand1;

    public final Value operand2;

    public BinaryOperatorInstruction(String resultName, String resultType,
                                     String operator, String operand1,
                                     String operand2, InstructionContext ic) {
        super(ic);
        this.resultValue = new Value(resultName, resultType);
        ic.valueTable().add(this.resultValue);
        this.operator = operator;
        this.operand1 = ic.valueTable().getValue(operand1);
        this.operand2 = ic.valueTable().getValue(operand2);
    }

    public BinaryOperatorInstruction(String resultName,
                                     String operator, String operand1,
                                     String operand2, InstructionContext ic) {
        super(ic);
        this.resultValue = ic.valueTable().getValue(resultName);
        this.operator = operator;
        this.operand1 = ic.valueTable().getValue(operand1);
        this.operand2 = ic.valueTable().getValue(operand2);
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitBinaryOperatorInstruction(this);
    }

    @Override
    public String toString() {
        return resultValue.simpleName() + " := " +
                operand1.simpleName() + " " + operator + " " +
                operand2.simpleName() + this.getComment();
    }
}
