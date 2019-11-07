//===--- FunctionNode.java -----------------------------------------------===//
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
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;

import java.util.ArrayList;

/*
 * CAstNode accessor wrapper for functions, for convenience.
 * Coupled with C++ translator raw output format.
 */

@SuppressWarnings("FieldCanBeLocal")
public class FunctionNode {

    // CHANGE THESE FOR FORMAT
    private final int NAME_IDX = 0;
    private final int RETURN_TYPE_IDX = 1;
    private final int POSITION_IDX = 2;
    private final int ARGUMENTS_IDX = 3;
    private final int BLOCKS_IDX = 4;

    private final CAstNode function;

    public FunctionNode(CAstNode n) {
        this.function = n;
    }

    public String getFunctionName() {
        return RawUtil.getStringValue(function, NAME_IDX);
    }

    public String getFunctionReturnType() {
        return RawUtil.getStringValue(function, RETURN_TYPE_IDX);
    }

    public Position getFunctionPosition() {
        return RawUtil.getPositionValue(function, POSITION_IDX);
    }

    public ArrayList<ArgumentNode> getArguments() {
        ArrayList<ArgumentNode> args = new ArrayList<>();
        for (CAstNode n : function.getChild(ARGUMENTS_IDX).getChildren()) {
            args.add(new ArgumentNode(n));
        }
        return args;
    }

    public ArrayList<BlockNode> getBlocks() {
        ArrayList<BlockNode> blocks = new ArrayList<>();
        for (CAstNode n : function.getChild(BLOCKS_IDX).getChildren()) {
            blocks.add(new BlockNode(n));
        }
        return blocks;
    }

}
