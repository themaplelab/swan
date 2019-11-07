//===--- BuiltinFunctionRefValue.java ------------------------------------===//
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

/*
 * Represents a function reference to a function we don't have the source code for.
 */

public class BuiltinFunctionRefValue extends Value {

    private final String function;

    public boolean summaryCreated = false;

    public BuiltinFunctionRefValue(String name, String type, String function) {
        super(name, type);
        this.function = function;
    }

    public String getFunction() {
        return this.function;
    }
}
