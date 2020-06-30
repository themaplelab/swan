/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

import ca.ualberta.maple.swan.parser.Instruction;
import ca.ualberta.maple.swan.parser.InstructionDef;
import ca.ualberta.maple.swan.parser.SILParser;
import ca.ualberta.maple.swan.parser.SILPrinter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class ParserTests {

    @ParameterizedTest
    @ValueSource(strings = {
            "%149 = apply %148<[Int], PartialRangeFrom<Int>>(%143, %146, %144) : $@convention(method) <τ_0_0 where τ_0_0 : MutableCollection><τ_1_0 where τ_1_0 : RangeExpression, τ_0_0.Index == τ_1_0.Bound> (@in_guaranteed τ_1_0, @in_guaranteed τ_0_0) -> @out τ_0_0.SubSequence"
    })
    void testInstruction(String inst) {
        try {
            SILParser parser = new SILParser(inst);
            InstructionDef i = parser.parseInstructionDef();
            // TODO: Finish
        } catch (Exception e) {
            Assertions.fail();
        }
    }

}
