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

public class TaintVariable  extends AbstractVariable<TaintVariable> {

    public enum KIND {
        UNTAINTED,
        SOURCE,
        TAINTED,
        SANITIZER,
        SINK
    }

    private boolean B = false;

    private KIND K = KIND.UNTAINTED;


    public TaintVariable() {}

    public TaintVariable(boolean b) {
        this.B = b;
    }

    public TaintVariable(boolean b, KIND k) {
        this.B = b;
        this.K = k;
    }

    @Override
    public void copyState(TaintVariable other) {
        if (other == null) {
            throw new IllegalArgumentException("other null");
        }
        B = other.B;
        K = other.K;
    }

    public void transferState(TaintVariable other) {
        if (other == null) {
            throw new IllegalArgumentException("other null");
        }
        B = other.B;
        switch (other.K) {
            case SANITIZER:
            case UNTAINTED:
                K = KIND.UNTAINTED;
                break;
            case TAINTED:
            case SINK:
            case SOURCE:
                K = KIND.TAINTED;
                break;
        }
    }

    public boolean sameValue(TaintVariable other) {
        if (other == null) {
            throw new IllegalArgumentException("other is null");
        }
        return (B == other.B) && (K == other.K);
    }

    public void or(TaintVariable other) {
        if (other == null) {
            throw new IllegalArgumentException("other is null");
        }
        B = B || other.B;
        K = B ? KIND.TAINTED : KIND.UNTAINTED;
    }


    @Override
    public String toString() {
        return (B ? "[TRUE]" : "[FALSE]") + " : [" + K + "]";
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    public KIND getKind() {
        return this.K;
    }

    public boolean getTaintedness() {
        return this.B;
    }
}
