//===--- FieldAliasValue.java --------------------------------------------===//
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

/*
 * Similar to FieldAlias, but for Array accesses.
 */

public class ArrayIndexAliasValue extends Value {

    public final int index;

    public final Value value;

    public ArrayIndexAliasValue(String name, String type, Value value, int index) {
        super(name, type);
        this.index = index;
        this.value = value;
    }
}
