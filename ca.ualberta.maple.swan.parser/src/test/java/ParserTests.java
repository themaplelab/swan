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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.io.*;
import java.net.URISyntaxException;

public class ParserTests {

    // TESTS:
    // 1. Module level (.sil files in directory)
    // 2. Module level DONT COMPARE (large files) (.sil files in directory)
    // 3. Function level (.sil files in directory)
    // 4. Instruction level (.sil files in directory)
    // 5. witness tables (.sil files in directory)
    // 6. v tables (.sil files in directory)
    // 7. global variables (.sil files in directory)
    // 8. Swift files (use swan-swiftc) (.swift files in directory)
    // 9. iOS xcodeproj (use swan-xcodebuild)
    // (.xcodeproj files in directory, necessary build options in CSV file)
    // 10. Demangler test (to make sure the demangler works at all)


    // 1. Module level (.sil files in directory)
    // Each .sil file must contain a module.
    @Test
    void testModuleParsing() throws Error, URISyntaxException, IOException {
        System.out.println("Testing modules");
        File fileDir = new File(getClass().getClassLoader()
                .getResource("sil/modules/").toURI());
        File[] silFiles = fileDir.listFiles((dir, name) -> name.endsWith(".sil"));
        for (File sil : silFiles) {
            System.out.println("    -> " + sil.getName());
            String expected = TestUtils.readFile(sil);
            SILParser parser = new SILParser(sil.toPath());
            SILModule module = parser.parseModule();
            String result = parser.print(module);
            // TODO: assertEquals dumps XML to terminal. Not sure why.
            Assertions.assertTrue(expected.equals(result));
        }
    }

    // 2. Module level DONT COMPARE (large files) (.sil files in directory)
    // Each .sil file must contain a module.
    // Don't compare because printing + comparing + modifying expected
    // is far too expensive for files that are 200k lines.
    @Test
    void testModuleParsingDontCompare() throws Error, URISyntaxException, IOException {
        System.out.println("Testing modules");
        File fileDir = new File(getClass().getClassLoader()
                .getResource("sil/modules-dont-compare/").toURI());
        File[] silFiles = fileDir.listFiles((dir, name) -> name.endsWith(".sil"));
        for (File sil : silFiles) {
            System.out.println("    -> " + sil.getName());
            SILParser parser = new SILParser(sil.toPath());
            SILModule module = parser.parseModule();
        }
    }

    // 3. Function level (.sil files in directory)
    // Each .sil file must contain a single function.
    @Test
    void testFunctionParsing() throws Error, URISyntaxException, IOException {
        System.out.println("Testing functions");
        File fileDir = new File(getClass().getClassLoader()
                .getResource("sil/functions/").toURI());
        File[] silFiles = fileDir.listFiles();
        for (File sil : silFiles) {
            System.out.println("    -> " + sil.getName());
            String expected = TestUtils.readFile(sil);
            SILParser parser = new SILParser(sil.toPath());
            String result = parser.print(parser.parseFunction());
            Assertions.assertEquals(expected, result);
        }
    }

    // 4. Instruction level (.sil files in directory)
    // Each line in the .sil files is considered individually as an
    // instruction. Empty commented/empty lines are ignored.
    @Test
    void testInstructionParsing() throws Error, URISyntaxException, IOException {
        System.out.println("Testing instructions");
        File fileDir = new File(getClass().getClassLoader()
                .getResource("sil/instructions/").toURI());
        File[] silFiles = fileDir.listFiles();
        for (File sil : silFiles) {
            System.out.println("    -> " + sil.getName());
            String instructionsFileBody = TestUtils.readFile(sil, false);
            String[] instructions = instructionsFileBody.split("\n");
            for (String instruction : instructions) {
                SILParser parser = new SILParser(instruction);
                String result = parser.print(parser.parseInstructionDef());
                Assertions.assertEquals(TestUtils.doReplacements(instruction), result);
            }
        }
    }

    // 5. witness tables (.sil files in directory)
    // Each .sil file must contain a single witness table.
    @Test
    void testWitnessTableParsing() throws Error, URISyntaxException, IOException {
        System.out.println("Testing witness tables");
        File fileDir = new File(getClass().getClassLoader()
                .getResource("sil/witness-tables/").toURI());
        File[] silFiles = fileDir.listFiles();
        for (File sil : silFiles) {
            System.out.println("    -> " + sil.getName());
            String expected = TestUtils.readFile(sil);
            SILParser parser = new SILParser(sil.toPath());
            String result = parser.print(parser.parseWitnessTable());
            Assertions.assertEquals(expected, result);
        }
    }

