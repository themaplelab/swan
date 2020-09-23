/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

import ca.ualberta.maple.swan.ir.*;
import ca.ualberta.maple.swan.parser.Error;
import ca.ualberta.maple.swan.parser.SILInstructionDef;
import ca.ualberta.maple.swan.parser.SILModule;
import ca.ualberta.maple.swan.parser.SILParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class TranslationTests {

    static boolean DEBUG = true;

    /* CSV based instruction-level tests.
     * Format: sil-instruction '~' swanir-instruction ( ":::" swanir-instruction )*
     */
    @ParameterizedTest
    @CsvFileSource(resources = "instructions.csv", delimiter = '~')
    void testInstructions(String silInst, String swanirInstructions) throws Error {
        assumeTrue(swanirInstructions != null && !swanirInstructions.isEmpty());
        if (DEBUG) System.out.println(silInst);
        SILParser parser = new SILParser(silInst);
        SILInstructionDef instruction = parser.parseInstructionDef();
        SILToRawSWANIR translator = new SILToRawSWANIR();
        if (swanirInstructions.trim().equals("NOP")) {
            if (DEBUG) System.out.println("  -> NOP");
            return;
        }
        // Translate after checking NOP because certain instructions require
        // non-null context elements for translation. e.g. struct
        InstructionDef[] swanir = translator.translateSILInstruction(instruction,
                new Context(null, null, null, scala.Option.apply(null)));
        if (swanir == null) {
            return;
        }
        assumeTrue(swanir != null);
        String[] compareTo = swanirInstructions.split(":::");
        Assertions.assertEquals(swanir.length, compareTo.length);
        try {
            for (int i = 0; i < swanir.length; ++i) {
                String actual = new SWANIRPrinter().print(swanir[i]);
                if (DEBUG) System.out.println("  -> " + actual); // DEBUG
                Assertions.assertEquals(actual.trim(), compareTo[i].trim());
            }
        } catch (Exception e) {
            System.out.println(e);
        }

    }

}
