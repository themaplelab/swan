//===--- SILIRToCAstTranslator.java --------------------------------------===//
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

import ca.maple.swan.swift.translator.cast.SwiftCAstNode;
import ca.maple.swan.swift.translator.operators.SILIRCAstOperator;
import ca.maple.swan.swift.translator.silir.BasicBlock;
import ca.maple.swan.swift.translator.silir.Function;
import ca.maple.swan.swift.translator.silir.context.ProgramContext;
import ca.maple.swan.swift.translator.silir.instructions.*;
import ca.maple.swan.swift.translator.silir.values.Argument;
import ca.maple.swan.swift.translator.silir.values.Value;
import ca.maple.swan.swift.translator.types.SILTypes;
import ca.maple.swan.swift.tree.EntityPrinter;
import ca.maple.swan.swift.tree.FunctionEntity;
import ca.maple.swan.swift.tree.ScriptEntity;
import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstLeafNode;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.cast.tree.impl.CAstOperator;
import com.ibm.wala.cast.tree.impl.CAstSymbolImpl;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;

/*
 * Translates SILIR to CAst. Nothing particularly interesting happens here since all of the
 * SIL nuanced are removed at the raw to SILIR translation step.
 */

public class SILIRToCAstTranslator {

    public static final CAstImpl Ast = new CAstImpl();
    private static final boolean DEBUG = true;

    public CAstEntity translate(File file, ProgramContext pc) {

        AbstractCodeEntity script = null;

        ArrayList<AbstractCodeEntity> allEntities = new ArrayList<>();

        for (Function f : pc.getFunctions()) {
            AbstractCodeEntity entity;
            if (f.getName().equals("main")) {
                entity = new ScriptEntity("main", file);
                script = entity;
            } else {
                entity = makeFunctionEntity(f);
            }
            allEntities.add(entity);
        }

        for (AbstractCodeEntity entity : allEntities) {
            Function f = pc.getFunction(entity.getName());
            Assertions.productionAssertion(f != null);
            WalkContext c = new WalkContext(pc, f, entity, allEntities);
            Visitor v = new Visitor(c);
            v.visit();
        }

        return script;

    }

    private FunctionEntity makeFunctionEntity(Function f) {
        ArrayList<String> argumentTypes = new ArrayList<>();
        ArrayList<String> argumentNames = new ArrayList<>();
        ArrayList<Position> argumentPositions = new ArrayList<>();
        for (Argument a : f.getArguments()) {
            argumentNames.add(a.name);
            argumentTypes.add(a.type);
            argumentPositions.add(a.position);
        }
        argumentNames.add(0, "self");
        argumentTypes.add(0, "self");
        argumentPositions.add(0, null);
        return new FunctionEntity(
                f.getName(),
                f.getReturnType(),
                argumentTypes,
                argumentNames,
                f.getPosition(),
                argumentPositions
        );
    }

    private static class WalkContext {

        public final ProgramContext pc;

        public final AbstractCodeEntity currentEntity;

        public final Function f;

        public final HashMap<String, AbstractCodeEntity> allEntities;

        public final ArrayList<CAstNode> blocks;

        public final ArrayList<CAstNode> currentBody;

        public final ArrayList<CAstNode> bbLabels;

        public final HashSet<Value> decls;

        public final HashSet<Value> globalDecls;

        public WalkContext(ProgramContext pc, Function f, AbstractCodeEntity currentEntity, ArrayList<AbstractCodeEntity> allEntities) {
            this.pc = pc;
            this.f = f;
            this.currentEntity = currentEntity;
            this.allEntities = new HashMap<>();
            for (AbstractCodeEntity entity : allEntities) {
                this.allEntities.put(entity.getName(), entity);
            }
            this.blocks = new ArrayList<>();
            this.currentBody = new ArrayList<>();
            bbLabels = new ArrayList<>();
            decls = new HashSet<>();
            globalDecls = new HashSet<>();
            for (BasicBlock bb : f.getBlocks()) {
                bbLabels.add(Ast.makeNode(CAstNode.LABEL_STMT, Ast.makeConstant("bb" + bb.getNumber())));
            }
        }

    }

