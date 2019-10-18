//===--- TaintVariable.java ----------------------------------------------===//
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

package ca.maple.swan.swift.taint;

import com.ibm.wala.fixpoint.AbstractVariable;
import com.ibm.wala.ipa.slicer.Statement;

import java.util.ArrayList;

public class TaintVariable extends AbstractVariable<TaintVariable> {

    private ArrayList<Statement> sources = new ArrayList<>();

    public TaintVariable() {}

    public TaintVariable(ArrayList<Statement> sources) {
        this.sources = new ArrayList<>(sources);
    }

    @Override
    public void copyState(TaintVariable other) {
        if (other == null) {
            throw new IllegalArgumentException("other null");
        }
        this.sources = new ArrayList<>(other.sources);
    }

    public boolean sameValue(TaintVariable other) {
        if (other == null) {
            throw new IllegalArgumentException("other is null");
        }
        return sources.equals(other.sources);
    }

    public void or(TaintVariable other) {
        if (other == null) {
            throw new IllegalArgumentException("other is null");
        }
        this.sources.addAll(other.sources);
    }


    @Override
    public String toString() {
        return (isTainted() ? "[TRUE]" : "[FALSE]") + ", |S| -> " + sources.size();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    public ArrayList<Statement> getSources() {
        return this.sources;
    }

    public boolean isTainted() {
        return !sources.isEmpty();
    }

}
