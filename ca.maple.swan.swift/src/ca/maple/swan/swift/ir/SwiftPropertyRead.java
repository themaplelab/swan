package ca.maple.swan.swift.ir;

import java.util.Collection;
import java.util.Collections;

import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.ir.ssa.AstPropertyRead;
import com.ibm.wala.types.TypeReference;

public class SwiftPropertyRead extends AstPropertyRead {
    public SwiftPropertyRead(int iindex, int result, int objectRef, int memberRef) {
        super(iindex, result, objectRef, memberRef);
    }

    /* (non-Javadoc)
     * @see com.ibm.domo.ssa.Instruction#getExceptionTypes()
     */
    @Override
    public Collection<TypeReference> getExceptionTypes() {
        return Collections.singleton(SwiftTypes.Root);
    }
}
