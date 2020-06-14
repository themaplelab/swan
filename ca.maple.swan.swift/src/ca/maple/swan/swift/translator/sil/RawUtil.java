//===--- RawUtil.java ----------------------------------------------------===//
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
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;

import java.util.ArrayList;

/*
 * Helpers for accessing raw CAstNodes from the C++ translator.
 */

public class RawUtil {

    public static class RawValue {
        public final String Name;
        public final String Type;

        public RawValue(String name, String type) {
            this.Name = name;
            this.Type = type;
        }

        public RawValue(String name) {
            this(name, null);
        }
    }

    private static Pair<ArrayList<RawValue>, ArrayList<RawValue>> getOperandsAndResults(CAstNode N) {
        // TODO: cache the results so that subsequent calls such as
        //       getResult()/getOperand() don't call this whole process each time?
        Assertions.productionAssertion(N.getChildren().size() >= 2);
        ArrayList<RawValue> operands = new ArrayList<>();
        ArrayList<RawValue> results = new ArrayList<>();
        for (CAstNode operand : N.getChild(0).getChildren()) {
            operands.add(new RawValue(
                    (String) operand.getChild(0).getValue(),
                    (String) operand.getChild(1).getValue()));
        }
        for (CAstNode result : N.getChild(1).getChildren()) {
            results.add(new RawValue(
                    (String) result.getChild(0).getValue(),
                    (String) result.getChild(1).getValue()));
        }
        return Pair.make(operands, results);
    }

    public static RawValue getSingleResult(CAstNode N) {
        return getResult(N, 0);
    }

    public static RawValue getResult(CAstNode N, int index) {
        return getOperandsAndResults(N).snd.get(index);
    }

    public static RawValue getSingleOperand(CAstNode N) {
        return getOperand(N, 0);
    }

    public static RawValue getOperand(CAstNode N, int index) {
        return getOperandsAndResults(N).fst.get(index);
    }


    public static String getStringValue(CAstNode N, int index) {
        Assertions.productionAssertion(N.getChildren().size() > index);
        return (String) N.getChild(index).getValue();
    }

    public static int getIntValue(CAstNode N, int index) {
        Assertions.productionAssertion(N.getChildren().size() > index);
        return (int) N.getChild(index).getValue();
    }

    public static Position getPositionValue(CAstNode N, int index) {
        Assertions.productionAssertion(N.getChildren().size() > index);
        return (Position) N.getChild(index).getValue();
    }
}
