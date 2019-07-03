//===--- SwiftSummary.java -----------------------------------------------===//
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

package ca.maple.swan.swift.ipa.summaries;

import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

public class SwiftSummary extends MethodSummary {

    private final int declaredParameters;

    public SwiftSummary(MethodReference ref, int declaredParameters) {
        super(ref);
        this.declaredParameters = declaredParameters;
    }

    @Override
    public int getNumberOfParameters() {
        return declaredParameters;
    }

    @Override
    public TypeReference getParameterType(int i) {
        return SwiftTypes.Root;
    }

}