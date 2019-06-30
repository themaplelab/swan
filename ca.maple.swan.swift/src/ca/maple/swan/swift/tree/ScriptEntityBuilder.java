//===--- ScriptEntityBuilder.java ----------------------------------------===//
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

package ca.maple.swan.swift.tree;

import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.cast.tree.impl.CAstSymbolImpl;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class ScriptEntityBuilder {

    public static ScriptEntity buildScriptEntity(File file, ArrayList<CAstEntityInfo> CAstEntityInfos) {

        // WORK IN PROGRESS
        CAstImpl Ast = new CAstImpl();
        ScriptEntity scriptEntity = null;
        ArrayList<AbstractCodeEntity> functionEntities = new ArrayList<>();
        HashMap<String, CAstEntityInfo> mappedInfo = new HashMap<>();

        System.out.println("\n\n=============CAST=ENTITIES=============\n\n");

        for (CAstEntityInfo info : CAstEntityInfos) {
            AbstractCodeEntity newEntity;
            if ((info.functionName.equals("main")) && (scriptEntity == null)) {
                newEntity = new ScriptEntity(file, info.sourcePositionRecorder);
                scriptEntity = (ScriptEntity)newEntity;
            } else {
                newEntity = new FunctionEntity(info.functionName, info.returnType, info.argumentTypes,
                        info.argumentNames, info.sourcePositionRecorder);
            }
            functionEntities.add(newEntity);
            if (info.basicBlocks.size() > 0) {
                newEntity.setAst(info.basicBlocks.get(0));
            }
            for (CAstNode node: info.variableTypes.keySet()) {
                newEntity.setNodeType(node, SwiftTypes.findOrCreateCAstType(info.variableTypes.get(node)));
            }
            mappedInfo.put(info.functionName, info);
        }
        assert(scriptEntity != null) : "Script Entity was not created most likely due to no \"main\" function found!";

        for (AbstractCodeEntity entity : functionEntities) {
            // Add scoped entities.
            for (CAstNode caller : mappedInfo.get(entity.getName()).callNodes) {
                entity.addScopedEntity(null, findCallee(caller, functionEntities)); // TODO: Handle null
            }

            // Add the CFG targets.
            for (CAstNode cfNode : mappedInfo.get(entity.getName()).cfNodes) {
                entity.setGotoTarget(cfNode, cfNode); // Apparently this is necessary.
                CAstNode target = findTarget(cfNode, mappedInfo.get(entity.getName()).basicBlocks);
                entity.setLabelledGotoTarget(cfNode, target, "GOTO"); // TODO: Handle null
            }

            // Translate (correct) the DECL_STMTs of the entity.
            // Expected initial DECL_STMT format:
            // DECL_STMT
            //   NAME (constant string)
            //   TYPE (constant string)
            for (CAstNode declNode : mappedInfo.get(entity.getName()).declNodes) {
                assert(declNode.getKind() == CAstNode.DECL_STMT) : "declNode is not of DECL_STMT kind";
                assert(declNode.getChild(0).getKind() == CAstNode.CONSTANT) : "declNode's first child is not a constant";
                assert(declNode.getChild(1).getKind() == CAstNode.CONSTANT) : "declNode's second child is not a constant";
                CAstNode symbol = Ast.makeConstant(
                        new CAstSymbolImpl((String)declNode.getChild(0).getValue(),
                                SwiftTypes.findOrCreateCAstType((String)declNode.getChild(1).getValue()))
                );
                declNode.getChildren().set(0, symbol);
                declNode.getChildren().set(1, Ast.makeNode(CAstNode.EMPTY)); // Workaround
            }
            EntityPrinter.print(entity);
        }

        System.out.println("\n==========END=OF=CAST=ENTITIES=========\n\n");

        return scriptEntity;
    }

    private static CAstEntity findCallee(CAstNode node, ArrayList<AbstractCodeEntity> entities) {
        assert(node.getKind() == CAstNode.CALL) : "node is not a CALL node!";
        assert(node.getChild(0).getKind() == CAstNode.FUNCTION_EXPR) : "node's first child is not a FUNCTION_EXPR!";
        CAstImpl Ast = new CAstImpl();
        for (CAstEntity entity : entities) {
            if (entity.getName().equals(node.getChild(0).getChild(0).getValue())) {
                node.getChildren().set(0, Ast.makeNode(CAstNode.FUNCTION_EXPR, Ast.makeConstant(entity)));
                return entity;
            }
        }
        return null;
    }

    private static CAstNode findTarget(CAstNode node, ArrayList<CAstNode> possibleTargets) {
        for (CAstNode possibleTarget : possibleTargets) {
            assert(possibleTarget.getKind() == CAstNode.BLOCK_STMT);
            assert(possibleTarget.getChild(0).getKind() == CAstNode.LABEL_STMT);
            if (node.getKind() == CAstNode.GOTO) {
                if (possibleTarget.getChild(0).getChild(0).getValue().equals(node.getChild(0).getValue())) {
                    return possibleTarget;
                }
            }
        }
        return null;
    }
}
