//===--- BuiltinHandler.java ---------------------------------------------===//
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

package ca.maple.swan.swift.translator.silir.summaries;

import ca.maple.swan.swift.translator.silir.context.InstructionContext;
import ca.maple.swan.swift.translator.silir.instructions.*;
import ca.maple.swan.swift.translator.silir.values.LiteralValue;
import ca.maple.swan.swift.translator.silir.values.Value;
import com.ibm.wala.util.debug.Assertions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

/*
 * Builtin handler for SILIR.
 */

public class BuiltinHandler {

    public static SILIRInstruction findSummary(String funcName, String resultName, String resultType, ArrayList<String> params, InstructionContext C) {

        // TODO: Proper string interpolation support.
        // TODO: Handle all Array, Set, and Dictionary container type functions.

        switch(funcName) {

            case "default argument 0 of (extension in Swift):Swift.BidirectionalCollection< where A.Element == Swift.String>.joined(separator: Swift.String) -> Swift.String": {
                return new LiteralInstruction("", resultName, resultName, C);
            }

            case "(extension in Swift):Swift.BidirectionalCollection< where A.Element == Swift.String>.joined(separator: Swift.String) -> Swift.String": {
                // TODO: here we need to add all elements of the array together and return it.
                // This is temporary.
                return new FieldReadInstruction(resultName, resultType, params.get(1), "value", C);
            }

            case "Swift.Dictionary.init() -> Swift.Dictionary<A, B>": {
                return new NewInstruction(resultName, resultType, C);
            }

            case "Swift.Dictionary.subscript.setter : (A) -> Swift.Optional<B>": {
                // setter(v0, v1, v2)
                // (v2.value).(v1.value) = v0.value.data
                // These result types are not correct.
                String temp1 = UUID.randomUUID().toString(); // v2.value
                C.bc.block.addInstruction(new FieldReadInstruction(temp1, "$Swift.Dictionary<A, B>", params.get(2), "value", C));
                String temp2 = UUID.randomUUID().toString(); // v0.value
                C.bc.block.addInstruction(new FieldReadInstruction(temp2, "$*Optional<A>", params.get(0), "value", C));
                String temp3 = UUID.randomUUID().toString(); // v0.value.data
                C.bc.block.addInstruction(new FieldReadInstruction(temp3, "$Any", temp2, "data", C));
                String temp4 = UUID.randomUUID().toString(); // v1.value
                C.bc.block.addInstruction(new FieldReadInstruction(temp4, "$Any", params.get(1), "value", C));
                // temp1.temp4 = temp3
                C.bc.block.addInstruction(new FieldWriteInstruction(temp1, temp4, temp3, true, C));
                return null;
            }

            case "Swift.Dictionary.subscript.getter : (A) -> Swift.Optional<B>": {
                // getter(v0, v1, v2)
                // v0.value = new $Swift.Optional<A>
                // v0.value.data = v2.(v1.value)
                // v0.value.type = "some"/"none"
                // These result types are not correct.
                String init = UUID.randomUUID().toString();
                C.bc.block.addInstruction(new NewInstruction(init, "$Swift.Optional<A>", C));
                C.bc.block.addInstruction(new FieldWriteInstruction(params.get(0), "value", init, C));
                String temp1 = UUID.randomUUID().toString(); // (v1.value)
                C.bc.block.addInstruction(new FieldReadInstruction(temp1, "$Any", params.get(1), "value", C));
                String temp2 = UUID.randomUUID().toString(); // v2.(v1.value)
                C.bc.block.addInstruction(new FieldReadInstruction(temp2, "$Any", params.get(2), temp1, true, C));
                String temp3 = UUID.randomUUID().toString(); // v0.value
                C.bc.block.addInstruction(new FieldReadInstruction(temp3, "$Any", params.get(0), "value", C));
                C.bc.block.addInstruction(new FieldWriteInstruction(temp3, "data", temp2, C));
                // Assume there is some.
                String temp4 = UUID.randomUUID().toString(); // "some"
                C.bc.block.addInstruction(new LiteralInstruction("some", temp4, "$String", C));
                C.bc.block.addInstruction(new FieldWriteInstruction(temp3, "type", temp4,  C));
                return null;
            }

            case "Swift.Array.subscript.getter : (Swift.Int) -> A" : {
                return new FieldReadWriteInstruction(params.get(0), "value", params.get(2), params.get(1), true, C);
            }

            case "Swift._allocateUninitializedArray<A>(Builtin.Word) -> (Swift.Array<A>, Builtin.RawPointer)" : {
                C.bc.block.addInstruction(new NewArrayTupleInstruction(resultName, resultType, C));
                String temp1 = UUID.randomUUID().toString();
                String temp2 = UUID.randomUUID().toString();
                C.bc.block.addInstruction(new NewInstruction(temp1, "$Array<Any>", C));
                C.bc.block.addInstruction(new NewInstruction(temp2, "$Builtin.RawPointer", C));
                C.bc.block.addInstruction(new FieldWriteInstruction(resultName, "0", temp1, C));
                C.bc.block.addInstruction(new FieldWriteInstruction(resultName, "1", temp2, C));
                return null;
            }
            case "default argument 1 of Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()":
            case "default argument 2 of Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()": {
                C.valueTable().add(new LiteralValue(resultName, resultType, ""));
                return null;
            }
            case "Swift.print(_: Any..., separator: Swift.String, terminator: Swift.String) -> ()": {
                return new PrintInstruction(params.get(0), C);
            }
            case "Swift.String.init(_builtinStringLiteral: Builtin.RawPointer, utf8CodeUnitCount: Builtin.Word, isASCII: Builtin.Int1) -> Swift.String":
            case "Swift.Int.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.Int":
            case "Swift.Double.init(_builtinFloatLiteral: Builtin.FPIEEE80) -> Swift.Double":
            case "Swift.Double.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.Double":
            case "Swift.UInt.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.UInt":
            case "Swift.Double.init(Swift.Int) -> Swift.Double": {
                return new ImplicitCopyInstruction(resultName, params.get(0), C);
            }
            case "Swift.Bool.init(_builtinBooleanLiteral: Builtin.Int1) -> Swift.Bool": {
                C.bc.block.addInstruction(new NewInstruction(resultName, resultType, C));
                C.bc.block.addInstruction(new FieldWriteInstruction(resultName, "_value", params.get(0), C));
                return null;
            }

            case "Swift.DefaultStringInterpolation.init(literalCapacity: Swift.Int, interpolationCount: Swift.Int) -> Swift.DefaultStringInterpolation":
            case "Swift.String.init(stringInterpolation: Swift.DefaultStringInterpolation) -> Swift.String": {
                return new LiteralInstruction("", resultName, resultType, C);
            }
            case "Swift.DefaultStringInterpolation.appendLiteral(Swift.String) -> ()":
            case "Swift.DefaultStringInterpolation.appendInterpolation<A>(A) -> ()":
            case "Swift.DefaultStringInterpolation.appendInterpolation<A where A: Swift.CustomStringConvertible>(A) -> ()":
            case "Swift.DefaultStringInterpolation.appendInterpolation<A where A: Swift.CustomStringConvertible, A: Swift.TextOutputStreamable>(A) -> ()": {
                return null;
            }
            default: {
                Assertions.UNREACHABLE("Should not be called without checking isBuiltIn(): " + funcName);
                return null;
            }
        }
    }

    private static final String[] summarizedBuiltins = new String[] {
            "(extension in Swift):Swift.BidirectionalCollection< where A.Element == Swift.String>.joined(separator: Swift.String) -> Swift.String",
            "default argument 0 of (extension in Swift):Swift.BidirectionalCollection< where A.Element == Swift.String>.joined(separator: Swift.String) -> Swift.String",
            "Swift.Dictionary.init() -> Swift.Dictionary<A, B>",
            "Swift.Dictionary.subscript.getter : (A) -> Swift.Optional<B>",
            "Swift.Dictionary.subscript.setter : (A) -> Swift.Optional<B>",
            "Swift.Array.subscript.getter : (Swift.Int) -> A",
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
            "Swift.String.isEmpty.getter : Swift.Bool",
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
}
