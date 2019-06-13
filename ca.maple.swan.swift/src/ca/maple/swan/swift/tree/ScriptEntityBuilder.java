package ca.maple.swan.swift.tree;

import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.impl.CAstImpl;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;


public class ScriptEntityBuilder {

    public static ScriptEntity buildScriptEntity(File file, ArrayList<CAstEntityInfo> CAstEntityInfos) {

        // WORK IN PROGRESS

        ScriptEntity scriptEntity = null;
        ArrayList<AbstractCodeEntity> functionEntities = new ArrayList<>();
        HashMap<String, CAstEntityInfo> mappedInfo = new HashMap<>();

        for (CAstEntityInfo info : CAstEntityInfos) {
            AbstractCodeEntity newEntity;
            if ((info.functionName.equals("main")) && (scriptEntity == null)) {
                newEntity = new ScriptEntity(file);
                scriptEntity = (ScriptEntity)newEntity;
            } else {
                newEntity = new FunctionEntity(info.functionName, info.returnType, info.argumentTypes);
            }
            functionEntities.add(newEntity);
            if (info.basicBlocks.size() > 0) {
                newEntity.setAst(info.basicBlocks.get(0));
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
            EntityPrinter.print(entity);
        }
        return scriptEntity;
    }

    private static CAstEntity findCallee(CAstNode node, ArrayList<AbstractCodeEntity> entities) {
        assert(node.getKind() == CAstNode.CALL);
        assert(node.getChild(0).getKind() == CAstNode.FUNCTION_EXPR);
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
