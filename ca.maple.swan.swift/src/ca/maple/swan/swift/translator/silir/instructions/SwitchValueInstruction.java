//===--- SwitchValueInstruction.java -------------------------------------===//
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

import ca.maple.swan.swift.translator.silir.BasicBlock;
import ca.maple.swan.swift.translator.silir.context.InstructionContext;
import ca.maple.swan.swift.translator.silir.values.Value;
import com.ibm.wala.util.collections.Pair;

import java.util.ArrayList;

public class SwitchValueInstruction extends SILIRInstruction {

    public final Value switchValue;

    public final ArrayList<Pair<Value, BasicBlock>> cases;

    // Can be null.
    public final BasicBlock defaultBlock;

    public SwitchValueInstruction(String switchValueName, ArrayList<Pair<String, BasicBlock>> cases, BasicBlock defaultBlock, InstructionContext ic) {
        super(ic);
        this.switchValue = ic.valueTable().getValue(switchValueName);
        this.cases = new ArrayList<>();
        for (Pair<String, BasicBlock> p : cases) {
            this.cases.add(Pair.make(ic.valueTable().getValue(p.fst), p.snd));
        }
        this.defaultBlock = defaultBlock;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("switch ");
        s.append(switchValue.simpleName());
        for (Pair<Value, BasicBlock> p : cases) {
            s.append("\n            case ");
            s.append(p.fst.simpleName());
            s.append(": bb");
            s.append(p.snd.getNumber());
        }
        if (defaultBlock != null) {
            s.append("\n            default: bb");
            s.append(defaultBlock.getNumber());
        }
        s.append(this.getComment());
        return s.toString();
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitSwitchValueInstruction(this);
    }
}
