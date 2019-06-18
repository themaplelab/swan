package ca.maple.swan.swift.ir;

import ca.maple.swan.swift.ssa.SwiftInstructionVisitor;
import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;

public class SwiftStoreProperty extends SSAArrayStoreInstruction {

    public SwiftStoreProperty(int iindex, int objectRef, int memberRef, int value) {
        super(iindex, objectRef, memberRef, value, SwiftTypes.Root);
    }

    @Override
    public void visit(IVisitor v) {
        ((SwiftInstructionVisitor)v).visitSwiftStoreProperty(this);
    }

}