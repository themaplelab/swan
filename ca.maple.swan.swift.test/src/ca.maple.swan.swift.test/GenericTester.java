//===--- GenericTester.java ----------------------------------------------===//
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

import ca.maple.swan.swift.server.SwiftAnalysisEngineServerDriver;
import ca.maple.swan.swift.translator.Settings.Mode;
import ca.maple.swan.swift.taint.TaintAnalysisDriver;
import ca.maple.swan.swift.translator.sil.RawData;
import ca.maple.swan.swift.translator.Settings;
import ca.maple.swan.swift.translator.spds.SwiftToSPDSTranslator;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.slicer.SDG;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


public class GenericTester extends Tester {

    public GenericTester(String[] args) {
        super();

        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        doTest(ns);
    }

    @Override
    protected void doTest(Namespace ns) {

        SDG<InstanceKey> sdg = null;

        // COMPILE

        try {

            Settings.mode = (ns.get("engine").equals("WALA")) ? Mode.WALA : Mode.SPDS;

            if (Settings.mode == Mode.WALA) {
                System.out.println("WALA Mode, Generating SDG (includes compilation)...");
                sdg = SwiftAnalysisEngineServerDriver.generateSDG(((String)ns.get("swiftc_args")).split(" "));
                System.out.println("Done generating SDG");
            } else if (Settings.mode == Mode.SPDS) {
                System.out.println("SPDS Mode, only translating to SILIR for now");
                RawData data = new RawData(ns.get("swiftc_args"), new CAstImpl());
                data.setup();
                SwiftToSPDSTranslator translator = new SwiftToSPDSTranslator(data);
                translator.translateToProgramContext();
            }
        } catch (Exception e) {
            System.err.println("Could not translate");
            e.printStackTrace();
            System.exit(1);
        }

        // RUN TAINT ANALYSIS

        try {
            if (Settings.mode.equals(Mode.WALA)) {
                System.out.println("Running taint analysis...");

                List<List<CAstSourcePositionMap.Position>> paths = TaintAnalysisDriver.doTaintAnalysis(
                        sdg,
                        ((ArrayList<String>)ns.get("sources")).toArray(new String[0]),
                        ((ArrayList<String>)ns.get("sinks")).toArray(new String[0]),
                        ((ArrayList<String>)ns.get("sanitizers")).toArray(new String[0])
                );

                System.out.println("Checking taint analysis results...\n");
                verifyAndReportResults(paths);
            } else {
                System.out.println("SPDS mode, no taint analysis for now");
                System.exit(0);
            }
        } catch (Exception e) {
            System.err.println("Taint analysis failed");
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    protected void verifyAndReportResults(List<List<CAstSourcePositionMap.Position>> results) {

        System.out.println("\n========= RESULTS =========");
        for (List<CAstSourcePositionMap.Position> path : results) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n-- PATH\n");
            try {
                for (int p_idx = 0; p_idx < path.size(); ++p_idx) {

                    CAstSourcePositionMap.Position pos = path.get(p_idx);

                    String line = Files.readAllLines(Paths.get(pos.getURL().toURI().getRawPath())).get(pos.getFirstLine() - 1);

                    sb.append("   ");

                    if (p_idx == 0) { // Source
                        sb.append("-- SOURCE");
                    } else if (p_idx == path.size() - 1) { // Sink
                        sb.append("-- SINK");
                    } else { // Intermediate
                        sb.append("-- INTERMEDIATE");
                    }

                    sb.append("\n      ");
                    sb.append(pos.getFirstLine());
                    sb.append(":");
                    sb.append(pos.getFirstCol());
                    sb.append(" in ");
                    sb.append(pos.getURL().toURI().getRawPath());
                    sb.append("\n      ");
                    int count = line.indexOf(line.trim());
                    sb.append(line.trim());
                    sb.append("\n      ");
                    for (int i = 0; i < pos.getFirstCol() - count - 1; i++) {
                        sb.append(" ");
                    }
                    sb.append("^\n");
                    System.out.print(sb.toString());
                    sb = new StringBuilder();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.out.println("\n====== END OF RESULTS ======");
    }

    @Override
    protected ArgumentParser configureParser() {
        ArgumentParser parser = ArgumentParsers.newFor("Tester").build();

        parser.addArgument( "-engine")
                .choices("WALA", "SPDS")
                .setDefault("WALA")
                .required(false);

        parser.addArgument("-swiftc-args").required(true).type(String.class);

        parser.addArgument("-sources").nargs("*").setDefault(new ArrayList<String>());

        parser.addArgument("-sinks").nargs("*").setDefault(new ArrayList<String>());

        parser.addArgument("-sanitizers").nargs("*").setDefault(new ArrayList<String>());

        return parser;
    }

    public static void main(String[] args) throws IllegalArgumentException {

        new GenericTester(args);

    }
}
