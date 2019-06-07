package ca.maple.swan.swift.tree;

import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.tree.CAstControlFlowMap;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.util.CAstPrinter;

import java.util.Collection;
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
        System.out.println("\t<SCOPED_ENTITIES>");

        Iterator iterator = entity.getAllScopedEntities().entrySet().iterator();
        while (iterator.hasNext()) {
            System.out.println("\t\t<SCOPED_ENTITY>");
            Map.Entry pair = (Map.Entry)iterator.next();
            System.out.println("\t\t\t<CALL_NODE>");
            System.out.println("\t\t\t" + ((CAstNode)pair.getKey()).toString().replace("\n", " | "));
            System.out.println("\t\t\t</CALL_NODE>");
            for (CAstEntity scopedEntity : (Collection<CAstEntity>)(pair.getValue())) {
                System.out.println("\t\t\t<ENTITY>" + scopedEntity.getName().toString().replace("\n", " | ") + "</ENTITY>");
            }
            System.out.println("\t\t</SCOPED_ENTITY>");
        }

        System.out.println("\t</SCOPED_ENTITIES>");

        System.out.println("\t<CONTROL_FLOW_EDGES>");

        CAstControlFlowMap map = entity.getControlFlow();
        for (CAstNode source : map.getMappedNodes()) {
            System.out.println("\t\t<CONTROL_FLOW>");
            for (Object label : map.getTargetLabels(source)) {
                System.out.println("\t\t\t<FROM>" + source.toString().replace("\n", " | ") + "</FROM>");
                System.out.println("\t\t\t<TO>" + map.getTarget(source, label).toString().replace("\n", " | ") + "</TO>");
            }
            System.out.println("\t\t</CONTROL_FLOW>");
        }

        System.out.println("\t</CONTROL_FLOW_EDGES>");

        System.out.println("\t<TOP_LEVEL_AST>");
        CAstPrinter.print(entity.getAST());
        System.out.println("\t</TOP_LEVEL_AST>");

        if (entity instanceof ScriptEntity) {
            System.out.println("</SCRIPT ENTITY>");
        } else if (entity instanceof FunctionEntity) {
            System.out.println("</FUNCTION ENTITY>");
        }

        System.out.println("====================");
    }
}
