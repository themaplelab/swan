//===--- TaintBFSPathFinder.java -----------------------------------------===//
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

import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.util.collections.FilterIterator;
import com.ibm.wala.util.graph.Graph;

import java.util.Iterator;

public class TaintBFSPathFinder extends com.ibm.wala.util.graph.traverse.BFSPathFinder<Statement> {

    private final TaintSolver S;
    private final Graph<Statement> G;

    public TaintBFSPathFinder(Graph<Statement> g, Statement src, Statement target, TaintSolver s) {
        super(g, src, target);
        this.S = s;
        this.G = g;
    }

    @Override
    protected Iterator<? extends Statement> getConnected(Statement n) {
        return new FilterIterator<>(G.getSuccNodes(n), (Statement s) -> S.getOut(s).isTainted());
    }

}
