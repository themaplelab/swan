package ca.maple.swan.swift.ipa.callgraph;

import ca.maple.swan.swift.ir.SwiftLanguage;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.propagation.PointerKeyFactory;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.IClassHierarchy;

public class SwiftSSAPropagationCallGraphBuilder extends SSAPropagationCallGraphBuilder {

    public SwiftSSAPropagationCallGraphBuilder(IClassHierarchy cha, AnalysisOptions options, IAnalysisCacheView cache,
                                               PointerKeyFactory pointerKeyFactory) {
        super(SwiftLanguage.Swift.getFakeRootMethod(cha, options, cache), options, cache, pointerKeyFactory);
    }
}
