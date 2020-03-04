//===--- SPDSRawToSILIRTranslator.java -----------------------------------===//
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
import ca.maple.swan.swift.translator.silir.context.InstructionContext;
import ca.maple.swan.swift.translator.silir.instructions.SILIRInstruction;
import ca.maple.swan.swift.translator.silir.instructions.spds.DynamicApplyInstruction;
import ca.maple.swan.swift.translator.silir.summaries.SPDSBuiltinHandler;
import ca.maple.swan.swift.translator.silir.values.DynamicFunctionRefValue;
import ca.maple.swan.swift.translator.wala.WALARawToSILIRTranslator;

import java.util.ArrayList;

public class SPDSRawToSILIRTranslator extends WALARawToSILIRTranslator {

    @Override
    protected boolean isBuiltinSummarized(String builtinName) {
        return super.isBuiltinSummarized(builtinName) || SPDSBuiltinHandler.isSummarized(builtinName);
    }

    @Override
    protected SILIRInstruction findBuiltinSummary(String funcName, String resultName, String resultType, ArrayList<String> params, InstructionContext C) {
        if (SPDSBuiltinHandler.isSummarized(funcName)) {
            return SPDSBuiltinHandler.findSummary(funcName, resultName, resultType, params, C);
        }
        return super.findBuiltinSummary(funcName, resultName, resultType, params, C);
    }

    @Override
    protected void handleDynamicApply(RawUtil.RawValue result, ArrayList<String> args, DynamicFunctionRefValue refValue, InstructionContext C) {
        C.bc.block.addInstruction(new DynamicApplyInstruction(refValue.getFunctions(), result.Name, result.Type, args, C));
    }

}
