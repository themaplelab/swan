/*
 * Copyright (c) 2021 the SWAN project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This software has dependencies with other licenses.
 * See https://github.com/themaplelab/swan/doc/LICENSE.md.
 */

import ca.ualberta.maple.swan.ir.Error;
import ca.ualberta.maple.swan.ir.*;
import ca.ualberta.maple.swan.ir.canonical.SWIRLPass;
import ca.ualberta.maple.swan.parser.SILModule;
import ca.ualberta.maple.swan.parser.SILPrinter;
import ca.ualberta.maple.swan.parser.SILPrinterOptions;
import ca.ualberta.maple.swan.test.TestDriver;
import ca.ualberta.maple.swan.utils.Logging;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Objects;

// Use for debugging/testing specific cases
// Project should have corresponding IDE run configurations
// Be sure to not commit unintended changes
public class Playground {

    @Test
    void testSIL() throws URISyntaxException {
        File silFile = new File(Objects.requireNonNull(getClass().getClassLoader()
                .getResource("playground/playground.sil")).toURI());
        Logging.printInfo("(Playground) testSIL: Testing " + silFile.getName());
        TestDriver.TestDriverOptions options = new TestDriver.TestDriverOptions();
        options.addSILCallBack((SILModule module) -> {
            SILPrinterOptions opts = new SILPrinterOptions();
            String result = new SILPrinter().print(module, opts);
            // Logging.printInfo(result);
            return scala.runtime.BoxedUnit.UNIT;
        });
        options.addRawSWIRLCallBack((Module module) -> {
            SWIRLPrinterOptions opts = new SWIRLPrinterOptions();
            String expected = new SWIRLPrinter().print(module, opts);
            // Logging.printInfo(expected);
            return scala.runtime.BoxedUnit.UNIT;
        });
        options.addCanSWIRLCallBack((CanModule module) -> {
            SWIRLPrinterOptions opts = new SWIRLPrinterOptions();
            String result = new SWIRLPrinter().print(module, opts);
            // Logging.printInfo(result);
            return scala.runtime.BoxedUnit.UNIT;
        });
        TestDriver.run(silFile, options);
    }

    @Test
    void testSwift() throws URISyntaxException {
        File swiftFile = new File(Objects.requireNonNull(getClass().getClassLoader()
                .getResource("playground/playground.swift")).toURI());
        Logging.printInfo("(Playground) testSwift: Testing " + swiftFile.getName());
        File silFile = TestUtils.dumpSILForSwiftFile(swiftFile);
        TestDriver.TestDriverOptions options = new TestDriver.TestDriverOptions();
        options.addSILCallBack((SILModule module) -> {
            SILPrinterOptions opts = new SILPrinterOptions();
            String result = new SILPrinter().print(module, opts);
            // Logging.printInfo(result);
            return scala.runtime.BoxedUnit.UNIT;
        });
        options.addRawSWIRLCallBack((Module module) -> {
            SWIRLPrinterOptions opts = new SWIRLPrinterOptions();
            String expected = new SWIRLPrinter().print(module, opts);
            // Logging.printInfo(expected);
            return scala.runtime.BoxedUnit.UNIT;
        });
        options.addCanSWIRLCallBack((CanModule module) -> {
            SWIRLPrinterOptions opts = new SWIRLPrinterOptions();
            String result = new SWIRLPrinter().print(module, opts);
            // Logging.printInfo(result);
            return scala.runtime.BoxedUnit.UNIT;
        });
        // Use group here because this playground in particular is useful
        // when creating models.
        ModuleGroup group = TestDriver.run(silFile, options);
        SWIRLPrinterOptions opts = new SWIRLPrinterOptions();
        String groupString = new SWIRLPrinter().print(group, opts);
        // Logging.printInfo(groupString);
    }

    @Test
    void testSWIRL() throws URISyntaxException, Error {
        File swirlFile = new File(Objects.requireNonNull(getClass().getClassLoader()
                .getResource("playground/playground.swirl")).toURI());
        Logging.printInfo("(Playground) testSWIRL: Testing " + swirlFile.getName());
        Module parsedModule = new SWIRLParser(swirlFile.toPath()).parseModule();
        CanModule canModule = new SWIRLPass().runPasses(parsedModule);
        // System.out.println(new SWIRLParser().print(canModule, new SWIRLPrinterOptions()));
    }
}
