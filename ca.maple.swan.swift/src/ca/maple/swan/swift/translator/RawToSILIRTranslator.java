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
import ca.maple.swan.swift.translator.silir.context.*;
import ca.maple.swan.swift.translator.silir.instructions.*;
import ca.maple.swan.swift.translator.silir.summaries.BuiltinHandler;
import ca.maple.swan.swift.translator.silir.values.*;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;

import java.math.BigDecimal;
import java.util.*;

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
 *
 * One of the most complicated parts of this translator is its handling of (asymmetric) coroutines. Effectively,
 * coroutines are decomposed and inlined - but since one can not be translated without the other, they are not "linked"
 * right away.
 *
 * Note: Some builtins (at least operators) take functions (closures) as parameters, and these are ignored when the
 *       operator call is replaced with an operator instruction.
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

        try {
            VisitProgram(pc);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return pc;

    }

    private void VisitProgram(ProgramContext pc) {
        for (Function f : pc.getFunctions()) {
            // Check if it is a coroutine.
            // This is a bit inefficient. Ideally we could ask the Swift compiler if a function is a coroutine.
            for (BasicBlock b : f.getBlocks()) {
                for (InstructionNode inst : pc.toTranslate.get(b)) {
                    if (inst.getName().equals("yield")) {
                        f.setCoroutine();
                    }
                }
            }
            if (!f.isCoroutine()) {
                VisitFunction(f, pc);
            }
        }
        for (Function f : pc.getFunctions()) {
            if (f.isCoroutine()) {
                pc.removeFunction(f);
            }
        }
    }

    private FunctionContext VisitCoroutine(Function f, ProgramContext pc, CoroutineContext cc, FunctionContext parentContext) {
        Function copy = new Function(f, pc);
        FunctionContext fc = new FunctionContext(copy, pc);
        fc.cc = cc;
        int i = 0;
        while (true) {
            if (i == copy.getBlocks().size()) {
                break;
            }
            BasicBlock b = copy.getBlock(i);
            VisitBlock(b, fc);
            ++i;
        }
        // Merge coroutine bodies.
        for (String token : fc.coroutines.keySet()) {
            FunctionContext curr = fc.coroutines.get(token);
            for (BasicBlock bb : curr.function.getBlocks()) {
                copy.addBlock(bb);
            }
        }
        return fc;
    }

    private void VisitFunction(Function f, ProgramContext pc) {
        FunctionContext fc = new FunctionContext(f, pc);
        int i = 0;
        while (true) {
            if (i == f.getBlocks().size()) {
                break;
            }
            BasicBlock b = f.getBlock(i);
            VisitBlock(b, fc);
            ++i;
        }
        // Merge coroutine bodies.
        for (String token : fc.coroutines.keySet()) {
            FunctionContext curr = fc.coroutines.get(token);
            for (BasicBlock bb : curr.function.getBlocks()) {
                f.addBlock(bb);
            }
        }
    }

    private void VisitBlock(BasicBlock b, FunctionContext fc) {
        BlockContext bc = new BlockContext(b, fc);
        if (fc.pc.toTranslate.containsKey(b)) {
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
        } // else is a generated block (for coroutine handling)
    }

    @Override
    protected CAstSourcePositionMap.Position getInstructionPosition(CAstNode N) {
        return (CAstSourcePositionMap.Position) N.getChild(1).getValue();
    }

    /********************** INSTRUCTION TRANSLATION **********************
     * Every instruction has some extra information commented before it.
     *
     * FREQUENCY: How often this instruction occurs in test cases. This is
     *            just set relative to the frequency of other instructions.
     *            VERY COMMON > COMMON > UNCOMMON > RARE > UNSEEN
     * STATUS: Whether this instruction is translated.
     * CONFIDENCE: Confidence level of translation. e.g. LOW would probably
     *             mean its an initial translation based off SIL.rst but
     *             could be wrong in practice.
     *
     * Please see https://github.com/apple/swift/blob/master/docs/SIL.rst
     * for more information about what each instruction does.
     */

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitAllocStack(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        return new NewInstruction(result.Name, result.Type, C);
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitAllocRef(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        return new NewInstruction(result.Name, result.Type, C);
    }

    @Override
    // FREQUENCY: UNCOMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitAllocRefDynamic(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        return new NewInstruction(result.Name, result.Type, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitAllocBox(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        return new NewInstruction(result.Name, result.Type, C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitAllocValueBuffer(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitAllocGlobal(CAstNode N, InstructionContext C) {
        String GlobalName = RawUtil.getStringValue(N, 0);
        String GlobalType = RawUtil.getStringValue(N, 1);
        return new NewGlobalInstruction(GlobalName, GlobalType, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitDeallocStack(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitDeallocBox(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitProjectBox(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        int Field = getIntValue(N, 2);
        // TODO: Do something with this field?
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitDeallocRef(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitDeallocPartialRef(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitDeallocValueBuffer(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitProjectValueBuffer(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitDebugValue(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitDebugValueAddr(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitLoad(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return tryAliasRead(result, operand.Name, "value", C);
    }


    private SILIRInstruction tryAliasWrite(String SourceName, String DestName, InstructionContext C) {
        Value v = C.valueTable().getPossibleAlias(DestName);
        if (v instanceof FieldAliasValue) {
            return new FieldWriteInstruction(((FieldAliasValue) v).value.name, ((FieldAliasValue) v).field, SourceName, C);
        } else if (v instanceof ArrayValue) {
            return new FieldWriteInstruction(DestName, "0", SourceName, C);
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
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitStore(CAstNode N, InstructionContext C) {
        String SourceName = RawUtil.getStringValue(N, 0);
        String DestName = RawUtil.getStringValue(N, 1);
        return tryAliasWrite(SourceName, DestName, C);
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitStoreBorrow(CAstNode N, InstructionContext C) {
        return visitStore(N, C);
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitLoadBorrow(CAstNode N, InstructionContext C) {
        return visitLoad(N, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitBeginBorrow(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitEndBorrow(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitAssign(CAstNode N, InstructionContext C) {
        String SourceName = RawUtil.getStringValue(N, 0);
        String DestName = RawUtil.getStringValue(N, 1);
        return tryAliasWrite(SourceName, DestName, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitAssignByWrapper(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitMarkUninitialized(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
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
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitMarkUninitializedBehavior(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitCopyAddr(CAstNode N, InstructionContext C) {
        // This is an assign since we can't handle pointer value copies.
        String SourceName = RawUtil.getStringValue(N, 0);
        String DestName = RawUtil.getStringValue(N, 1);
        return new AssignInstruction(DestName, SourceName, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitDestroyAddr(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitIndexAddr(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue idx = getOperand(N, 1);
        RawValue result = getSingleResult(N);
        Value literal = C.valueTable().getValue(idx.Name);
        Assertions.productionAssertion(literal instanceof LiteralValue);
        return new FieldAliasInstruction(result.Name, result.Type, operand.Name, ((LiteralValue)literal).literal.toString(), C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitTailAddr(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitIndexRawPointer(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitBindMemory(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitBeginAccess(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitEndAccess(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitBeginUnpairedAccess(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitEndUnpairedAccess(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitStrongRetain(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitStrongRelease(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitSetDeallocating(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitStrongRetainUnowned(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitUnownedRetain(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitUnownedRelease(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitLoadWeak(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitStoreWeak(CAstNode N, InstructionContext C) {
        return visitStore(N, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitLoadUnowned(CAstNode N, InstructionContext C) {
        return visitLoad(N, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitStoreUnowned(CAstNode N, InstructionContext C) {
        return visitStore(N, C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitFixLifetime(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitEndLifetime(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitMarkDependence(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitIsUnique(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitIsEscapingClosure(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new UnaryOperatorInstruction(result.Name, result.Type, "binary_arb", operand.Name, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitCopyBlock(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new AssignInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitCopyBlockWithoutEscaping(CAstNode N, InstructionContext C) {
        return visitCopyBlock(N, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitFunctionRef(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        String FuncName = RawUtil.getStringValue(N, 2);
        Function f = C.bc.fc.pc.getFunction(FuncName);
        return (f == null)
                ? new BuiltinInstruction(FuncName, result.Name, result.Type, C)
                : new FunctionRefInstruction(result.Name, result.Type, f, C);
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitDynamicFunctionRef(CAstNode N, InstructionContext C) {
        return visitFunctionRef(N, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitPrevDynamicFunctionRef(CAstNode N, InstructionContext C) {
        return visitFunctionRef(N, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitGlobalAddr(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        String GlobalName = RawUtil.getStringValue(N, 2);
        return new AssignGlobalInstruction(result.Name, result.Type, GlobalName, C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitGlobalValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitIntegerLiteral(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        int Integer = RawUtil.getIntValue(N, 2);
        return new LiteralInstruction(Integer, result.Name, result.Type, C);
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitFloatLiteral(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        float Float = ((BigDecimal) N.getChild(2).getValue()).floatValue();
        return new LiteralInstruction(Float, result.Name, result.Type, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitStringLiteral(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        String StringValue = RawUtil.getStringValue(N, 2);
        return new LiteralInstruction(StringValue, result.Name, result.Type, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitClassMethod(CAstNode N, InstructionContext C) {
        return visitFunctionRef(N, C);
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitObjCMethod(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        String FuncName = RawUtil.getStringValue(N, 2);
        return new BuiltinInstruction(FuncName, result.Name, result.Type, C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitSuperMethod(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitObjCSuperMethod(CAstNode N, InstructionContext C) {
        return visitObjCMethod(N, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitWitnessMethod(CAstNode N, InstructionContext C) {
        return visitFunctionRef(N, C);
    }

    private Function createFakeFunction(String name, String returnType, ArrayList<Argument> args, InstructionContext C) {
        Function f = new Function(name, returnType, null, args, true);
        BasicBlock bb = new BasicBlock(0);
        String randomName = UUID.randomUUID().toString();
        bb.addInstruction(new NewInstruction(randomName, returnType, C));
        bb.addInstruction(new ReturnInstruction(randomName, C));
        f.addBlock(bb);
        return f;
    }

    private boolean handleComparisonOperators(String resultName, String resultType, String operator, String operand1, String operand2, InstructionContext C) {
        String tempValue = UUID.randomUUID().toString();
        String[] comparisonOperators = {"==", "!=", "<=", ">="};
        if (Arrays.asList(comparisonOperators).contains(operator)) {
            String temp = UUID.randomUUID().toString();
            C.bc.block.addInstruction(new BinaryOperatorInstruction(temp, "$Int", operator, operand1, operand2, C));
            C.bc.block.addInstruction(new NewInstruction(resultName, resultType, C));
            C.bc.block.addInstruction(new FieldWriteInstruction(resultName, "_value", temp, C));
            return true;
        }
        return false;
    }

    private boolean handleInfixOperators(String resultName, String resultType, String operator, String operand1, String operand2, InstructionContext C) {
        String tempValue = UUID.randomUUID().toString();
        String actualOperator = null;
        switch (operator) {
            case "-=":
                actualOperator = "-";
                break;
            case "+=":
                actualOperator = "+";
                break;
            case "*=":
                actualOperator = "*";
                break;
            case "/=":
                actualOperator = "/";
                break;
            case "%=":
                actualOperator = "%";
                break;
            // TODO: Complete
            default:
                return handleComparisonOperators(resultName, resultType, operator, operand1, operand2, C);
        }
        FieldReadInstruction inst = new FieldReadInstruction(tempValue, C.valueTable().getValue(operand1).type, operand1, "value", C);
        C.bc.block.addInstruction(inst);
        C.bc.block.addInstruction(new BinaryOperatorInstruction(tempValue, actualOperator, tempValue, operand2, C));
        C.bc.block.addInstruction(new FieldWriteInstruction(operand1, "value", tempValue, C));
        return true;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitApply(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        String FuncRefValue = RawUtil.getStringValue(N, 2);
        Value refValue = C.valueTable().getValue(FuncRefValue);
        CAstNode FuncNode = N.getChild(3);
        ArrayList<String> args = new ArrayList<>();
        for (CAstNode arg : FuncNode.getChildren()) {
            args.add((String) arg.getValue());
        }
        if (N.getChildren().size() == 5) { // An operator.
            CAstNode OperatorNode = N.getChild(4);
            if (OperatorNode.getKind() == CAstNode.UNARY_EXPR) {
                String operator = getStringValue(OperatorNode, 0);
                String operand = getStringValue(OperatorNode, 1);
                return new UnaryOperatorInstruction(result.Name, result.Type, operator, operand, C);
            } else if (OperatorNode.getKind() == CAstNode.BINARY_EXPR) {
                String operator = getStringValue(OperatorNode, 0);
                String operand1 = getStringValue(OperatorNode, 1);
                String operand2 = getStringValue(OperatorNode, 2);
                if (!handleInfixOperators(result.Name, result.Type, operator, operand1, operand2, C)) {
                    return new BinaryOperatorInstruction(result.Name, result.Type, operator, operand1, operand2, C);
                } else {
                    return null;
                }
            } else {
                Assertions.UNREACHABLE("Unexpected kind");
                return null;
            }
        }
        if (refValue instanceof BuiltinFunctionRefValue) {
            String name = ((BuiltinFunctionRefValue) refValue).getFunction();
            if (BuiltinHandler.isSummarized(name)) {
                return BuiltinHandler.findSummary(name, result.Name, result.Type, args, C);
            } else {
                // Make a function for builtins we don't have summaries for.
                ArrayList<Argument> funcArgs = new ArrayList<>();
                for (String arg : args) {
                    Argument a = new Argument(arg, C.valueTable().getValue(arg).type);
                    funcArgs.add(a);
                }
                Function f = createFakeFunction(((BuiltinFunctionRefValue) refValue).getFunction(), result.Type, funcArgs, C);
                C.bc.fc.pc.addFunction(f.getName(), f);
                ((BuiltinFunctionRefValue) refValue).summaryCreated = true;
                return new ApplyInstruction(refValue.name, result.Name, result.Type, args, C);
            }
        } else if (refValue instanceof FunctionRefValue) {
            return new ApplyInstruction(FuncRefValue, result.Name, result.Type, args, C);
        } else {
            Assertions.UNREACHABLE("Unexpected function ref value type");
            return null;
        }
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitBeginApply(CAstNode N, InstructionContext C) {
        ArrayList<RawValue> yieldedValues = new ArrayList<>();
        for (CAstNode n : N.getChild(0).getChildren()) {
            yieldedValues.add(new RawValue((String)n.getChild(0).getValue(), (String)n.getChild(1).getValue()));
        }
        String FuncRefValue = RawUtil.getStringValue(N, 2);
        String Token = RawUtil.getStringValue(N, 1);
        Value refValue = C.valueTable().getValue(FuncRefValue);
        CAstNode FuncNode = N.getChild(3);
        ArrayList<String> args = new ArrayList<>();
        for (CAstNode arg : FuncNode.getChildren()) {
            args.add((String) arg.getValue());
        }
        if (N.getChildren().size() == 5) { // An operator.
            Assertions.UNREACHABLE("This is weird that this occured");
            return null;
        }
        if (refValue instanceof BuiltinFunctionRefValue) {
            // TODO: Handle builtins that return multiple values
            Assertions.UNREACHABLE("Not yet handled");
            return null;
        } else if (refValue instanceof FunctionRefValue) {
            ((FunctionRefValue) refValue).ignore = true;
            BasicBlock bb = new BasicBlock(C.bc.fc.function.getBlocks().size());
            C.bc.fc.function.addBlock(bb);
            ArrayList<Value> values = new ArrayList<>();
            for (RawValue value : yieldedValues) {
                values.add(new Value(value.Name, value.Type));
            }
            FunctionContext coroutine = this.VisitCoroutine(
                    ((FunctionRefValue) refValue).getFunction(),
                    C.bc.fc.pc,
                    new CoroutineContext(values, bb),
                    C.bc.fc);
            C.bc.fc.coroutines.put(Token, coroutine);
            GotoInstruction inst = new GotoInstruction(coroutine.function.getBlock(0), C);
            inst.setComment("coroutine " + coroutine.function.getName());
            C.bc.block.addInstruction(inst);
            C.bc.block = bb;
            return null;
        } else {
            Assertions.UNREACHABLE("Unexpected function ref value type");
            return null;
        }
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitAbortApply(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        FunctionContext fc = C.bc.fc.coroutines.get(operand.Name);
        CoroutineContext cc = fc.cc;
        Assertions.productionAssertion(cc.unwindBlock != null);
        BasicBlock unwindBlock = cc.unwindBlock;
        BasicBlock bb = new BasicBlock(C.bc.fc.function.getBlocks().size());
        C.bc.fc.function.addBlock(bb);
        cc.returnUnwindBlock = bb;
        cc.linkUnwind();
        GotoInstruction inst = new GotoInstruction(unwindBlock, C);
        inst.setComment("unwind coroutine " + fc.getFunction().getName());
        C.bc.block.addInstruction(inst);
        C.bc.block = bb;
        return null;
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitEndApply(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        FunctionContext fc = C.bc.fc.coroutines.get(operand.Name);
        CoroutineContext cc = fc.cc;
        Assertions.productionAssertion(cc.resumeBlock != null);
        BasicBlock resumeBlock = cc.resumeBlock;
        BasicBlock bb = new BasicBlock(C.bc.fc.function.getBlocks().size());
        C.bc.fc.function.addBlock(bb);
        cc.returnResumeBlock = bb;
        cc.linkResume();
        GotoInstruction inst = new GotoInstruction(resumeBlock, C);
        inst.setComment("resume coroutine " + fc.getFunction().getName());
        C.bc.block.addInstruction(inst);
        C.bc.block = bb;
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitPartialApply(CAstNode N, InstructionContext C) {
        // TODO
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitBuiltin(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        String functionName = getStringValue(N, 2);
        String builtinIntermediate = UUID.randomUUID().toString();
        C.bc.block.addInstruction(new BuiltinInstruction(functionName, builtinIntermediate, "temp", C));
        ArrayList<Argument> funcArgs = new ArrayList<>();
        ArrayList<String> args = new ArrayList<>();
        for (CAstNode argNode : N.getChild(3).getChildren()) {
            String arg = (String)argNode.getValue();
            Argument a = new Argument(arg, C.valueTable().getValue(arg).type);
            funcArgs.add(a);
            args.add(arg);
        }
        Function f = createFakeFunction(functionName, result.Type, funcArgs, C);
        C.bc.fc.pc.addFunction(f.getName(), f);
        ((BuiltinFunctionRefValue) C.valueTable().getValue(builtinIntermediate)).summaryCreated = true;
        return new ApplyInstruction(builtinIntermediate, result.Name, result.Type, args, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitMetatype(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        return new LiteralInstruction(result.Type, result.Name, result.Type, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitValueMetatype(CAstNode N, InstructionContext C) {
        return visitMetatype(N, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitExistentialMetatype(CAstNode N, InstructionContext C) {
        return visitMetatype(N, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitObjCProtocol(CAstNode N, InstructionContext C) {
        // Creates a $Protocol value, which is then used in calls to builtin functions.
        // Could also make it a LiteralInstruction - doesn't really matter.
        RawValue result = getSingleResult(N);
        return new NewInstruction(result.Name, result.Type, C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitRetainValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitRetainValueAddr(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitUnmanagedRetainValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitCopyValue(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitReleaseValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitReleaseValueAddr(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitUnmanagedReleaseValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitDestroyValue(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitAutoreleaseValue(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
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
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitTupleExtract(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        RawValue operand = getSingleOperand(N);
        String field = getStringValue(N, 2);
        return tryAliasRead(result, operand.Name, field, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitTupleElementAddr(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        String Index = getStringValue(N, 2);
        return new FieldAliasInstruction(result.Name, result.Type, operand.Name, Index, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitDestructureTuple(CAstNode N, InstructionContext C) {
        RawValue result1 = getResult(N, 0);
        RawValue result2 = getResult(N, 1);
        RawValue operand = getSingleOperand(N);
        C.bc.block.addInstruction(new FieldReadInstruction(result1.Name, result1.Type, operand.Name, "0", C));
        C.bc.block.addInstruction(new FieldReadInstruction(result2.Name, result2.Type, operand.Name, "1", C));
        // Handle allocateunitarray builtin.
        if (C.valueTable().getValue(operand.Name) instanceof ArrayTupleValue) {
            C.valueTable().replace(C.valueTable().getValue(result1.Name), new ArrayValue(result1.Name, result1.Type));
            C.valueTable().replace(C.valueTable().getValue(result2.Name), new ArrayValue(result2.Name, result2.Type));
            C.bc.block.addInstruction(new AssignInstruction(result1.Name, result2.Name, C));
        }
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
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
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitStructExtract(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        String StructName = getStringValue(N, 2);
        String FieldName = getStringValue(N, 3);
        return tryAliasRead(result, StructName, FieldName, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitStructElementAddr(CAstNode N, InstructionContext C) {
        String StructName = getStringValue(N, 2);
        String FieldName = getStringValue(N, 3);
        RawValue result = getSingleResult(N);
        return new FieldAliasInstruction(result.Name, result.Type, StructName, FieldName, C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitDestructureStruct(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitObject(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitRefElementAddr(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        String FieldName = RawUtil.getStringValue(N, 2);
        return new FieldAliasInstruction(result.Name, result.Type, operand.Name, FieldName, C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitRefTailAddr(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitEnum(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        String EnumName = getStringValue(N, 2);
        String CaseName = getStringValue(N, 3);
        C.bc.block.addInstruction(new NewInstruction(result.Name, result.Type, C));
        // Add the case name so we can later compare it in instructions such as switch typeof.
        String tempName = UUID.randomUUID().toString();
        C.bc.block.addInstruction(new LiteralInstruction(CaseName, tempName, "$String", C));
        C.bc.block.addInstruction(new FieldWriteInstruction(result.Name, "type", tempName, C));
        try {
            RawValue operand = getSingleOperand(N); // Is optional, so can cause exception.
            C.bc.block.addInstruction(new FieldWriteInstruction(result.Name, "data", operand.Name, C));
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitUncheckedEnumData(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return tryAliasRead(result, operand.Name, "data", C);
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitInitEnumDataAddr(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new FieldAliasInstruction(result.Name, result.Type, operand.Name, "data", C);
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitInjectEnumAddr(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitUncheckedTakeEnumDataAddr(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new FieldAliasInstruction(result.Name, result.Type, operand.Name, "data", C);
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitSelectEnum(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        String EnumName = getStringValue(N, 2);
        ArrayList<Pair<String, String>> cases = new ArrayList<>();
        for (CAstNode CaseNode : N.getChild(3).getChildren()) {
            String CaseName = getStringValue(CaseNode, 0);
            String CaseValue = getStringValue(CaseNode, 1);
            cases.add(Pair.make(CaseName, CaseValue));
        }
        String defaultName = null;
        if (N.getChildren().size() > 4) {
            defaultName = getStringValue(N, 4);
        }
        String enumTypeValue = UUID.randomUUID().toString();
        C.bc.block.addInstruction(new FieldReadInstruction(enumTypeValue, "$String", EnumName, "type", C));
        return new SwitchAssignValueInstruction(result.Name, result.Type, enumTypeValue, cases, defaultName, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitSelectEnumAddr(CAstNode N, InstructionContext C) {
        RawValue result = getSingleResult(N);
        String EnumName = getStringValue(N, 2);
        ArrayList<Pair<String, String>> cases = new ArrayList<>();
        for (CAstNode CaseNode : N.getChild(3).getChildren()) {
            String CaseName = getStringValue(CaseNode, 0);
            String CaseValue = getStringValue(CaseNode, 1);
            cases.add(Pair.make(CaseName, CaseValue));
        }
        String defaultName = null;
        if (N.getChildren().size() > 4) {
            defaultName = getStringValue(N, 4);
        }
        String actualEnumValue = UUID.randomUUID().toString();
        C.bc.block.addInstruction(new FieldReadInstruction(EnumName, "temp", EnumName, "value", C));
        String enumTypeValue = UUID.randomUUID().toString();
        C.bc.block.addInstruction(new FieldReadInstruction(enumTypeValue, "$String", actualEnumValue, "type", C));
        return new SwitchAssignValueInstruction(result.Name, result.Type, enumTypeValue, cases, defaultName, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitInitExistentialAddr(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitInitExistentialValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitDeinitExistentialAddr(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitDeinitExistentialValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitOpenExistentialAddr(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitOpenExistentialValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitInitExistentialRef(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitOpenExistentialRef(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitInitExistentialMetatype(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitOpenExistentialMetatype(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitAllocExistentialBox(CAstNode N, InstructionContext C) {
        return visitAllocBox(N, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitProjectExistentialBox(CAstNode N, InstructionContext C) {
        return visitProjectBox(N, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitOpenExistentialBox(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitOpenExistentialBoxValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitDeallocExistentialBox(CAstNode N, InstructionContext C) {
        // NOP
        return null;
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitProjectBlockStorage(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        C.bc.block.addInstruction(new NewInstruction(result.Name, result.Type, C));
        C.bc.block.addInstruction(new FieldWriteInstruction(result.Name, "value", operand.Name, C));
        return null;
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: MED
    protected SILIRInstruction visitInitBlockStorageHeader(CAstNode N, InstructionContext C) {
        // Objc instruction, the result of which is a block. The result is used in calls
        // to objc builtins, so we can just set the result to the operand.
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        String funcRef = getStringValue(N, 2);
        String unusedReturnValue = UUID.randomUUID().toString();
        ArrayList<String> args = new ArrayList<>();
        args.add(operand.Name);
        C.bc.block.addInstruction(new ApplyInstruction(funcRef, unusedReturnValue, "temp", args, C));
        C.bc.block.addInstruction(new AssignInstruction(result.Name, operand.Name, C));
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitUpcast(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitAddressToPointer(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitPointerToAddress(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitUncheckedRefCast(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitUncheckedRefCastAddr(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitUncheckedAddrCast(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitUncheckedTrivialBitCast(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitUncheckedBitwiseCast(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitUncheckedOwnershipConversion(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitRefToRawPointer(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitRawPointerToRef(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitRefToUnowned(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitUnownedToRef(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitRefToUnmanaged(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitUnmanagedToRef(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitConvertFunction(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitConvertEscapeToNoEscape(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitThinFunctionToPointer(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitPointerToThinFunction(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitClassifyBridgeObject(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitValueToBridgeObject(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitRefToBridgeObject(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitBridgeObjectToRef(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitBridgeObjectToWord(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitThinToThickFunction(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitThickToObjCMetatype(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitObjCToThickMetatype(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitObjCMetatypeToObject(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitObjCExistentialMetatypeToObject(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitUnconditionalCheckedCast(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitUnconditionalCheckedCastAddr(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        return new ImplicitCopyInstruction(result.Name, operand.Name, C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitUnconditionalCheckedCastValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitCondFail(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        String Literal1 = UUID.randomUUID().toString();
        C.bc.block.addInstruction(new LiteralInstruction(1, Literal1, "$Builtin.Int1", C));
        String ConditionIntermediate = UUID.randomUUID().toString();
        C.bc.block.addInstruction(new BinaryOperatorInstruction(ConditionIntermediate, "$Bool", "==", operand.Name, Literal1, C));
        C.bc.block.addInstruction(new ConditionalThrowInstruction(ConditionIntermediate, C));
        return null;
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitUnreachable(CAstNode N, InstructionContext C) {
        String LiteralResult = UUID.randomUUID().toString();
        LiteralInstruction inst = new LiteralInstruction("unreachable", LiteralResult, "$String", C);
        C.bc.block.addInstruction(inst);
        return new ThrowInstruction(LiteralResult, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitReturn(CAstNode N, InstructionContext C) {
        if (C.bc.fc.cc != null) {
            GotoInstruction gotoInstr = new GotoInstruction(null, C);
            gotoInstr.setComment("return from coroutine");
            C.bc.fc.cc.gotoReturnResume = gotoInstr;
            return gotoInstr;
        }
        RawValue operand = getSingleOperand(N);
        return new ReturnInstruction(operand.Name, C);
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitThrow(CAstNode N, InstructionContext C) {
        RawValue Error = getSingleOperand(N);
        return new ThrowInstruction(Error.Name, C);
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitYield(CAstNode N, InstructionContext C) {
        if (C.bc.fc.cc == null) {
            Assertions.UNREACHABLE("Coroutine should have a coroutine context");
            return null;
        }
        int ResumeLabel = Integer.parseInt(RawUtil.getStringValue(N, 0));
        int UnwindLabel = Integer.parseInt(RawUtil.getStringValue(N, 1));
        C.bc.fc.cc.resumeBlock = C.bc.fc.function.getBlock(ResumeLabel);
        C.bc.fc.cc.unwindBlock = C.bc.fc.function.getBlock(UnwindLabel);
        int i = 0;
        for (CAstNode value : N.getChild(2).getChildren()) {
            Value receiver = C.bc.fc.cc.yieldedValues.get(i);
            C.bc.block.addInstruction(new AssignInstruction(receiver.name, receiver.type, (String)value.getValue(), C));
            ++i;
        }
        GotoInstruction inst = new GotoInstruction(C.bc.fc.cc.returnBlock, C);
        inst.setComment("yield");
        return inst;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitUnwind(CAstNode N, InstructionContext C) {
        if (C.bc.fc.cc == null) {
            Assertions.UNREACHABLE("Coroutine should have a coroutine context");
            return null;
        }
        GotoInstruction gotoInstr = new GotoInstruction(null, C);
        gotoInstr.setComment("unwind");
        C.bc.fc.cc.gotoReturnUnwind = gotoInstr;
        return gotoInstr;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitBr(CAstNode N, InstructionContext C) {
        String DestBranch = RawUtil.getStringValue(N, 0);
        int DestBlockNo = Integer.parseInt(DestBranch);
        ArrayList<String> args = new ArrayList<>();
        for (CAstNode arg : N.getChild(1).getChildren()) {
            String ArgName = (String)arg.getValue();
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
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
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
            String ArgName = (String)arg.getValue();
            trueArgs.add(ArgName);
        }
        BasicBlock trueBB = assignBlockArgs(Integer.parseInt(TrueDestName), trueArgs, C);
        ArrayList<String> falseArgs = new ArrayList<>();
        for (CAstNode arg : N.getChild(2).getChildren()) {
            String ArgName = (String)arg.getValue();
            falseArgs.add(ArgName);
        }
        BasicBlock falseBB = assignBlockArgs(Integer.parseInt(FalseDestName), falseArgs, C);
        C.bc.block.addInstruction(new BinaryOperatorInstruction(IntermediateConditionName, "$Bool", "==", IntermediateLiteralName, CondOperandName, C));
        return new ConditionalBranchInstruction(IntermediateConditionName, trueBB, falseBB, C);
    }

    @Override
    // FREQUENCY: VERY RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitSwitchValue(CAstNode N, InstructionContext C) {
        String ConditionName = (String) N.getChild(0).getValue();
        ArrayList<Pair<String, BasicBlock>> cases = new ArrayList<>();
        boolean createdGetInstruction = false;
        String tempDataValueName = null;
        for (CAstNode Case : N.getChild(1).getChildren()) {
            String CaseName = getStringValue(Case, 0);
            int DestBB = getIntValue(Case, 1);
            BasicBlock destBlock = C.bc.fc.function.getBlock(DestBB);
            cases.add(Pair.make(CaseName, destBlock));
            if (destBlock.getArguments().size() > 0) {
                if (!createdGetInstruction) {
                    tempDataValueName = UUID.randomUUID().toString();
                    C.bc.block.addInstruction(new FieldReadInstruction(tempDataValueName, destBlock.getArgument(0).type, CaseName, "data", C));
                    createdGetInstruction = true;
                }
                C.bc.block.addInstruction(new ImplicitCopyInstruction(destBlock.getArgument(0).name, tempDataValueName, C));
            }
        }
        BasicBlock defaultBlock = null;
        if (N.getChildren().size() > 2) {
            int DestBB = getIntValue(N, 2);
            defaultBlock = C.bc.fc.function.getBlock(DestBB);
        }
        return new SwitchValueInstruction(ConditionName, cases, defaultBlock, C);
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitSelectValue(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: VERY COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitSwitchEnum(CAstNode N, InstructionContext C) {
        String EnumName = (String) N.getChild(0).getValue();
        ArrayList<Pair<String, BasicBlock>> cases = new ArrayList<>();
        // If the enum has data and at least one case expects an argument, we generate an
        // instruction that puts the data in a temporary value to then copy to the block argument.
        // We only want to create this instruction once so we keep track of a bool.
        boolean createdGetInstruction = false;
        String tempDataValueName = null;
        for (CAstNode Case : N.getChild(1).getChildren()) {
            String CaseName = getStringValue(Case, 0);
            String tempCaseValue = UUID.randomUUID().toString();
            C.bc.block.addInstruction(new LiteralInstruction(CaseName, tempCaseValue, "$String", C));
            int DestBB = getIntValue(Case, 1);
            BasicBlock destBlock = C.bc.fc.function.getBlock(DestBB);
            cases.add(Pair.make(tempCaseValue, destBlock));
            // If the block takes an argument, do an implicit copy.
            // A block has zero or exactly one argument.
            if (destBlock.getArguments().size() > 0) {
                if (!createdGetInstruction) {
                    tempDataValueName = UUID.randomUUID().toString();
                    C.bc.block.addInstruction(new FieldReadInstruction(tempDataValueName, destBlock.getArgument(0).type, EnumName, "data", C));
                    createdGetInstruction = true;
                }
                C.bc.block.addInstruction(new AssignInstruction(destBlock.getArgument(0).name, destBlock.getArgument(0).type, tempDataValueName, C));
            }
        }
        BasicBlock defaultBlock = null;
        // If has default.
        if (N.getChildren().size() > 2) {
            // Default block takes no block arguments.
            int DestBB = getIntValue(N, 2);
            defaultBlock = C.bc.fc.function.getBlock(DestBB);
        }
        String tempName = UUID.randomUUID().toString();
        C.bc.block.addInstruction(new FieldReadInstruction(tempName, "$String", EnumName, "type", C));
        return new SwitchValueInstruction(tempName, cases, defaultBlock, C);
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitSwitchEnumAddr(CAstNode N, InstructionContext C) {
        String EnumName = (String) N.getChild(0).getValue();
        ArrayList<Pair<String, BasicBlock>> cases = new ArrayList<>();
        boolean createdGetInstruction = false;
        String tempDataValueName = null;
        for (CAstNode Case : N.getChild(1).getChildren()) {
            String CaseName = getStringValue(Case, 0);
            String tempCaseValue = UUID.randomUUID().toString();
            C.bc.block.addInstruction(new LiteralInstruction(CaseName, tempCaseValue, "$String", C));
            int DestBB = getIntValue(Case, 1);
            BasicBlock destBlock = C.bc.fc.function.getBlock(DestBB);
            cases.add(Pair.make(tempCaseValue, destBlock));
            if (destBlock.getArguments().size() > 0) {
                if (!createdGetInstruction) {
                    tempDataValueName = UUID.randomUUID().toString();
                    C.bc.block.addInstruction(new FieldReadInstruction(tempDataValueName, destBlock.getArgument(0).type, EnumName, "data", C));
                    createdGetInstruction = true;
                }
                C.bc.block.addInstruction(new AssignInstruction(destBlock.getArgument(0).name, destBlock.getArgument(0).type, tempDataValueName, C));
            }
        }
        BasicBlock defaultBlock = null;
        if (N.getChildren().size() > 2) {
            int DestBB = getIntValue(N, 2);
            defaultBlock = C.bc.fc.function.getBlock(DestBB);
        }
        String actualEnumValue = UUID.randomUUID().toString();
        C.bc.block.addInstruction(new FieldReadInstruction(actualEnumValue, "temp", EnumName, "value", C));
        String tempName = UUID.randomUUID().toString();
        C.bc.block.addInstruction(new FieldReadInstruction(tempName, "$String", actualEnumValue, "type", C));
        return new SwitchValueInstruction(tempName, cases, defaultBlock, C);
    }

    @Override
    // FREQUENCY: RARE
    // STATUS: TRANSLATED
    // CONFIDENCE: LOW - because the C++ way of getting the method is not verified.
    protected SILIRInstruction visitDynamicMethodBr(CAstNode N, InstructionContext C) {
        // ObjC method implementation lookup, just treat as arb conditional branch.
        RawValue operand = getSingleOperand(N);
        String methodName = getStringValue(N, 2);
        BasicBlock hasMethodBB = C.bc.fc.function.getBlock(getIntValue(N, 3));
        BasicBlock noMethodBB = C.bc.fc.function.getBlock(getIntValue(N, 4));
        // Create a new function ref for this method and use it as the argument to hasMethodBB.
        String functionRef = UUID.randomUUID().toString();
        C.bc.block.addInstruction(new BuiltinInstruction(methodName, functionRef, "temp", C));
        C.bc.block.addInstruction(new ImplicitCopyInstruction(hasMethodBB.getArgument(0).name, hasMethodBB.getArgument(0).type, C));
        String condition = UUID.randomUUID().toString();
        C.bc.block.addInstruction(new UnaryOperatorInstruction(condition, "$Bool", "unary_arb", operand.Name, C));
        C.bc.block.addInstruction(new ConditionalBranchInstruction(condition, hasMethodBB, noMethodBB, C));
        return null;
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitCheckedCastBr(CAstNode N, InstructionContext C) {
        RawValue operand = getSingleOperand(N);
        String condition = UUID.randomUUID().toString();
        C.bc.block.addInstruction(new UnaryOperatorInstruction(condition, "$Bool", "unary_arb", operand.Name, C));
        BasicBlock successBB = C.bc.fc.function.getBlock(getIntValue(N, 2));
        BasicBlock failureBB = C.bc.fc.function.getBlock(getIntValue(N, 3));
        C.bc.block.addInstruction(new ConditionalBranchInstruction(condition, successBB, failureBB, C));
        return null;
    }

    @Override
    // FREQUENCY: UNSEEN
    // STATUS: UNHANDLED
    // CONFIDENCE:
    protected SILIRInstruction visitCheckedCastValueBr(CAstNode N, InstructionContext C) {
        System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitCheckedCastAddrBr(CAstNode N, InstructionContext C) {
        RawValue operand1 = getOperand(N, 0);
        RawValue operand2 = getOperand(N, 1);
        String condition = UUID.randomUUID().toString();
        C.bc.block.addInstruction(new BinaryOperatorInstruction(condition, "$Bool", "binary_arb", operand1.Name, operand2.Name, C));
        BasicBlock successBB = C.bc.fc.function.getBlock(getIntValue(N, 2));
        BasicBlock failureBB = C.bc.fc.function.getBlock(getIntValue(N, 3));
        C.bc.block.addInstruction(new ConditionalBranchInstruction(condition, successBB, failureBB, C));
        return null;
    }

    @Override
    // FREQUENCY: COMMON
    // STATUS: TRANSLATED
    // CONFIDENCE: HIGH
    protected SILIRInstruction visitTryApply(CAstNode N, InstructionContext C) {
        int NormalBB = Integer.parseInt(getStringValue(N, 0));
        int ErrorBB = Integer.parseInt(getStringValue(N, 1));
        String FuncRefValue = RawUtil.getStringValue(N, 2);
        Value refValue = C.valueTable().getValue(FuncRefValue);
        CAstNode FuncNode = N.getChild(3);
        ArrayList<String> args = new ArrayList<>();
        for (CAstNode arg : FuncNode.getChildren()) {
            args.add((String) arg.getValue());
        }
        if (N.getChildren().size() == 5) {
            // An operator. No catching needed. This really shouldn't occur in SIL anyway.
            RawValue result = new RawValue(
                    C.bc.fc.function.getBlock(NormalBB).getArgument(0).name,
                    C.bc.fc.function.getBlock(NormalBB).getArgument(0).getType());
            CAstNode OperatorNode = N.getChild(4);
            if (OperatorNode.getKind() == CAstNode.UNARY_EXPR) {
                String operator = getStringValue(OperatorNode, 0);
                String operand = getStringValue(OperatorNode, 1);
                C.bc.block.addInstruction(new UnaryOperatorInstruction(result.Name, result.Type, operator, operand, C));
            } else if (OperatorNode.getKind() == CAstNode.BINARY_EXPR) {
                String operator = getStringValue(OperatorNode, 0);
                String operand1 = getStringValue(OperatorNode, 1);
                String operand2 = getStringValue(OperatorNode, 2);
                if (!handleInfixOperators(result.Name, result.Type, operator, operand1, operand2, C)) {
                    C.bc.block.addInstruction(new BinaryOperatorInstruction(result.Name, result.Type, operator, operand1, operand2, C));
                }
            } else {
                Assertions.UNREACHABLE("Unexpected kind");
                return null;
            }
            return new GotoInstruction(C.bc.fc.function.getBlock(NormalBB), C); // Go straight to normal block.
        }
        if (refValue instanceof BuiltinFunctionRefValue) { // No point of catching anything.
            RawValue result = new RawValue(
                    C.bc.fc.function.getBlock(NormalBB).getArgument(0).name,
                    C.bc.fc.function.getBlock(NormalBB).getArgument(0).getType());
            String name = ((BuiltinFunctionRefValue) refValue).getFunction();
            if (BuiltinHandler.isSummarized(name)) {
                return BuiltinHandler.findSummary(name, result.Name, result.Type, args, C);
            } else {
                // Make a function for builtins we don't have summaries for.
                ArrayList<Argument> funcArgs = new ArrayList<>();
                for (String arg : args) {
                    Argument a = new Argument(arg, C.valueTable().getValue(arg).type);
                    funcArgs.add(a);
                }
                Function f = createFakeFunction(((BuiltinFunctionRefValue) refValue).getFunction(), result.Type, funcArgs, C);
                C.bc.fc.pc.addFunction(f.getName(), f);
                ((BuiltinFunctionRefValue) refValue).summaryCreated = true;
                ApplyInstruction inst = new ApplyInstruction(refValue.name, result.Name, result.Type, args, C);
                inst.setComment("try_apply on builtin");
                C.bc.block.addInstruction(inst);
                return new GotoInstruction(C.bc.fc.function.getBlock(NormalBB), C); // Go straight to normal block.
            }
        } else if (refValue instanceof FunctionRefValue) {
            return new TryApplyInstruction(FuncRefValue, C.bc.fc.function.getBlock(NormalBB), C.bc.fc.function.getBlock(ErrorBB), args, C);
        } else {
            Assertions.UNREACHABLE("Unexpected function ref value type");
            return null;
        }
    }
}
