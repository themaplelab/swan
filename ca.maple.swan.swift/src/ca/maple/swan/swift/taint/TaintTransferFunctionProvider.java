//===--- TaintTransferFunctionProvider.java ------------------------------===//
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
import com.ibm.wala.dataflow.graph.ITransferFunctionProvider;
import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.slicer.Statement;

import java.util.HashSet;

public class TaintTransferFunctionProvider implements ITransferFunctionProvider<Statement, TaintVariable> {

    private final CallGraph cg;
    private final HashSet<String> sources;
    private final HashSet<String> sanitizers;
    private final HashSet<String> sinks;

    public TaintTransferFunctionProvider(CallGraph cg, HashSet<String> sources, HashSet<String> sanitizers, HashSet<String> sinks) {
        this.cg = cg;
        this.sources = sources;
        this.sanitizers = sanitizers;
        this.sinks = sinks;
    }


    @Override
    public UnaryOperator<TaintVariable> getNodeTransferFunction(Statement Statement) {
        // TODO: No need to check for source if rhs is tainted. But need to have the solver to check.
        if (SSSDeterminer.checkSource(sources, Statement, cg)) {
            return new TaintTransferFunction(true, TaintVariable.KIND.SOURCE);
        } else if (SSSDeterminer.checkSink(sinks, Statement, cg)) {
            return new TaintTransferFunction(true, TaintVariable.KIND.SINK);
        } else if (SSSDeterminer.checkSanitizer(sanitizers, Statement, cg)) {
            return new TaintTransferFunction(false, TaintVariable.KIND.SANITIZER);
        } else {
            return TaintIdentity.instance();
        }
    }

    @Override
    public boolean hasNodeTransferFunctions() {
        return true;
    }

    @Override
    public UnaryOperator<TaintVariable> getEdgeTransferFunction(Statement from, Statement to) {
        return null;
    }

    @Override
    public boolean hasEdgeTransferFunctions() {
        return false;
    }

    @Override
    public AbstractMeetOperator<TaintVariable> getMeetOperator() {
        return TaintUnion.instance();
    }


}
