package ca.maple.swan.swift.translator.silir.post;

import ca.maple.swan.swift.translator.silir.BasicBlock;
import ca.maple.swan.swift.translator.silir.Function;
import ca.maple.swan.swift.translator.silir.context.ProgramContext;
import ca.maple.swan.swift.translator.silir.instructions.SILIRInstruction;

public class LineNumberGenerator {

    public static void generateLineNumbers(ProgramContext pc) {
        int counter = 1;
        for (Function f : pc.getFunctions()) {
            f.setLineNumber(counter);
            for (BasicBlock bb : f.getBlocks()) {
                bb.setLineNumber(counter);
                for (SILIRInstruction i : bb.getInstructions()) {
                    i.setLineNumber(counter);
                    ++counter;
                }
            }
        }
    }

}
