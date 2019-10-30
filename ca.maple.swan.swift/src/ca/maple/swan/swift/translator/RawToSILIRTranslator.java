//===--- RawToSILIRTranslator.java ---------------------------------------===//
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

import ca.maple.swan.swift.translator.raw.*;
import ca.maple.swan.swift.translator.silir.BasicBlock;
import ca.maple.swan.swift.translator.silir.Function;
import ca.maple.swan.swift.translator.silir.context.BlockContext;
import ca.maple.swan.swift.translator.silir.context.FunctionContext;
import ca.maple.swan.swift.translator.silir.context.InstructionContext;
import ca.maple.swan.swift.translator.silir.context.ProgramContext;
import ca.maple.swan.swift.translator.silir.instructions.*;
import ca.maple.swan.swift.translator.silir.summaries.BuiltinHandler;
import ca.maple.swan.swift.translator.silir.values.*;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.shrikeBT.ConditionalBranchInstruction;
import com.ibm.wala.util.debug.Assertions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import static ca.maple.swan.swift.translator.raw.RawUtil.*;

/*
 * Translates the raw representation given from the C++ SIL translator to SILIR.
 * The raw representation used CAst purely for convenience.
 *
 * The big point about this translator is that it removes pointers by using field references.
 * e.g. dereferencing a pointer (say p) would look like "p.value".
 *      writing to a pointer:       p.value = x
 *      reading from a pointer:     x = p.value
 *
 * Therefore, the translator needs to be careful to handle instructions that involve pointers.
 */

@SuppressWarnings("unused")
public class RawToSILIRTranslator extends SILInstructionVisitor<SILIRInstruction, InstructionContext> {

    public ProgramContext translate(CAstNode n) {

        RootNode root = new RootNode(n);

        HashMap<BasicBlock, ArrayList<InstructionNode>> toTranslate = new HashMap<>();

        ProgramContext pc = new ProgramContext(toTranslate);
        for (FunctionNode function : root.getFunctions()) {
            String functionName = function.getFunctionName();
            String returnType = function.getFunctionReturnType();
            Position funcPosition = function.getFunctionPosition();
            ArrayList<Argument> arguments = new ArrayList<>();
            for (ArgumentNode arg : function.getArguments()) {
                String argName = arg.getName();
                String argType = arg.getType();
                Position argPosition = arg.getPosition();
                arguments.add(new Argument(argName, argType, argPosition));
            }

            Function f = new Function(functionName, returnType, funcPosition, arguments);
            int blockNo = 0;
            for (BlockNode block : function.getBlocks()) {
                ArrayList<Argument> bbArguments = new ArrayList<>();
                for (ArgumentNode arg : block.getArguments()) {
                    bbArguments.add(new Argument(arg.getName(), arg.getType()));
                }
                BasicBlock bb = new BasicBlock(blockNo, bbArguments);
                f.addBlock(bb);
                toTranslate.put(bb, block.getInstructions());
                ++blockNo;
            }

            pc.addFunction(functionName, f);
        }

        VisitProgram(pc);

        return pc;

    }

    private void VisitProgram(ProgramContext pc) {
        for (Function f : pc.getFunctions()) {
            VisitFunction(f, pc);
        }
    }

    private void VisitFunction(Function f, ProgramContext pc) {
        FunctionContext fc = new FunctionContext(f, pc);
        for (BasicBlock b : f.getBlocks()) {
            VisitBlock(b, fc);
        }

    }

    private void VisitBlock(BasicBlock b, FunctionContext fc) {
        BlockContext bc = new BlockContext(b, fc);
        for (InstructionNode inst : fc.pc.toTranslate.get(b)) {
            try {
                InstructionContext ic = new InstructionContext(bc, getInstructionPosition(inst.getInstruction()));
                SILIRInstruction result = this.visit(inst.getInstruction(), ic);
                if (result != null) {
                    bc.block.addInstruction(result);
                }
            } catch (Exception e) {
                System.err.println("Could not translate " + inst.getName());
                e.printStackTrace();
            }
        }
    }

    @Override
    protected CAstSourcePositionMap.Position getInstructionPosition(CAstNode N) {
        return (CAstSourcePositionMap.Position) N.getChild(1).getValue();
    }

