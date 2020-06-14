//===--- InstructionContext.java -----------------------------------------===//
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

package ca.maple.swan.swift.translator.swanir.context;

import ca.maple.swan.swift.translator.swanir.values.GlobalValueTable;
import ca.maple.swan.swift.translator.swanir.values.ValueTable;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;

/*
 * Holds anything an instruction would need for translation.
 */

public class InstructionContext {

    public BlockContext bc;

    public final Position position;

    public InstructionContext(BlockContext bc, Position position) {
        this.bc = bc;
        this.position = position;
    }

    public ValueTable valueTable() {
        return this.bc.fc.pc.vt;
    }

    public GlobalValueTable globalValueTable() {
        return this.bc.fc.pc.globalValues;
    }
}
