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
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.slicer.*;

import java.util.*;

public class TaintAnalysis {

    public static ArrayList<ArrayList<CAstSourcePositionMap.Position>> doTaintAnalysis(
            SDG<InstanceKey> sdg,
            String[] sources,
            String[] sinks,
            String[] sanitizers) {

        // TODO: Combine with known SSS here.

        return SDGUtil.findSSSPaths(
                sdg,
                new HashSet<String>(Arrays.asList(sources)),
                new HashSet<String>(Arrays.asList(sinks)),
                new HashSet<String>(Arrays.asList(sanitizers))
        );
    }
}