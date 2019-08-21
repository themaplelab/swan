package ca.maple.swan.swift.translator.values;

import ca.maple.swan.swift.translator.SILInstructionContext;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.util.debug.Assertions;

public class SILField extends SILValue {

    private final SILValue object;
    private final Object field;

    public SILField(String name, String type, SILInstructionContext C, SILValue object, Object fieldName) {
        super(name, type, C);
        this.object = object;
        this.field = fieldName;
    }

    public SILValue getObject() {
        return object;
    }

    public Object getField() {
        return field;
    }

    @Override
    public CAstNode getVarNode() {
        Assertions.UNREACHABLE("This is undefined behavior, for now");
        return null;
    }
}
