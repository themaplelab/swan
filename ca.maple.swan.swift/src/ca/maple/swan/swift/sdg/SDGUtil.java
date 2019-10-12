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
import com.ibm.wala.dataflow.graph.*;
import com.ibm.wala.fixpoint.*;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.slicer.*;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.graph.traverse.BFSPathFinder;
import com.ibm.wala.util.graph.traverse.DFS;
import com.ibm.wala.util.intset.MutableMapping;
import com.ibm.wala.util.intset.MutableSharedBitVectorIntSet;
import com.ibm.wala.util.intset.OrdinalSetMapping;
import com.ibm.wala.viz.DotUtil;
import com.ibm.wala.viz.NodeDecorator;

import javax.swing.plaf.nimbus.State;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

import static com.ibm.wala.ipa.slicer.Statement.Kind.*;

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

    public static class BooleanFramework<T> extends BasicFramework<T, BooleanVariable> {

        public BooleanFramework(
                Graph<T> flowGraph, ITransferFunctionProvider<T, BooleanVariable> transferFunctionProvider) {
           super(flowGraph, transferFunctionProvider);
        }
    }

    /** OUT = b */
    public static class BooleanTransferFunction extends UnaryOperator<BooleanVariable> {

        private final boolean b;

        public BooleanTransferFunction(boolean b) {
            this.b = b;
        }

        @Override
        public byte evaluate(BooleanVariable lhs, BooleanVariable rhs) throws IllegalArgumentException {
            if (lhs == null) {
                throw new IllegalArgumentException("lhs == null");
            }
            boolean val = b;
            BooleanVariable booleanVariable = new BooleanVariable(val);
            if (lhs.sameValue(booleanVariable)) {
                return NOT_CHANGED;
            } else {
                lhs.copyState(booleanVariable);
                return CHANGED;
            }
        }

        @Override
        public int hashCode() {
            return 9802;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof BooleanIdentity);
        }

        @Override
        public String toString() {
            return "" + b;
        }
    }

    public static boolean checkSource(HashSet<String> sources, Statement s) {
        if (s.getKind() == NORMAL_RET_CALLER) {
            CallSiteReference cs = ((NormalReturnCaller)s).getInstruction().getCallSite();
            CGNode node = s.getNode();
            Set<CGNode> it = CG.getPossibleTargets(node, cs);
            for (CGNode target: it) {
                CAstAbstractModuleLoader.DynamicMethodObject m = (CAstAbstractModuleLoader.DynamicMethodObject) target.getMethod();
                if (sources.contains(m.getEntity().getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean checkSanitizer(HashSet<String> sanitizers, Statement s) {
        return false;
    }

    public static BooleanSolver solveSDG(SDG<InstanceKey> sdg, HashSet<String> Sources, HashSet<String> Sanitizers) throws CancelException {
        ITransferFunctionProvider<Statement, BooleanVariable> functions =
                new ITransferFunctionProvider<Statement, BooleanVariable>() {
                    @Override
                    public UnaryOperator<BooleanVariable> getNodeTransferFunction(Statement Statement) {
                        if (checkSource(Sources, Statement)) {
                            return new BooleanTransferFunction(true);
                        } else if (checkSanitizer(Sanitizers, Statement)) {
                            return new BooleanTransferFunction(false);
                        } else {
                            return BooleanIdentity.instance();
                        }
                    }

                    @Override
                    public boolean hasNodeTransferFunctions() {
                        return true;
                    }

                    @Override
                    public UnaryOperator<BooleanVariable> getEdgeTransferFunction(Statement from, Statement to) {
                        return BooleanIdentity.instance();
                    }

                    @Override
                    public boolean hasEdgeTransferFunctions() {
                        return true;
                    }

                    @Override
                    public AbstractMeetOperator<BooleanVariable> getMeetOperator() {
                        return BooleanUnion.instance();
                    }
                };

        BooleanFramework<Statement> F = new BooleanFramework<>(sdg, functions);
        BooleanSolver s = new BooleanSolver(F);
        s.solve(null);

        try {
            Graph<Statement> g = pruneSDG(sdg);
            DotUtil.writeDotFile(g, makeNodeDecorator(s), "sdg.dot ", "/Users/tiganov/Desktop/sdg.dot");
        } catch (Exception e) {
            System.err.println("Could not make pdf");
            e.printStackTrace();
        }

        return s;
    }

    private static Graph<Statement> pruneSDG(final SDG<?> sdg) {
        Predicate<Statement> f =
                s -> {
                    if (s.getNode().equals(sdg.getCallGraph().getFakeRootNode())) {
                        return false;
                    } else if (s instanceof MethodExitStatement || s instanceof MethodEntryStatement) {
                        return false;
                    } else {
                        return true;
                    }
                };
        return GraphSlicer.prune(sdg, f);
    }

    private static NodeDecorator<Statement> makeNodeDecorator(BooleanSolver solver) {
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


    public static ArrayList<ArrayList<CAstSourcePositionMap.Position>> findSSSPaths(
            SDG<InstanceKey> sdg,
            HashSet<String> sources,
            HashSet<String> sinks,
            HashSet<String> sanitizers) {

        System.out.println("SDGUtil.findSSSPaths running with\n");

        System.out.println("Sources: ");
        sources.forEach(s -> System.out.print(s));
        System.out.println("\n");

        System.out.println("Sinks: ");
        sinks.forEach(s -> System.out.print(s));
        System.out.println("\n");

        System.out.println("Sanitizers: ");
        sanitizers.forEach(s -> System.out.print(s));
        System.out.println("\n");

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


        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ArrayList<>(new ArrayList<>());
    }

}
