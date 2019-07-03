//===--- SwiftSummarizedFunction.java ------------------------------------===//
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

package ca.maple.swan.swift.ipa.summaries;

import ca.maple.swan.swift.cfg.SwiftInducedCFG;
import com.ibm.wala.cfg.InducedCFG;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.ipa.summaries.SummarizedMethodWithNames;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.MethodReference;

public class SwiftSummarizedFunction extends SummarizedMethodWithNames {

    public SwiftSummarizedFunction(MethodReference ref, MethodSummary summary, IClass declaringClass)
            throws NullPointerException {
        super(ref, summary, declaringClass);
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public InducedCFG makeControlFlowGraph(SSAInstruction[] instructions) {
        return new SwiftInducedCFG(instructions, this, Everywhere.EVERYWHERE);
    }

}