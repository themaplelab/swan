//===--- SILConstant.java ------------------------------------------------===//
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

package ca.maple.swan.swift.translator.values;

import ca.maple.swan.swift.translator.SILInstructionContext;
import com.ibm.wala.cast.tree.CAstNode;

/*
 * Useful for literals.
 */

public class SILConstant extends SILValue {

    private Object value;
    private final CAstNode node;

    public SILConstant(String name, String type, SILInstructionContext C, Object value) {
        super(name, type, C);
        this.value = value;
        node = Ast.makeConstant(value);
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public CAstNode getVarNode() {
        return node;
    }

    public CAstNode getCAst() {
        return node;
    }
}
