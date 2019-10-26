//===--- BasicBlock.java -------------------------------------------------===//
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

package ca.maple.swan.swift.translator.silir;

import ca.maple.swan.swift.translator.silir.instructions.SILIRInstruction;
import ca.maple.swan.swift.translator.silir.values.Argument;

import java.util.ArrayList;
import java.util.Iterator;

/*
 * Represents a SILIR basic block.
 */

public class BasicBlock {

    private final boolean PRINT_IMPLICIT_INSTRUCTIONS = false;

    private final int number;

    private ArrayList<SILIRInstruction> instructions;

    private ArrayList<Argument> arguments;

    public BasicBlock(int number) {
        this(number, null);
    }

    public BasicBlock(int number, ArrayList<Argument> arguments) {
        this.number = number;
        instructions = new ArrayList<>();
        if (arguments != null) {
            this.arguments = arguments;
        } else {
            arguments = new ArrayList<>();
        }
    }

    public int getNumber() {
        return this.number;
    }

    public ArrayList<Argument> getArguments() {
        return this.arguments;
    }

    public Argument getArgument(int i) {
        return this.arguments.get(i);
    }

    public void addInstruction(SILIRInstruction instruction) {
        this.instructions.add(instruction);
    }

    public ArrayList<SILIRInstruction> getInstructions() {
        return this.instructions;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder("bb" + number);
        if (arguments.size() > 0) {
            s.append("(");
            Iterator<Argument> it = arguments.iterator();
            while (it.hasNext()) {
                Argument a = it.next();
                s.append(a.simpleName());
                s.append(" : ");
                s.append(a.getType());
                if (it.hasNext()) {
                    s.append(", ");
                }
            }
            s.append(") ");
        }
        s.append(":\n");

        for (SILIRInstruction instruction : instructions) {
            try {
                if (instruction.isExplicit() || PRINT_IMPLICIT_INSTRUCTIONS) {
                    instruction.toString(); // To trigger error
                    s.append("\t\t");
                    s.append(instruction.toString());
                }
            } catch (Exception e ){
                System.err.println("(Block #" + this.number + ") Could not print instruction :" + instruction.getClass().getName());
            }

        }
        return s.toString();
    }

}
