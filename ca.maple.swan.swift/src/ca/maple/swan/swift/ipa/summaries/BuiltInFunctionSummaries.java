//===--- BuiltInFunctionSummaries.java -----------------------------------===//
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

package ca.maple.swan.swift.ipa.summaries;

import ca.maple.swan.swift.translator.RawAstTranslator;
import ca.maple.swan.swift.translator.SILInstructionContext;
import ca.maple.swan.swift.translator.values.SILTuple;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.util.debug.Assertions;

import java.util.ArrayList;
import java.util.Arrays;

import static com.ibm.wala.cast.tree.CAstNode.OBJECT_LITERAL;

public class BuiltInFunctionSummaries {

    public static CAstNode findSummary(String funcName, String resultName, String resultType, SILInstructionContext C, ArrayList<CAstNode> params) {

        switch(funcName) {

            /*************** LITERALS ****************/
            case "Swift._allocateUninitializedArray<A>(Builtin.Word) -> (Swift.Array<A>, Builtin.RawPointer)" : {
                /*
                 * Known use cases:
                 * 1. Initialize memory for a Stringß
                 *
                 * Result: SILBuiltinTuple with elements
                 * 1. SILValue representing the array
                 * 2. SILPointer pointing to the SILValue
                 *
                 * We return an empty node because there is no point of representing the operation
                 * in the CAst. When destructure_tuple is called later, the elements will "appear".
                 */
                SILTuple.SILBuiltinPointerTuple resultTuple = new SILTuple.SILBuiltinPointerTuple(resultName, resultType, C);
                C.valueTable.addValue(resultTuple);
                return RawAstTranslator.Ast.makeNode(CAstNode.EMPTY);
            }

            default: {
                Assertions.UNREACHABLE("Should not be called without checking isBuiltIn(): " + funcName);
                return null;
            }
        }
    }

    private static String[] summarizedBuiltins = new String[] {
            "Swift._allocateUninitializedArray<A>(Builtin.Word) -> (Swift.Array<A>, Builtin.RawPointer)"
    };

    public static boolean isSummarized(String name) {
        return Arrays.stream(summarizedBuiltins).anyMatch(name::equals);
    }

    private static String[] builtins = new String[] {
            "Swift.Int.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.Int",
            "Swift.String.init(_builtinStringLiteral: Builtin.RawPointer, utf8CodeUnitCount: Builtin.Word, isASCII: Builtin.Int1) -> Swift.String",
            "Swift.Bool.init(_builtinBooleanLiteral: Builtin.Int1) -> Swift.Bool",
            "default argument 1 of Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()",
            "Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()",
            "default argument 2 of Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()",
            "static Swift.Int.- infix(Swift.Int, Swift.Int) -> Swift.Int",
            "static Swift.Int.+ infix(Swift.Int, Swift.Int) -> Swift.Int",
            "static Swift.Double.* infix(Swift.Double, Swift.Double) -> Swift.Double",
            "Swift.Double.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.Double",
            "Swift.DefaultStringInterpolation.init(literalCapacity: Swift.Int, interpolationCount: Swift.Int) -> Swift.DefaultStringInterpolation",
            "Swift.DefaultStringInterpolation.appendLiteral(Swift.String) -> ()",
            "Swift.DefaultStringInterpolation.appendInterpolation<A where A: Swift.CustomStringConvertible, A: Swift.TextOutputStreamable>(A) -> ()",
            "Swift.String.init(stringInterpolation: Swift.DefaultStringInterpolation) -> Swift.String",
            "Swift.Double.init(_builtinFloatLiteral: Builtin.FPIEEE80) -> Swift.Double",
            "Swift.DefaultStringInterpolation.appendInterpolation<A where A: Swift.CustomStringConvertible>(A) -> ()",
            "static Swift.Int./ infix(Swift.Int, Swift.Int) -> Swift.Int",
            "Swift.Double.init(Swift.Int) -> Swift.Double",
            "static Swift.Double./ infix(Swift.Double, Swift.Double) -> Swift.Double",
            "Swift.DefaultStringInterpolation.appendInterpolation<A>(A) -> ()",
            "Swift.UInt.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.UInt"
    };

    public static boolean isBuiltIn(String name) {
        return Arrays.stream(builtins).anyMatch(name::equals);
    }
}
