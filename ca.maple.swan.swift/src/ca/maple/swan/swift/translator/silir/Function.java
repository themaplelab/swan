//===--- Function.java ---------------------------------------------------===//
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

import ca.maple.swan.swift.translator.silir.printing.ValueNameSimplifier;
import ca.maple.swan.swift.translator.silir.values.Argument;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;

import java.util.ArrayList;
import java.util.Iterator;

/*
 * Represents a SILIR function.
 */

public class Function {

    private ArrayList<BasicBlock> blocks;

    private ArrayList<Argument> arguments;

    private final String name;

    private final String returnType;

    private final Position position;

    public Function(String name, String returnType, Position position) {
        this(name, returnType, position, null);
    }

    public Function(String name, String returnType, Position position, ArrayList<Argument> arguments) {
        this.name = name;
        this.returnType = returnType;
        this.position = position;
        blocks = new ArrayList<>();
        if (arguments != null) {
            this.arguments = arguments;
        } else {
            this.arguments = new ArrayList<>();
        }
    }

    public void addBlock(BasicBlock bb) {
        this.blocks.add(bb);
    }

    public ArrayList<BasicBlock> getBlocks() {
        return this.blocks;
    }

    public BasicBlock getBlock(int i) {
        return this.blocks.get(i);
    }

    public ArrayList<Argument> getArguments() {
        return this.arguments;
    }

    public String getName() {
        return this.name;
    }

    public String getReturnType() {
        return this.returnType;
    }

    public Position getPosition() {
        return this.position;
    }

    @Override
    public String toString() {
        ValueNameSimplifier.clear();
        StringBuilder s = new StringBuilder("func ");
        s.append("`");
        s.append(this.name);
        s.append("`");
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
        s.append("{\n");
        for (BasicBlock bb : this.blocks) {
            s.append("\t");
            s.append(bb.toString());
        }
        s.append("}\n");
        return s.toString();
    }
}