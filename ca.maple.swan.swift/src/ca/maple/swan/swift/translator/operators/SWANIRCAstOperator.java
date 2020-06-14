//===--- SWANIRCAstOperator.java -----------------------------------------===//
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

package ca.maple.swan.swift.translator.operators;

import com.ibm.wala.cast.tree.CAstLeafNode;
import com.ibm.wala.cast.tree.CAstNode;

public class SWANIRCAstOperator implements CAstLeafNode {

    private final String op;

    protected SWANIRCAstOperator(String op) {
        this.op = op;
    }

    @Override
    public String toString() {
        return "OP:" + op;
    }

    @Override
    public int getKind() {
        return CAstNode.OPERATOR;
    }

    @Override
    public Object getValue() {
        return op;
    }

    public static final SWANIRCAstOperator OP_BINARY_ARBITRARY = new SWANIRCAstOperator("binary_arb");

    public static final SWANIRCAstOperator OP_UNARY_ARBITRARY = new SWANIRCAstOperator("unary_arb");


}
