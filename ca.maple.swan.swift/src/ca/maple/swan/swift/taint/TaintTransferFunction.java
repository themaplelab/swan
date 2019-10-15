//===--- TaintTransferFunction.java --------------------------------------===//
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

import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.ipa.slicer.Statement;

import java.util.ArrayList;

public class TaintTransferFunction extends UnaryOperator<TaintVariable> {

    public enum FUNCTION_KIND {
        SOURCE,
        SINK,
        SANITIZER
    }

    private final Statement statement;

    private final FUNCTION_KIND k;

    public TaintTransferFunction(FUNCTION_KIND k, Statement t) {
        this.k = k;
        this.statement = t;
    }

    @Override
    public byte evaluate(TaintVariable lhs, TaintVariable rhs) throws IllegalArgumentException {
        if (lhs == null) {
            throw new IllegalArgumentException("lhs == null");
        }
        ArrayList<Statement> sources = new ArrayList<>(rhs.getSources());
        switch (k) {
            case SOURCE:
                sources.add(statement);
                break;
            case SINK:
                TaintPathRecorder.recordPath(rhs.getSources(), statement);
                break;
            case SANITIZER:
                sources.clear();
                break;
        }
        TaintVariable taintVariable = new TaintVariable(sources);
        if (lhs.sameValue(taintVariable)) {
            return NOT_CHANGED;
        } else {
            lhs.copyState(taintVariable);
            return CHANGED;
        }
    }

    @Override
    public int hashCode() {
        return 9802;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof TaintTransferFunction);
    }

    @Override
    public String toString() {
        return "" + k;
    }
}