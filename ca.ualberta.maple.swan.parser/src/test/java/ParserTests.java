/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

import ca.ualberta.maple.swan.parser.*;
import ca.ualberta.maple.swan.parser.Error;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Objects;

public class ParserTests {

    // This test parses every instruction in instructions.csv and checks that
    // what the SILPrinter outputs is consistent with the input. Every
    // instruction in the CSV must end with the '~' delimiter because
    // optionally a specific string to check against can be supplied.
    // The CSV can contain comments as long as they start with "#".
    @ParameterizedTest
    // Use '~' because it's never used in SIL (I think).
    // Although, it might be used in a string (e.g. string_literal).
    @CsvFileSource(resources = "instructions.csv", delimiter = '~')
    void testSingleInstruction(String inst, String compareTo) throws Error {
        SILParser parser = new SILParser(inst);
        SILInstructionDef i = parser.parseInstructionDef();
        inst = doReplacements(inst);
        if (compareTo == null || compareTo.equals("")) {
            compareTo = PrintExtensions$.MODULE$.InstructionDefPrinter(i).description();
        }
        Assertions.assertEquals(inst, compareTo);
    }

    // Test demangler
    @Test
    void testFunctionDemangling() throws Error {
        String inst = "%32 = function_ref @$sSS10FoundationE19_bridgeToObjectiveCSo8NSStringCyF : $@convention(method) (@guaranteed String) -> @owned NSString, scope 901 // user: %33~";
        String demangledFunctionName = "(extension in Foundation):Swift.String._bridgeToObjectiveC() -> __C.NSString";
        SILParser parser = new SILParser(inst);
        SILInstructionDef i = parser.parseInstructionDef();
        Assertions.assertTrue(i.instruction() instanceof SILInstruction.operator);
        SILOperator op = ((SILInstruction.operator) i.instruction()).op();
        Assertions.assertTrue(op instanceof SILOperator.functionRef);
        Assertions.assertEquals(((SILOperator.functionRef) op).name().demangled(), demangledFunctionName);
    }

    // Test parsing whole functions
    @Test
    void testFunctionParsing() throws Error, NullPointerException, URISyntaxException {
        SILParser parser = new SILParser(new File(getClass().getClassLoader()
                .getResource("programs/function1.sil").toURI()).toPath());
        SILFunction function = parser.parseFunction();
        Assertions.assertTrue(true);
    }

    // Account for any known transformations that the parser does,
    // such as superficial type conversions, here.
    String doReplacements(String inst) {
        inst = inst.replaceAll("\\[Int\\]", "Array<Int>");
        // Not sure why this .1 appears in practice after "enumelt". Doesn't seem
        // necessary.
        inst = inst.split("//")[0];
        inst = inst.trim();
        return inst;
    }

}
