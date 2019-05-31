package ca.maple.swan.swift.ipa.callgraph;

import ca.maple.swan.swift.ir.SwiftLanguage;
import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.ipa.callgraph.AstSSAPropagationCallGraphBuilder;
import com.ibm.wala.cast.ipa.callgraph.GlobalObjectKey;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.propagation.AbstractFieldPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKeyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.util.strings.Atom;

public class SwiftSSAPropagationCallGraphBuilder extends AstSSAPropagationCallGraphBuilder {

    @Override
    protected boolean useObjectCatalog() {
        return true;
    }

    @Override
    public GlobalObjectKey getGlobalObject(Atom language) {
        assert language.equals(SwiftLanguage.Swift.getName());
        return new GlobalObjectKey(cha.lookupClass(SwiftTypes.Root));
    }

    @Override
    protected AbstractFieldPointerKey fieldKeyForUnknownWrites(AbstractFieldPointerKey abstractFieldPointerKey) {
        return null;
    }

    @Override
    protected boolean sameMethod(CGNode opNode, String definingMethod) {
        return definingMethod.equals(opNode.getMethod().getReference().getDeclaringClass().getName().toString());
    }


    public SwiftSSAPropagationCallGraphBuilder(IClassHierarchy cha, AnalysisOptions options, IAnalysisCacheView cache,
                                               PointerKeyFactory pointerKeyFactory) {
        super(SwiftLanguage.Swift.getFakeRootMethod(cha, options, cache), options, cache, pointerKeyFactory);
    }
}
