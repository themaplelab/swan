//===--- DictionaryWriteInstruction.java ---------------------------------===//
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

package ca.maple.swan.swift.translator.swanir.instructions.dictionary;

import ca.maple.swan.swift.translator.swanir.context.InstructionContext;
import ca.maple.swan.swift.translator.swanir.instructions.ISWANIRVisitor;
import ca.maple.swan.swift.translator.swanir.instructions.SWANIRInstruction;
import ca.maple.swan.swift.translator.swanir.values.Value;

public class DictionaryWriteInstruction extends SWANIRInstruction {

    public final Value result;

    public final Value operand;

    public DictionaryWriteInstruction(String resultName, String operand, InstructionContext ic) {
        super(ic);
        this.result = ic.valueTable().getValue(resultName);
        this.operand = ic.valueTable().getValue(operand);
    }

    @Override
    public void visit(ISWANIRVisitor v) {
        v.visitDictionaryWriteInstruction(this);
    }

    @Override
    public String toString() {
        return result.simpleName() + "[\"*\"]" + " := " + operand.simpleName() + this.getComment();
    }
}