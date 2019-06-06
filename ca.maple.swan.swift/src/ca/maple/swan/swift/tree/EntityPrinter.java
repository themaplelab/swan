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
        if (entity instanceof ScriptEntity) {
            System.out.println("SCRIPT ENTITY:");
            System.out.println("\tFILE: " + ((ScriptEntity)entity).getFileName());
        } else if (entity instanceof FunctionEntity) {
            System.out.println("FUNCTION ENTITY:");
        }
        System.out.println("\tFUNCTION NAME: " + entity.getName());
        System.out.println("\tSCOPED ENTITIES:");

        Iterator iterator = entity.getAllScopedEntities().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry pair = (Map.Entry)iterator.next();
            System.out.println("\t\t" + (CAstNode)pair.getKey() + " -->");
            for (CAstEntity scopedEntity : (Collection<CAstEntity>)(pair.getValue())) {
                System.out.println("\t\t\t" + scopedEntity.getName());
            }
        }

        System.out.println("\tCONTROL FLOW EDGES:");

        CAstControlFlowMap map = entity.getControlFlow();
        for (CAstNode source : map.getMappedNodes()) {
            for (Object label : map.getTargetLabels(source)) {
                System.out.println("\t\t" + source + " --> " + map.getTarget(source, label));
            }
        }

        System.out.println("\tTOP LEVEL AST:");
        CAstPrinter.print(entity.getAST());
    }
}
