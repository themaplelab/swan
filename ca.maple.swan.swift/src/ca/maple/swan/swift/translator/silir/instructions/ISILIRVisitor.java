//===--- ISILIRVisitor.java ----------------------------------------------===//
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

package ca.maple.swan.swift.translator.silir.instructions;

public abstract class ISILIRVisitor {

    public abstract void visitApplyInstruction(ApplyInstruction instruction);

    public abstract void visitArrayLengthInstruction(ArrayLengthInstruction instruction);

    public abstract void visitArrayReadInstruction(ArrayReadInstruction instruction);

    public abstract void visitArrayWriteInstruction(ArrayWriteInstruction instruction);

    public abstract void visitAssignGlobalInstruction(AssignGlobalInstruction instruction);

    public abstract void visitAssignInstruction(AssignInstruction instruction);

    public abstract void visitBinaryOperatorInstruction(BinaryOperatorInstruction instruction);

    public abstract void visitBuiltinInstruction(BuiltinInstruction instruction);

    public abstract void visitConditionalBranchInstruction(ConditionalBranchInstruction instruction);

    public abstract void visitConditionalThrowInstruction(ConditionalThrowInstruction instruction);

    public abstract void visitFieldAliasInstruction(FieldAliasInstruction instruction);

    public abstract void visitFieldReadInstruction(FieldReadInstruction instruction);

    public abstract void visitFieldWriteInstruction(FieldWriteInstruction instruction);

    public abstract void visitFunctionRefInstruction(FunctionRefInstruction instruction);

    public abstract void visitGotoInstruction(GotoInstruction instruction);

    public abstract void visitImplicitCopyInstruction(ImplicitCopyInstruction instruction);

    public abstract void visitLiteralInstruction(LiteralInstruction instruction);

    public abstract void visitLoadArrayIndexInstruction(ArrayIndexAliasInstruction instruction);

    public abstract void visitNewArrayTupleInst(NewArrayTupleInstruction instruction);

    public abstract void visitNewGlobalInstruction(NewGlobalInstruction instruction);

    public abstract void visitNewInstruction(NewInstruction instruction);

    public abstract void visitPrintInstruction(PrintInstruction instruction);

    public abstract void visitReturnInstruction(ReturnInstruction instruction);

    public abstract void visitSwitchAssignValueInstruction(SwitchAssignValueInstruction instruction);

    public abstract void visitSwitchValueInstruction(SwitchValueInstruction instruction);

    public abstract void visitThrowInstruction(ThrowInstruction instruction);

    public abstract void visitTryApplyInstruction(TryApplyInstruction instruction);

    public abstract void visitUnaryOperatorInstruction(UnaryOperatorInstruction instruction);

}
