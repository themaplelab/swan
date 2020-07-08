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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.util.HashSet;

public class ParserTests {

    // This test parses every instruction in instructions.csv and checks that
    // what the SILPrinter outputs is consistent with the input. Every
    // instruction in the CSV must end with the '~' delimiter because
    // optionally a specific string to check against can be supplied.
    // The CSV can contain comments as long as they start with "#".
    @ParameterizedTest
    // Use '~' because it's never used in SIL (I think).
    @CsvFileSource(resources = "instructions.csv", delimiter = '~')
    void testSingleInstruction(String inst, String compareTo) throws Error {
        inst = doReplacements(inst);
        SILParser parser = new SILParser(inst);
        InstructionDef i = parser.parseInstructionDef();
        if (compareTo == null || compareTo.equals("")) {
            compareTo = PrintExtensions$.MODULE$.InstructionDefPrinter(i).description();
        }
        Assertions.assertEquals(inst, compareTo);
    }

    // Account for any known transformations that the parser does,
    // such as superficial type conversions, here.
    String doReplacements(String inst) {
        inst = inst.replaceAll("\\[Int\\]", "Array<Int>");
        // Sometimes there is a space after unreachable.
        inst = inst.replaceAll("unreachable ,", "unreachable,");
        // Not sure why this .1 appears in practice after "enumelt". Doesn't seem
        // necessary.
        inst = inst.replaceAll("enumelt\\.1", "enumelt");
        inst = inst.split("//")[0];
        inst = inst.trim();
        return inst;
    }

}
