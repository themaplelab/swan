package ca.maple.swan.swift.ssa;

import ca.maple.swan.swift.ir.SwiftStoreProperty;
import com.ibm.wala.cast.ir.ssa.AstInstructionVisitor;

public interface SwiftInstructionVisitor extends AstInstructionVisitor {

    default void visitSwiftInvoke(SwiftInvokeInstruction inst) {

    }

    default void visitSwiftStoreProperty(SwiftStoreProperty inst) {

    }

}