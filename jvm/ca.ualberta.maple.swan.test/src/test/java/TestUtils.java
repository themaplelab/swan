/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2021 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

import ca.ualberta.maple.swan.ir.Error;
import ca.ualberta.maple.swan.ir.*;
import ca.ualberta.maple.swan.parser.SILModule;
import ca.ualberta.maple.swan.parser.SILPrinter;
import ca.ualberta.maple.swan.parser.SILPrinterOptions;
import ca.ualberta.maple.swan.test.TestDriver;
import ca.ualberta.maple.swan.utils.Logging;
import org.junit.jupiter.api.Assertions;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;

public class TestUtils {

    // For now, do a bunch of janky string manipulations to make the output
    // match the expected. Should probably (later) write a custom comparator
    // function that doesn't report inequality due to things like extra
    // newlines.

    // Account for any known transformations that the parser does,
    // such as superficial type conversions, here.
    static String doReplacements(String line) {
        //inst = inst.replaceAll("\\[([a-zA-Z0-9]+)\\]", "Array<$1>");
        if (line.startsWith("func ")) {
            return "";
        }
        if (line.startsWith("@_hasStorage")) {
            return "";
        }
        if (line.startsWith("typealias")) {
            return "";
        }
        if (line.contains("{ get set }")) {
            return "";
        }
        if (line.startsWith("sil_property ")) { // Remove this because it's not supported yet
            return "";
        }
        line = line.replace("unwind ,", "unwind,");
        line = line.replace(" -> (@error Error)", " -> @error Error");
        line = line.split("//")[0];
        line = line.replaceAll("\\s+$", ""); // right trim
        return line;
    }

    static String readFile(File file) throws IOException {
        return readFile(file, true);
    }

    static String readFileRegular(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()));
    }

    static String readFile(File file, Boolean emptyLines) throws IOException {
        InputStream in = new FileInputStream(file);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder result = new StringBuilder();
        ArrayList<String> scopes = new ArrayList<String>();
        String line;
        boolean delete = false;
        while((line = reader.readLine()) != null) {
            // Empty line, preserve empty lines
            if (line.trim().isEmpty() && !result.toString().endsWith("\n\n") && emptyLines) {
                result.append(System.lineSeparator());
                continue;
            }
            if (delete) {
                if (line.equals("}")) {
                    delete = false;
                }
                line = "";
            } else if (line.startsWith("struct") ||
                       line.startsWith("protocol") ||
                       line.startsWith("final class") ||
                       line.startsWith("class") ||
                       line.startsWith("extension") ||
                       line.startsWith("@_inheritsConvenienceInitializers")) {
                line = "";
                delete = true;
            }
            line = doReplacements(line);
            // For commented out lines
            if (line.trim().length() > 0) {
                if (line.trim().startsWith("sil_scope")) {
                    scopes.add(line);
                } else {
                    result.append(line);
                    result.append(System.lineSeparator());
                }
            }
        }
        if (!scopes.isEmpty()) {
            // Assume "sil_canonical\n" is the first line
            result.insert(result.indexOf(System.lineSeparator()),
                    System.lineSeparator() + System.lineSeparator()
                            + String.join(System.lineSeparator() + System.lineSeparator(), scopes));
        }
        return result.toString();
    }

    static ModuleGroup silFileTestPipeline(File sil) {
        TestDriver.TestDriverOptions options = new TestDriver.TestDriverOptions();
        options.addSILCallBack((SILModule module) -> {
            SILPrinterOptions opts = new SILPrinterOptions();
            String result = new SILPrinter().print(module, opts);
            // Logging.printInfo(result);
            try {
                // Test SIL parser output == input
                String expected = TestUtils.readFile(sil);
                Assertions.assertTrue(expected.equals(result));
            } catch (IOException e) {
                e.printStackTrace();
                Assertions.fail();
            }
            return scala.runtime.BoxedUnit.UNIT;
        });
        options.addRawSWIRLCallBack((Module module) -> {
            SWIRLPrinterOptions opts = new SWIRLPrinterOptions();
            String expected = new SWIRLPrinter().print(module, opts);
            // Logging.printInfo(expected);
            try {
                // Test SWIRL parser output == input
                Path tempPath = Files.createTempFile(null, ".compare.swirl");
                Logging.printInfo("Writing translated SWIRL to " + tempPath.toFile().getName());
                Files.write(tempPath, expected.getBytes());
                Module parsedModule = new SWIRLParser(tempPath).parseModule();
                String result = new SWIRLPrinter().print(parsedModule, opts);
                Assertions.assertTrue(expected.equals(result));
            } catch (IOException | Error e) {
                e.printStackTrace();
                Assertions.fail();
            }
            return scala.runtime.BoxedUnit.UNIT;
        });
        options.addCanSWIRLCallBack((CanModule module) -> {
            SWIRLPrinterOptions opts = new SWIRLPrinterOptions();
            String result = new SWIRLPrinter().print(module, opts);
            // Logging.printInfo(result);
            return scala.runtime.BoxedUnit.UNIT;
        });
        return TestDriver.run(sil, options);
        // Didn't fail or blow up, good.
    }

    static File dumpSILForSwiftFile(File swift) throws URISyntaxException {
        File swiftc = new File(Objects.requireNonNull(TestUtils.class.getClassLoader()
                .getResource("utils/swan-swiftc")).toURI());
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
        } catch (InterruptedException | IOException e) {
            assert p != null;
            p.destroy();
        }
        Assertions.assertEquals(p.exitValue(), 0);
        File sil = new File(swift.getAbsolutePath() + ".sil");
        Assertions.assertTrue(sil.exists());
        return sil;
    }
}
