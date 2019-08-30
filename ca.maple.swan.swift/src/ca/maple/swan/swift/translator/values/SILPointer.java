package ca.maple.swan.swift.translator.values;

import ca.maple.swan.swift.translator.SILInstructionContext;
import com.ibm.wala.cast.tree.CAstNode;

public class SILPointer extends SILValue {

    private SILValue pointsTo;

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

    @Override
    public CAstNode copy(String ResultName, String ResultType) {
        C.valueTable.addValue(this.copyPointer(ResultName, ResultType));
        return null;
    }

    public SILPointer copyPointer(String newName, String newType) {
        return new SILPointer(newName, newType, C, dereference());
    }

    public void replaceUnderlyingVar(SILValue to)  {
        C.valueTable.removeValue(pointsTo.getName());
        pointsTo = to;
    }
}
