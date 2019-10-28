//===--- ValueTable.java -------------------------------------------------===//
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

package ca.maple.swan.swift.translator.silir.values;

import com.ibm.wala.util.debug.Assertions;

import java.util.HashMap;

/*
 * Stores values during SIL -> SILIR translation.
 */

public class ValueTable {

    private HashMap<String, Value> values;

    public ValueTable() {
        this.values = new HashMap<>();
    }

    // For implicit copies. Useful for reducing IR assignment statement bloat.
    public void copy(String to, String from) {
        Assertions.productionAssertion(has(from));
        values.put(to, values.get(from));
    }

    public Value getValue(String s) {
        // We need to know if aliases show up where they are not expected.
        Assertions.productionAssertion(!(values.get(s) instanceof FieldAliasValue));
        Assertions.productionAssertion(has(s));
        return values.get(s);
    }

    public Value getPossibleAlias(String s) {
        Assertions.productionAssertion(has(s));

        return values.get(s);
    }

    public void add(Value v) {
        values.put(v.name, v);
    }

    public void add(String s, Value v) {
        values.put(s, v);
    }

    public boolean has(Value v) {
        return values.containsKey(v.name);
    }

    public boolean has(String s) {
        return values.containsKey(s);
    }
}
