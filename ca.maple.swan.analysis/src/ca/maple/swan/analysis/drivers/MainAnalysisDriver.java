//===--- MainAnalysisDriver.java - Fires up framework --------------------===//
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
///
/// This file links with the shared C++ library and calls the translator
/// on the given Swift file, then prints the resultant CAstEntity.
///
//===---------------------------------------------------------------------===//

package ca.maple.swan.analysis.drivers;

import ca.maple.swan.analysis.translator.SwiftToCAstTranslator;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.util.CAstPrinter;

/// Class that houses the main method of the program, and links with the C++
/// shared library (ca.maple.swan.translator).
public class MainAnalysisDriver {

    static {
        String sharedDir = "/ca.maple.swan.translator/build/libs/swiftWala/shared/";
        String libName = "libswiftWala";

        String SWANDir = "";
        try {
            SWANDir = System.getenv("PATH_TO_SWAN");
        } catch (Exception e) {
            System.err.println("Error: PATH_TO_SWAN path not set! Exiting...");
            System.exit(1);
        }

        // Try to load both dylib and so (instead of checking OS).
        try {
            System.load(SWANDir + sharedDir + libName + ".dylib");
        } catch (Exception dylibException) {
            try {
                System.load(SWANDir + sharedDir + libName + ".so");
            } catch (Exception soException) {
                System.err.println("Could not find shared library!");
                soException.printStackTrace();
            }
        }
    }

    /// Main method that takes in a Swift filename as an argument. Then the framework
    /// is fired up using SwiftToCastTranslator. Lastly, the result is printed to terminal.
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: One input file expected!");
        }
        else {
            try {
                SwiftToCAstTranslator translator = new SwiftToCAstTranslator(args[0]);
                CAstEntity entity = translator.translateToCAst();
                String astString = CAstPrinter.print(entity);
                System.out.println(astString);
            }
            catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
    }
}
