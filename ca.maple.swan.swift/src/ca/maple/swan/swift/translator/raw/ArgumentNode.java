//===--- ArgumentNode.java -----------------------------------------------===//
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

/*
 * CAstNode accessor wrapper for arguments, for convenience.
 * Coupled with C++ translator raw output format.
 */

@SuppressWarnings("FieldCanBeLocal")
public class ArgumentNode {

    // CHANGE THESE FOR FORMAT
    private final int NAME_IDX = 0;
    private final int TYPE_IDX = 1;
    private final int POSITION_IDX = 2;

    private final CAstNode argument;

    public ArgumentNode(CAstNode n) {
        this.argument = n;
    }

    public String getName() {
        return RawUtil.getStringValue(argument, NAME_IDX);
    }

    public String getType() {
        return RawUtil.getStringValue(argument, TYPE_IDX);
    }

    public Position getPosition() {
        return RawUtil.getPositionValue(argument,POSITION_IDX);
    }
}
