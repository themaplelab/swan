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


package ca.maple.swan.swift.translator.silir.values;

/*
 * For cases where the address of a field is derived, we alias the access path instead of modelling memory.
 * There may be edge cases where this value is used unexpectedly. e.g. written to a global.
 */

public class FieldAliasValue extends Value {

    public final String field;

    public final Value value;

    public FieldAliasValue(String name, String type, Value value, String field) {
        super(name, type);
        this.field = field;
        this.value = value;
    }
}
