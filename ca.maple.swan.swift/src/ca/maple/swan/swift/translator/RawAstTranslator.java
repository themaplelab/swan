//===--- RawAstTranslator.java -------------------------------------------===//
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

import ca.maple.swan.swift.ipa.summaries.BuiltInFunctionSummaries;
import ca.maple.swan.swift.tree.*;
import ca.maple.swan.swift.types.AnyCAstType;
import ca.maple.swan.swift.visualization.ASTtoDot;
import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.ir.translator.AbstractEntity;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.cast.tree.impl.CAstSymbolImpl;
import com.ibm.wala.util.debug.Assertions;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.ibm.wala.cast.tree.CAstNode.*;

public class RawAstTranslator {

    private static CAstImpl Ast = new CAstImpl();

    // WARNING: Coupled with C++ string for unknown refs.
    private static String unhandledRefName = "UNKNOWN_DYNAMIC_REF";

    public static ScriptEntity translate(File file, ArrayList<CAstEntityInfo> CAstEntityInfos) {
        boolean DEBUG = true;
        ScriptEntity scriptEntity = null;
        ArrayList<AbstractCodeEntity> functionEntities = new ArrayList<>();
        HashMap<String, CAstEntityInfo> mappedInfo = new HashMap<>();

        if (DEBUG) System.out.println("\n\n=============CAST=ENTITIES=============\n\n");

        for (CAstEntityInfo info : CAstEntityInfos) {
            AbstractCodeEntity newEntity;
            if ((info.functionName.equals("main")) && (scriptEntity == null)) {
                String scriptName = "script " + file.getName();
                newEntity = new ScriptEntity(scriptName, file, info.sourcePositionRecorder);
                scriptEntity = (ScriptEntity)newEntity;
                info.functionName = scriptName;
            } else {
                newEntity = new FunctionEntity(info.functionName, info.returnType, info.argumentTypes,
                        info.argumentNames, info.sourcePositionRecorder, info.functionPosition, info.argumentPositions);
            }
            functionEntities.add(newEntity);

            mappedInfo.put(info.functionName, info);

        }

        for (AbstractCodeEntity entity : functionEntities) {
            translateEntity(entity, mappedInfo.get(entity.getName()).basicBlocks, functionEntities);
            if (DEBUG) EntityPrinter.print(entity);
        }

        assert(scriptEntity != null) : "Script Entity was not created most likely due to no \"main\" function found.";

        if (DEBUG) ASTtoDot.print(functionEntities);
        if (DEBUG) System.out.println("\n==========END=OF=CAST=ENTITIES=========\n\n");

        return scriptEntity;
    }

    private static void translateEntity(AbstractCodeEntity entity, ArrayList<CAstNode> basicBlocks,
                                        ArrayList<AbstractCodeEntity> allEntities) {
        ArrayList<CAstNode> newBasicBlocks = new ArrayList<>();
        for (CAstNode block : basicBlocks) {
            newBasicBlocks.add(translateBasicBlock(entity, block, allEntities));
        }
        List<CAstNode> body = new ArrayList<>(newBasicBlocks.get(0).getChildren());
        for (int i = 1; i < newBasicBlocks.size(); i++) {
            body.add(newBasicBlocks.get(i));
        }
        CAstNode parentBlock = Ast.makeNode(CAstNode.BLOCK_STMT, body);
        entity.setAst(parentBlock);
    }