    private static class Visitor extends ISILIRVisitor {

        private WalkContext c;

        public Visitor(WalkContext c) {
            this.c = c;
        }

        public void visit() {
            ArrayList<CAstNode> topLevelBody = new ArrayList<>();
            for (BasicBlock bb : c.f.getBlocks()) {
                c.currentBody.clear();
                c.decls.clear();
                c.globalDecls.clear();
                c.currentBody.add(c.bbLabels.get(bb.getNumber()));
                for (SILIRInstruction instruction : bb.getInstructions()) {
                    try {
                        instruction.visit(this);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                ArrayList<CAstNode> declStmts = new ArrayList<>();
                for (Value v : c.decls) {
                    declStmts.add(
                            Ast.makeNode(
                                    CAstNode.DECL_STMT,
                                    Ast.makeConstant(
                                            new CAstSymbolImpl(v.name, SILTypes.getType(v.type)))));
                }
                c.currentBody.addAll(1, declStmts);
                ArrayList<CAstNode> globalDeclStmts = new ArrayList<>();
                for (Value v : c.globalDecls) {
                    globalDeclStmts.add(
                            Ast.makeNode(
                                    SwiftCAstNode.GLOBAL_DECL_STMT,
                                    Ast.makeConstant(
                                            new CAstSymbolImpl(v.name, SILTypes.getType(v.type)))));
                }
                c.currentBody.addAll(1, globalDeclStmts);
                if (bb.getNumber() == 0) {
                    topLevelBody.addAll(c.currentBody);
                }
                c.blocks.add(Ast.makeNode(CAstNode.BLOCK_STMT, new ArrayList<>(c.currentBody)));
            }
            topLevelBody.addAll(c.blocks.subList(1, c.blocks.size()));
            c.currentEntity.setAst(Ast.makeNode(CAstNode.BLOCK_STMT, topLevelBody));
            if (DEBUG) {
                EntityPrinter.print(c.currentEntity);
            }
        }

        private CAstNode makeVarNode(Value v) {
            CAstNode n = Ast.makeNode(CAstNode.VAR, Ast.makeConstant(v.name));
            c.currentEntity.getNodeTypeMap().add(n, SILTypes.getType(v.type));
            c.decls.add(v);
            return n;
        }

        private CAstNode makeGlobalVarNode(Value v) {
            CAstNode n = Ast.makeNode(CAstNode.VAR, Ast.makeConstant(v.name));
            c.currentEntity.getNodeTypeMap().add(n, SILTypes.getType(v.type));
            c.globalDecls.add(v);
            return n;
        }

        private CAstNode makeGotoNode(BasicBlock bb) {
            CAstNode n = Ast.makeNode(CAstNode.GOTO, Ast.makeConstant("bb" + bb.getNumber()));
            c.currentEntity.setGotoTarget(n, c.bbLabels.get(bb.getNumber()));
            return n;
        }

        private void addNode(CAstNode n) {
            c.currentBody.add(n);
        }

        private void setNodePosition(CAstNode n, SILIRInstruction instruction) {
            c.currentEntity.setNodePosition(n, instruction.ic.position);
        }

        public CAstNode ifStmtSwitch(Stack<Pair<Value, BasicBlock>> cases, Value toCompare, BasicBlock defaultBlock) {
            if (!cases.empty()) {
                Pair<Value, BasicBlock> Case = cases.pop();
                return Ast.makeNode(
                        CAstNode.IF_STMT,
                        Ast.makeNode(
                                CAstNode.BINARY_EXPR,
                                CAstOperator.OP_EQ,
                                makeVarNode(toCompare),
                                makeVarNode(Case.fst)),
                        makeGotoNode(Case.snd),
                        ifStmtSwitch(cases, toCompare, defaultBlock));
            } else {
                if (defaultBlock != null) {
                    return makeGotoNode(defaultBlock);
                }
                return Ast.makeNode(CAstNode.EMPTY);
            }
        }

        @Override
        public void visitSwitchValueInstruction(SwitchValueInstruction instruction) {
            Stack<Pair<Value, BasicBlock>> cases = new Stack<>();
            for (Pair<Value, BasicBlock> c : instruction.cases) {
                cases.push(c);
            }
            CAstNode n = ifStmtSwitch(cases, instruction.switchValue, instruction.defaultBlock);
            setNodePosition(n, instruction);
            addNode(n);
        }

        @Override
        public void visitApplyInstruction(ApplyInstruction instruction) {
            ArrayList<CAstNode> args = new ArrayList<>();
            args.add(makeVarNode(instruction.functionRefValue));
            args.add(Ast.makeConstant("do"));
            for (Value v : instruction.args) {
                args.add(makeVarNode(v));
            }
            CAstNode callNode = Ast.makeNode(CAstNode.CALL, args);
            c.currentEntity.setGotoTarget(callNode, callNode);
            CAstNode n =
                    Ast.makeNode(
                            CAstNode.ASSIGN,
                            makeVarNode(instruction.result),
                            callNode);
            setNodePosition(n, instruction);
            addNode(n);
        }

        @Override
        public void visitAssignGlobalInstruction(AssignGlobalInstruction instruction) {
            CAstNode n =
                    Ast.makeNode(
                            CAstNode.ASSIGN,
                            makeVarNode(instruction.to),
                            makeVarNode(instruction.from));
            setNodePosition(n, instruction);
            addNode(n);
        }

        @Override
        public void visitAssignInstruction(AssignInstruction instruction) {
            CAstNode n =
                    Ast.makeNode(
                            CAstNode.ASSIGN,
                            makeVarNode(instruction.to),
                            makeVarNode(instruction.from));
            setNodePosition(n, instruction);
            addNode(n);
        }

        @Override
        public void visitConditionalBranchInstruction(ConditionalBranchInstruction instruction) {
            CAstNode n =
                    Ast.makeNode(
                            CAstNode.IF_STMT,
                            makeVarNode(instruction.conditionValue),
                            makeGotoNode(instruction.trueBlock),
                            makeGotoNode(instruction.falseBlock));
            setNodePosition(n, instruction);
            addNode(n);
        }

        private CAstLeafNode getOperator(String s) {
            CAstLeafNode operator;
            switch (s) {
                case "==":
                    operator = CAstOperator.OP_EQ;
                    break;
                case "!=":
                    operator = CAstOperator.OP_NE;
                    break;
                case "+":
                    operator = CAstOperator.OP_ADD;
                    break;
                case "-":
                    operator = CAstOperator.OP_SUB;
                    break;
                case "binary_arb":
                    operator = SILIRCAstOperator.OP_BINARY_ARBITRARY;
                    break;
                case "unary_arb":
                    operator = SILIRCAstOperator.OP_UNARY_ARBITRARY;
                    break;
                default:
                    operator = CAstOperator.OP_EQ;
                    System.err.println("Operator not handled: " + s + ". Defaulting to ==");
                    break;
            }
            return  operator;
        }

        @Override
        public void visitBinaryOperatorInstruction(BinaryOperatorInstruction instruction) {
            CAstLeafNode operator;

            CAstNode n =
                    Ast.makeNode(
                            CAstNode.ASSIGN,
                            makeVarNode(instruction.resultValue),
                            Ast.makeNode(
                                    CAstNode.BINARY_EXPR,
                                    getOperator(instruction.operator),
                                    makeVarNode(instruction.operand1),
                                    makeVarNode(instruction.operand2)));
            setNodePosition(n, instruction);
            addNode(n);
        }

        @Override
        public void visitBuiltinInstruction(BuiltinInstruction instruction) {
            if (instruction.value.summaryCreated) {
                CAstEntity entity = (c.allEntities.get(instruction.functionName));
                Assertions.productionAssertion(entity != null);
                CAstNode n =
                        Ast.makeNode(
                                CAstNode.ASSIGN,
                                makeVarNode(instruction.value),
                                Ast.makeNode(
                                        CAstNode.FUNCTION_EXPR,
                                        Ast.makeConstant(entity)));
                c.currentEntity.addScopedEntity(null, entity);
                setNodePosition(n, instruction);
                addNode(n);
            }
        }

        @Override
        public void visitFieldAliasInstruction(FieldAliasInstruction instruction) {
            // NOP
        }

        @Override
        public void visitFieldReadInstruction(FieldReadInstruction instruction) {
            CAstNode n =
                    Ast.makeNode(
                            CAstNode.ASSIGN,
                            makeVarNode(instruction.result),
                            Ast.makeNode(
                                    CAstNode.OBJECT_REF,
                                    makeVarNode(instruction.operand),
                                    Ast.makeConstant(instruction.field)));
            setNodePosition(n, instruction);
            addNode(n);
        }

        @Override
        public void visitFieldReadWriteInstruction(FieldReadWriteInstruction instruction) {
            CAstNode n =
                    Ast.makeNode(
                            CAstNode.ASSIGN,
                            Ast.makeNode(
                                    CAstNode.OBJECT_REF,
                                    makeVarNode(instruction.resultValue),
                                    Ast.makeConstant(instruction.resultField)),
                            Ast.makeNode(
                                    CAstNode.OBJECT_REF,
                                    makeVarNode(instruction.operandValue),
                                    Ast.makeConstant(instruction.operandField)));
            setNodePosition(n, instruction);
            addNode(n);
        }

        @Override
        public void visitFieldWriteInstruction(FieldWriteInstruction instruction) {
            CAstNode n =
                    Ast.makeNode(
                            CAstNode.ASSIGN,
                            Ast.makeNode(
                                    CAstNode.OBJECT_REF,
                                    makeVarNode(instruction.writeTo),
                                    Ast.makeConstant(instruction.field)),
                            makeVarNode(instruction.operand));
            setNodePosition(n, instruction);
            addNode(n);
        }

        public CAstNode ifStmtAssignSwitch(Value result, Stack<Pair<Value, Value>> cases, Value toCompare, Value defaultValue) {
            if (!cases.empty()) {
                Pair<Value, Value> Case = cases.pop();
                return Ast.makeNode(
                        CAstNode.IF_STMT,
                        Ast.makeNode(
                                CAstNode.BINARY_EXPR,
                                CAstOperator.OP_EQ,
                                makeVarNode(toCompare),
                                makeVarNode(Case.fst)),
                        Ast.makeNode(
                                CAstNode.ASSIGN,
                                makeVarNode(result),
                                makeVarNode(Case.snd)
                        ),
                        ifStmtAssignSwitch(result, cases, toCompare, defaultValue));
            } else {
                if (defaultValue != null) {
                    return Ast.makeNode(
                            CAstNode.ASSIGN,
                            makeVarNode(result),
                            makeVarNode(defaultValue));
                }
                return Ast.makeNode(CAstNode.EMPTY);
            }
        }

        @Override
        public void visitSwitchAssignValueInstruction(SwitchAssignValueInstruction instruction) {
            Stack<Pair<Value, Value>> cases = new Stack<>();
            for (Pair<Value, Value> c : instruction.cases) {
                cases.push(c);
            }
            CAstNode n = ifStmtAssignSwitch(instruction.result, cases, instruction.switchValue, instruction.defaultValue);
            setNodePosition(n, instruction);
            addNode(n);
        }

        @Override
        public void visitFunctionRefInstruction(FunctionRefInstruction instruction) {
            CAstEntity entity = (c.allEntities.get(instruction.value.getFunction().getName()));
            Assertions.productionAssertion(entity != null);
            CAstNode n =
                    Ast.makeNode(
                            CAstNode.ASSIGN,
                            makeVarNode(instruction.value),
                            Ast.makeNode(
                                    CAstNode.FUNCTION_EXPR,
                                    Ast.makeConstant(entity)));
            c.currentEntity.addScopedEntity(null, entity);
            setNodePosition(n, instruction);
            addNode(n);
        }

        @Override
        public void visitGotoInstruction(GotoInstruction instruction) {
            CAstNode n = makeGotoNode(instruction.bb);
            setNodePosition(n, instruction);
            addNode(n);
        }

        @Override
        public void visitImplicitCopyInstruction(ImplicitCopyInstruction instruction) {
            // NOP
        }

        @Override
        public void visitLiteralInstruction(LiteralInstruction instruction) {
            CAstNode n =
                    Ast.makeNode(
                            CAstNode.ASSIGN,
                            makeVarNode(instruction.literal),
                            Ast.makeConstant(instruction.literal.getLiteral()));
            setNodePosition(n, instruction);
            addNode(n);
        }

        @Override
        public void visitNewArrayTupleInst(NewArrayTupleInstruction instruction) {
            CAstNode n =
                    Ast.makeNode(
                            CAstNode.ASSIGN,
                            makeVarNode(instruction.value),
                            Ast.makeNode(
                                    CAstNode.NEW,
                                    Ast.makeConstant(instruction.value.type)));
            setNodePosition(n, instruction);
            addNode(n);
        }

        @Override
        public void visitNewGlobalInstruction(NewGlobalInstruction instruction) {
            CAstNode n =
                    Ast.makeNode(
                            CAstNode.ASSIGN,
                            makeGlobalVarNode(instruction.value),
                            Ast.makeNode(
                                    CAstNode.NEW,
                                    Ast.makeConstant(instruction.value.type)));
            setNodePosition(n, instruction);
            addNode(n);
        }

        @Override
        public void visitNewInstruction(NewInstruction instruction) {
            CAstNode n =
                    Ast.makeNode(
                            CAstNode.ASSIGN,
                            makeVarNode(instruction.value),
                            Ast.makeNode(
                                    CAstNode.NEW,
                                    Ast.makeConstant(instruction.value.type)));
            setNodePosition(n, instruction);
            addNode(n);
        }

        @Override
        public void visitPrintInstruction(PrintInstruction instruction) {
            CAstNode n =
                    Ast.makeNode(
                            CAstNode.ECHO,
                            makeVarNode(instruction.value));
            setNodePosition(n, instruction);
            addNode(n);
        }

        @Override
        public void visitConditionalThrowInstruction(ConditionalThrowInstruction instruction) {
            CAstNode n =
                    Ast.makeNode(
                            CAstNode.IF_STMT,
                            makeVarNode(instruction.conditionValue),
                            Ast.makeNode(CAstNode.THROW)
                    );
            setNodePosition(n, instruction);
            addNode(n);
        }

        @Override
        public void visitThrowInstruction(ThrowInstruction instruction) {
            CAstNode n =
                    Ast.makeNode(
                            CAstNode.THROW
                    );
            setNodePosition(n, instruction);
            addNode(n);
        }

        @Override
        public void visitReturnInstruction(ReturnInstruction instruction) {
            CAstNode n =
                    (instruction.hasReturnVal()
                            ? Ast.makeNode(CAstNode.RETURN, makeVarNode(instruction.getReturnVal()))
                            : Ast.makeNode(CAstNode.RETURN));
            setNodePosition(n, instruction);
            addNode(n);
        }

        @Override
        public void visitUnaryOperatorInstruction(UnaryOperatorInstruction instruction) {
            CAstNode n =
                    Ast.makeNode(
                            CAstNode.ASSIGN,
                            Ast.makeNode(
                                    CAstNode.UNARY_EXPR,
                                    getOperator(instruction.operator),
                                    makeVarNode(instruction.operand)

                            ),
                            makeVarNode(instruction.resultValue)
                    );
            setNodePosition(n, instruction);
            addNode(n);
        }

        @Override
        public void visitYieldInstruction(YieldInstruction instruction) {
            // TODO
            System.err.println("ERROR: Unhandled instruction: " + new Exception().getStackTrace()[0].getMethodName());
        }
    }

}
