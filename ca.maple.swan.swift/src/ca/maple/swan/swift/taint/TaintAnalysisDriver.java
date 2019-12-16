//===--- TaintAnalysisDriver.java ----------------------------------------===//
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

import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.slicer.*;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.viz.DotUtil;
import com.ibm.wala.viz.NodeDecorator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

/*
 * Main class for running a taint analysis.
 */

public class TaintAnalysisDriver {

    public static TaintSolver solveSDG(SDG<InstanceKey> sdg, HashSet<String> Sources, HashSet<String> Sanitizers, HashSet<String> Sinks) throws CancelException {

        Graph<Statement> graph = pruneSDG(sdg);

        TaintTransferFunctionProvider functions = new TaintTransferFunctionProvider(sdg.getCallGraph(), Sources, Sanitizers, Sinks);
        TaintFramework f = new TaintFramework(graph, functions);
        TaintSolver s = new TaintSolver(f);
        s.solve(null);

        return s;
    }

    private static Graph<Statement> pruneSDG(final SDG<?> sdg) {
        Predicate<Statement> f =
                s -> {
                    if (s.getNode().equals(sdg.getCallGraph().getFakeRootNode())) {
                        return false;
                    } else return !(s instanceof MethodExitStatement) && !(s instanceof MethodEntryStatement);
                };
        return GraphSlicer.prune(sdg, f);
    }

    private static NodeDecorator<Statement> makeNodeDecorator(TaintSolver solver) {
        // TODO: Add red color to nodes that are tainted. May require extending DotUtil.java.
        return s -> {
            switch (s.getKind()) {
                case HEAP_PARAM_CALLEE:
                case HEAP_PARAM_CALLER:
                case HEAP_RET_CALLEE:
                case HEAP_RET_CALLER:
                    HeapStatement h = (HeapStatement) s;
                    return s.getKind() + "\\n" + h.getNode() + "\\n" + h.getLocation();
                case EXC_RET_CALLEE:
                case EXC_RET_CALLER:
                case NORMAL:
                case NORMAL_RET_CALLEE:
                case NORMAL_RET_CALLER:
                case PARAM_CALLEE:
                case PARAM_CALLER:
                case PHI:
                default:
                    return s.toString() + " | " + solver.getOut(s);
            }
        };
    }


    private static List<List<CAstSourcePositionMap.Position>> findSSSPaths(
            SDG<InstanceKey> sdg,
            HashSet<String> sources,
            HashSet<String> sinks,
            HashSet<String> sanitizers) {

        System.out.println("SDGUtil.findSSSPaths running with\n");

        System.out.println("Sources: ");
        sources.forEach(System.out::print);
        System.out.println("\n");

        System.out.println("Sinks: ");
        sinks.forEach(System.out::print);
        System.out.println("\n");

        System.out.println("Sanitizers: ");
        sanitizers.forEach(System.out::print);
        System.out.println("\n");



        try {
            TaintPathRecorder.clear();
            TaintSolver s = solveSDG(sdg, sources, sanitizers, sinks);
            // TODO: Prune called again here, should be cached.
            return TaintPathRecorder.getPaths(pruneSDG(sdg), s);
        } catch (CancelException e) {
            e.printStackTrace();
            return new ArrayList<>(new ArrayList<>());
        }
    }

    public static List<List<CAstSourcePositionMap.Position>> doTaintAnalysis(
            SDG<InstanceKey> sdg,
            String[] sources,
            String[] sinks,
            String[] sanitizers) {

        return findSSSPaths(
                sdg,
                new HashSet<>(Arrays.asList(sources)),
                new HashSet<>(Arrays.asList(sinks)),
                new HashSet<>(Arrays.asList(sanitizers))
        );
    }

}
