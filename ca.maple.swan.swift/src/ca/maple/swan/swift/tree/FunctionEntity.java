package ca.maple.swan.swift.tree;

import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.tree.*;

import java.util.Collection;

public class FunctionEntity extends AbstractCodeEntity {

    // WORK IN PROGRESS

    String functionName;

    public FunctionEntity(String name) {
        // Temporary
        super(new SwiftFunction());
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
        return new String[0]; // TODO?
    }

    @Override
    public CAstNode[] getArgumentDefaults() {
        return new CAstNode[0]; // TODO?
    }

    @Override
    public int getArgumentCount() {
        return 0; // TODO?
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

    @Override
    public String toString() {
        return "function " + this.functionName;
    }

}
