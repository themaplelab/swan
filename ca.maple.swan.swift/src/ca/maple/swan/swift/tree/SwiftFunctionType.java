package ca.maple.swan.swift.tree;

import com.ibm.wala.cast.tree.CAstType;

import java.util.Collection;
import java.util.List;

public class SwiftFunctionType implements CAstType.Function {

    // TODO: Implement these methods for ScriptEntity and FunctionEntity. Might have to add a constructor.

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
        return "CodeBody";
    }

    @Override
    public Collection<CAstType> getSupertypes() {
        return null;
    }
}