    private static CAstNode translateBasicBlock(AbstractCodeEntity entity, CAstNode BB,
                                                ArrayList<AbstractCodeEntity> allEntities) {
        assert(BB.getKind() == BLOCK_STMT);
        assert(BB.getChild(0).getKind() == LABEL_STMT);
        assert(BB.getChild(0).getChild(0).getKind() == CONSTANT);
        ArrayList<CAstNode> newAst = new ArrayList<>();
        for (CAstNode n : BB.getChildren()) {
            switch (n.getKind()) {
                case DECL_STMT: {
                    assert(n.getChild(0).getKind() == CONSTANT);
                    assert(n.getChild(1).getKind() == CONSTANT);
                    CAstNode symbol = Ast.makeConstant(
                            new CAstSymbolImpl((String)n.getChild(0).getValue(), new AnyCAstType())
                    );
                    newAst.add(Ast.makeNode(DECL_STMT, Ast.makeConstant(symbol.getValue())));
                    break;
                }
                case ASSIGN: {
                    assert(n.getChild(0) != null);
                    assert(n.getChild(1) != null);
                    CAstNode src = n.getChild(1);
                    switch(src.getKind()) {
                        case CALL: {
                            assert (src.getKind() == FUNCTION_EXPR);
                            assert (src.getChild(0).getKind() == CONSTANT);
                            String funcName = (String)src.getChild(0).getValue();
                            CAstNode summary = BuiltInFunctionSummaries.findSummary(src);
                            if (summary != null) {
                                // We have a summary for this CALL we can use instead.
                                newAst.add(Ast.makeNode(ASSIGN, n.getChild(0), summary));
                                continue;
                            } else if (funcName.equals(unhandledRefName)) {
                                // We don't know which function we are referencing, so we will replace it with a constant.
                                newAst.add(Ast.makeNode(ASSIGN, n.getChild(0), Ast.makeConstant(unhandledRefName)));
                                continue;
                            } else if (BuiltInFunctionSummaries.isBuiltIn(funcName)) {
                                // The function is a builtin, that we evidently don't have a summary for, so we replace
                                // the CALL with a constant.
                                newAst.add(Ast.makeNode(ASSIGN, n.getChild(0), Ast.makeConstant("UNHANDLED_BUILTIN")));
                                continue;
                            } else {
                                // We are calling a function which is represented by an entity.
                                entity.addScopedEntity(null, findCallee(funcName, allEntities));
                            }
                            break;
                        }
                        case FUNCTION_EXPR: {
                            assert(n.getChild(0).getKind() == CONSTANT);
                            String funcName = (String)n.getChild(0).getValue();
                            if (BuiltInFunctionSummaries.findSummary(src) != null) {
                                // We don't create an entity for a summary, so if this FUNC_EXPR is used, WALA
                                // will try to find a corresponding entity.
                                Assertions.UNREACHABLE("Undefined behavior: Functions with summaries should only be used in calls.");
                            } else if (funcName.equals(unhandledRefName)) {
                                // We don't know which function we are referencing, so we will replace it with a constant.
                                newAst.add(Ast.makeNode(ASSIGN, n.getChild(0), Ast.makeConstant(unhandledRefName)));
                                continue;
                            } else if (BuiltInFunctionSummaries.isBuiltIn(funcName)) {
                                // The function is a builtin, that we evidently don't have a summary for, so we replace
                                // the FUNCTION_EXPR with a constant.
                                newAst.add(Ast.makeNode(ASSIGN, n.getChild(0), Ast.makeConstant("UNHANDLED_BUILTIN")));
                                continue;
                            } else {
                                // We are referencing a function which is represented by an entity.
                                entity.addScopedEntity(null, findCallee(funcName, allEntities));
                            }
                            break;
                        }
                    }
                    newAst.add(n);
                    break;
                }
                case CALL: {
                    Assertions.UNREACHABLE("CALL should not be a root node.");
                }
                case FUNCTION_EXPR: {
                    Assertions.UNREACHABLE("FUNCTION_EXPR should not be a root node.");
                }
                default: {
                    newAst.add(n);
                }
            }
        }
        return Ast.makeNode(BLOCK_STMT, newAst);
    }

    private static AbstractEntity findCallee(String functionName , ArrayList<AbstractCodeEntity> entities) {
        CAstImpl Ast = new CAstImpl();
        for (AbstractEntity entity : entities) {
            if (entity.getName().equals(functionName)) {
                return entity;
            }
        }
        Assertions.UNREACHABLE("could not find callee for: " + functionName);
        return null;
    }
}
