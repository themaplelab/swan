package ca.maple.swan.swift.ipa.callgraph;

import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.Entrypoint;

public class SwiftAnalysisOptions extends AnalysisOptions {

    public SwiftAnalysisOptions(AnalysisScope scope, Iterable<? extends Entrypoint> e) {
        super(scope, e);
    }
}