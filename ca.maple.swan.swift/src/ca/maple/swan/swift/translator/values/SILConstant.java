package ca.maple.swan.swift.translator.values;

import ca.maple.swan.swift.translator.SILInstructionContext;
import com.ibm.wala.cast.tree.CAstNode;

public class SILConstant extends SILValue {

    private Object value;
    private final CAstNode node;

    public SILConstant(String name, String type, SILInstructionContext C, Object value) {
        super(name, type, C);
        this.value = value;
        node = Ast.makeConstant(value);
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public CAstNode getVarNode() {
        return node;
    }

    public CAstNode getCAst() {
        return node;
    }
}
