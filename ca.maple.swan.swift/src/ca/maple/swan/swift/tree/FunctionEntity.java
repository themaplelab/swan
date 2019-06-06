package ca.maple.swan.swift.tree;

import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.tree.*;

import java.util.Collection;
import java.util.List;

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
}
