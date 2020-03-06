//===--- BlockContext.java -----------------------------------------------===//
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

import ca.maple.swan.swift.translator.swanir.BasicBlock;
import ca.maple.swan.swift.translator.swanir.values.Argument;
import ca.maple.swan.swift.translator.swanir.values.Value;

/*
 * Holds anything a basic block would need for translation.
 */

public class BlockContext {

    public FunctionContext fc;

    public BasicBlock block;

    public BlockContext(BasicBlock b, FunctionContext fc) {
        this.fc = fc;
        this.block = b;
        for (Argument a : b.getArguments()) {
            this.fc.pc.vt.add(a.name, new Value(a.name, a.type));
        }
    }
}
