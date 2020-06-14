//===--- BlockNode.java --------------------------------------------------===//
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

package ca.maple.swan.swift.translator.sil;

import com.ibm.wala.cast.tree.CAstNode;

import java.util.ArrayList;

/*
 * CAstNode accessor wrapper for blocks, for convenience.
 * Coupled with C++ translator raw output format.
 */

public class BlockNode {

    private final CAstNode block;

    public BlockNode(CAstNode n) {
        this.block = n;
    }

    public ArrayList<InstructionNode> getInstructions() {
        ArrayList<InstructionNode> instructions = new ArrayList<>();
        for (CAstNode inst : block.getChildren().subList(1, block.getChildren().size())) {
            instructions.add(new InstructionNode(inst));
        }
        return instructions;
    }

    public ArrayList<ArgumentNode> getArguments() {
        ArrayList<ArgumentNode> arguments = new ArrayList<>();
        for (CAstNode block : block.getChild(0).getChildren()) {
            arguments.add(new ArgumentNode(block));
        }
        return arguments;
    }
}
