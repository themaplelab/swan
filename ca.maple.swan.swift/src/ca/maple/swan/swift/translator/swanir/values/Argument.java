//===--- Argument.java ---------------------------------------------------===//
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

import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;

/*
 * A regular value that can also hold a CAst position.
 */

public class Argument extends Value {

    public final Position position;

    public Argument(String name, String type) {
        this(name, type, null);
    }

    public Argument(String name, String type, Position position) {
        super(name, type);
        this.position = position;
    }
}
