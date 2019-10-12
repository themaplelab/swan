//===--- TaintIdentity.java ----------------------------------------------===//
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

public class TaintIdentity extends UnaryOperator<TaintVariable> {

    private static final TaintIdentity SINGLETON = new TaintIdentity();

    public static TaintIdentity instance() {
        return SINGLETON;
    }

    private TaintIdentity() {}

    @Override
    public byte evaluate(TaintVariable lhs, TaintVariable rhs) throws IllegalArgumentException {
        if (lhs == null) {
            throw new IllegalArgumentException("lhs == null");
        }

        if (lhs.sameValue(rhs)) {
            return NOT_CHANGED;
        } else {
            lhs.transferState(rhs);
            return CHANGED;
        }
    }

    @Override
    public String toString() {
        return "Id ";
    }

    @Override
    public int hashCode() {
        return 9802;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof TaintIdentity);
    }

    @Override
    public boolean isIdentity() {
        return false;
    }
}
