package ca.maple.swan.swift.ir;

import java.util.Collection;
import java.util.Collections;

import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.ir.ssa.AstPropertyWrite;
import com.ibm.wala.types.TypeReference;

public class SwiftPropertyWrite extends AstPropertyWrite {

    public SwiftPropertyWrite(int iindex, int objectRef, int memberRef, int value) {
        super(iindex, objectRef, memberRef, value);
    }

    /*
     * (non-Javadoc)
     * @see com.ibm.domo.ssa.Instruction#getExceptionTypes()
     */
    @Override
    public Collection<TypeReference> getExceptionTypes() {
        return Collections.singleton(SwiftTypes.Root);
    }

}