//===--- SILIRToSPDSTranslator.java --------------------------------------===//
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

import ca.maple.swan.swift.translator.silir.instructions.*;
import ca.maple.swan.swift.translator.silir.instructions.spds.DynamicApplyInstruction;
import ca.maple.swan.swift.translator.silir.instructions.spds.RuleInstruction;

public class SILIRToSPDSTranslator {

    private static class Visitor extends ISPDSSILIRVisitor {

        @Override
        public void visitDynamicApplyInstruction(DynamicApplyInstruction instruction) {

        }

        @Override
        public void visitRuleInstruction(RuleInstruction instruction) {

        }

        @Override
        public void visitApplyInstruction(ApplyInstruction instruction) {

        }

        @Override
        public void visitArrayReadInstruction(ArrayReadInstruction instruction) {

        }

        @Override
        public void visitArrayWriteInstruction(ArrayWriteInstruction instruction) {

        }

        @Override
        public void visitAssignGlobalInstruction(AssignGlobalInstruction instruction) {

        }

        @Override
        public void visitAssignInstruction(AssignInstruction instruction) {

        }

        @Override
        public void visitBinaryOperatorInstruction(BinaryOperatorInstruction instruction) {

        }

        @Override
        public void visitBuiltinInstruction(BuiltinInstruction instruction) {

        }

        @Override
        public void visitConditionalBranchInstruction(ConditionalBranchInstruction instruction) {

        }

        @Override
        public void visitConditionalThrowInstruction(ConditionalThrowInstruction instruction) {

        }

        @Override
        public void visitFieldReadInstruction(FieldReadInstruction instruction) {

        }

        @Override
        public void visitFieldWriteInstruction(FieldWriteInstruction instruction) {

        }

        @Override
        public void visitFunctionRefInstruction(FunctionRefInstruction instruction) {

        }

        @Override
        public void visitGotoInstruction(GotoInstruction instruction) {

        }

        @Override
        public void visitLiteralInstruction(LiteralInstruction instruction) {

        }

        @Override
        public void visitNewArrayTupleInst(NewArrayTupleInstruction instruction) {

        }

        @Override
        public void visitNewGlobalInstruction(NewGlobalInstruction instruction) {

        }

        @Override
        public void visitNewInstruction(NewInstruction instruction) {

        }

        @Override
        public void visitPrintInstruction(PrintInstruction instruction) {

        }

        @Override
        public void visitReturnInstruction(ReturnInstruction instruction) {

        }

        @Override
        public void visitSwitchAssignValueInstruction(SwitchAssignValueInstruction instruction) {

        }

        @Override
        public void visitSwitchValueInstruction(SwitchValueInstruction instruction) {

        }

        @Override
        public void visitThrowInstruction(ThrowInstruction instruction) {

        }

        @Override
        public void visitTryApplyInstruction(TryApplyInstruction instruction) {

        }

        @Override
        public void visitUnaryOperatorInstruction(UnaryOperatorInstruction instruction) {

        }
    }

}
