//===--- SwiftToSPDSTranslator.java --------------------------------------===//
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

package ca.maple.swan.swift.translator.spds;

// WIP

import ca.maple.swan.swift.translator.sil.RawData;
import ca.maple.swan.swift.translator.swanir.context.ProgramContext;

public class SwiftToSPDSTranslator {

    private static final boolean DEBUG = true;

    private final RawData rawData;

    public SwiftToSPDSTranslator(RawData data) {
        this.rawData = data;
    }

    public ProgramContext translateToProgramContext() {
        ProgramContext pc = new SPDSRawToSWANIRTranslator().translate(this.rawData.getRawData().getChild(1));
        if (DEBUG) {
            pc.pruneIR();
            pc.generateLineNumbers();
            pc.printFunctions();
        }
        return pc;
    }

    public void translateSWANIRtoSPDS() {
        // TODO
        SPDSRawToSWANIRTranslator translator =  new SPDSRawToSWANIRTranslator();
        // translator.translate() ?
    }


}
