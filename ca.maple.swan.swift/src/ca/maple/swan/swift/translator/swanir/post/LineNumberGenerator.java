package ca.maple.swan.swift.translator.swanir.post;

import ca.maple.swan.swift.translator.swanir.BasicBlock;
import ca.maple.swan.swift.translator.swanir.Function;
import ca.maple.swan.swift.translator.swanir.context.ProgramContext;
import ca.maple.swan.swift.translator.swanir.instructions.SWANIRInstruction;

public class LineNumberGenerator {

    public static void generateLineNumbers(ProgramContext pc) {
        int counter = 1;
        for (Function f : pc.getFunctions()) {
            f.setLineNumber(counter);
            for (BasicBlock bb : f.getBlocks()) {
                bb.setLineNumber(counter);
                for (SWANIRInstruction i : bb.getInstructions()) {
                    i.setLineNumber(counter);
                    ++counter;
                }
            }
        }
    }

}
