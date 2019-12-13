//===--- IRPruner.java ---------------------------------------------------===//
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

package ca.maple.swan.swift.translator.silir.printing;

import ca.maple.swan.swift.translator.silir.BasicBlock;
import ca.maple.swan.swift.translator.silir.Function;
import ca.maple.swan.swift.translator.silir.instructions.*;
import ca.maple.swan.swift.translator.silir.values.ValueTable;

import java.util.ArrayList;

/*
 * Prunes the IR from unused instruction results. Mainly done for making debugging easier.
 */

public class IRPruner extends ISILIRVisitor {

    private final ValueTable vt;

    private boolean kill;

    public IRPruner(Function f, ValueTable vt) {
        this.vt = vt;
        for (BasicBlock b : f.getBlocks()) {
            ArrayList<SILIRInstruction> killList = new ArrayList<>();
            for (SILIRInstruction instruction : b.getInstructions()) {
                kill = false;
                instruction.visit(this);
                if (kill) {
                    killList.add(instruction);
                }
            }
            for (SILIRInstruction instruction : killList) {
                b.getInstructions().remove(instruction);
            }
        }
    }

    @Override
    public void visitApplyInstruction(ApplyInstruction instruction) {
        
    }

    @Override
    public void visitAssignGlobalInstruction(AssignGlobalInstruction instruction) {
        
    }

    @Override
    public void visitAssignInstruction(AssignInstruction instruction) {
        if (instruction.from == instruction.to) {
            kill = true;
        }
    }

    @Override
    public void visitBinaryOperatorInstruction(BinaryOperatorInstruction instruction) {
        
    }

    @Override
    public void visitBuiltinInstruction(BuiltinInstruction instruction) {
        if (!instruction.value.summaryCreated) {
            kill = true;
        }
    }

    @Override
    public void visitConditionalBranchInstruction(ConditionalBranchInstruction instruction) {
        
    }

    @Override
    public void visitConditionalThrowInstruction(ConditionalThrowInstruction instruction) {
        
    }

    @Override
    public void visitFieldAliasInstruction(FieldAliasInstruction instruction) {
        
    }

    @Override
    public void visitFieldReadInstruction(FieldReadInstruction instruction) {
        
    }

    @Override
    public void visitFieldReadWriteInstruction(FieldReadWriteInstruction instruction) {
        
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
    public void visitImplicitCopyInstruction(ImplicitCopyInstruction instruction) {
        
    }

    @Override
    public void visitLiteralInstruction(LiteralInstruction instruction) {
        if (!vt.isUsed(instruction.literal.name)) {
            kill = true;
        }
    }

    @Override
    public void visitNewArrayTupleInst(NewArrayTupleInstruction instruction) {
        
    }

    @Override
    public void visitNewGlobalInstruction(NewGlobalInstruction instruction) {
        
    }

    @Override
    public void visitNewInstruction(NewInstruction instruction) {
        if (!vt.isUsed(instruction.value.name)) {
            kill = true;
        }
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
