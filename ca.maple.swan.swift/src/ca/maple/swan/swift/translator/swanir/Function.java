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

package ca.maple.swan.swift.translator.swanir;

import ca.maple.swan.swift.translator.swanir.context.ProgramContext;
import ca.maple.swan.swift.translator.swanir.printing.ValueNameSimplifier;
import ca.maple.swan.swift.translator.swanir.values.Argument;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;

import java.util.ArrayList;
import java.util.Iterator;

/*
 * Represents a SWANIR function.
 */

public class Function {

    private ArrayList<BasicBlock> blocks;

    private ArrayList<Argument> arguments;

    private final String name;

    private final String returnType;

    private final Position position;

    private boolean isCoroutine = false;

    private boolean isSWANIRGenerated = false;

    private int lineNumber = -1;

    public Function(String name, String returnType, Position position) {
        this(name, returnType, position, null);
    }

    public Function(String name, String returnType, Position position, ArrayList<Argument> arguments, boolean isSWANIRGenerated) {
        this(name, returnType, position, arguments);
        this.isSWANIRGenerated = isSWANIRGenerated;
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

    public Function(Function f, ProgramContext pc) {
        this.name = f.getName();
        this.returnType = f.getReturnType();
        this.position = f.getPosition();
        this.arguments = f.getArguments();
        blocks = new ArrayList<>();
        for (BasicBlock b : f.getBlocks()) {
            BasicBlock copyBB = new BasicBlock(b);
            pc.toTranslate.put(copyBB, pc.toTranslate.get(b));
            blocks.add(copyBB);
        }
    }

    public void setLineNumber(int n) {
        this.lineNumber = n;
    }

    public int getLineNumber() {
        return this.lineNumber;
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

    public boolean isCoroutine() {
        return isCoroutine;
    }

    public void setCoroutine() {
        isCoroutine = true;
    }

    @Override
    public String toString() {
        ValueNameSimplifier.clear();
        StringBuilder s = new StringBuilder();
        if (isSWANIRGenerated) {
            s.append("// Generated (fake)\n");
        }
        s.append("func ");
        s.append(this.returnType);
        s.append(" ");
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
        int i = 0;
        for (BasicBlock bb : this.blocks) {
            bb.setNumber(i);
            ++i;
        }
        for (BasicBlock bb : this.blocks) {
            s.append("    ");
            s.append(bb.toString());
        }
        s.append("}\n");
        return s.toString();
    }
}
