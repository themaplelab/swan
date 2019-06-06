package ca.maple.swan.swift.tree;

import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.CAstType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class ScriptEntityBuilder {

    public static ScriptEntity buildScriptEntity(File file, ArrayList<CAstEntityInfo> CAstEntityInfos) {

        // WORK IN PROGRESS

        ScriptEntity scriptEntity = null;
        ArrayList<AbstractCodeEntity> functionEntities = new ArrayList<>();
        HashMap<String, CAstEntityInfo> mappedInfo = new HashMap<>();

        for (CAstEntityInfo info : CAstEntityInfos) {
            if ((info.functionName.equals("main")) && (scriptEntity == null)) {
                scriptEntity = new ScriptEntity(file, new CAstType.Function(){

                    @Override
                    public String getName() {
                        return null; // TODO
                    }

                    @Override
                    public Collection<CAstType> getSupertypes() {
                        return null; // TODO
                    }

                    @Override
                    public CAstType getReturnType() {
                        return null; // TODO
                    }

                    @Override
                    public List<CAstType> getArgumentTypes() {
                        return null; // TODO
                    }

                    @Override
                    public Collection<CAstType> getExceptionTypes() {
                        return null; // TODO
                    }

                    @Override
                    public int getArgumentCount() {
                        return 0; // TODO
                    }
                });
                scriptEntity.setAst(info.basicBlocks.get(0));
            } else {
                AbstractCodeEntity functionEntity = new FunctionEntity(info.functionName);
                functionEntities.add(functionEntity);
                if (info.basicBlocks.size() > 0) {
                    functionEntity.setAst(info.basicBlocks.get(0));
                }
            }
            mappedInfo.put(info.functionName, info);
        }
        assert(scriptEntity != null) : "Script Entity was not created most likely due to no \"main\" function found!";

        for (AbstractCodeEntity entity : functionEntities) {
            // Add scoped entities.
            for (CAstNode caller : mappedInfo.get(entity.getName()).callNodes) {
                entity.addScopedEntity(caller, findCallee(caller, functionEntities)); // TODO: Handle null
            }

            // Add the CFG targets.
            for (CAstNode cfNode : mappedInfo.get(entity.getName()).cfNodes) {
                entity.setGotoTarget(cfNode, cfNode); // Apparently this is necessary.
                entity.setGotoTarget(cfNode, findTarget(cfNode, mappedInfo.get(entity.getName()).cfNodes)); // TODO: Handle null
            }
        }
        scriptEntity.print();
        return scriptEntity;
    }

    private static CAstEntity findCallee(CAstNode node, ArrayList<AbstractCodeEntity> entities) {
        assert(node.getKind() == CAstNode.CALL);
        assert(node.getChild(0).getKind() == CAstNode.FUNCTION_EXPR);
        for (CAstEntity entity : entities) {
            if (entity.getName().equals((String)node.getChild(0).getChild(0).getValue())) {
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
                if (possibleTarget.getChild(0).getValue().equals(node.getChild(0).getValue())) {
                    return possibleTarget;
                }
            }
        }
        return null;
    }
}
