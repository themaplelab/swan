package ca.maple.swan.swift.tree;

import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.tree.*;
import com.ibm.wala.cast.util.CAstPrinter;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class FunctionEntity extends AbstractCodeEntity {

    // WORK IN PROGRESS

    String functionName;

    public FunctionEntity(String name) {
        // Temporary
        super(new CAstType.Function() {
            @Override
            public CAstType getReturnType() {
                return null;
            }

            @Override
            public List<CAstType> getArgumentTypes() {
                return null;
            }

            @Override
            public Collection<CAstType> getExceptionTypes() {
                return null;
            }

            @Override
            public int getArgumentCount() {
                return 0;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public Collection<CAstType> getSupertypes() {
                return null;
            }
        });
        this.functionName = name;
    }

    @Override
    public int getKind() {
        return CAstEntity.FUNCTION_ENTITY;
    }

    @Override
    public String getName() {
        return this.functionName;
    }

    @Override
    public String[] getArgumentNames() {
        return new String[0];
    }

    @Override
    public CAstNode[] getArgumentDefaults() {
        return new CAstNode[0];
    }

    @Override
    public int getArgumentCount() {
        return 0;
    }

    @Override
    public CAstSourcePositionMap.Position getNamePosition() {
        return null;
    }

    @Override
    public CAstSourcePositionMap.Position getPosition(int i) {
        return null;
    }

    @Override
    public Collection<CAstQualifier> getQualifiers() {
        return null;
    }

    public void print() {
        System.out.println("FUNCTION ENTITY:");
        System.out.println("\tFUNCTION NAME: " + this.getName());
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
