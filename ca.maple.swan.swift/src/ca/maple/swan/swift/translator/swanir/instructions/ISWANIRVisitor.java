//===--- ISWANIRVisitor.java ----------------------------------------------===//
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

package ca.maple.swan.swift.translator.swanir.instructions;

/*
 * Visitor for ALL SWANIR instructions.
 */

import ca.maple.swan.swift.translator.swanir.instructions.allocation.*;
import ca.maple.swan.swift.translator.swanir.instructions.array.*;
import ca.maple.swan.swift.translator.swanir.instructions.basic.*;
import ca.maple.swan.swift.translator.swanir.instructions.control.*;
import ca.maple.swan.swift.translator.swanir.instructions.dictionary.*;
import ca.maple.swan.swift.translator.swanir.instructions.field.*;
import ca.maple.swan.swift.translator.swanir.instructions.functions.*;
import ca.maple.swan.swift.translator.swanir.instructions.operators.*;
import ca.maple.swan.swift.translator.swanir.instructions.spds.*;

public abstract class ISWANIRVisitor {

    // SPDS

    public abstract void visitDynamicApplyInstruction(DynamicApplyInstruction instruction);

    public abstract void visitRuleInstruction(RuleInstruction instruction);

    // Regular

    // *** BASIC ***

    public abstract void visitAssignGlobalInstruction(AssignGlobalInstruction instruction);

    public abstract void visitAssignInstruction(AssignInstruction instruction);

    public abstract void visitPrintInstruction(PrintInstruction instruction);

    public abstract void visitLiteralInstruction(LiteralInstruction instruction);

    // *** ALLOCATION ***

    public abstract void visitNewArrayTupleInst(NewArrayTupleInstruction instruction);

    public abstract void visitNewGlobalInstruction(NewGlobalInstruction instruction);

    public abstract void visitNewInstruction(NewInstruction instruction);

    // *** FUNCTIONS ***

    public abstract void visitFunctionRefInstruction(FunctionRefInstruction instruction);

    public abstract void visitBuiltinInstruction(BuiltinInstruction instruction);

    public abstract void visitApplyInstruction(ApplyInstruction instruction);

    public abstract void visitTryApplyInstruction(TryApplyInstruction instruction);

    public abstract void visitReturnInstruction(ReturnInstruction instruction);

    public abstract void visitThrowInstruction(ThrowInstruction instruction);

    // *** FIELD ***

    public abstract void visitDynamicFieldReadInstruction(DynamicFieldReadInstruction instruction);

    public abstract void visitStaticFieldReadInstruction(StaticFieldReadInstruction instruction);

    public abstract void visitDynamicFieldWriteInstruction(DynamicFieldWriteInstruction instruction);

    public abstract void visitStaticFieldWriteInstruction(StaticFieldWriteInstruction instruction);

    // *** ARRAY ***

    public abstract void visitWildcardArrayReadInstruction(WildcardArrayReadInstruction instruction);

    public abstract void visitDynamicArrayReadInstruction(DynamicArrayReadInstruction instruction);

    public abstract void visitStaticArrayReadInstruction(StaticArrayReadInstruction instruction);

    public abstract void visitWildcardArrayWriteInstruction(WildcardArrayWriteInstruction instruction);

    public abstract void visitDynamicArrayWriteInstruction(DynamicArrayWriteInstruction instruction);

    public abstract void visitStaticArrayWriteInstruction(StaticArrayWriteInstruction instruction);

    // ** DICTIONARY ***

    public abstract void visitDictionaryReadInstruction(DictionaryReadInstruction instruction);

    public abstract void visitDictionaryWriteInstruction(DictionaryWriteInstruction instruction);

    // *** OPERATORS ***

    public abstract void visitUnaryOperatorInstruction(UnaryOperatorInstruction instruction);

    public abstract void visitBinaryOperatorInstruction(BinaryOperatorInstruction instruction);

    // *** CONTROL ***

    public abstract void visitGotoInstruction(GotoInstruction instruction);

    public abstract void visitSwitchAssignValueInstruction(SwitchAssignValueInstruction instruction);

    public abstract void visitSwitchValueInstruction(SwitchValueInstruction instruction);

    public abstract void visitConditionalBranchInstruction(ConditionalBranchInstruction instruction);

    public abstract void visitConditionalThrowInstruction(ConditionalThrowInstruction instruction);

}
