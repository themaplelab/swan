//===--- UnaryOperatorInstruction.java -----------------------------------===//
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

public class UnaryOperatorInstruction extends SILIRInstruction {

    public final Value resultValue;

    public final String operator;

    public final Value operand;

    public UnaryOperatorInstruction(String resultName, String resultType,
                                     String operator, String operand, InstructionContext ic) {
        super(ic);
        this.resultValue = new Value(resultName, resultType);
        ic.valueTable().add(this.resultValue);
        this.operator = operator;
        this.operand = ic.valueTable().getValue(operand);
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitUnaryOperatorInstruction(this);
    }

    @Override
    public String toString() {
        return resultValue.simpleName()+ " := " + operator + " " +
                operand.simpleName() + this.getComment();
    }
}
