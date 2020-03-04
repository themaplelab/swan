//===--- AnnotationTester.java -------------------------------------------===//
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

package ca.maple.swan.swift.test;

import com.ibm.wala.cast.tree.CAstSourcePositionMap;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class AnnotationTester extends GenericTester {

    public AnnotationTester(String[] args) {
        super(args);
    }

    @Override
    protected void verifyAndReportResults(List<List<CAstSourcePositionMap.Position>> results) {

        boolean failed = false;

        try {
            for (List<CAstSourcePositionMap.Position> positions : results) {

                for (int p_idx = 0; p_idx < positions.size(); ++p_idx) {

                    CAstSourcePositionMap.Position pos = positions.get(p_idx);

                    String line = Files.readAllLines(Paths.get(pos.getURL().toURI().getRawPath())).get(pos.getFirstLine() - 1);

                    String patternStr;

                    if (p_idx == 0) { // Source
                        patternStr = "//source";
                    } else if (p_idx == positions.size() - 1) { // Sink
                        patternStr = "//sink";
                    } else { // Intermediate
                        patternStr = "//intermediate";
                    }

                    Pattern pattern = Pattern.compile(patternStr);
                    Matcher m = pattern.matcher(line);

                    if (!m.find()) {
                        System.err.println("Missing annotation: " + patternStr);
                        System.err.println(pos.getURL().toURI().getRawPath() + ":" + pos.getFirstLine());
                        System.err.println(line + "\n");
                        failed = true;
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        if (failed) {
            System.err.println("Failed test.");
            System.exit(1);
        } else {
            System.err.println("Test passed.");
            System.exit(0);
        }

    }

    public static void main(String[] args) throws IllegalArgumentException {

        new AnnotationTester(args);

    }
}
