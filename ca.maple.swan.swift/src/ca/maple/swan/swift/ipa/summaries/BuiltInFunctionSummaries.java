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
import ca.maple.swan.swift.translator.values.SILTuple;
import ca.maple.swan.swift.translator.values.SILValue;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.impl.CAstOperator;
import com.ibm.wala.util.debug.Assertions;

import java.util.ArrayList;
import java.util.Arrays;

import static ca.maple.swan.swift.translator.RawAstTranslator.Ast;
import static com.ibm.wala.cast.tree.CAstNode.*;

public class BuiltInFunctionSummaries {

    public static CAstNode findSummary(String funcName, String resultName, String resultType, SILInstructionContext C, ArrayList<CAstNode> params) {

        switch(funcName) {

            /*************** LITERALS ****************/
            case "Swift._allocateUninitializedArray<A>(Builtin.Word) -> (Swift.Array<A>, Builtin.RawPointer)" : {
                ArrayList<CAstNode> Fields = new ArrayList<>();
                SILValue allocatedArray = new SILValue(resultName + "_value/pointer", "Any", C);
                C.valueTable.addValue(allocatedArray);
                Fields.add(Ast.makeConstant("TUPLE"));
                Fields.add(Ast.makeConstant("0"));
                Fields.add(allocatedArray.getVarNode());
                Fields.add(Ast.makeConstant("1"));
                CAstNode PointerRef = Ast.makeNode(OBJECT_REF, Ast.makeNode(THIS), Ast.makeConstant("0"));
                C.parent.setGotoTarget(PointerRef, PointerRef);
                Fields.add(PointerRef);
                ArrayList<String> types = new ArrayList<>();
                types.add("Any");
                types.add("Any");
                SILTuple resultTuple = new SILTuple(resultName, resultType, C, types);
                C.valueTable.addValue(resultTuple);
                CAstNode resultNode = Ast.makeNode(OBJECT_LITERAL, Fields);
                C.parent.setGotoTarget(resultNode, resultNode);
                return resultNode;
            }
            case "default argument 1 of Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()": {
                return Ast.makeNode(CAstNode.VAR); // Signifies that we should just create a variable for the result.
            }
            case "default argument 2 of Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()": {
                return Ast.makeNode(CAstNode.VAR);
            }
            case "Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()": {
                return C.valueTable.getValue((String)params.get(0).getValue()).getVarNode();
            }
            case "Swift.String.init(_builtinStringLiteral: Builtin.RawPointer, utf8CodeUnitCount: Builtin.Word, isASCII: Builtin.Int1) -> Swift.String": {
                return C.valueTable.getValue((String)params.get(0).getValue()).getVarNode();
            }
            case "Swift.Int.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.Int": {
                return C.valueTable.getValue((String)params.get(0).getValue()).getVarNode();
            }
            case "Swift.Double.init(_builtinFloatLiteral: Builtin.FPIEEE80) -> Swift.Double": {
                return C.valueTable.getValue((String)params.get(0).getValue()).getVarNode();
            }
            case "Swift.Double.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.Double": {
                return C.valueTable.getValue((String)params.get(0).getValue()).getVarNode();
            }
            case "Swift.UInt.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.UInt": {
                return C.valueTable.getValue((String)params.get(0).getValue()).getVarNode();
            }
            case "Swift.Double.init(Swift.Int) -> Swift.Double": {
                return C.valueTable.getValue((String)params.get(0).getValue()).getVarNode();
            }
            case "Swift.Bool.init(_builtinBooleanLiteral: Builtin.Int1) -> Swift.Bool": {
                // For now, we will keep the numerical boolean representation (integer).
                return C.valueTable.getValue((String)params.get(0).getValue()).getVarNode();
            }
            case "static Swift.String.+ infix(Swift.String, Swift.String) -> Swift.String": {
                return Ast.makeConstant(
                        (String)((SILConstant)C.valueTable.getValue((String)params.get(0).getValue())).getValue() +
                                (String)((SILConstant)C.valueTable.getValue((String)params.get(1).getValue())).getValue()
                );
            }
            case "Swift.DefaultStringInterpolation.init(literalCapacity: Swift.Int, interpolationCount: Swift.Int) -> Swift.DefaultStringInterpolation": {
                return Ast.makeConstant("StringInterpolation");
            }
            case "Swift.String.init(stringInterpolation: Swift.DefaultStringInterpolation) -> Swift.String": {
                return Ast.makeConstant("StringInterpolation");
            }
            case "Swift.DefaultStringInterpolation.appendLiteral(Swift.String) -> ()": {
                return Ast.makeNode(BINARY_EXPR, CAstOperator.OP_ADD,
                        params.get(0), params.get(1));
            }
            case "Swift.DefaultStringInterpolation.appendInterpolation<A>(A) -> ()": {
                return Ast.makeNode(BINARY_EXPR, CAstOperator.OP_ADD,
                        params.get(0), params.get(1));
            }
            case "Swift.DefaultStringInterpolation.appendInterpolation<A where A: Swift.CustomStringConvertible, A: Swift.TextOutputStreamable>(A) -> ()": {
                return Ast.makeNode(BINARY_EXPR, CAstOperator.OP_ADD,
                        params.get(0), params.get(1));
            }
            case "Swift.DefaultStringInterpolation.appendInterpolation<A where A: Swift.CustomStringConvertible>(A) -> ()": {
                return Ast.makeNode(BINARY_EXPR, CAstOperator.OP_ADD,
                        params.get(0), params.get(1));
            }
            default: {
                Assertions.UNREACHABLE("Should not be called without checking isBuiltIn(): " + funcName);
                return null;
            }
        }
    }

    private static String[] summarizedBuiltins = new String[] {
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
        return Arrays.stream(summarizedBuiltins).anyMatch(name::equals);
    }

    private static String[] builtins = new String[] {
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
            "(extension in Swift):Swift.RawRepresentable< where A: Swift.Hashable, A.RawValue: Swift.Hashable>._rawHashValue(seed: Swift.Int) -> Swift.Int"
    };

    public static boolean isBuiltIn(String name) {
        return Arrays.stream(builtins).anyMatch(name::equals);
    }
}
