package ca.maple.swan.swift.translator;

import ca.maple.swan.swift.ipa.summaries.BuiltInFunctionSummaries;
import ca.maple.swan.swift.tree.*;
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
import java.util.List;

import static com.ibm.wala.cast.tree.CAstNode.*;

public class RawAstTranslator {

    private static CAstImpl Ast = new CAstImpl();

    public static ScriptEntity translate(File file, ArrayList<CAstEntityInfo> CAstEntityInfos) {
        boolean DEBUG = true;
        ScriptEntity scriptEntity = null;
        ArrayList<AbstractCodeEntity> functionEntities = new ArrayList<>();

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

            translateEntity(newEntity, info.basicBlocks);
            if (DEBUG) EntityPrinter.print(newEntity);

            // Do away with types for now.
            for (CAstNode node: info.variableTypes.keySet()) {
                newEntity.setNodeType(node, new AnyCAstType());
            }
        }

        assert(scriptEntity != null) : "Script Entity was not created most likely due to no \"main\" function found.";

        if (DEBUG) ASTtoDot.print(functionEntities);
        if (DEBUG) System.out.println("\n==========END=OF=CAST=ENTITIES=========\n\n");

        return scriptEntity;
    }

    private static void translateEntity(AbstractCodeEntity entity, ArrayList<CAstNode> basicBlocks) {
        ArrayList<CAstNode> newBasicBlocks = new ArrayList<>();
        for (CAstNode block : basicBlocks) {
            newBasicBlocks.add(translateBasicBlock(block));
        }
        List<CAstNode> body = new ArrayList<>(newBasicBlocks.get(0).getChildren());
        for (int i = 1; i < newBasicBlocks.size(); i++) {
            body.add(newBasicBlocks.get(i));
        }
        CAstNode parentBlock = Ast.makeNode(CAstNode.BLOCK_STMT, body);
        entity.setAst(parentBlock);
    }

    private static CAstNode translateBasicBlock(CAstNode BB) {
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
                            CAstNode summary = BuiltInFunctionSummaries.findSummary(src);
                            if (summary != null) {
                                newAst.add(Ast.makeNode(ASSIGN, n.getChild(0), summary));
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
                default: {
                    newAst.add(n);
                }

            }
        }
        return Ast.makeNode(BLOCK_STMT, newAst);
    }
}
