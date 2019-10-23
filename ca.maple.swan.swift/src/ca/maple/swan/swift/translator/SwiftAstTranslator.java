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

import ca.maple.swan.swift.loader.SwiftLoader;
import com.ibm.wala.cast.ir.ssa.CAstBinaryOp;
import com.ibm.wala.cast.ir.translator.AstTranslator;
import com.ibm.wala.cast.js.ssa.JSInstructionFactory;
import com.ibm.wala.cast.js.types.JavaScriptTypes;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.loader.DynamicCallSiteReference;
import com.ibm.wala.cast.tree.*;
import com.ibm.wala.cast.tree.impl.CAstOperator;
import com.ibm.wala.cast.tree.visit.CAstVisitor;
import com.ibm.wala.cast.types.AstMethodReference;
import com.ibm.wala.cast.util.CAstPrinter;
import com.ibm.wala.cfg.AbstractCFG;
import com.ibm.wala.cfg.IBasicBlock;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.debug.Assertions;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static ca.maple.swan.swift.translator.SwiftCAstNode.GLOBAL_DECL_STMT;

/*
 * This class is borrows lots from the JSAstTranslator. Translates SIL specific CAst to WALA IR.
 */

public class SwiftAstTranslator extends AstTranslator {

    // TODO: Replace with Swift types.

    public SwiftAstTranslator(SwiftLoader loader) {
        super(loader);
    }

    private static final boolean DEBUG = false;

    @Override
    protected boolean useDefaultInitValues() {
        return false;
    }

    @Override
    protected boolean hasImplicitGlobals() {
        return false;
    }

    @Override
    protected boolean treatGlobalsAsLexicallyScoped() {
        return false;
    }

    @Override
    protected TypeReference defaultCatchType() {
        return JavaScriptTypes.Root;
    }

    @Override
    protected TypeReference makeType(CAstType type) {
        return TypeReference.findOrCreate(JavaScriptTypes.jsLoader, type.getName());
    }

    @Override
    protected boolean defineType(CAstEntity type, WalkContext wc) {
        Assertions.UNREACHABLE();
        return false;
    }

    @Override
    protected void defineField(CAstEntity topEntity, WalkContext wc, CAstEntity n) {
        Assertions.UNREACHABLE();
    }

    @Override
    protected String composeEntityName(WalkContext parent, CAstEntity f) {
        if (f.getKind() == CAstEntity.SCRIPT_ENTITY) return f.getName();
        else return parent.getName() + '/' + f.getName();
    }

    @Override
    protected void declareFunction(CAstEntity N, WalkContext context) {
        String fnName = composeEntityName(context, N);
        if (N.getKind() == CAstEntity.SCRIPT_ENTITY) {
            ((SwiftLoader) loader).defineScriptType('L' + fnName, N.getPosition(), N, context);
        } else if (N.getKind() == CAstEntity.FUNCTION_ENTITY) {
            ((SwiftLoader) loader).defineFunctionType('L' + fnName, N.getPosition(), N, context);
        } else {
            Assertions.UNREACHABLE();
        }
    }

    @Override
    protected void defineFunction(
            CAstEntity N,
            WalkContext definingContext,
            AbstractCFG<SSAInstruction, ? extends IBasicBlock<SSAInstruction>> cfg,
            SymbolTable symtab,
            boolean hasCatchBlock,
            Map<IBasicBlock<SSAInstruction>, TypeReference[]> caughtTypes,
            boolean hasMonitorOp,
            AstLexicalInformation LI,
            AstMethod.DebuggingInformation debugInfo) {
        if (DEBUG) System.err.println(("\n\nAdding code for " + N));
        String fnName = composeEntityName(definingContext, N);

        if (DEBUG) System.err.println(cfg);

        ((SwiftLoader) loader)
                .defineCodeBodyCode(
                        'L' + fnName, cfg, symtab, hasCatchBlock, caughtTypes, hasMonitorOp, LI, debugInfo);
    }

    @Override
    protected void doThrow(WalkContext context, int exception) {
        context
                .cfg()
                .addInstruction(insts.ThrowInstruction(context.cfg().getCurrentInstruction(), exception));
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
        MethodReference ref = AstMethodReference.fnReference(JavaScriptTypes.CodeBody);

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
    protected void doNewObject(
            WalkContext context, CAstNode newNode, int result, Object type, int[] arguments) {
        assert arguments == null;
        TypeReference typeRef =
                TypeReference.findOrCreate(JavaScriptTypes.jsLoader, TypeName.string2TypeName("L" + type));

        context
                .cfg()
                .addInstruction(
                        insts.NewInstruction(
                                context.cfg().getCurrentInstruction(),
                                result,
                                NewSiteReference.make(context.cfg().getCurrentInstruction(), typeRef)));
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
    public void doArrayRead(
            WalkContext context, int result, int arrayValue, CAstNode arrayRef, int[] dimValues) {
        Assertions.UNREACHABLE("doArrayRead() called!");
    }

    @Override
    public void doArrayWrite(
            WalkContext context, int arrayValue, CAstNode arrayRef, int[] dimValues, int rval) {
        Assertions.UNREACHABLE("doArrayWrite() called!");
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
    protected void doPrimitive(int resultVal, WalkContext context, CAstNode primitiveCall) {
        Assertions.UNREACHABLE("Primitives are not supported");
        // Perhaps in the future we can move built in operations here to make the CAst frontend less messy.
    }

    @Override
    protected boolean doVisit(CAstNode n, WalkContext context, CAstVisitor<WalkContext> visitor) {
        switch (n.getKind()) {
            case GLOBAL_DECL_STMT:
                CAstSymbol s = (CAstSymbol) n.getChild(0).getValue();
                context.getGlobalScope().declare(s);
                return true;
            default:
            {
                return false;
            }
        }
    }

    // For now, we will keep this "Any" type from JS.

    public static final CAstType Any =
            new CAstType() {

                @Override
                public String getName() {
                    return "Any";
                }

                @Override
                public Collection<CAstType> getSupertypes() {
                    return Collections.emptySet();
                }
            };

    @Override
    protected CAstType topType() {
        return Any;
    }

    @Override
    protected CAstType exceptionType() {
        return Any;
    }

    @Override
    protected CAstSourcePositionMap.Position[] getParameterPositions(CAstEntity e) {
        if (e.getKind() == CAstEntity.SCRIPT_ENTITY) {
            return new CAstSourcePositionMap.Position[0];
        } else {
            CAstSourcePositionMap.Position[] ps = new CAstSourcePositionMap.Position[e.getArgumentCount()];
            for (int i = 2; i < e.getArgumentCount(); i++) {
                ps[i] = e.getPosition(i - 2);
            }
            return ps;
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

    @Override
    protected void doPrologue(WalkContext context) {
        // No prologue needed in Swift.
    }

    @Override
    protected void leaveDeclStmt(CAstNode n, WalkContext c, CAstVisitor<WalkContext> visitor) {
        CAstSymbol s = (CAstSymbol) n.getChild(0).getValue();
        if (!c.currentScope().contains(s.name())) {
            c.currentScope().declare(s);
        }
    }

}