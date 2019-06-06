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
            if ((info.functionName == "main") && (scriptEntity == null)) {
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
                functionEntity.setAst(info.basicBlocks.get(0));
            }
            mappedInfo.put(info.functionName, info);
        }
        assert(scriptEntity != null) : "Script Entity was not created most likely due to no \"main\" function found!";

        // Add scoped entities for each entity.
        for (AbstractCodeEntity entity : functionEntities) {
            for (CAstNode caller : mappedInfo.get(entity.getName()).callNodes) {
                entity.addScopedEntity(caller, findCallee(caller, functionEntities));
            }
        }

        return scriptEntity;
    }

    private static CAstEntity findCallee(CAstNode node, ArrayList<AbstractCodeEntity> entities) {
        assert(node.getKind() == CAstNode.CALL);
        for (CAstEntity entity : entities) {
            if (entity.getName() == node.getValue()) {
                return entity;
            }
        }
        return null;
    }
}
