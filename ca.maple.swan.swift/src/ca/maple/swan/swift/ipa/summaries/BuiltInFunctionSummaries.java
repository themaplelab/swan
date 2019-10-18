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

import ca.maple.swan.swift.translator.SILInstructionContext;
import ca.maple.swan.swift.translator.values.SILConstant;
import ca.maple.swan.swift.translator.values.SILStruct;
import ca.maple.swan.swift.translator.values.SILTuple;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.impl.CAstOperator;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;

import java.util.ArrayList;
import java.util.Arrays;

import static ca.maple.swan.swift.translator.RawAstTranslator.Ast;
import static com.ibm.wala.cast.tree.CAstNode.*;

/*
 * SIL has many builtin functions that can be nuanced and annoying.
 * We try to handle them here as best as possible. If we can summarize one,
 * we replace the whole CALL node with something else. We keep track of
 * ones we can summarize, and builtins which we have yet to summarize.
 */

public class BuiltInFunctionSummaries {

    public static CAstNode findSummary(String funcName, String resultName, String resultType, SILInstructionContext C, ArrayList<CAstNode> params) {

        // TODO: String interpolation support.

        switch(funcName) {

            /*************** LITERALS ****************/
            case "Swift._allocateUninitializedArray<A>(Builtin.Word) -> (Swift.Array<A>, Builtin.RawPointer)" : {
                C.valueTable.addValue(new SILTuple.SILUnitArrayTuple(resultName, resultType, C));
                return null;
            }
            case "default argument 1 of Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()":
            case "default argument 2 of Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()": {
                return Ast.makeNode(CAstNode.VAR); // Signifies that we should just create a variable for the result.
            }
            case "Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()": {
                C.instructions.add(Ast.makeNode(CAstNode.ECHO,
                        C.valueTable.getValue((String)params.get(0).getValue()).getVarNode()));
                return null;
            }
            case "Swift.String.init(_builtinStringLiteral: Builtin.RawPointer, utf8CodeUnitCount: Builtin.Word, isASCII: Builtin.Int1) -> Swift.String":
            case "Swift.Int.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.Int":
            case "Swift.Double.init(_builtinFloatLiteral: Builtin.FPIEEE80) -> Swift.Double":
            case "Swift.Double.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.Double":
            case "Swift.UInt.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.UInt":
            case "Swift.Double.init(Swift.Int) -> Swift.Double": {
                return C.valueTable.getValue((String)params.get(0).getValue()).getVarNode();
            }
            case "Swift.Bool.init(_builtinBooleanLiteral: Builtin.Int1) -> Swift.Bool": {
                // For now, we will keep the numerical boolean representation (integer).
                ArrayList<Pair<String, String>> fields = new ArrayList<>();
                fields.add(Pair.make("_value", (String)params.get(0).getValue()));
                SILStruct BoolValue = new SILStruct(resultName, resultType, C, fields);
                C.valueTable.addValue(BoolValue);
                C.parent.setGotoTarget(BoolValue.getVarNode(), BoolValue.getVarNode());
                return null;
            }
            case "static Swift.String.+ infix(Swift.String, Swift.String) -> Swift.String": {
                return Ast.makeConstant(
                        (String)((SILConstant)C.valueTable.getValue((String)params.get(0).getValue())).getValue() +
                                (String)((SILConstant)C.valueTable.getValue((String)params.get(1).getValue())).getValue()
                );
            }
            case "Swift.DefaultStringInterpolation.init(literalCapacity: Swift.Int, interpolationCount: Swift.Int) -> Swift.DefaultStringInterpolation":
            case "Swift.String.init(stringInterpolation: Swift.DefaultStringInterpolation) -> Swift.String": {
                return Ast.makeConstant("StringInterpolation");
            }
            case "Swift.DefaultStringInterpolation.appendLiteral(Swift.String) -> ()":
            case "Swift.DefaultStringInterpolation.appendInterpolation<A>(A) -> ()":
            case "Swift.DefaultStringInterpolation.appendInterpolation<A where A: Swift.CustomStringConvertible>(A) -> ()":
            case "Swift.DefaultStringInterpolation.appendInterpolation<A where A: Swift.CustomStringConvertible, A: Swift.TextOutputStreamable>(A) -> ()": {
                return Ast.makeNode(BINARY_EXPR, CAstOperator.OP_ADD,
                        params.get(0), params.get(1));
            }
            default: {
                Assertions.UNREACHABLE("Should not be called without checking isBuiltIn(): " + funcName);
                return null;
            }
        }
    }

