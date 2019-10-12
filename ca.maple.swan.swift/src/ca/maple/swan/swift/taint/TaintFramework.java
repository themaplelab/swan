//===--- TaintFramework.java ---------------------------------------------===//
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

import com.ibm.wala.dataflow.graph.BasicFramework;
import com.ibm.wala.dataflow.graph.ITransferFunctionProvider;
import com.ibm.wala.util.graph.Graph;

public class TaintFramework<T> extends BasicFramework<T, TaintVariable> {

    public TaintFramework(
            Graph<T> flowGraph, ITransferFunctionProvider<T, TaintVariable> transferFunctionProvider) {
        super(flowGraph, transferFunctionProvider);
    }
}
