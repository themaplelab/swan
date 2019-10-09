//===--- SDGUtil.java ----------------------------------------------------===//
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

package ca.maple.swan.swift.sdg;

import ca.maple.swan.swift.taint.TaintAnalysis;
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
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.graph.traverse.BFSPathFinder;
import com.ibm.wala.util.graph.traverse.DFS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.ibm.wala.ipa.slicer.Statement.Kind.NORMAL_RET_CALLER;

public class SDGUtil {

    static CallGraph CG = null;

    interface EndpointFinder {

        boolean endpoint(Statement s);

    }

    private static final EndpointFinder sourceEndpoints = s -> {
        if (s.getKind() == NORMAL_RET_CALLER) {
            CallSiteReference cs = ((NormalReturnCaller)s).getInstruction().getCallSite();
            CGNode node = s.getNode();
            Set<CGNode> it = CG.getPossibleTargets(node, cs);
            String testSource = "testTaint.source() -> Swift.String";
            for (CGNode target: it) {
                CAstAbstractModuleLoader.DynamicMethodObject m = (CAstAbstractModuleLoader.DynamicMethodObject) target.getMethod();
                if (m.getEntity().getName().equals(testSource)) {
                    return true;
                }
            }
        }
        return false;
    };

    private static final EndpointFinder sinkEndpoints = s -> {
        if (s.getKind()== Statement.Kind.PARAM_CALLEE) {
            CAstAbstractModuleLoader.DynamicMethodObject m = (CAstAbstractModuleLoader.DynamicMethodObject) s.getNode().getMethod();
            String ref = m.getEntity().getName();
            String testSink = "testTaint.sink(sunk: Swift.String) -> ()";
            if (ref.equals(testSink)) {
                return true;
            }
        }

        return false;
    };

    public static Set<List<Statement>> getPaths(SDG<? extends InstanceKey> G, EndpointFinder sources, EndpointFinder sinks) {
        Set<List<Statement>> result = HashSetFactory.make();
        CG = G.getCallGraph();
        for(Statement src : G) {
            if (sources.endpoint(src)) {
                for(Statement dst : G) {
                    if (sinks.endpoint(dst)) {
                        BFSPathFinder<Statement> paths = new BFSPathFinder<>(G, src, dst);
                        List<Statement> path = paths.find();
                        if (path != null) {
                            result.add(path);
                        } else {
                            System.out.println(DFS.getReachableNodes(G, Collections.singleton(src)));
                        }
                    }
                }
            }
        }
        return result;
    }

    public static void printPath(List<Statement> path) throws IOException {
        for(Statement s: path) {
            IMethod m = s.getNode().getMethod();
            boolean ast = m instanceof AstMethod;
            switch (s.getKind()) {
                case NORMAL: {
                    if (ast) {
                        CAstSourcePositionMap.Position p = ((AstMethod)m).getSourcePosition(((NormalStatement)s).getInstructionIndex());
                        if (p != null) {
                            SourceBuffer buf = new SourceBuffer(p);
                            System.out.println(buf + " (" + p + ")");
                        }
                    }
                    break;
                }
                case PARAM_CALLEE: {
                    if (ast) {
                        try {
                            CAstSourcePositionMap.Position p = ((AstMethod)m).getSourcePosition();
                            if (p != null) {
                                SourceBuffer buf = new SourceBuffer(p);
                                System.out.println(buf + " (" + p + ")");
                            }
                        } catch (Exception e) {}
                    }
                    break;
                }
                case PARAM_CALLER: {
                    if (ast) {
                        CAstSourcePositionMap.Position p = ((AstMethod)m).getSourcePosition(((ParamCaller)s).getInstructionIndex());
                        if (p != null) {
                            SourceBuffer buf = new SourceBuffer(p);
                            System.out.println(buf + " (" + p + ")");
                        }
                    }
                    break;
                }
                case NORMAL_RET_CALLER: {
                    if (ast) {
                        CAstSourcePositionMap.Position p = ((AstMethod)m).getSourcePosition(((NormalReturnCaller)s).getInstructionIndex());
                        if (p != null) {
                            SourceBuffer buf = new SourceBuffer(p);
                            System.out.println(buf + " (" + p + ")");
                        }
                    }
                    break;
                }
            }
        }
    }

    public static void printPaths(Set<List<Statement>> paths) throws IOException {
        for(List<Statement> path : paths) {
            printPath(path);
        }
    }


    public static ArrayList<ArrayList<CAstSourcePositionMap.Position>> findSSSPaths(
            SDG<InstanceKey> sdg,
            ArrayList<String> sources,
            ArrayList<String> sinks,
            ArrayList<String> sanitizers) {

        System.out.println("SDGUtil.findSSSPaths running with\n" +
                "Sources: " + sources + "\n" +
                "Sinks: " + sinks + "\n" +
                "Sanitizers: " + sanitizers);

        Set<List<Statement>> paths = getPaths(sdg, sourceEndpoints, sinkEndpoints);
        try {
            if (paths.size() > 0) {
                System.out.println("*** DUMPING TAINT ANALYSIS PATHS... ***");
                System.out.println(paths);
                printPaths(paths);
                System.out.println("*** ...FINSIHED DUMPING TAINT ANALYSIS PATHS ***");
            } else {
                System.out.println("*** NO TAINT ANALYSIS PATHS ***");
            }
        } catch (IOException e) {}

        return new ArrayList<>(new ArrayList<>());
    }

}
