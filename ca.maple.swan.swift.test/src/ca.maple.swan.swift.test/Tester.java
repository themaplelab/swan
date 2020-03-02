package ca.maple.swan.swift.test;

import ca.maple.swan.swift.server.SwiftAnalysisEngineServerDriver;
import ca.maple.swan.swift.server.Server.Mode;
import ca.maple.swan.swift.taint.TaintAnalysisDriver;
import ca.maple.swan.swift.translator.RawData;
import ca.maple.swan.swift.translator.spds.SwiftToSPDSTranslator;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.slicer.SDG;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.action.StoreTrueArgumentAction;
import net.sourceforge.argparse4j.inf.ArgumentAction;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Tester {

    public static void main(String[] args) throws IllegalArgumentException {

        ArgumentParser parser = configureParser();

        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        Mode mode = Mode.WALA;
        SDG<InstanceKey> sdg = null;

        // COMPILE

        try {

            mode = (ns.get("engine").equals("WALA")) ? Mode.WALA : Mode.SPDS;

            if (mode == Mode.WALA) {
                System.out.println("WALA Mode, Generating SDG (includes compilation)...");
                sdg = SwiftAnalysisEngineServerDriver.generateSDG(((String)ns.get("swiftc_args")).split(" "));
                System.out.println("Done generating SDG");
            } else if (mode == Mode.SPDS) {
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
            if (mode.equals(Mode.WALA)) {
                System.out.println("Running taint analysis...");

                List<List<CAstSourcePositionMap.Position>> paths = TaintAnalysisDriver.doTaintAnalysis(
                        sdg,
                        ((ArrayList<String>)ns.get("sources")).toArray(new String[0]),
                        ((ArrayList<String>)ns.get("sinks")).toArray(new String[0]),
                        ((ArrayList<String>)ns.get("sanitizers")).toArray(new String[0])
                );

                System.out.println("Checking taint analysis results...\n");
                verifyResults(paths);
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

    private static void verifyResults(List<List<CAstSourcePositionMap.Position>> results) {

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

    private static ArgumentParser configureParser() {
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
}
