//===--- DynamicFunctionRefValue.java ------------------------------------===//
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

import ca.maple.swan.swift.translator.silir.Function;

import java.util.ArrayList;

/*
 * A function ref that, due to dynamic dispatch, can resolve to multiple functions. Therefore,
 * all possible functions are stored.
 */

public class DynamicFunctionRefValue extends Value {

    private final ArrayList<Function> functions;

    public boolean ignore = false;

    public DynamicFunctionRefValue(String name, String type, ArrayList<Function> functions) {
        super(name, type);
        this.functions = functions;
    }

    public ArrayList<Function> getFunctions() {
        return this.functions;
    }
}
