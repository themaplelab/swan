//===--- SILValueTable.java ----------------------------------------------===//
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

package ca.maple.swan.swift.translator.values;

import ca.maple.swan.swift.translator.SILInstructionContext;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.util.debug.Assertions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/*
 * Table that holds the SIL value information. The idea here is that
 * we want to statically know and keep track of all SIL values ("registers")
 * as much as possible so we can do away with things like pointers, and
 * handle the many nuances of SIL.
 */

public class SILValueTable {

    private final HashMap<String, SILValue> values;

    // For keeping track of which values need a DECL_STMT generated.
    private final ArrayList<SILValue> undeclaredValues;

    private final Set<String> declared;

    private final ArrayList<SILValue> globals;

    public SILValueTable() {
        values = new HashMap<>();
        undeclaredValues = new ArrayList<>();
        declared = new HashSet<>();
        globals = new ArrayList<>();
    }

    public SILValue getValue(String valueName) {
        Assertions.productionAssertion(values.containsKey(valueName));
        return values.get(valueName);
    }

    public SILValue getGlobalValue(String valueName, SILInstructionContext C) {
        if (!values.containsKey(valueName)) {
            // Dummy type, doesn't matter anyways.
            SILValue val = new SILValue(valueName, "global", C);
            this.addGlobalValue(val);
            C.parent.setGotoTarget(val.getVarNode(), val.getVarNode());
        }
        return values.get(valueName);
    }

    public void copyValue(String newName, String toCopy) {
        values.put(newName, getValue(toCopy));
    }

    public void copyValue(String newName, SILValue toCopy) {
        values.put(newName, toCopy);
    }

    public boolean hasValue(String valueName) {
        return values.containsKey(valueName);
    }

    public void removeValue(String valueName) {
        values.remove(valueName);
    }

    public SILValue getAndRemoveValue(String valueName) {
        Assertions.productionAssertion(values.containsKey(valueName));
        SILValue toReturn = values.get(valueName);
        values.remove(valueName);
        return toReturn;
    }

    public void replaceValue(String name, SILValue newValue) {
        values.replace(name, newValue);
    }

    public void addValue(SILValue v) {
        values.put(v.name, v);
        undeclaredValues.add(v);
    }

    public void addGlobalValue(SILValue v) {
        values.put(v.name, v);
        globals.add(v);
    }

    public void addAll(SILValueTable t) {
        this.values.putAll(t.values);
    }

    public void addArg(SILValue v) {
        values.put(v.name, v);
    }

    public void clearValues() {
        values.clear();
    }

    public ArrayList<CAstNode> getDecls() {
        // TODO: Only generate decls for those variables that actually appear.
        ArrayList<CAstNode> decls = new ArrayList<>();
        for (SILValue v : undeclaredValues) {
            if (!declared.contains(v)) {
                decls.add(v.getDecl());
                declared.add(v.getName());
            }

        }
        for (SILValue v : globals) {
            if (!declared.contains(v)) {
                decls.add(v.getGlobalDecl());
                declared.add(v.getName());
            }
        }
        undeclaredValues.clear();
        globals.clear();
        return decls;
    }

}
