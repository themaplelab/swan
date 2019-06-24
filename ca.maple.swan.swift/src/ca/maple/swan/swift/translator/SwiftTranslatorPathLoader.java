//===--- SwiftTranslatorPathLoader.java ----------------------------------===//
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

package ca.maple.swan.swift.translator;

public class SwiftTranslatorPathLoader {

    public static void load() {
        String sharedDir = "/ca.maple.swan.translator/build/libs/swiftWala/shared/";
        String libName = "libswiftWala";

        String SWANDir = "";
        try {
            SWANDir = System.getenv("PATH_TO_SWAN");
        } catch (Exception e) {
            System.err.println("Error: PATH_TO_SWAN path not set! Exiting...");
            e.printStackTrace();
        }

        // Try to load both dylib and so (instead of checking OS).
        try {
            System.load(SWANDir + sharedDir + libName + ".dylib");
        } catch (UnsatisfiedLinkError dylibException) {
            try {
                System.load(SWANDir + sharedDir + libName + ".so");
            } catch (UnsatisfiedLinkError soException) {
                System.err.println("Could not find shared library!");
                soException.printStackTrace();
            }
        }
    }
}
