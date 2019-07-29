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

import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.util.SourceBuffer;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.slicer.*;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.graph.traverse.BFSPathFinder;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class TaintAnalysis {

    interface EndpointFinder {

        boolean endpoint(Statement s);

    }

    /************************************ EXAMPLES *************************************/
    public static EndpointFinder sendMessageSink = new EndpointFinder() {
        @Override
        public boolean endpoint(Statement s) {
            if (s.getKind()== Statement.Kind.PARAM_CALLER) {
                MethodReference ref = ((ParamCaller)s).getInstruction().getCallSite().getDeclaredTarget();
                if (ref.getName().toString().equals("sendTextMessage")) {
                    return true;
                }
            }

            return false;
        }
    };

    public static EndpointFinder documentWriteSink = new EndpointFinder() {
        @Override
        public boolean endpoint(Statement s) {
            if (s.getKind()== Statement.Kind.PARAM_CALLEE) {
                String ref = ((ParamCallee)s).getNode().getMethod().toString();
                if (ref.equals("<Code body of function Lpreamble.js/DOMDocument/Document_prototype_write>")) {
                    return true;
                }
            }

            return false;
        }
    };

    public static EndpointFinder getDeviceSource = new EndpointFinder() {
        @Override
        public boolean endpoint(Statement s) {
            if (s.getKind()== Statement.Kind.NORMAL_RET_CALLER) {
                MethodReference ref = ((NormalReturnCaller)s).getInstruction().getCallSite().getDeclaredTarget();
                if (ref.getName().toString().equals("getDeviceId")) {
                    return true;
                }
            }

            return false;
        }
    };

    public static EndpointFinder documentUrlSource = new EndpointFinder() {
        @Override
        public boolean endpoint(Statement s) {
            if (s.getKind()== Statement.Kind.NORMAL) {
                NormalStatement ns = (NormalStatement) s;
                SSAInstruction inst = ns.getInstruction();
                if (inst instanceof SSAGetInstruction) {
                    if (((SSAGetInstruction)inst).getDeclaredField().getName().toString().equals("URL")) {
                        return true;
                    }
                }
            }

            return false;
        }
    };
    /************************************ EXAMPLES *************************************/

    public static EndpointFinder swiftSources = new EndpointFinder() {
        @Override
        public boolean endpoint(Statement s) {
            switch(s.getKind()) {
                case PARAM_CALLER:
                    System.out.println(s);
                    break;
                case PARAM_CALLEE:
                    System.out.println(s);
                    break;
                case NORMAL_RET_CALLER:
                    System.out.println(s);
                    break;
                case NORMAL_RET_CALLEE:
                    System.out.println(s);
                    break;
                case EXC_RET_CALLER:
                    System.out.println(s);
                    break;
                case EXC_RET_CALLEE:
                    System.out.println(s);
                    break;
                case HEAP_PARAM_CALLER:
                    System.out.println(s);
                    break;
                case HEAP_PARAM_CALLEE:
                    System.out.println(s);
                    break;
                case HEAP_RET_CALLER:
                    System.out.println(s);
                    break;
                case HEAP_RET_CALLEE:
                    System.out.println(s);
                    break;
            }
            /*
            if (s.getKind()== Statement.Kind.NORMAL_RET_CALLER) {
                MethodReference ref = ((NormalReturnCaller)s).getInstruction().getCallSite().getDeclaredTarget();
                String st = ref.getName().toString();
                System.out.println(st);
            }
             */
            return false;
        }
    };

    public static EndpointFinder swiftSinks = new EndpointFinder() {
        @Override
        public boolean endpoint(Statement s) {
            return false;
        }
    };


    public static Set<List<Statement>> getPaths(SDG<? extends InstanceKey> G, EndpointFinder sources, EndpointFinder sinks) {
        Set<List<Statement>> result = HashSetFactory.make();
        for(Statement src : G) {
            if (sources.endpoint(src)) {
                for(Statement dst : G) {
                    if (sinks.endpoint(dst)) {
                        BFSPathFinder<Statement> paths = new BFSPathFinder<Statement>(G, src, dst);
                        List<Statement> path = paths.find();
                        if (path != null) {
                            result.add(path);
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
                        SourceBuffer buf = new SourceBuffer(p);
                        System.out.println(buf + " (" + p + ")");
                    }
                    break;
                }
                case PARAM_CALLER: {
                    if (ast) {
                        CAstSourcePositionMap.Position p = ((AstMethod)m).getSourcePosition(((ParamCaller)s).getInstructionIndex());
                        SourceBuffer buf = new SourceBuffer(p);
                        System.out.println(buf + " (" + p + ")");
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
}