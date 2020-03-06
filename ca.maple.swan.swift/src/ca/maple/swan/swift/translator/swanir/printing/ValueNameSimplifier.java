//===--- ValueNameSimplifier.java ----------------------------------------===//
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

package ca.maple.swan.swift.translator.swanir.printing;

import java.util.HashMap;

/*
 * To make the IR more readable, this class exists to simplify variable names.
 * Almost all instructions use this in their toString().
 *
 * e.g. "v5" instead of "0x7fa539915680" for a variable name.
 */

public class ValueNameSimplifier {

    // This sets whether to print simple values.
    private static final boolean SIMPLIFY = true;

    static int counter = 0;

    private static HashMap<String, Integer> values = new HashMap<>();

    public static void clear() {
        values.clear();
        counter = 0;
    }

    public static String get(String s) {
        if (!SIMPLIFY) {
            return s;
        }
        if (values.containsKey(s)) {
            return "v" + values.get(s);
        } else {
            values.put(s, counter);
            ++counter;
            return "v" + (counter - 1);
        }
    }
}
