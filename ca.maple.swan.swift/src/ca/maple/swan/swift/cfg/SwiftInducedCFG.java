//===--- SwiftInducedCFG.java --------------------------------------------===//
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

package ca.maple.swan.swift.cfg;

import ca.maple.swan.swift.ssa.SwiftInstructionVisitor;
import ca.maple.swan.swift.ssa.SwiftInvokeInstruction;
import com.ibm.wala.cast.ir.cfg.AstInducedCFG;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ssa.SSAInstruction;

public class SwiftInducedCFG extends AstInducedCFG {

    public class SwiftPEIVisitor extends AstPEIVisitor implements SwiftInstructionVisitor {

        public SwiftPEIVisitor(boolean[] r) {
            super(r);
        }

        @Override
        public void visitSwiftInvoke(SwiftInvokeInstruction inst) {
            breakBasicBlock();
        }

    }

    public class SwiftBranchVisitor extends AstBranchVisitor implements SwiftInstructionVisitor {

        public SwiftBranchVisitor(boolean[] r) {
            super(r);
        }

    }

    public SwiftInducedCFG(SSAInstruction[] instructions, IMethod method, Context context) {
        super(instructions, method, context);
    }


    @Override
    protected BranchVisitor makeBranchVisitor(boolean[] r) {
        return new SwiftBranchVisitor(r);
    }

    @Override
    protected PEIVisitor makePEIVisitor(boolean[] r) {
        return new SwiftPEIVisitor(r);
    }

}