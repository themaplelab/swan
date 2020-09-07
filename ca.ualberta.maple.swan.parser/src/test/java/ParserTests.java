/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

import ca.ualberta.maple.swan.parser.*;
import ca.ualberta.maple.swan.parser.Error;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.io.*;
import java.net.URISyntaxException;

public class ParserTests {

    // This test parses every instruction in instructions.csv and checks that
    // what the SILPrinter outputs is consistent with the input. Every
    // instruction in the CSV must end with the '~' delimiter because
    // optionally a specific string to check against can be supplied.
    // The CSV can contain comments as long as they start with "#".
    @ParameterizedTest
    // Use '~' because it's never used in SIL (I think).
    // Although, it might be used in a string (e.g. string_literal).
    @CsvFileSource(resources = "sil/instructions.csv", delimiter = '~')
    void testSingleInstruction(String inst, String compareTo) throws Error {
        SILParser parser = new SILParser(inst);
        SILInstructionDef i = parser.parseInstructionDef();
        inst = doReplacements(inst);
        if (compareTo == null || compareTo.equals("")) {
            compareTo = PrintExtensions$.MODULE$.InstructionDefPrinter(i).description();
        }
        Assertions.assertEquals(inst, compareTo);
    }

    // Test demangler
    @Test
    void testFunctionDemangling() throws Error {
        String inst = "%32 = function_ref @$sSS10FoundationE19_bridgeToObjectiveCSo8NSStringCyF : $@convention(method) (@guaranteed String) -> @owned NSString, scope 901 // user: %33~";
        String demangledFunctionName = "(extension in Foundation):Swift.String._bridgeToObjectiveC() -> __C.NSString";
        SILParser parser = new SILParser(inst);
        SILInstructionDef i = parser.parseInstructionDef();
        Assertions.assertTrue(i.instruction() instanceof SILInstruction.operator);
        SILOperator op = ((SILInstruction.operator) i.instruction()).op();
        Assertions.assertTrue(op instanceof SILOperator.functionRef);
        Assertions.assertEquals(((SILOperator.functionRef) op).name().demangled(), demangledFunctionName);
    }

    // Test parsing whole function
    @Test
    void testFunctionParsing() throws Error, NullPointerException, URISyntaxException {
        File testFile = new File(getClass().getClassLoader()
                .getResource("sil/function1.sil").toURI());
        SILParser parser = new SILParser(testFile.toPath());
        SILFunction function = parser.parseFunction();
        Assertions.assertTrue(true); // Just check we don't blow up for now
    }

    // Test parsing witness table
    @Test
    void testWitnessTableParsing() throws Error, NullPointerException, URISyntaxException, IOException {
        String testFilePath = "sil/witness-table1.sil";
        File testFile = new File(getClass().getClassLoader()
                .getResource(testFilePath).toURI());
        SILParser parser = new SILParser(testFile.toPath());
        SILWitnessTable table = parser.parseWitnessTable();
        String parsedTable = PrintExtensions$.MODULE$.WitnessTablePrinter(table).description();
        String expected = readFile(testFile);
        Assertions.assertEquals(expected, parsedTable);
    }

    // Test that the sym-linked swan-swiftc tool doesn't blow up.
    @Test
    void getSILUsingSwanSwiftc() throws IOException, URISyntaxException, InterruptedException {
        String testFilePath = "swift/ArrayAccess1.swift";
        File testFile = new File(getClass().getClassLoader()
                .getResource(testFilePath).toURI());
        File swanSwiftcFile = new File(getClass().getClassLoader()
                .getResource("symlink-utils/swan-swiftc").toURI());
        Process p = Runtime.getRuntime().exec("python " + swanSwiftcFile.getAbsolutePath() + " " + testFile.getAbsolutePath());
        p.waitFor();
        // Check exit code for now
        Assertions.assertEquals(p.exitValue(), 0);
        readFile(testFile);
    }

    // This test uses swan-xcodebuild to generate SIL for all xcodeprojects.
    // The format of the csv is
    // <xcodeproj_path>, <scheme>, <optional_xcodebuild_args>
    // The CSV can contain comments as long as they start with "#".
    // TODO: Separate into slow test suite.
    @ParameterizedTest
    @CsvFileSource(resources = "xcodeproj/projects.csv")
    void getSILForAllXcodeProjects(String xcodeproj, String scheme, String optionalArgs) throws URISyntaxException, IOException {
        String projectPath = "xcodeproj/" + xcodeproj;
        File testProjectFile = new File(getClass().getClassLoader()
                .getResource(projectPath).toURI());
        File swanXcodebuildFile = new File(getClass().getClassLoader()
                .getResource("symlink-utils/swan-xcodebuild").toURI());
        ProcessBuilder pb = new ProcessBuilder("python",
                swanXcodebuildFile.getAbsolutePath(),
                "-project", testProjectFile.getAbsolutePath(), "-scheme", scheme,
                optionalArgs != null ? optionalArgs : "");
        pb.inheritIO();
        Process p = null;
        try {
            p = pb.start();
            p.waitFor();
        } catch (InterruptedException e) {
            p.destroy();
        }
        // Check exit code for now
        Assertions.assertEquals(p.exitValue(), 0);

        // Iterate through SIL files
        File silDir = new File(testProjectFile.getParentFile().getAbsoluteFile() + "/sil/");
        Assertions.assertTrue(silDir.exists());
        File[] silFiles = silDir.listFiles();
        Assertions.assertNotEquals(null, silFiles);
        for (File sil : silFiles) {
            readFile(sil);
        }
    }

    // Account for any known transformations that the parser does,
    // such as superficial type conversions, here.
    String doReplacements(String inst) {
        inst = inst.replaceAll("\\[Int\\]", "Array<Int>");
        // Not sure why this .1 appears in practice after "enumelt". Doesn't seem
        // necessary.
        inst = inst.split("//")[0];
        inst = inst.replaceAll("\\s+$", ""); // right trim
        return inst;
    }

    String readFile(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        assert in != null;
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder result = new StringBuilder();
        String line;
        while((line = reader.readLine()) != null) {
            // Empty line, preserve empty lines
            if (line.trim().isEmpty()) {
                result.append(System.lineSeparator());
                continue;
            }
            line = doReplacements(line);
            // For commented out lines
            if (line.trim().length() > 0) {
                result.append(line);
                result.append(System.lineSeparator());
            }
        }
        return result.toString();
    }
}
