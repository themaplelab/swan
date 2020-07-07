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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

public class ParserTests {

    // This test parses every instruction in instructions.csv and checks that
    // what the SILPrinter outputs is consistent with the input. Every
    // instruction in the CSV must end with the '~' delimiter because
    // optionally a specific string to check against can be supplied.
    // The CSV can contain comments as long as they start with "//" and end
    // with '~'.
    @ParameterizedTest
    // Use '~' because it's never used in SIL (I think).
    @CsvFileSource(resources = "instructions.csv", delimiter = '~')
    void testSingleInstruction(String inst, String compareTo) {
        // Handle comments in the CSV.
        if (inst.startsWith("//")) {
            return;
        }
        try {
            inst = doReplacements(inst);
            SILParser parser = new SILParser(inst);
            InstructionDef i = parser.parseInstructionDef();
            if (compareTo == null || compareTo.equals("")) {
                compareTo = PrintExtensions$.MODULE$.InstructionDefPrinter(i).description();
            }
            Assertions.assertEquals(inst, compareTo);
        } catch (Exception e) {
            e.printStackTrace();
            Assertions.fail();
        }
    }

    // Account for any known transformations that the parser does,
    // such as superficial type conversions, here.
    String doReplacements(String inst) {
        inst = inst.replaceAll("\\[Int\\]", "Array<Int>");
        return inst;
    }

}
