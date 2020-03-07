//===--- ProgramContext.java ---------------------------------------------===//
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

package ca.maple.swan.swift.translator.swanir.context;

import ca.maple.swan.swift.translator.sil.InstructionNode;
import ca.maple.swan.swift.translator.swanir.BasicBlock;
import ca.maple.swan.swift.translator.swanir.Function;
import ca.maple.swan.swift.translator.swanir.post.IRPruner;
import ca.maple.swan.swift.translator.swanir.post.LineNumberGenerator;
import ca.maple.swan.swift.translator.swanir.values.GlobalValueTable;
import ca.maple.swan.swift.translator.swanir.values.ValueTable;

import java.util.*;

/*
 * Holds anything a program would need for translation.
 */

public class ProgramContext {

    private HashMap<String, Function> allFunctions;
    private HashMap<String, Function> summaries;
    public HashMap<BasicBlock, ArrayList<InstructionNode>> toTranslate;
    public ValueTable vt;

    public GlobalValueTable globalValues = new GlobalValueTable();

    public ProgramContext(HashMap<BasicBlock, ArrayList<InstructionNode>> toTranslate) {
        this.allFunctions = new LinkedHashMap<>(); // LinkedHashMap is important here! Ordering can affect global accessing.
        this.summaries = new HashMap<>();
        this.toTranslate = toTranslate;
        this.vt = new ValueTable();
    }

    public void addFunction(String s, Function f) {
        allFunctions.put(s, f);
    }

    public Function getFunction(String s) {
        if (allFunctions.containsKey(s)) {
            return allFunctions.get(s);
        }
        return getSummary(s);
    }

    public void addSummaries(ArrayList<Function> functions) {
        for (Function f : functions) {
            this.summaries.put(f.getName(), f);
        }
    }

    private Function getSummary(String s) {
        if (summaries.containsKey(s)) {
            Function f = summaries.get(s);
            if (!allFunctions.containsKey(f.getName())) {
                allFunctions.put(f.getName(), f);
            }
            return f;
        }
        return null;
    }

    public Set<String> getFunctionNames() {
        return allFunctions.keySet();
    }

    public ArrayList<Function> getFunctions() {
        ArrayList<Function> functions = new ArrayList<>();
        for (String s : allFunctions.keySet()) {
            functions.add(allFunctions.get(s));
        }
        return functions;
    }

    public void removeFunction(Function f) {
        this.allFunctions.remove(f.getName());
    }

    public void pruneIR() {
        for (Function f : getFunctions()) {
            new IRPruner(f, vt);
        }
    }

    public void generateLineNumbers() {
        LineNumberGenerator.generateLineNumbers(this);
    }

    public void printFunctions() {
        System.out.println("/=======================SWANIR=============================");
        for (Function f : getFunctions()) {
            System.out.println(f);
        }
        System.out.println("=========================================================\\");

    }
}
