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

public class TaintTransferFunction extends UnaryOperator<TaintVariable> {

    // Force the kind onto the TaintVariable.
    private final TaintVariable.KIND k;

    public TaintTransferFunction(boolean b, TaintVariable.KIND k) {
        this.k = k;
    }

    @Override
    public byte evaluate(TaintVariable lhs, TaintVariable rhs) throws IllegalArgumentException {
        if (lhs == null) {
            throw new IllegalArgumentException("lhs == null");
        }
        boolean b;
        switch (k) {
            case SOURCE:
                b = true;
                break;
            case SINK:
                if (rhs.getTaintedness()) {
                    b = true;
                    break;
                }
            case SANITIZER:
                b = false;
                break;
            default:
                b = false;
                break;
        }
        TaintVariable TaintVariable = new TaintVariable(b, k);
        if (lhs.sameValue(TaintVariable)) {
            return NOT_CHANGED;
        } else {
            lhs.copyState(TaintVariable);
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