//===--- DynamicApplyInstruction.java ------------------------------------===//
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

package ca.maple.swan.swift.translator.silir.instructions.spds;

import ca.maple.swan.swift.translator.silir.Function;
import ca.maple.swan.swift.translator.silir.context.InstructionContext;
import ca.maple.swan.swift.translator.silir.instructions.ISILIRVisitor;
import ca.maple.swan.swift.translator.silir.instructions.SILIRInstruction;
import ca.maple.swan.swift.translator.silir.values.Value;

import java.util.ArrayList;
import java.util.Iterator;

/*
 * Apply that calls multiple functions (for protocols).
 */

public class DynamicApplyInstruction extends SILIRInstruction {

    public final ArrayList<Function> functions;

    public final Value result;

    public final ArrayList<Value> args;

    public DynamicApplyInstruction(ArrayList<Function> functions, String resultName, String resultType, ArrayList<String> args, InstructionContext ic) {
        super(ic);
        Value result = new Value(resultName, resultType);
        ic.valueTable().add(result);
        this.functions = functions;
        this.result = result;
        this.args = new ArrayList<>();
        for (String arg : args) {
            this.args.add(ic.valueTable().getValue(arg));
        }
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitDynamicApplyInstruction(this);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(result.simpleName());
        s.append(" :=");
        for (Function f : functions) {
            s.append("\n            ");
            s.append(f.getName());
        }
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
