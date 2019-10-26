//===--- InstructionNode.java --------------------------------------------===//
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

package ca.maple.swan.swift.translator.raw;

import com.ibm.wala.cast.tree.CAstNode;

/*
 * CAstNode accessor wrapper for instructions, for convenience.
 * Coupled with C++ translator raw output format.
 */

@SuppressWarnings("FieldCanBeLocal")
public class InstructionNode {

    // CHANGE THESE FOR FORMAT
    private final int NAME_IDX = 0;

    private final CAstNode instruction;

    public InstructionNode(CAstNode n) {
        this.instruction = n;
    }

    public CAstNode getInstruction() {
        return this.instruction;
    }

    public String getName() {
        return RawUtil.getStringValue(this.instruction, NAME_IDX);
    }
}
