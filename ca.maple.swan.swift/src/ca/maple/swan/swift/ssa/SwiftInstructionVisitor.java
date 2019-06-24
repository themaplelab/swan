//===--- SwiftInstructionVisitor.java ------------------------------------===//
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

package ca.maple.swan.swift.ssa;

import ca.maple.swan.swift.ir.SwiftStoreProperty;
import com.ibm.wala.cast.ir.ssa.AstInstructionVisitor;

public interface SwiftInstructionVisitor extends AstInstructionVisitor {

    default void visitSwiftInvoke(SwiftInvokeInstruction inst) {

    }

    default void visitSwiftStoreProperty(SwiftStoreProperty inst) {

    }

}