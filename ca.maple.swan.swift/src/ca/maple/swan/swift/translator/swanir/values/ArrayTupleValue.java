//===--- ArrayTupleValue.java --------------------------------------------===//
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
 * Meant to signify that the value was created by the
 * "Swift._allocateUninitializedArray<A>(Builtin.Word) -> (Swift.Array<A>, Builtin.RawPointer)"
 * builtin. The second value is a pointer to the first so we have to handle this special case.
 */

public class ArrayTupleValue extends Value {

    public ArrayTupleValue(String name, String type) {
        super(name,type);
    }

}
