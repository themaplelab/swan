package ca.maple.swan.swift.ipa.summaries;

import ca.maple.swan.swift.cfg.SwiftInducedCFG;
import com.ibm.wala.cfg.InducedCFG;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.ipa.summaries.SummarizedMethodWithNames;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.MethodReference;

public class SwiftSummarizedFunction extends SummarizedMethodWithNames {

    public SwiftSummarizedFunction(MethodReference ref, MethodSummary summary, IClass declaringClass)
            throws NullPointerException {
        super(ref, summary, declaringClass);
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public InducedCFG makeControlFlowGraph(SSAInstruction[] instructions) {
        return new SwiftInducedCFG(instructions, this, Everywhere.EVERYWHERE);
    }

}