    private static final String[] summarizedBuiltins = new String[] {
            "Swift._allocateUninitializedArray<A>(Builtin.Word) -> (Swift.Array<A>, Builtin.RawPointer)",
            "default argument 1 of Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()",
            "default argument 2 of Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()",
            "Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()",
            "Swift.String.init(_builtinStringLiteral: Builtin.RawPointer, utf8CodeUnitCount: Builtin.Word, isASCII: Builtin.Int1) -> Swift.String",
            "Swift.Int.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.Int",
            "Swift.UInt.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.UInt",
            "Swift.Double.init(_builtinFloatLiteral: Builtin.FPIEEE80) -> Swift.Double",
            "Swift.Double.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.Double",
            "Swift.Double.init(Swift.Int) -> Swift.Double",
            "Swift.Bool.init(_builtinBooleanLiteral: Builtin.Int1) -> Swift.Bool",
            "static Swift.String.+ infix(Swift.String, Swift.String) -> Swift.String",
            "Swift.DefaultStringInterpolation.init(literalCapacity: Swift.Int, interpolationCount: Swift.Int) -> Swift.DefaultStringInterpolation",
            "Swift.String.init(stringInterpolation: Swift.DefaultStringInterpolation) -> Swift.String",
            "Swift.DefaultStringInterpolation.appendLiteral(Swift.String) -> ()",
            "Swift.DefaultStringInterpolation.appendInterpolation<A>(A) -> ()",
            "Swift.DefaultStringInterpolation.appendInterpolation<A where A: Swift.CustomStringConvertible, A: Swift.TextOutputStreamable>(A) -> ()",
            "Swift.DefaultStringInterpolation.appendInterpolation<A where A: Swift.CustomStringConvertible>(A) -> ()"
    };

    public static boolean isSummarized(String name) {
        //noinspection SimplifyStreamApiCallChains
        return Arrays.stream(summarizedBuiltins).anyMatch(name::equals);
    }

    private static final String[] builtins = new String[] {
            "static Swift.Int.- infix(Swift.Int, Swift.Int) -> Swift.Int",
            "static Swift.Int.+ infix(Swift.Int, Swift.Int) -> Swift.Int",
            "static Swift.Int.* infix(Swift.Int, Swift.Int) -> Swift.Int",
            "static Swift.Double.* infix(Swift.Double, Swift.Double) -> Swift.Double",
            "static Swift.Int./ infix(Swift.Int, Swift.Int) -> Swift.Int",
            "static Swift.Double./ infix(Swift.Double, Swift.Double) -> Swift.Double",
            "(extension in Swift):Swift.BinaryInteger.description.getter : Swift.String",
            "Swift.?? infix<A>(Swift.Optional<A>, @autoclosure () throws -> A) throws -> A",
            "Swift.~= infix<A where A: Swift.Equatable>(A, A) -> Swift.Bool",
            "Swift.== infix<A where A: Swift.RawRepresentable, A.RawValue: Swift.Equatable>(A, A) -> Swift.Bool",
            "(extension in Swift):Swift.RawRepresentable< where A: Swift.Hashable, A.RawValue: Swift.Hashable>.hashValue.getter : Swift.Int",
            "(extension in Swift):Swift.RawRepresentable< where A: Swift.Hashable, A.RawValue: Swift.Hashable>.hash(into: inout Swift.Hasher) -> ()",
            "(extension in Swift):Swift.RawRepresentable< where A: Swift.Hashable, A.RawValue: Swift.Hashable>._rawHashValue(seed: Swift.Int) -> Swift.Int",
            "Swift.?? infix<A>(Swift.Optional<A>, @autoclosure () throws -> A) throws -> A"
    };

    public static boolean isBuiltIn(String name) {
        //noinspection SimplifyStreamApiCallChains
        return Arrays.stream(builtins).anyMatch(name::equals);
    }
}
