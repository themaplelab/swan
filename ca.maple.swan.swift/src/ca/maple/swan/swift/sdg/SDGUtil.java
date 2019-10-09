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

import com.ibm.wala.cast.tree.CAstSourcePositionMap;

import java.util.ArrayList;

public class SDGUtil {

    public static ArrayList<ArrayList<CAstSourcePositionMap.Position>> findSSSPaths(
            ArrayList<String> sources,
            ArrayList<String> sinks,
            ArrayList<String> sanitizers) {

        System.out.println("SDGUtil.findSSSPaths running with\n" +
                "Sources: " + sources + "\n" +
                "Sinks: " + sinks + "\n" +
                "Sanitizers: " + sanitizers);

        return new ArrayList<>(new ArrayList<>());
    }

}
