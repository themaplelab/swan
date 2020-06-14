//===--- Value.java ------------------------------------------------------===//
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

package ca.maple.swan.swift.translator.swanir.values;

import ca.maple.swan.swift.translator.swanir.printing.ValueNameSimplifier;

/*
 * Generic value for SWANIR.
 */

public class Value {

    public final String name;

    public final String type;

    public Value(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return this.name;
    }

    public String getType() {
        return this.type;
    }

    public String simpleName() {
        return ValueNameSimplifier.get(this.name);
    }
}