    // 6. v tables (.sil files in directory)
    // Files are parsed as module so that multiple sil_global's can
    // be tested.
    @Test
    void testVTableParsing() throws Error, URISyntaxException, IOException {
        System.out.println("Testing v tables");
        File fileDir = new File(getClass().getClassLoader()
                .getResource("sil/v-tables/").toURI());
        File[] silFiles = fileDir.listFiles();
        for (File sil : silFiles) {
            System.out.println("    -> " + sil.getName());
            String expected = TestUtils.readFile(sil);
            SILParser parser = new SILParser(sil.toPath());
            String result = parser.print(parser.parseModule());
            // Remove excess newlines
            result = result.replaceAll("\n\n\n", "\n\n");
            expected = expected.replaceAll("\n\n\n", "\n\n");
            expected = expected.trim() + "\n";
            result = result.trim() + "\n";
            Assertions.assertEquals(expected, result);
        }
    }

    // 7. global variables (.sil files in directory)
    // Files are parsed as module so that multiple sil_global's can
    // be tested.
    @Test
    void testGlobalVariableParsing() throws Error, URISyntaxException, IOException {
        System.out.println("Testing global variables");
        File fileDir = new File(getClass().getClassLoader()
                .getResource("sil/global-variables/").toURI());
        File[] silFiles = fileDir.listFiles();
        for (File sil : silFiles) {
            System.out.println("    -> " + sil.getName());
            String expected = TestUtils.readFile(sil);
            SILParser parser = new SILParser(sil.toPath());
            String result = parser.print(parser.parseModule());
            // Remove excess newlines
            result = result.replaceAll("\n\n\n", "\n\n");
            expected = expected.replaceAll("\n\n\n", "\n\n");
            expected = expected.trim() + "\n";
            result = result.trim() + "\n";
            Assertions.assertEquals(expected, result);
        }
    }

    // 8. Swift files (use swan-swiftc) (.swift files in directory)
    // NOTE: If these files import libraries, `-sdk` will probably be needed
    @Test
    void testSwiftSingleFileParsing() throws Error, URISyntaxException, IOException {
        System.out.println("Testing Swift single files");
        File fileDir = new File(getClass().getClassLoader()
                .getResource("swift/single/").toURI());
        File[] swiftFiles = fileDir.listFiles();
        for (File swift : swiftFiles) {
            if (!swift.getAbsolutePath().endsWith(".swift")) {
                continue;
            }
            System.out.println("    -> " + swift.getName());
            File swiftc = new File(getClass().getClassLoader()
                    .getResource("symlink-utils/swan-swiftc").toURI());
            Assertions.assertTrue(swiftc.exists());
            // Generate SIL
            ProcessBuilder pb = new ProcessBuilder("python",
                    swiftc.getAbsolutePath(),
                    swift.getAbsolutePath());
            pb.inheritIO();
            Process p = null;
            try {
                p = pb.start();
                p.waitFor();
            } catch (InterruptedException e) {
                p.destroy();
            }
            Assertions.assertEquals(p.exitValue(), 0);
            File sil = new File(swift.getAbsolutePath() + ".sil");
            Assertions.assertTrue(sil.exists());
            String expected = TestUtils.readFile(sil);
            SILParser parser = new SILParser(sil.toPath());
            SILModule module = parser.parseModule();
            String result = parser.print(module);
            // Remove excess newlines
            expected = expected.trim() + "\n";
            result = result.trim() + "\n";
            Assertions.assertEquals(expected, result);
        }
    }

    // 9. iOS xcodeproj (use swan-xcodebuild)
    // This test uses swan-xcodebuild to generate SIL for all xcodeprojects.
    // The format of the csv is
    // <xcodeproj_path>, <scheme>, <optional_xcodebuild_args>
    // The CSV can contain comments as long as they start with "#".
    @ParameterizedTest
    @Disabled
    @CsvFileSource(resources = "xcodeproj/projects.csv")
    void getSILForAllXcodeProjects(String xcodeproj, String scheme, String optionalArgs) throws URISyntaxException, IOException, Error {
        System.out.println("Testing " + xcodeproj);
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
        // Assertions.assertEquals(p.exitValue(), 0);

        // Iterate through SIL files
        File silDir = new File(testProjectFile.getParentFile().getAbsoluteFile() + "/sil/");
        Assertions.assertTrue(silDir.exists());
        File[] silFiles = silDir.listFiles();
        Assertions.assertNotEquals(null, silFiles);
        for (File sil : silFiles) {
            System.out.println("    -> " + sil.getName());
            String expected = TestUtils.readFile(sil);
            SILParser parser = new SILParser(sil.toPath());
            String result = parser.print(parser.parseModule());
            expected = expected.trim() + "\n";
            result = result.trim() + "\n";
            Assertions.assertTrue(expected.equals(result));
        }
    }

    // 10. Demangler test
    @Test
    void testFunctionDemangling() {
        String mangledFunctionName = "$sSS10FoundationE19_bridgeToObjectiveCSo8NSStringCyF";
        String demangledFunctionName = "(extension in Foundation):Swift.String._bridgeToObjectiveC() -> __C.NSString";
        SILMangledName name = new SILMangledName(mangledFunctionName);
        Assertions.assertEquals(demangledFunctionName, name.demangled());
    }
}
