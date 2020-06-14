//===--- SPDSRawToSWANIRTranslator.java ----------------------------------===//
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

import ca.maple.swan.swift.translator.sil.RawUtil;
import ca.maple.swan.swift.translator.swanir.context.InstructionContext;
import ca.maple.swan.swift.translator.swanir.instructions.spds.DynamicApplyInstruction;
import ca.maple.swan.swift.translator.swanir.values.DynamicFunctionRefValue;
import ca.maple.swan.swift.translator.wala.WALARawToSWANIRTranslator;

import java.util.ArrayList;

public class SPDSRawToSWANIRTranslator extends WALARawToSWANIRTranslator {

    @Override
    protected void handleDynamicApply(RawUtil.RawValue result, ArrayList<String> args, DynamicFunctionRefValue refValue, InstructionContext C) {
        C.bc.block.addInstruction(new DynamicApplyInstruction(refValue.getFunctions(), result.Name, result.Type, args, C));
    }

}
