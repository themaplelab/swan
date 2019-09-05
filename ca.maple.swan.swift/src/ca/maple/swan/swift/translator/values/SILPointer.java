package ca.maple.swan.swift.translator.values;

import ca.maple.swan.swift.translator.SILInstructionContext;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.util.debug.Assertions;

public class SILPointer extends SILValue {

    private SILValue pointsTo;
    private boolean hasValue = true;

    public SILPointer(String name, String type, SILInstructionContext C, SILValue pointsTo) {
        super(name, type, C);
        this.pointsTo = pointsTo;
    }

    public SILPointer(String name, String type, SILInstructionContext C) {
        this(name, type, C, null);
        this.hasValue = false;
    }

    public SILValue dereference() {
        if (pointsTo == null) { // This isn't ideal;
            return this;
        }
        return pointsTo;
    }

    public boolean hasValue() {
        return this.hasValue;
    }

    public CAstNode getUnderlyingVar() {
        if (pointsTo == null) { // This isn't ideal;
            return this.varNode;
        }
        return pointsTo.getVarNode();
    }

    @Override
    public CAstNode getVarNode() {
        return getUnderlyingVar();
    }

    @Override
    public CAstNode copy(String ResultName, String ResultType) {
        C.valueTable.addValue(this.copyPointer(ResultName, ResultType));
        return null;
    }

    public SILPointer copyPointer(String newName, String newType) {
        // If the pointer has no underlying value, it will conveniently
        // point the new pointer to this pointer.
        return new SILPointer(newName, newType, C, dereference());
    }

    public void replaceUnderlyingVar(SILValue to)  {
        C.valueTable.removeValue(pointsTo.getName());
        pointsTo = to;
    }
}
