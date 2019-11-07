//===--- SwitchAssignValueInstruction.java -------------------------------===//
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
import com.ibm.wala.util.collections.Pair;

import java.util.ArrayList;

public class SwitchAssignValueInstruction extends SILIRInstruction {

    public final Value result;

    public final Value switchValue;

    public final ArrayList<Pair<Value, Value>> cases;

    // Can be null.
    public final Value defaultValue;

    public SwitchAssignValueInstruction(String resultName, String resultType, String switchValueName,
                                  ArrayList<Pair<String, String>> cases, String defaultValueName, InstructionContext ic) {
        super(ic);
        this.result = new Value(resultName, resultType);
        ic.valueTable().add(this.result);
        this.switchValue = ic.valueTable().getValue(switchValueName);
        this.defaultValue = defaultValueName != null ? ic.valueTable().getValue(defaultValueName) : null;
        this.cases = new ArrayList<>();
        for (Pair<String, String> p : cases) {
            this.cases.add(Pair.make(ic.valueTable().getValue(p.fst), ic.valueTable().getValue(p.snd)));
        }
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("switch ");
        s.append(switchValue.simpleName());
        for (Pair<Value, Value> p : cases) {
            s.append("\n            case ");
            s.append(p.fst.simpleName());
            s.append(": ");
            s.append(p.snd.simpleName());
        }
        if (defaultValue != null) {
            s.append("\n            default: ");
            s.append(defaultValue.simpleName());
        }
        s.append("\n");
        return s.toString();
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitSwitchAssignValueInstruction(this);
    }
}
