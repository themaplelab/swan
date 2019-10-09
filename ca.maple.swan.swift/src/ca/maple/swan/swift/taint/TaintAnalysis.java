//===--- TaintAnalysis.java ----------------------------------------------===//
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

import ca.maple.swan.swift.sdg.SDGUtil;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.loader.CAstAbstractModuleLoader;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.util.SourceBuffer;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.slicer.*;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.graph.traverse.BFSPathFinder;
import com.ibm.wala.util.graph.traverse.DFS;

import java.io.IOException;
import java.util.*;

import static com.ibm.wala.ipa.slicer.Statement.Kind.NORMAL_RET_CALLER;

/*
 * This code is mostly borrowed from https://github.com/wala/Examples.
 * Currently, it can find the sources and sinks, but not a path in between. (TODO)
 *
 * TODO: Move whatever is for path finding needed to SDGUtil.java.
 *   Only have whatever is needed for setting up calls to SDGUtil here.
 */

public class TaintAnalysis {

    public static ArrayList<ArrayList<CAstSourcePositionMap.Position>> doTaintAnalysis(
            SDG<InstanceKey> sdg,
            String[] sources,
            String[] sinks,
            String[] sanitizers) {

        // TODO: Combine with known SSS here.

        return SDGUtil.findSSSPaths(
                sdg,
                new ArrayList<String>(Arrays.asList(sources)),
                new ArrayList<String>(Arrays.asList(sinks)),
                new ArrayList<String>(Arrays.asList(sanitizers))
        );
    }
}