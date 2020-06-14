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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class AnnotationTester extends GenericTester {

    public AnnotationTester(String[] args) {
        super(args);
    }

    @Override
    protected void verifyAndReportResults(List<List<CAstSourcePositionMap.Position>> results, String testFile) {

        System.out.println("Checking taint analysis annotations...\n");

        boolean failed = false;

        HashMap<String, ArrayList<ArrayList<String>>> annotations = getAnnotations(results, testFile);

        System.err.println();

        try {
            for (List<CAstSourcePositionMap.Position> positions : results) {

                int source_idx = -1;

                for (int p_idx = 0; p_idx < positions.size(); ++p_idx) {

                    CAstSourcePositionMap.Position pos = positions.get(p_idx);

                    String requiredAnnotation;

                    if (p_idx == 0) { // Source
                        source_idx = pos.getFirstLine();
                        requiredAnnotation = "//source:" + source_idx;
                    } else if (p_idx == positions.size() - 1) { // Sink
                        requiredAnnotation = "//sink:" + source_idx;
                    } else { // Intermediate
                        requiredAnnotation = "//intermediate:" + source_idx;
                    }

                    boolean foundAnnotation = false;
                    String file = pos.getURL().toURI().getRawPath();
                    String line = Files.readAllLines(Paths.get(file)).get(pos.getFirstLine() - 1);
                    ArrayList<String> annotationsForLine = annotations.get(file).get(pos.getFirstLine() - 1);
                    for (String annotation : annotationsForLine) {
                        if (annotation.equals(requiredAnnotation)) {
                            foundAnnotation = true;
                            annotationsForLine.remove(requiredAnnotation);
                            break;
                        }
                    }

                    if (!foundAnnotation) {
                        System.err.println("Missing annotation: " + requiredAnnotation);
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

        // Check remaining annotations
        for (String fileKey : annotations.keySet()) {
            for (int line_idx = 0; line_idx < annotations.get(fileKey).size(); ++line_idx) {
            ArrayList<String> lines = annotations.get(fileKey).get(line_idx);
                for (int idx = 0; idx < lines.size(); ++idx) {
                    try {
                        System.err.println("Annotation with no matching path: " + lines.get(idx));
                        System.err.println(fileKey + ":" + Integer.toString(line_idx + 1));
                        String line = Files.readAllLines(Paths.get(fileKey)).get(line_idx);
                        System.err.println(line + "\n");
                        failed = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                }
            }
        }

        if (failed) {
            System.err.println("Failed test.");
            System.exit(1);
        } else {
            System.err.println("Test passed.");
            System.exit(0);
        }

    }

    // Mapping of Filename to ArrayList (len == |file numbers|)
    // of ArrayList<String> which are the annotations
    // probably could be done more efficiently.
    protected HashMap<String, ArrayList<ArrayList<String>>> getAnnotations(List<List<CAstSourcePositionMap.Position>> results, String testFile) {
        HashMap<String, ArrayList<ArrayList<String>>> map = new HashMap<>();

        map.put(testFile, new ArrayList<>());

        // Populate an empty map
        for (List<CAstSourcePositionMap.Position> path : results) {
            for (CAstSourcePositionMap.Position node : path) {
                try {
                    String file = node.getURL().toURI().getRawPath();
                    if (!map.containsKey(file)) {
                        ArrayList<ArrayList<String>> annotations = new ArrayList<>();
                        map.put(file, annotations);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }

            }
        }

        // Read annotations in
        for (String fileKey : map.keySet()) {
            try {
                List<String> lines = Files.readAllLines(Paths.get(fileKey));
                // First add an empty ArrayList for every line.
                for (int line_idx = 0; line_idx < lines.size(); ++line_idx) {
                    map.get(fileKey).add(line_idx, new ArrayList<>());
                }
                for (int line_idx = 0; line_idx < lines.size(); ++line_idx) {
                    String line = lines.get(line_idx);
                    String[] patterns = {"(//source:[0-9]+)", "(//intermediate:[0-9]+)", "(//sink:[0-9]+)"};
                    for (String pattern : patterns) {
                        Pattern p = Pattern.compile(pattern);
                        Matcher m = p.matcher(line);
                        while (m.find()) {
                            map.get(fileKey).get(line_idx).add(m.group());
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        return map;
    }

    public static void printAnnotations(HashMap<String, ArrayList<ArrayList<String>>> map) {
        for (String fileKey : map.keySet()) {
            System.out.println();
            for (ArrayList<String> lines : map.get(fileKey)) {
                StringBuilder sb = new StringBuilder();
                sb.append("[");
                for (int idx = 0; idx < lines.size(); ++idx) {
                    sb.append(lines.get(idx));
                    if (idx != lines.size() - 1) {
                        sb.append(", ");
                    }
                }
                sb.append("]\n");
                System.out.print(sb.toString());
            }
        }
    }

    public static void main(String[] args) throws IllegalArgumentException {

        new AnnotationTester(args);

    }
}
