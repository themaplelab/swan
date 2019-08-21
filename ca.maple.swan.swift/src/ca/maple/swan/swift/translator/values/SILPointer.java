package ca.maple.swan.swift.translator.values;

import ca.maple.swan.swift.translator.SILInstructionContext;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.util.debug.Assertions;

public class SILPointer extends SILValue {

    private final SILValue pointsTo;

    public SILPointer(String name, String type, SILInstructionContext C, SILValue pointsTo) {
        super(name, type, C);
        this.pointsTo = pointsTo;
    }

    public SILValue dereference() {
        return pointsTo;
    }

    public CAstNode getUnderlyingVar() {
        return pointsTo.getVarNode();
    }

    @Override
    public CAstNode getVarNode() {
        return getUnderlyingVar();
    }

    public SILPointer copyPointer(String newName, String newType) {
        return new SILPointer(newName, newType, C, dereference());
    }
}
