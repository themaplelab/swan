package ca.maple.swan.swift.tree;

import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.impl.CAstControlFlowRecorder;
import com.ibm.wala.cast.util.CAstPrinter;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

public class EntityPrinter {
    public static void print(AbstractCodeEntity entity) {
        System.out.println("====================");
        if (entity instanceof ScriptEntity) {
            System.out.println("<SCRIPT_ENTITY>");
            System.out.println("\t<FILE>" + ((ScriptEntity)entity).getFileName() + "</FILE>");
        } else if (entity instanceof FunctionEntity) {
            System.out.println("<FUNCTION_ENTITY>");
        }
        System.out.println("\t<FUNCTION_NAME>" + entity.getName() + "</FUNCTION_NAME>");

        if (!entity.getAllScopedEntities().equals(Collections.emptyMap())) {
            System.out.println("\t<SCOPED_ENTITIES>");

            Iterator iterator = entity.getAllScopedEntities().entrySet().iterator();
            while (iterator.hasNext()) {
                System.out.println("\t\t<SCOPED_ENTITY>");
                Map.Entry pair = (Map.Entry) iterator.next();
                System.out.println("\t\t\t<CALL_NODE>");
                System.out.println("\t\t\t" + ((CAstNode) pair.getKey()).toString().replace("\n", " | "));
                System.out.println("\t\t\t</CALL_NODE>");
                for (CAstEntity scopedEntity : (Collection<CAstEntity>) (pair.getValue())) {
                    System.out.println("\t\t\t<ENTITY>" + scopedEntity.getName().toString().replace("\n", " | ") + "</ENTITY>");
                }
                System.out.println("\t\t</SCOPED_ENTITY>");
            }

            System.out.println("\t</SCOPED_ENTITIES>");
        }

        if (!entity.getControlFlow().getMappedNodes().isEmpty()) {
            System.out.println("\t<CONTROL_FLOW_EDGES>");
            System.out.println("\t<NOTE>Reflexive edges are ommited!</NOTE>");

            CAstControlFlowRecorder map = entity.getControlFlow();
            for (CAstNode source : map.getMappedNodes()) {
                if (map.isMapped(source) && !(map.getTargetLabels(source).isEmpty())) {
                    System.out.println("\t\t<CONTROL_FLOW>");
                    System.out.println("\t\t\t<FROM>" + source.toString().replace("\n", " | ") + "</FROM>");
                    for (Object label : map.getTargetLabels(source)) {
                        CAstNode target = map.getTarget(source, label);
                        if (target != source) {
                            System.out.println("\t\t\t<TO>" + target.toString().replace("\n", " | ") + "</TO>");
                        }
                    }
                    System.out.println("\t\t</CONTROL_FLOW>");
                }
            }
            System.out.println("\t</CONTROL_FLOW_EDGES>");
        }

        if (entity.getAST() != null) {
            System.out.println("\t<TOP_LEVEL_AST>");
            CAstPrinter.print(entity.getAST());
            System.out.println("\t</TOP_LEVEL_AST>");
        }

        if (entity instanceof ScriptEntity) {
            System.out.println("</SCRIPT ENTITY>");
        } else if (entity instanceof FunctionEntity) {
            System.out.println("</FUNCTION ENTITY>");
        }

        System.out.println("====================");
    }
}
