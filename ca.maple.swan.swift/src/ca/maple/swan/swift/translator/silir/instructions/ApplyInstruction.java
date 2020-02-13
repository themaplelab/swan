//===--- ApplyInstruction.java -------------------------------------------===//
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

import java.util.ArrayList;
import java.util.Iterator;

public class ApplyInstruction extends SILIRInstruction {

    public final Value functionRefValue;

    public final Value result;

    public final ArrayList<Value> args;

    public ApplyInstruction(String funcRef, String resultName, String resultType, ArrayList<String> args, InstructionContext ic) {
        super(ic);
        Value result = new Value(resultName, resultType);
        ic.valueTable().add(result);
        this.functionRefValue = ic.valueTable().getValue(funcRef);
        this.result = result;
        this.args = new ArrayList<>();
        for (String arg : args) {
            this.args.add(ic.valueTable().getValue(arg));
        }
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitApplyInstruction(this);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(result.simpleName());
        s.append(" := ");
        s.append(functionRefValue.simpleName());
        s.append("(");
        Iterator<Value> it = args.iterator();
        while (it.hasNext()) {
            s.append(it.next().simpleName());
            if (it.hasNext()) {
                s.append(", ");
            }
        }
        s.append(")");
        s.append(this.getComment());
        return s.toString();
    }
}
