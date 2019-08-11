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

import com.ibm.wala.cast.tree.CAstNode;

import java.util.Arrays;

public class BuiltInFunctionSummaries {

    public static CAstNode findSummary(CAstNode cAstNode) {
        assert(cAstNode.getKind() == CAstNode.CALL);
        assert(cAstNode.getChild(0).getKind() == CAstNode.FUNCTION_EXPR);
        assert(cAstNode.getChild(1).getValue().equals("do"));

        switch((String)cAstNode.getChild(0).getValue()) {

            /*************** LITERALS ****************/
            case "Swift.Int.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.Int": {
                // (Builtin.IntLiteral, @thin Int.Type)
                assert(cAstNode.getChild(2).getKind() == CAstNode.CONSTANT);
                return cAstNode.getChild(2);
            }
            case "Swift.Double.init(_builtinFloatLiteral: Builtin.FPIEEE80) -> Swift.Double": {
                assert(cAstNode.getChild(2).getKind() == CAstNode.CONSTANT);
                return cAstNode.getChild(2);
            }

            /********** OPERATOR FUNCTIONS ***********/
            // These are here in case we want to change how operator functions are
            // handled later. For now, the translator itself takes care of them.

            case "static Swift.Int.+ infix(Swift.Int, Swift.Int) -> Swift.Int": {
                // (Int, Int, @thin Int.Type)
                return null;

            }
            default: {
                return null;
            }
        }
    }

    private static String[] builtins = new String[] {
            "Swift.Int.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.Int",
            "Swift.String.init(_builtinStringLiteral: Builtin.RawPointer, utf8CodeUnitCount: Builtin.Word, isASCII: Builtin.Int1) -> Swift.String",
            "Swift.Bool.init(_builtinBooleanLiteral: Builtin.Int1) -> Swift.Bool",
            "default argument 1 of Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()",
            "Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()",
            "default argument 2 of Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()",
            "Swift._allocateUninitializedArray<A>(Builtin.Word) -> (Swift.Array<A>, Builtin.RawPointer)",
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
