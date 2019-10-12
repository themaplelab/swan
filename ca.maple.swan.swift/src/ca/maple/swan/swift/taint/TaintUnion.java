//===--- TaintUnion.java -------------------------------------------------===//
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

import com.ibm.wala.dataflow.graph.AbstractMeetOperator;

public class TaintUnion extends AbstractMeetOperator<TaintVariable> {

    private static final TaintUnion SINGLETON = new TaintUnion();

    public static TaintUnion instance() {
        return SINGLETON;
    }

    private TaintUnion() {}

    /** @see java.lang.Object#toString() */
    @Override
    public String toString() {
        return "UNION";
    }

    @Override
    public int hashCode() {
        return 9901;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof TaintUnion);
    }

    @Override
    public byte evaluate(TaintVariable lhs, TaintVariable[] rhs) throws NullPointerException {
        if (rhs == null) {
            throw new IllegalArgumentException("null rhs");
        }
        TaintVariable U = new TaintVariable();
        U.copyState(lhs);
        for (TaintVariable R : rhs) {
            if (R != null) {
                U.or(R);
            }
        }
        if (!lhs.sameValue(U)) {
            lhs.transferState(U);
            return CHANGED;
        } else {
            return NOT_CHANGED;
        }
    }
}
