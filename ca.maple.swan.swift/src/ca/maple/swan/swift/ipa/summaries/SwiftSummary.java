package ca.maple.swan.swift.ipa.summaries;

import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

public class SwiftSummary extends MethodSummary {

    private final int declaredParameters;

    public SwiftSummary(MethodReference ref, int declaredParameters) {
        super(ref);
        this.declaredParameters = declaredParameters;
    }

    @Override
    public int getNumberOfParameters() {
        return declaredParameters;
    }

    @Override
    public TypeReference getParameterType(int i) {
        return SwiftTypes.Root;
    }

}