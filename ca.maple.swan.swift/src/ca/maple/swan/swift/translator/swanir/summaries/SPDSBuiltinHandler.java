//===--- SPDSBuiltinHandler.java -----------------------------------------===//
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

package ca.maple.swan.swift.translator.swanir.summaries;

/*
 * Handler for when using SPDS. Some builtins can be modelled by a rule instead of IR.
 */

import ca.maple.swan.swift.translator.swanir.context.InstructionContext;
import ca.maple.swan.swift.translator.swanir.instructions.SWANIRInstruction;
import com.ibm.wala.util.debug.Assertions;

import java.util.ArrayList;
import java.util.Arrays;

public class SPDSBuiltinHandler {

    public static SWANIRInstruction findSummary(String funcName, String resultName, String resultType, ArrayList<String> params, InstructionContext C) {
        switch (funcName) {
            default: {
                Assertions.UNREACHABLE("Should not be called without checking isSummarized(): " + funcName);
                return null;
            }
        }
    }

    private static final String[] summarizedBuiltins = new String[] {

    };

    public static boolean isSummarized(String name) {
        //noinspection SimplifyStreamApiCallChains
        return Arrays.stream(summarizedBuiltins).anyMatch(name::equals);
    }

}
