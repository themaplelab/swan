//===--- SwiftAstTranslator.java -----------------------------------------===//
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

package ca.maple.swan.swift.translator;

import com.ibm.wala.cast.ir.ssa.CAstBinaryOp;
import com.ibm.wala.cast.ir.translator.AstTranslator;
import com.ibm.wala.cast.js.loader.JavaScriptLoader;
import com.ibm.wala.cast.js.ssa.JSInstructionFactory;
import com.ibm.wala.cast.js.translator.JSAstTranslator;
import com.ibm.wala.cast.js.types.JavaScriptMethods;
import com.ibm.wala.cast.js.types.JavaScriptTypes;
import com.ibm.wala.cast.loader.DynamicCallSiteReference;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.tree.CAstType;
import com.ibm.wala.cast.tree.impl.CAstOperator;
import com.ibm.wala.cast.tree.visit.CAstVisitor;
import com.ibm.wala.cast.types.AstMethodReference;
import com.ibm.wala.cast.util.CAstPrinter;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.debug.Assertions;

import java.util.Arrays;

/*
 * We use this class to override functionality of the JSAstTranslator.
 *
 * Most likely this class will be further extended as problematic limitations or
 * implications of the JS translator for Swift translation are discovered.
 */

public class SwiftAstTranslator extends JSAstTranslator {
    public SwiftAstTranslator(JavaScriptLoader loader) {
        super(loader);
    }

    @Override
    protected void doMaterializeFunction(
            CAstNode n, WalkContext context, int result, int exception, CAstEntity fn) {
        String fnName = composeEntityName(context, fn);
        IClass cls = loader.lookupClass(TypeName.findOrCreate("L" + fnName));
        TypeReference type = cls.getReference();

        context
                .cfg()
                .addInstruction(
                        insts.NewInstruction(
                                context.cfg().getCurrentInstruction(),
                                result,
                                new NewSiteReference(
                                        context.cfg().getCurrentInstruction(), type)));
    }

    @Override
    protected TypeReference makeType(CAstType type) {
        return TypeReference.findOrCreate(JavaScriptTypes.jsLoader, type.getName());
    }

    @Override
    protected void doCall(
            WalkContext context,
            CAstNode call,
            int result,
            int exception,
            CAstNode name,
            int receiver,
            int[] arguments) {
        MethodReference ref =
                name.getValue().equals("ctor")
                        ? JavaScriptMethods.ctorReference
                        : name.getValue().equals("dispatch")
                        ? JavaScriptMethods.dispatchReference
                        : AstMethodReference.fnReference(JavaScriptTypes.CodeBody);

        context
                .cfg()
                .addInstruction(
                        ((JSInstructionFactory) insts)
                                .Invoke(
                                        context.cfg().getCurrentInstruction(),
                                        receiver,
                                        result,
                                        arguments,
                                        exception,
                                        new DynamicCallSiteReference(ref, context.cfg().getCurrentInstruction())));


        // TODO: Is this needed? We only need to handle catches for try_apply.

        context.cfg().addPreNode(call, context.getUnwindState());

        // this new block is for the normal termination case
        context.cfg().newBlock(true);

        // exceptional case: flow to target given in CAst, or if null, the exit node
        if (context.getControlFlow().getTarget(call, null) != null)
            context.cfg().addPreEdge(call, context.getControlFlow().getTarget(call, null), true);
        else context.cfg().addPreEdgeToExit(call, true);
    }

    @Override
    protected void doFieldWrite(
            WalkContext context, int receiver, CAstNode elt, CAstNode parent, int rval) {
        this.visit(elt, context, this);

        context
                .cfg()
                .addInstruction(
                        ((JSInstructionFactory) insts)
                                .PropertyWrite(
                                        context.cfg().getCurrentInstruction(), receiver, context.getValue(elt), rval));
    }

