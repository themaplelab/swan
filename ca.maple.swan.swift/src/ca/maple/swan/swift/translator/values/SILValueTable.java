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

import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.util.debug.Assertions;

import java.util.ArrayList;
import java.util.HashMap;

public class SILValueTable {

    private final HashMap<String, SILValue> values;
    private final ArrayList<SILValue> undeclaredValues;

    public SILValueTable() {
        values = new HashMap<>();
        undeclaredValues = new ArrayList<>();
    }

    public SILValue getValue(String valueName) {
        Assertions.productionAssertion(values.containsKey(valueName));
        return values.get(valueName);
    }

    public void copyValue(String newName, String toCopy) {
        values.put(newName, getValue(toCopy));
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

    public void addValue(SILValue v) {
        values.put(v.name, v);
        undeclaredValues.add(v);
    }

    public void clearValues() {
        values.clear();
    }

    public ArrayList<CAstNode> getDecls() {
        ArrayList<CAstNode> decls = new ArrayList<>();
        for (SILValue v : undeclaredValues) {
            decls.add(v.getDecl());
        }
        undeclaredValues.clear();
        return decls;
    }

}
