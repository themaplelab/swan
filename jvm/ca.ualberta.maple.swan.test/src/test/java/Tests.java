/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

import ca.ualberta.maple.swan.drivers.DefaultDriver;
import ca.ualberta.maple.swan.ir.Error;
import ca.ualberta.maple.swan.ir.*;
import ca.ualberta.maple.swan.test.TestDriver;
import ca.ualberta.maple.swan.utils.Logging;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

public class Tests {

    // INPUT: SIL files
    // Smoke test and test parsers/printers parity
    // Full pipeline
    @Test
    void testSILModules() throws URISyntaxException {
        File fileDir = new File(Objects.requireNonNull(getClass().getClassLoader()
                .getResource("sil/modules/")).toURI());
        File[] silFiles = fileDir.listFiles((dir, name) -> name.endsWith(".sil"));
        assert silFiles != null;
        for (File sil : silFiles) {
            Logging.printInfo("(Tests) testSILModules: Testing " + sil.getName());
            TestUtils.silFileTestPipeline(sil);
            Logging.printInfo("PASS\n");
        }
    }

    // INPUT: Large SIL files
    // Smoke test (too large to do string comparisons)
    // Full pipeline
    @Test
    void testLargeSILModules() throws URISyntaxException {
        File fileDir = new File(Objects.requireNonNull(getClass().getClassLoader()
                .getResource("sil/large-modules/")).toURI());
        File[] silFiles = fileDir.listFiles((dir, name) -> name.endsWith(".sil"));
        assert silFiles != null;
        for (File sil : silFiles) {
            Logging.printInfo("(Tests) testLargeSILModules: Testing " + sil.getName());
            TestDriver.TestDriverOptions options = new TestDriver.TestDriverOptions();
            TestDriver.run(sil, options);
            Logging.printInfo("PASS\n");
        }
    }

    // INPUT: SWIRL files
    // Test printer parity with existing modules (for language regression)
    // Not that useful, though.
    @Test
    void testSWIRLModules() throws URISyntaxException, Error, IOException {
        File fileDir = new File(Objects.requireNonNull(getClass().getClassLoader()
                .getResource("swirl/modules/")).toURI());
        File[] swirlFiles = fileDir.listFiles((dir, name) -> name.endsWith(".swirl"));
        assert swirlFiles != null;
        for (File swirl : swirlFiles) {
            Logging.printInfo("(Tests) testSWIRLModules: Testing " + swirl.getName());
            String expected = TestUtils.readFileRegular(swirl);
            Module parsedModule = new SWIRLParser(swirl.toPath()).parseModule();
            SWIRLPrinterOptions opts = new SWIRLPrinterOptions();
            String result = new SWIRLPrinter().print(parsedModule, opts);
            Assertions.assertTrue(expected.equals(result));
            Logging.printInfo("PASS\n");
        }
    }

    // INPUT: Swift files
    // Dump SIL and smoke test and test parsers/printers parity
    // Same as testSILModules but with SIL dumping
    // Full pipeline
    @Test
    void testSwiftFiles() throws URISyntaxException {
        File fileDir = new File(Objects.requireNonNull(getClass().getClassLoader()
                .getResource("swift/")).toURI());
        for (File swift : Objects.requireNonNull(fileDir.listFiles((dir, name) -> name.endsWith(".swift")))) {
            Logging.printInfo("(Tests) testSwiftFiles: Testing " + swift.getName());
            File sil = TestUtils.dumpSILForSwiftFile(swift);
            TestUtils.silFileTestPipeline(sil);
            Logging.printInfo("PASS\n");
        }
    }

    // INPUT: swan-dir
    // Smoke test and also lightly verify that functions got merged
    @Test
    void testDefaultDriver() throws URISyntaxException {
        File fileDir = new File(Objects.requireNonNull(getClass().getClassLoader()
                .getResource("example-swan-dir/")).toURI());
        Logging.printInfo("(Tests) testDefaultDriver: Testing " + fileDir.getName());
        ModuleGroup group = DefaultDriver.run(fileDir);
        String result = new SWIRLPrinter().print(group, new SWIRLPrinterOptions());
        Assertions.assertTrue(result.contains("func [model] @`Swift.Array.subscript.getter"));
        Assertions.assertTrue(result.contains("func [linked] @`Sourceful.SyntaxTextView.colorTextView"));
        // System.out.println(result);
    }
}