    @Override
    protected void doFieldRead(
            WalkContext context, int result, int receiver, CAstNode elt, CAstNode readNode) {
        this.visit(elt, context, this);
        int x = context.currentScope().allocateTempValue();

        context
                .cfg()
                .addInstruction(
                        ((JSInstructionFactory) insts)
                                .AssignInstruction(context.cfg().getCurrentInstruction(), x, receiver));


        if (elt.getKind() == CAstNode.CONSTANT && elt.getValue() instanceof String) {
            String field = (String) elt.getValue();
            // symtab needs to have this value
            context.currentScope().getConstantValue(field);
            context
                    .cfg()
                    .addInstruction(
                            ((JSInstructionFactory) insts)
                                    .GetInstruction(context.cfg().getCurrentInstruction(), result, x, field));
        } else {
            context
                    .cfg()
                    .addInstruction(
                            ((JSInstructionFactory) insts)
                                    .PropertyRead(
                                            context.cfg().getCurrentInstruction(), result, x, context.getValue(elt)));
        }

    }

    @Override
    protected com.ibm.wala.shrikeBT.IBinaryOpInstruction.IOperator translateBinaryOpcode(CAstNode op) {
        if (op == CAstOperator.OP_ADD) {
            return com.ibm.wala.shrikeBT.IBinaryOpInstruction.Operator.ADD;
        } else if (op == CAstOperator.OP_DIV) {
            return com.ibm.wala.shrikeBT.IBinaryOpInstruction.Operator.DIV;
        } else if (op == CAstOperator.OP_LSH) {
            return com.ibm.wala.shrikeBT.IShiftInstruction.Operator.SHL;
        } else if (op == CAstOperator.OP_MOD) {
            return com.ibm.wala.shrikeBT.IBinaryOpInstruction.Operator.REM;
        } else if (op == CAstOperator.OP_MUL) {
            return com.ibm.wala.shrikeBT.IBinaryOpInstruction.Operator.MUL;
        } else if (op == CAstOperator.OP_RSH) {
            return com.ibm.wala.shrikeBT.IShiftInstruction.Operator.SHR;
        } else if (op == CAstOperator.OP_SUB) {
            return com.ibm.wala.shrikeBT.IBinaryOpInstruction.Operator.SUB;
        } else if (op == CAstOperator.OP_URSH) {
            return com.ibm.wala.shrikeBT.IShiftInstruction.Operator.USHR;
        } else if (op == CAstOperator.OP_BIT_AND) {
            return com.ibm.wala.shrikeBT.IBinaryOpInstruction.Operator.AND;
        } else if (op == CAstOperator.OP_BIT_OR) {
            return com.ibm.wala.shrikeBT.IBinaryOpInstruction.Operator.OR;
        } else if (op == CAstOperator.OP_BIT_XOR) {
            return com.ibm.wala.shrikeBT.IBinaryOpInstruction.Operator.XOR;
        } else if (op == CAstOperator.OP_CONCAT) {
            return CAstBinaryOp.CONCAT;
        } else if (op == CAstOperator.OP_EQ) {
            return CAstBinaryOp.EQ;
        } else if (op == CAstOperator.OP_STRICT_EQ) {
            return CAstBinaryOp.STRICT_EQ;
        } else if (op == CAstOperator.OP_GE) {
            return CAstBinaryOp.GE;
        } else if (op == CAstOperator.OP_GT) {
            return CAstBinaryOp.GT;
        } else if (op == CAstOperator.OP_LE) {
            return CAstBinaryOp.LE;
        } else if (op == CAstOperator.OP_LT) {
            return CAstBinaryOp.LT;
        } else if (op == CAstOperator.OP_NE) {
            return CAstBinaryOp.NE;
        } else if (op == CAstOperator.OP_STRICT_NE) {
            return CAstBinaryOp.STRICT_NE;
        } else if (op == CAstOperator.OP_REL_AND) {
            return com.ibm.wala.shrikeBT.IBinaryOpInstruction.Operator.AND;
        } else if (op == CAstOperator.OP_REL_OR) {
            return com.ibm.wala.shrikeBT.IBinaryOpInstruction.Operator.OR;
        } else {
            Assertions.UNREACHABLE("cannot translate " + CAstPrinter.print(op));
            return null;
        }
    }



}