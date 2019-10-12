//===--- TaintSolver.java ------------------------------------------------===//
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

import com.ibm.wala.dataflow.graph.DataflowSolver;
import com.ibm.wala.dataflow.graph.IKilldallFramework;

public class TaintSolver<T> extends DataflowSolver<T, TaintVariable> {

    public TaintSolver(IKilldallFramework<T, TaintVariable> problem) {
        super(problem);
    }

    @Override
    protected TaintVariable makeNodeVariable(T n, boolean IN) {
        return new TaintVariable();
    }

    @Override
    protected TaintVariable makeEdgeVariable(T src, T dst) {
        return new TaintVariable();
    }

    @Override
    protected TaintVariable[] makeStmtRHS(int size) {
        return new TaintVariable[size];
    }
}
