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

package ca.maple.swan.swift.translator.swanir.instructions.functions;

import ca.maple.swan.swift.translator.swanir.BasicBlock;
import ca.maple.swan.swift.translator.swanir.context.InstructionContext;
import ca.maple.swan.swift.translator.swanir.instructions.ISWANIRVisitor;
import ca.maple.swan.swift.translator.swanir.instructions.SWANIRInstruction;
import ca.maple.swan.swift.translator.swanir.values.Value;

import java.util.ArrayList;
import java.util.Iterator;

public class TryApplyInstruction extends SWANIRInstruction {

    public final Value functionRefValue;

    public final BasicBlock normalBB;

    public final BasicBlock errorBB;

    public final ArrayList<Value> args;

    public TryApplyInstruction(String funcRef, BasicBlock normalBB, BasicBlock errorBB, ArrayList<String> args, InstructionContext ic) {
        super(ic);
        this.normalBB = normalBB;
        this.errorBB = errorBB;
        this.functionRefValue = ic.valueTable().getValue(funcRef);
        this.args = new ArrayList<>();
        for (String arg : args) {
            this.args.add(ic.valueTable().getValue(arg));
        }
    }

    @Override
    public void visit(ISWANIRVisitor v) {
        v.visitTryApplyInstruction(this);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("try ");
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
        s.append(" normal: ");
        s.append(normalBB.getNumber());
        s.append(", error: ");
        s.append(errorBB.getNumber());
        s.append(this.getComment());
        return s.toString();
    }
}
