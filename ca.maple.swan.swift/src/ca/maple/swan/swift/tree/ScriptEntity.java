package ca.maple.swan.swift.tree;

import com.ibm.wala.cast.ir.translator.AbstractScriptEntity;
import com.ibm.wala.cast.tree.*;
import com.ibm.wala.cast.util.CAstPrinter;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public class ScriptEntity extends AbstractScriptEntity {

    // WORK IN PROGRESS

    // ScriptEntity -> AbstractScriptEntity -> AbstractCodeEntity -> AbstractEntity -> CAstEntity
    // AbstractScriptEntity:
    //      Handles basic filename, kind, name, etc
    // AbstractCodeEntity:
    //      Handles AST, CFG, types
    // AbstractEntity:
    //      Handles scoped entities.
    // CAstEntity:
    //      Defines types and methods, which are all implemented by the above so we don't deal with
    //      this class directly.

    // What this class expects from the C++ translator:
    //      Map<String, CAstNode>
    //      String - Function name, first entry should be "main"
    //      CAstNode - Basic Block #0 of the SILFunction

    public ScriptEntity(File file, CAstType type) {
        super(file, type);
    }

    @Override
    public CAstSourcePositionMap.Position getNamePosition() {
        return null;
    }

    @Override
    public CAstSourcePositionMap.Position getPosition(int i) {
        return null;
    }

    public void print() {
        System.out.println("SCRIPT ENTITY:");
        System.out.println("\tFUNCTION NAME: " + this.getName());
        System.out.println("\tFILE: " + this.getFile());
        System.out.println("\tSCOPED ENTITIES:");

        Iterator iterator = this.getAllScopedEntities().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry pair = (Map.Entry)iterator.next();
            System.out.println("\t\t" + (CAstNode)pair.getKey() + " -->");
            for (CAstEntity entity : (Collection<CAstEntity>)(pair.getValue())) {
                System.out.println("\t\t\t" + entity.getName());
            }
        }

        System.out.println("\tCONTROL FLOW EDGES:");

        CAstControlFlowMap map = getControlFlow();
        for (CAstNode source : map.getMappedNodes()) {
            for (Object label : map.getTargetLabels(source)) {
                System.out.println("\t\t" + source + " --> " + map.getTarget(source, label));
            }
        }

        System.out.println("\tTOP LEVEL AST:");
        CAstPrinter.print(getAST());
    }
}
