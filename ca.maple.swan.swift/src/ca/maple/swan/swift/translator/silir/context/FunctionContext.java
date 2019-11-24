//===--- FunctionContext.java --------------------------------------------===//
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

package ca.maple.swan.swift.translator.silir.context;

import ca.maple.swan.swift.translator.silir.Function;
import ca.maple.swan.swift.translator.silir.values.Argument;
import ca.maple.swan.swift.translator.silir.values.Value;
import ca.maple.swan.swift.translator.silir.values.ValueTable;

import java.util.HashMap;

/*
 * Holds anything a function would need for translation.
 */

public class FunctionContext {

    public Function function;

    public ProgramContext pc;

    public ValueTable vt;

    public HashMap<String, FunctionContext> coroutines;

    public CoroutineContext cc = null;

    public FunctionContext(Function f, ProgramContext pc) {
        this.function = f;
        this.pc  = pc;
        this.vt = new ValueTable();
        this.coroutines = new HashMap<>();
        for (Argument a : f.getArguments()) {
            this.vt.add(a.name, new Value(a.name, a.type));
        }
    }

    public Function getFunction() {
        return this.function;
    }
}
