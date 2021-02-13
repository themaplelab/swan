/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

import ca.ualberta.maple.swan.ir.CanModule;
import ca.ualberta.maple.swan.ir.Exceptions;
import ca.ualberta.maple.swan.ir.Module;
import ca.ualberta.maple.swan.ir.canonical.SWIRLPass;
import ca.ualberta.maple.swan.ir.raw.SWIRLGen;
import ca.ualberta.maple.swan.parser.Error;
import ca.ualberta.maple.swan.parser.SILModule;
import ca.ualberta.maple.swan.parser.SILParser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;

public class Tests {

    @Test
    // Just smoke test for now.
    void testSPDSTranslation() throws Error, URISyntaxException, Exceptions.IncorrectSWIRLStructureException,
            Exceptions.UnexpectedSILFormatException, Exceptions.UnexpectedSILTypeBehaviourException {
        System.out.println("Testing modules");
        File fileDir = new File(getClass().getClassLoader()
                .getResource("sil/modules/").toURI());
        File[] silFiles = fileDir.listFiles((dir, name) -> name.endsWith(".sil"));
        for (File sil : silFiles) {
            System.out.println("    -> " + sil.getName());
            SILParser parser = new SILParser(sil.toPath());
            SILModule silModule = parser.parseModule();
            // System.out.println(new SILPrinter().print(silModule));
            // System.out.println("============================================");
            Module swirlModule = SWIRLGen.translateSILModule(silModule);
            // System.out.print(new SWIRLPrinter().print(swirlModule));
            // System.out.println("============================================");
            CanModule canSwirlModule = SWIRLPass.runPasses(swirlModule);
            // System.out.print(new SWIRLPrinter().print(canSwirlModule, new SWIRLPrinterOptions()));
        }
    }


}
