/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

import ca.ualberta.maple.swan.ir.*;
import ca.ualberta.maple.swan.ir.canonical.SWIRLPass;
import ca.ualberta.maple.swan.ir.raw.SWIRLGen.Context;
import ca.ualberta.maple.swan.ir.raw.SWIRLGen;
import ca.ualberta.maple.swan.parser.*;
import ca.ualberta.maple.swan.parser.Error;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class TranslationTests {

    static boolean DEBUG = true;

    /* CSV based instruction-level tests.
     * Format: sil-instruction '~' swirl-instruction ( ":::" swirl-instruction )*
     * For _Raw_ SWIRL.
     */
    @ParameterizedTest
    @CsvFileSource(resources = "instructions.csv", delimiter = '~')
    void testInstructions(String silInst, String swirlInstructions) throws Error, Exceptions.UnexpectedSILFormatException,
            Exceptions.IncorrectSWIRLStructureException, Exceptions.UnexpectedSILTypeBehaviourException {
        assumeTrue(swirlInstructions != null && !swirlInstructions.isEmpty());
        if (DEBUG) System.out.println(silInst);
        SILParser parser = new SILParser(silInst);
        SILInstructionDef instruction = parser.parseInstructionDef();
        if (swirlInstructions.trim().equals("NOP")) {
            if (DEBUG) System.out.println("  -> NOP");
            return;
        }
        // Translate after checking NOP because certain instructions require
        // non-null context elements for translation. e.g. struct
        RawInstructionDef[] swirl = SWIRLGen.translateSILInstruction(
                instruction, new Context(createEmptyModule(), null, null,
                        scala.Option.apply(null), new RefTable(), new scala.collection.mutable.HashSet<String>(), null, null));
        if (swirl == null) {
            return;
        }
        String[] compareTo = swirlInstructions.split(":::");
        Assertions.assertEquals(swirl.length, compareTo.length);
        try {
            for (int i = 0; i < swirl.length; ++i) {
                String actual = new SWIRLPrinter().print(swirl[i]);
                if (DEBUG) System.out.println("  -> " + actual); // DEBUG
                Assertions.assertEquals(compareTo[i].trim(), actual.trim());
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    @Test
    // Just smoke test for now.
    void testModuleParsing() throws Error, URISyntaxException, Exceptions.IncorrectSWIRLStructureException,
            Exceptions.UnexpectedSILFormatException, Exceptions.UnexpectedSILTypeBehaviourException {
        System.out.println("Testing modules");
        File fileDir = new File(getClass().getClassLoader()
                .getResource("sil/modules/").toURI());
        File[] silFiles = fileDir.listFiles((dir, name) -> name.endsWith("test.sil"));
        for (File sil : silFiles) {
            System.out.println("    -> " + sil.getName());
            SILParser parser = new SILParser(sil.toPath());
            SILModule silModule = parser.parseModule();
            // System.out.println(new SILPrinter().print(silModule));
            // System.out.println("============================================");
            Module swirlModule = SWIRLGen.translateSILModule(silModule);
            System.out.print(new SWIRLPrinter().print(swirlModule, new SWIRLPrinterOptions()));
            // System.out.println("============================================");
            CanModule canSwirlModule = SWIRLPass.runPasses(swirlModule);
            // System.out.print(new SWIRLPrinter().print(canSwirlModule, new SWIRLPrinterOptions()));
        }
    }

    @ParameterizedTest
    @Disabled
    @CsvFileSource(resources = "xcodeproj/projects.csv")
    void getSILForAllXcodeProjects(String xcodeproj, String scheme, String optionalArgs) throws URISyntaxException,
            IOException, Error, Exceptions.IncorrectSWIRLStructureException,
            Exceptions.UnexpectedSILFormatException, Exceptions.UnexpectedSILTypeBehaviourException {
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
            e.printStackTrace();
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
            System.out.println("    -> " + sil.getName());
            SILParser parser = new SILParser(sil.toPath());
            SILModule silModule = parser.parseModule();
            Module swirlModule = SWIRLGen.translateSILModule(silModule);
            System.out.print(new SWIRLPrinter().print(swirlModule, new SWIRLPrinterOptions()));
        }
    }

    SILModule createEmptyModule() {
        SILFunction[] functions = {};
        SILWitnessTable[] witnessTables = {};
        SILVTable[] silvTables = {};
        String[] imports = {};
        SILGlobalVariable[] globalVariables = {};
        SILScope[] scopes = {};
        SILProperty[] properties = {};
        StructInit[] inits = StructInit.populateInits();
        return new SILModule(functions, witnessTables, silvTables,
                imports, globalVariables, scopes, properties, inits);
    }

}
