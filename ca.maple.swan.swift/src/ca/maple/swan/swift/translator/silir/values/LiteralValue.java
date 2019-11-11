//===--- LiteralValue.java -----------------------------------------------===//
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
 * A value that can hold a literal. The "literal" field should be reasonable
 * since it is used in a makeConstant() call.
 */

public class LiteralValue extends Value {

    private final Object literal;

    public LiteralValue(String name, String type, Object literal) {
        super(name, type);
        this.literal = literal;
    }

    public Object getLiteral() {
        return this.literal;
    }
}