    @Override
    protected SILIRInstruction visitAllocStack(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        return new NewInstruction(result.Name, result.Type, C);
    }

    @Override
    protected SILIRInstruction visitAllocRef(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        return new NewInstruction(result.Name, result.Type, C);
    }

    @Override
    protected SILIRInstruction visitAllocRefDynamic(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        return new NewInstruction(result.Name, result.Type, C);
    }

    @Override
    protected SILIRInstruction visitAllocBox(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        return new NewInstruction(result.Name, result.Type, C);
    }

    @Override
    protected SILIRInstruction visitAllocValueBuffer(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitAllocGlobal(CAstNode N, InstructionContext C) {
        String GlobalName = RawUtil.getStringValue(N, 0);
        String GlobalType = RawUtil.getStringValue(N, 1);
        return new NewGlobalInstruction(GlobalName, GlobalType, C);
    }

    @Override
    protected SILIRInstruction visitDeallocStack(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    protected SILIRInstruction visitDeallocBox(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    protected SILIRInstruction visitProjectBox(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitDeallocRef(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    protected SILIRInstruction visitDeallocPartialRef(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitDeallocValueBuffer(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitProjectValueBuffer(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitDebugValue(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    protected SILIRInstruction visitDebugValueAddr(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    protected SILIRInstruction visitLoad(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return tryAliasRead(result, operand.Name, "value", C);
    }

    private SILIRInstruction tryAliasWrite(String SourceName, String DestName, InstructionContext C) {
        Value v = C.valueTable().getPossibleAlias(DestName);
        if (v instanceof FieldAliasValue) {
            return new FieldWriteInstruction(((FieldAliasValue) v).value.name, ((FieldAliasValue) v).field, SourceName, C);
        } else {
            return new FieldWriteInstruction(DestName, "value", SourceName, C);
        }
    }

    private SILIRInstruction tryAliasRead(RawValue result, String operand, String field, InstructionContext C) {
        Value v = C.valueTable().getPossibleAlias(operand);
        if (v instanceof FieldAliasValue) {
            return new FieldReadInstruction(result.Name, result.Type, ((FieldAliasValue) v).value.name, ((FieldAliasValue) v).field, C);
        } else {
            return new FieldReadInstruction(result.Name, result.Type, operand, field, C);
        }
    }

    @Override
    protected SILIRInstruction visitStore(CAstNode N, InstructionContext C) {
        String SourceName = RawUtil.getStringValue(N, 0);
        String DestName = RawUtil.getStringValue(N, 1);
        return tryAliasWrite(SourceName, DestName, C);
    }

    @Override
    protected SILIRInstruction visitStoreBorrow(CAstNode N, InstructionContext C) {
        return visitStore(N, C);
    }

    @Override
    protected SILIRInstruction visitLoadBorrow(CAstNode N, InstructionContext C) {
        return visitLoad(N, C);
    }

    @Override
    protected SILIRInstruction visitBeginBorrow(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitEndBorrow(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    protected SILIRInstruction visitAssign(CAstNode N, InstructionContext C) {
        String SourceName = RawUtil.getStringValue(N, 0);
        String DestName = RawUtil.getStringValue(N, 1);
        return tryAliasWrite(SourceName, DestName, C);
    }

    @Override
    protected SILIRInstruction visitAssignByWrapper(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitMarkUninitialized(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitMarkFunctionEscape(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        // Why does the SIL.rst show a result, but sometimes there isn't one. Is it optional?
        try {
            RawValue result = getSingleResult(N); // May be no result and cause an exception.
            return new ImplicitCopyInstruction(result.Name, operand.Name, C);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected SILIRInstruction visitMarkUninitializedBehavior(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitCopyAddr(CAstNode N, InstructionContext C) {
        // This is an assign since we can't handle pointer value copies.
        String SourceName = RawUtil.getStringValue(N, 0);
        String DestName = RawUtil.getStringValue(N, 1);
        return new AssignInstruction(DestName, SourceName, C);
    }

    @Override
    protected SILIRInstruction visitDestroyAddr(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    protected SILIRInstruction visitIndexAddr(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitTailAddr(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitIndexRawPointer(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitBindMemory(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitBeginAccess(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitEndAccess(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    protected SILIRInstruction visitBeginUnpairedAccess(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitEndUnpairedAccess(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitStrongRetain(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitStrongRelease(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitSetDeallocating(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitStrongRetainUnowned(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitUnownedRetain(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitUnownedRelease(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitLoadWeak(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    protected SILIRInstruction visitStoreWeak(CAstNode N, InstructionContext C) {
        return visitStore(N, C);
    }

    @Override
    protected SILIRInstruction visitLoadUnowned(CAstNode N, InstructionContext C) {
        return visitLoad(N, C);
    }

    @Override
    protected SILIRInstruction visitStoreUnowned(CAstNode N, InstructionContext C) {
        return visitStore(N, C);
    }

    @Override
    protected SILIRInstruction visitFixLifetime(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitEndLifetime(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    protected SILIRInstruction visitMarkDependence(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitIsUnique(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitIsEscapingClosure(CAstNode N, InstructionContext C) {
        // TODO: Maybe create a custom or dummy operator here for a unary instruction?
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitCopyBlock(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new AssignInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitCopyBlockWithoutEscaping(CAstNode N, InstructionContext C) {
        return visitCopyBlock(N, C);
    }

    @Override
    protected SILIRInstruction visitFunctionRef(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        String FuncName = RawUtil.getStringValue(N, 2);
        Function f = C.bc.fc.pc.getFunction(FuncName);
        return (f == null)
                ? new BuiltinInstruction(FuncName, result.Name, result.Type, C)
                : new FunctionRefInstruction(result.Name, result.Type, f, C);
    }

    @Override
    protected SILIRInstruction visitDynamicFunctionRef(CAstNode N, InstructionContext C) {
        return visitFunctionRef(N, C);
    }

    @Override
    protected SILIRInstruction visitPrevDynamicFunctionRef(CAstNode N, InstructionContext C) {
        return visitFunctionRef(N, C);
    }

    @Override
    protected SILIRInstruction visitGlobalAddr(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        String GlobalName = RawUtil.getStringValue(N, 2);
        return new AssignGlobalInstruction(result.Name, result.Type, GlobalName, C);
    }

    @Override
    protected SILIRInstruction visitGlobalValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitIntegerLiteral(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        int Integer = RawUtil.getIntValue(N, 2);
        return new LiteralInstruction(Integer, result.Name, result.Type, C);
    }

    @Override
    protected SILIRInstruction visitFloatLiteral(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        float Float = ((BigDecimal) N.getChild(2).getValue()).floatValue();
        return new LiteralInstruction(Float, result.Name, result.Type, C);
    }

    @Override
    protected SILIRInstruction visitStringLiteral(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        String StringValue = RawUtil.getStringValue(N, 2);
        return new LiteralInstruction(StringValue, result.Name, result.Type, C);
    }

    @Override
    protected SILIRInstruction visitClassMethod(CAstNode N, InstructionContext C) {
        return visitFunctionRef(N, C);
    }

    @Override
    protected SILIRInstruction visitObjCMethod(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        String FuncName = RawUtil.getStringValue(N, 2);
        return new BuiltinInstruction(FuncName, result.Name, result.Type, C);
    }

    @Override
    protected SILIRInstruction visitSuperMethod(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitObjCSuperMethod(CAstNode N, InstructionContext C) {
        return visitObjCMethod(N, C);
    }

    @Override
    protected SILIRInstruction visitWitnessMethod(CAstNode N, InstructionContext C) {
        return visitFunctionRef(N, C);
    }

    @Override
    protected SILIRInstruction visitApply(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        String FuncRefValue = RawUtil.getStringValue(N, 2);
        Value refValue = C.valueTable().getValue(FuncRefValue);
        // If not operator, or operator overloaded.
        if (N.getChildren().size() <= 4 || C.bc.fc.pc.getFunction(refValue.name) != null) {
            CAstNode FuncNode = N.getChild(3);
            ArrayList<String> args = new ArrayList<>();
            for (CAstNode arg : FuncNode.getChildren()) {
                args.add((String) arg.getValue());
            }
            if (refValue instanceof BuiltinFunctionRefValue) {
                String name = ((BuiltinFunctionRefValue) refValue).getFunction();
                if (BuiltinHandler.isBuiltIn(name) || BuiltinHandler.isSummarized(name)) {
                    if (BuiltinHandler.isSummarized(name)) {
                        return BuiltinHandler.findSummary(name, result.Name, result.Type, args, C);
                    } else {
                        // TEMPORARY SOLUTION
                        return new LiteralInstruction("unsummarized builtin", result.Name, result.Type, C);
                    }
                } else {
                    // TODO: Make new function here. What to then do with builtin ref?
                    return null;
                }
            } else if (refValue instanceof FunctionRefValue) {
                return new ApplyInstruction(FuncRefValue, result.Name, result.Type, args, C);
            } else {
                Assertions.UNREACHABLE("Unexpected value type");
                return null;
            }
        } else { // Else an operator.
            CAstNode OperatorNode = N.getChild(4);
            if (OperatorNode.getKind() == CAstNode.UNARY_EXPR) {
                String operator = getStringValue(OperatorNode, 0);
                String operand = getStringValue(OperatorNode, 1);
                return new UnaryOperatorInstruction(result.Name, result.Type, operator, operand, C);
            } else if (OperatorNode.getKind() == CAstNode.BINARY_EXPR) {
                String operator = getStringValue(OperatorNode, 0);
                String operand1 = getStringValue(OperatorNode, 1);
                String operand2 = getStringValue(OperatorNode, 2);
                return new BinaryOperatorInstruction(result.Name, result.Type, operator, operand1, operand2, C);
            } else {
                Assertions.UNREACHABLE("Unexpected kind");
                return null;
            }
        }
    }

    @Override
    protected SILIRInstruction visitBeginApply(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitAbortApply(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitEndApply(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitPartialApply(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitBuiltin(CAstNode N, InstructionContext C) {
        // TODO: Create new function here too
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitMetatype(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        return new LiteralInstruction(result.Type, result.Name, result.Type, C);
    }

    @Override
    protected SILIRInstruction visitValueMetatype(CAstNode N, InstructionContext C) {
        return visitMetatype(N, C);
    }

    @Override
    protected SILIRInstruction visitExistentialMetatype(CAstNode N, InstructionContext C) {
        return visitMetatype(N, C);
    }

    @Override
    protected SILIRInstruction visitObjCProtocol(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitRetainValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitRetainValueAddr(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitUnmanagedRetainValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitCopyValue(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitReleaseValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitReleaseValueAddr(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitUnmanagedReleaseValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitDestroyValue(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    protected SILIRInstruction visitAutoreleaseValue(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    protected SILIRInstruction visitTuple(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        Value TupleValue = new Value(result.Name, result.Type);
        C.valueTable().add(TupleValue);
        C.bc.block.addInstruction(new NewInstruction(result.Name, result.Type, C));
        int index = 0;
        for (CAstNode field : N.getChild(2).getChildren()) {
            String FieldName = RawUtil.getStringValue(field, 0);
            C.bc.block.addInstruction(new FieldWriteInstruction(TupleValue.name, String.valueOf(index), FieldName, C));
            ++index;
        }
        return null;
    }

    @Override
    protected SILIRInstruction visitTupleExtract(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        RawValue operand = getSingleOperand(N);
        String field = getStringValue(N, 2);
        return tryAliasRead(result, operand.Name, field, C);
    }

    @Override
    protected SILIRInstruction visitTupleElementAddr(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        String Index = getStringValue(N, 2);
        return new FieldAliasInstruction(result.Name, result.Type, operand.Name, Index, C);
    }

    @Override
    protected SILIRInstruction visitDestructureTuple(CAstNode N, InstructionContext C) {
        RawValue result1 = getResult(N, 0);
        RawValue result2 = getResult(N, 1);
        RawValue operand = getSingleOperand(N);
        C.bc.block.addInstruction(new FieldReadInstruction(result1.Name, result1.Type, operand.Name, "0", C));
        C.bc.block.addInstruction(new FieldReadInstruction(result2.Name, result2.Type, operand.Name, "1", C));
        // Handle allocateunitarray builtin.
        if (C.valueTable().getValue(operand.Name) instanceof ArrayTupleValue) {
            C.bc.block.addInstruction(new AssignInstruction(result1.Name, result2.Name, C));
        }
        return null;
    }

    @Override
    protected SILIRInstruction visitStruct(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        Value StructValue = new Value(result.Name, result.Type);
        C.valueTable().add(StructValue);
        C.bc.block.addInstruction(new NewInstruction(result.Name, result.Type, C));
        for (CAstNode field : N.getChild(2).getChildren()) {
            String FieldName = RawUtil.getStringValue(field, 0);
            String FieldValue = RawUtil.getStringValue(field, 1);
            C.bc.block.addInstruction(new FieldWriteInstruction(StructValue.name, FieldName, FieldValue, C));
        }
        return null;
    }

    @Override
    protected SILIRInstruction visitStructExtract(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        String StructName = getStringValue(N, 2);
        String FieldName = getStringValue(N, 3);
        return tryAliasRead(result, StructName, FieldName, C);
    }

    @Override
    protected SILIRInstruction visitStructElementAddr(CAstNode N, InstructionContext C) {
        String StructName = getStringValue(N, 2);
        String FieldName = getStringValue(N, 3);
        RawValue result = getSingleResult(N);
        return new FieldAliasInstruction(result.Name, result.Type, StructName, FieldName, C);
    }

    @Override
    protected SILIRInstruction visitDestructureStruct(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitObject(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitRefElementAddr(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        String FieldName = RawUtil.getStringValue(N, 2);
        return new FieldAliasInstruction(result.Name, result.Type, operand.Name, FieldName, C);
    }

    @Override
    protected SILIRInstruction visitRefTailAddr(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitEnum(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        String EnumName = getStringValue(N, 2);
        String CaseName = getStringValue(N, 3);
        C.bc.block.addInstruction(new NewInstruction(result.Name, result.Type, C));
        try {
            RawValue operand = getSingleOperand(N); // Is optional, so can cause exception.
            C.bc.block.addInstruction(new FieldWriteInstruction(result.Name, "data", operand.Name, C));
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    protected SILIRInstruction visitUncheckedEnumData(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return tryAliasRead(result, operand.Name, "data", C);
    }

    @Override
    protected SILIRInstruction visitInitEnumDataAddr(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new FieldAliasInstruction(result.Name, result.Type, operand.Name, "data", C);
    }

    @Override
    protected SILIRInstruction visitInjectEnumAddr(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    protected SILIRInstruction visitUncheckedTakeEnumDataAddr(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new FieldAliasInstruction(result.Name, result.Type, operand.Name, "data", C);
    }

    @Override
    protected SILIRInstruction visitSelectEnum(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitSelectEnumAddr(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitInitExistentialAddr(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitInitExistentialValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitDeinitExistentialAddr(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    protected SILIRInstruction visitDeinitExistentialValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitOpenExistentialAddr(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitOpenExistentialValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitInitExistentialRef(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitOpenExistentialRef(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitInitExistentialMetatype(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitOpenExistentialMetatype(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitAllocExistentialBox(CAstNode N, InstructionContext C) {
        return visitAllocBox(N, C);
    }

    @Override
    protected SILIRInstruction visitProjectExistentialBox(CAstNode N, InstructionContext C) {
        return visitProjectBox(N, C);
    }

    @Override
    protected SILIRInstruction visitOpenExistentialBox(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitOpenExistentialBoxValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitDeallocExistentialBox(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    protected SILIRInstruction visitProjectBlockStorage(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitInitBlockStorageHeader(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitUpcast(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitAddressToPointer(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitPointerToAddress(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitUncheckedRefCast(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitUncheckedRefCastAddr(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitUncheckedAddrCast(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitUncheckedTrivialBitCast(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitUncheckedBitwiseCast(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitUncheckedOwnershipConversion(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitRefToRawPointer(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitRawPointerToRef(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitRefToUnowned(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitUnownedToRef(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitRefToUnmanaged(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitUnmanagedToRef(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitConvertFunction(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitConvertEscapeToNoEscape(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitThinFunctionToPointer(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitPointerToThinFunction(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitClassifyBridgeObject(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitValueToBridgeObject(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitRefToBridgeObject(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitBridgeObjectToRef(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitBridgeObjectToWord(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitThinToThickFunction(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitThickToObjCMetatype(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitObjCToThickMetatype(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitObjCMetatypeToObject(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitObjCExistentialMetatypeToObject(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitUnconditionalCheckedCast(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitUnconditionalCheckedCastAddr(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitUnconditionalCheckedCastValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitCondFail(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitUnreachable(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitReturn(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        return new ReturnInstruction(operand.Name, C);
    }

    @Override
    protected SILIRInstruction visitThrow(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitYield(CAstNode N, InstructionContext C) {
        // TODO: TEMPORARY, NEED TO HANDLE BRANCHING TO RESUME/UNWIND
        String ResumeLabel = RawUtil.getStringValue(N, 0);
        String UnwindLabel = RawUtil.getStringValue(N, 1);
        ArrayList<String> values = new ArrayList<>();
        for (CAstNode value : N.getChild(2).getChildren()) {
            values.add((String)value.getValue());
        }
        return new YieldInstruction(values, C);
    }

    @Override
    protected SILIRInstruction visitUnwind(CAstNode N, InstructionContext C) {
        return new ReturnInstruction(C);
    }

    @Override
    protected SILIRInstruction visitBr(CAstNode N, InstructionContext C) {
        String DestBranch = RawUtil.getStringValue(N, 0);
        int DestBlockNo = Integer.parseInt(DestBranch);
        ArrayList<String> args = new ArrayList<>();
        for (CAstNode arg : N.getChild(1).getChildren()) {
            String ArgName = RawUtil.getStringValue(arg, 0);
            args.add(ArgName);
        }
        return doBranch(DestBlockNo, args, C);
    }

    private BasicBlock assignBlockArgs(int dest, ArrayList<String> args, InstructionContext C) {
        BasicBlock bb = C.bc.fc.function.getBlock(dest);
        for (int i = 0; i < args.size(); ++i) {
            C.bc.block.addInstruction(new AssignInstruction(bb.getArgument(i).name, bb.getArgument(i).type, args.get(i), C));
        }
        return bb;
    }

    private SILIRInstruction doBranch(int dest, ArrayList<String> args, InstructionContext C) {
        BasicBlock bb = assignBlockArgs(dest, args, C);
        return new GotoInstruction(bb, C);
    }

    @Override
    protected SILIRInstruction visitCondBr(CAstNode N, InstructionContext C) {
        String CondOperandName = getStringValue(N, 0);
        String TrueDestName = getStringValue(N, 1);
        String FalseDestName = getStringValue(N, 2);
        // These types can be whatever, really.
        String IntermediateLiteralName = UUID.randomUUID().toString();
        C.bc.block.addInstruction(new LiteralInstruction(1, IntermediateLiteralName, "$Builtin.Int1", C));
        String IntermediateConditionName = UUID.randomUUID().toString();
        // Set arguments
        ArrayList<String> trueArgs = new ArrayList<>();
        for (CAstNode arg : N.getChild(1).getChildren()) {
            String ArgName = RawUtil.getStringValue(arg, 0);
            trueArgs.add(ArgName);
        }
        BasicBlock trueBB = assignBlockArgs(Integer.parseInt(TrueDestName), trueArgs, C);
        ArrayList<String> falseArgs = new ArrayList<>();
        for (CAstNode arg : N.getChild(2).getChildren()) {
            String ArgName = RawUtil.getStringValue(arg, 0);
            falseArgs.add(ArgName);
        }
        BasicBlock falseBB = assignBlockArgs(Integer.parseInt(FalseDestName), falseArgs, C);
        C.bc.block.addInstruction(new BinaryOperatorInstruction(IntermediateConditionName, "$Bool", "==", IntermediateLiteralName, CondOperandName, C));
        return new CondtionBranchInstruction(IntermediateConditionName, trueBB, falseBB, C);
    }

    @Override
    protected SILIRInstruction visitSwitchValue(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitSelectValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitSwitchEnum(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitSwitchEnumAddr(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitDynamicMethodBr(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitCheckedCastBr(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitCheckedCastValueBr(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitCheckedCastAddrBr(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    protected SILIRInstruction visitTryApply(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }
}
