//===--- TaintPathRecorder.java ------------------------------------------===//
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
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.slicer.*;
import com.ibm.wala.util.graph.Graph;

import java.util.*;

public class TaintPathRecorder {

    private static HashMap<Statement, LinkedList<Statement>> sourcesAndTargets = new HashMap<>();

    public static void recordPath(ArrayList<Statement> sources, Statement target) {
        for (Statement src : sources) {
            sourcesAndTargets.computeIfAbsent(src, (k) ->
                    new LinkedList<>()
            );
            sourcesAndTargets.get(src).add(target);
        }
    }

    public static Set<Statement> getSources() {
        return sourcesAndTargets.keySet();
    }

    public static List<Statement> getTargets(Statement src) {
        return sourcesAndTargets.get(src);
    }

    public static void clear() {
        sourcesAndTargets.clear();
    }

    public static CAstSourcePositionMap.Position getPositionFromStatement(Statement s) {
        IMethod m = s.getNode().getMethod();
        switch (s.getKind()) {
            case NORMAL:
                return ((AstMethod) m).getSourcePosition(((NormalStatement) s).getInstructionIndex());
            case PHI:
                break;
            case PI:
                break;
            case CATCH:
                break;
            case PARAM_CALLER:
                return ((AstMethod) m).getSourcePosition(((ParamCaller) s).getInstructionIndex());
            case PARAM_CALLEE:
                return ((AstMethod) m).getSourcePosition();
            case NORMAL_RET_CALLER:
                return ((AstMethod) m).getSourcePosition(((NormalReturnCaller)s).getInstructionIndex());
            case NORMAL_RET_CALLEE:
                break;
            case EXC_RET_CALLER:
                break;
            case EXC_RET_CALLEE:
                break;
            case HEAP_PARAM_CALLER:
                break;
            case HEAP_PARAM_CALLEE:
                break;
            case HEAP_RET_CALLER:
                break;
            case HEAP_RET_CALLEE:
                break;
            case METHOD_ENTRY:
                break;
            case METHOD_EXIT:
                break;
        }
        return null;
    }

    public static List<CAstSourcePositionMap.Position> getPositionsFromStatements(List<Statement> statements) {
        // TODO: Add source function itself to the beginning of the path.
        List<CAstSourcePositionMap.Position> path = new ArrayList<>();
        if (statements == null) {
            return path;
        }
        Set<Integer> seenLines = new HashSet<>();
        for (Statement s : statements) {
            CAstSourcePositionMap.Position p = null;
            IMethod m = s.getNode().getMethod();
            boolean ast = m instanceof AstMethod;
            if (ast) {
                p = getPositionFromStatement(s);
            }
            if (p != null) {
                if (p.getFirstCol() != p.getLastCol() || p.getFirstLine() != p.getLastLine()) {
                    if (!path.isEmpty() && !(path.get(0).equals(p))) {
                        if (!(p.getFirstLine() == p.getLastLine() && seenLines.contains(p.getFirstLine()))) {
                            path.add(0, p);
                        }
                        seenLines.add(p.getFirstLine());
                    } else if (path.isEmpty()) {
                        path.add(0, p);
                        seenLines.add(p.getFirstLine());
                    }
                }
            }
        }
        return path;
    }

    public static List<List<CAstSourcePositionMap.Position>> getPaths(Graph<Statement> g, TaintSolver s) {
        ArrayList<List<CAstSourcePositionMap.Position>> paths = new ArrayList<>();
        for (Statement src : getSources()) {
            for (Statement target : getTargets(src)) {
                Iterator<Statement> preds = g.getPredNodes(target);
                while (preds.hasNext()) {
                    Statement t = preds.next();
                    TaintBFSPathFinder finder = new TaintBFSPathFinder(g, src, t, s);
                    List<CAstSourcePositionMap.Position> path = getPositionsFromStatements(finder.find());
                    if (!path.isEmpty()) {
                        paths.add(path);
                    }
                }
            }
        }
        return paths;
    }

}
