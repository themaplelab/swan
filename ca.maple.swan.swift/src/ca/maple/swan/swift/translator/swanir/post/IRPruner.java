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

package ca.maple.swan.swift.translator.swanir.post;

import ca.maple.swan.swift.translator.swanir.BasicBlock;
import ca.maple.swan.swift.translator.swanir.Function;
import ca.maple.swan.swift.translator.swanir.instructions.*;
import ca.maple.swan.swift.translator.swanir.instructions.basic.AssignGlobalInstruction;
import ca.maple.swan.swift.translator.swanir.instructions.basic.AssignInstruction;
import ca.maple.swan.swift.translator.swanir.instructions.basic.LiteralInstruction;
import ca.maple.swan.swift.translator.swanir.instructions.allocation.NewArrayTupleInstruction;
import ca.maple.swan.swift.translator.swanir.instructions.allocation.NewGlobalInstruction;
import ca.maple.swan.swift.translator.swanir.instructions.allocation.NewInstruction;
import ca.maple.swan.swift.translator.swanir.instructions.array.*;
import ca.maple.swan.swift.translator.swanir.instructions.basic.PrintInstruction;
import ca.maple.swan.swift.translator.swanir.instructions.control.*;
import ca.maple.swan.swift.translator.swanir.instructions.dictionary.DictionaryReadInstruction;
import ca.maple.swan.swift.translator.swanir.instructions.dictionary.DictionaryWriteInstruction;
import ca.maple.swan.swift.translator.swanir.instructions.field.DynamicFieldReadInstruction;
import ca.maple.swan.swift.translator.swanir.instructions.field.DynamicFieldWriteInstruction;
import ca.maple.swan.swift.translator.swanir.instructions.field.StaticFieldReadInstruction;
import ca.maple.swan.swift.translator.swanir.instructions.field.StaticFieldWriteInstruction;
import ca.maple.swan.swift.translator.swanir.instructions.functions.*;
import ca.maple.swan.swift.translator.swanir.instructions.operators.BinaryOperatorInstruction;
import ca.maple.swan.swift.translator.swanir.instructions.operators.UnaryOperatorInstruction;
import ca.maple.swan.swift.translator.swanir.instructions.spds.DynamicApplyInstruction;
import ca.maple.swan.swift.translator.swanir.instructions.spds.RuleInstruction;
import ca.maple.swan.swift.translator.swanir.values.ValueTable;

import java.util.ArrayList;

/*
 * Prunes the IR from unused instruction results. Mainly done for making debugging easier.
 */

public class IRPruner extends ISWANIRVisitor {

    private final ValueTable vt;

    private boolean kill;

    public IRPruner(Function f, ValueTable vt) {
        this.vt = vt;
        for (BasicBlock b : f.getBlocks()) {
            ArrayList<SWANIRInstruction> killList = new ArrayList<>();
            for (SWANIRInstruction instruction : b.getInstructions()) {
                kill = false;
                instruction.visit(this);
                if (kill) {
                    killList.add(instruction);
                }
            }
            for (SWANIRInstruction instruction : killList) {
                b.getInstructions().remove(instruction);
            }
        }
    }

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
    public void visitWildcardArrayReadInstruction(WildcardArrayReadInstruction instruction) {

    }

    @Override
    public void visitDynamicArrayReadInstruction(DynamicArrayReadInstruction instruction) {

    }

    @Override
    public void visitStaticArrayReadInstruction(StaticArrayReadInstruction instruction) {

    }

    @Override
    public void visitWildcardArrayWriteInstruction(WildcardArrayWriteInstruction instruction) {

    }

    @Override
    public void visitDynamicArrayWriteInstruction(DynamicArrayWriteInstruction instruction) {

    }

    @Override
    public void visitStaticArrayWriteInstruction(StaticArrayWriteInstruction instruction) {

    }

    @Override
    public void visitDictionaryReadInstruction(DictionaryReadInstruction instruction) {

    }

    @Override
    public void visitDictionaryWriteInstruction(DictionaryWriteInstruction instruction) {

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
        if (instruction.ic.bc.fc.pc.getFunction(instruction.functionName) == null) {
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
    public void visitDynamicFieldReadInstruction(DynamicFieldReadInstruction instruction) {

    }

    @Override
    public void visitStaticFieldReadInstruction(StaticFieldReadInstruction instruction) {

    }

    @Override
    public void visitDynamicFieldWriteInstruction(DynamicFieldWriteInstruction instruction) {

    }

    @Override
    public void visitStaticFieldWriteInstruction(StaticFieldWriteInstruction instruction) {

    }

    @Override
    public void visitFunctionRefInstruction(FunctionRefInstruction instruction) {
        
    }

    @Override
    public void visitGotoInstruction(GotoInstruction instruction) {
        
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
