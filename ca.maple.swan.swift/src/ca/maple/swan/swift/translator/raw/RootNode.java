//===--- RootNode.java ---------------------------------------------------===//
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

import java.util.ArrayList;

/*
 * CAstNode accessor wrapper for the very root node, for convenience.
 * Coupled with C++ translator raw output format.
 */

public class RootNode {

    private final CAstNode root;

    public RootNode(CAstNode n) {
        this.root = n;
    }

    public ArrayList<FunctionNode> getFunctions() {
        ArrayList<FunctionNode> functionNodes = new ArrayList<>();
        for (CAstNode n : this.root.getChildren()) {
            functionNodes.add(new FunctionNode(n));
        }
        return functionNodes;
    }
}
