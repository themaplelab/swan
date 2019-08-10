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

import ca.maple.swan.swift.types.AnyCAstType;
import ca.maple.swan.swift.visualization.ASTtoDot;
import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.cast.tree.impl.CAstSymbolImpl;
import com.ibm.wala.util.debug.Assertions;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/*
 * This class builds all of the CAstEntities required for translation, based on the given CAstEntityInfos.
 */
public class ScriptEntityBuilder {

    public static ScriptEntity buildScriptEntity(File file, ArrayList<CAstEntityInfo> CAstEntityInfos) {

        boolean DEBUG = true;

        CAstImpl Ast = new CAstImpl();
        ScriptEntity scriptEntity = null;
        ArrayList<AbstractCodeEntity> functionEntities = new ArrayList<>();

        // MappedInfo is used to do post-processing on the CAst entities after they have been created.
        HashMap<String, CAstEntityInfo> mappedInfo = new HashMap<>();

        if (DEBUG) System.out.println("\n\n=============CAST=ENTITIES=============\n\n");

        // Create the CAst entities representing the [functions of the] script.
        for (CAstEntityInfo info : CAstEntityInfos) {
            AbstractCodeEntity newEntity;
            // A SIL script should have a "main" function (that is not explicit in the Swift code). This main function
            // is represented by a ScriptEntity since it is the root of the script.
            if ((info.functionName.equals("main")) && (scriptEntity == null)) {
                // We want to name the ScriptEntity with its filename, so the cha has a unique type for the script.
                // TODO: Add full path to name because you can possibly have multiple .swift files with the same name
                //       but with different paths.
                String scriptName = "script " + file.getName(); // Omit the "L" here because it is added later.
                mappedInfo.put(scriptName, info);
                newEntity = new ScriptEntity(scriptName, file, info.sourcePositionRecorder);
                scriptEntity = (ScriptEntity)newEntity;
                info.functionName = scriptName;
            } else { // Any other function should fall under a FunctionEntity.
                newEntity = new FunctionEntity(info.functionName, info.returnType, info.argumentTypes,
                        info.argumentNames, info.sourcePositionRecorder, info.functionPosition, info.argumentPositions);
                mappedInfo.put(info.functionName, info);
            }
            functionEntities.add(newEntity);

            // Set the entity's AST to be the first basic block of the function, with the other basic blocks as
            // children of the first block.
            if (info.basicBlocks.size() > 0) {
                List<CAstNode> body = new ArrayList<>();
                body.addAll(info.basicBlocks.get(0).getChildren());
                for (int i = 1; i < info.basicBlocks.size(); i++) {
                    body.add(info.basicBlocks.get(i));
                }
                CAstNode parentBlock = Ast.makeNode(CAstNode.BLOCK_STMT, body);
                newEntity.setAst(parentBlock);
            }

            // Set the node type map.
            for (CAstNode node: info.variableTypes.keySet()) {
                newEntity.setNodeType(node, new AnyCAstType());
            }
        }
        assert(scriptEntity != null) : "Script Entity was not created most likely due to no \"main\" function found.";

        // Do post-processing on the entities.
        for (AbstractCodeEntity entity : functionEntities) {
            // Add scoped entities.
            for (CAstNode caller : mappedInfo.get(entity.getName()).funcNodes) {
                CAstEntity target = findCallee(caller, functionEntities);
                assert(target != null) : "could not find a target";
                entity.addScopedEntity(null, target);
                assert(target.getAST() != null) : "target's AST is null";
                entity.setGotoTarget(caller, target.getAST());
            }

            // Add the CFG targets.
            for (CAstNode cfNode : mappedInfo.get(entity.getName()).cfNodes) {
                CAstNode target = findTarget(cfNode, mappedInfo.get(entity.getName()).basicBlocks);
                if (target != null) {
                    entity.setGotoTarget(cfNode, target);
                }
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
                        new CAstSymbolImpl((String)declNode.getChild(0).getValue(), new AnyCAstType())
                );
                /* TODO: Mutating the AST like this is not recommended and is bad practice. AST translation needs to be
                 *  done in explicit "translation" steps. That is, generate a new AST from the old one. Albeit,
                 *  generating an entire new AST is inefficient and is not needed for a simple mutation like this.
                 */
                declNode.getChildren().set(0, symbol);
                declNode.getChildren().set(1, Ast.makeConstant("Any"));
            }

            // Map every node in the AST to itself.
            ReflexiveMapper.mapEntity(entity);
            if (DEBUG) EntityPrinter.print(entity);
        }
        ASTtoDot.print(functionEntities);
        if (DEBUG) System.out.println("\n==========END=OF=CAST=ENTITIES=========\n\n");
        return scriptEntity;
    }

    // Finds the entity a FUNCTION_EXPR node calls by looking up the function name.
    private static CAstEntity findCallee(CAstNode node, ArrayList<AbstractCodeEntity> entities) {
        assert(node.getKind() == CAstNode.FUNCTION_EXPR) : "node is not a FUNCTION_EXPR node";
        CAstImpl Ast = new CAstImpl();
        String functionName = (String)node.getChild(0).getValue();
        for (CAstEntity entity : entities) {
            if (entity.getName().equals(functionName)) {
                // node.getChildren().set(0, Ast.makeNode(CAstNode.FUNCTION_EXPR, Ast.makeConstant(entity)));
                return entity;
            }
        }
        assert(false) : "could not find callee for: " + functionName;
        return null;
    }

    // Finds the basic block node a control flow node goes to to by looking up the basic block name.
    private static CAstNode findTarget(CAstNode node, ArrayList<CAstNode> possibleTargets) {
        for (CAstNode possibleTarget : possibleTargets) {
            assert(possibleTarget.getKind() == CAstNode.BLOCK_STMT) : "possibleTarget is not a BLOCK_STMT";
            assert(possibleTarget.getChild(0).getKind() == CAstNode.LABEL_STMT) : "possibleTarget's first child is not a LABEL_STMT";
            if (node.getKind() == CAstNode.GOTO) {
                if (possibleTarget.getChild(0).getChild(0).getValue().equals(node.getChild(0).getValue())) {
                    return possibleTarget;
                }
            } else {
                Assertions.UNREACHABLE("Only GOTOs are supported for now");
            }
        }
        assert(false) : "could not find target";
        return null;
    }
}
