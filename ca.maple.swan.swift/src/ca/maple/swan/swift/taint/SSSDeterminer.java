//===--- SSSDeterminer.java ----------------------------------------------===//
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

import com.ibm.wala.cast.loader.CAstAbstractModuleLoader;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.slicer.NormalReturnCaller;
import com.ibm.wala.ipa.slicer.Statement;

import java.util.HashSet;
import java.util.Set;

import static com.ibm.wala.ipa.slicer.Statement.Kind.NORMAL_RET_CALLER;

/*
 * Methods for determining if a statement is a Source, Sink, or Sanitizer.
 */

public class SSSDeterminer {

    // TODO: Add way of comparing argument position and configuration (e.g. JSON format that Phasar uses).

    public static boolean checkSource(HashSet<String> sources, Statement s, CallGraph CG) {
        if (s.getKind() == NORMAL_RET_CALLER) {
            CallSiteReference cs = ((NormalReturnCaller) s).getInstruction().getCallSite();
            CGNode node = s.getNode();
            Set<CGNode> it = CG.getPossibleTargets(node, cs);
            for (CGNode target : it) {
                CAstAbstractModuleLoader.DynamicMethodObject m = (CAstAbstractModuleLoader.DynamicMethodObject) target.getMethod();
                if (sources.contains(m.getEntity().getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean checkSanitizer(HashSet<String> sanitizers, Statement s, CallGraph CG) {
        // TODO:

        return false;
    }

    public static boolean checkSink(HashSet<String> sinks, Statement s, CallGraph CG) {
        if (s.getKind()== Statement.Kind.PARAM_CALLEE) {
            CAstAbstractModuleLoader.DynamicMethodObject m = (CAstAbstractModuleLoader.DynamicMethodObject) s.getNode().getMethod();
            String ref = m.getEntity().getName();
            return sinks.contains(ref);
        }
        return false;
    }
}
