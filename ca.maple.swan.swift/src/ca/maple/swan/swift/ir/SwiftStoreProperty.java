//===--- SwiftStoreProperty.java -----------------------------------------===//
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

package ca.maple.swan.swift.ir;

import ca.maple.swan.swift.ssa.SwiftInstructionVisitor;
import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;

public class SwiftStoreProperty extends SSAArrayStoreInstruction {

    public SwiftStoreProperty(int iindex, int objectRef, int memberRef, int value) {
        super(iindex, objectRef, memberRef, value, SwiftTypes.Root);
    }

    @Override
    public void visit(IVisitor v) {
        ((SwiftInstructionVisitor)v).visitSwiftStoreProperty(this);
    }

}