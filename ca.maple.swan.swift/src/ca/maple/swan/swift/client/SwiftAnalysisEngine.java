//===--- SwiftAnalysisEngine.java ----------------------------------------===//
//
// This source file is part of the SWAN open source project
//
// Copyright (c) 2019 Maple @ University of Alberta
// All rights reserved. This program and the accompanying materials (unless
// otherwise specified by a license inside of the accompanying material)
// are made available under the terms of the Eclipse Public License v2.0
// which accompanies this distribution, and is available at
// http://www.eclipse.org/legal/epl-v20.html
//
//===---------------------------------------------------------------------===//

package ca.maple.swan.swift.client;

import ca.maple.swan.swift.loader.SwiftLoaderFactory;
import com.ibm.wala.cast.ipa.callgraph.CAstAnalysisScope;
import com.ibm.wala.cast.js.client.JavaScriptAnalysisEngine;
import com.ibm.wala.cast.js.client.impl.ZeroCFABuilderFactory;
import com.ibm.wala.cast.js.ipa.callgraph.JSAnalysisOptions;
import com.ibm.wala.cast.js.loader.JavaScriptLoader;
import com.ibm.wala.classLoader.SourceModule;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.IClassHierarchy;

import java.util.Collections;

/*
 * This class extends the JavaScriptAnalysisEngine because we need to
 * be able to set a custom loader factory in order to be able to use
 * our own extended AstTranslator.
 */

public abstract class SwiftAnalysisEngine<I extends InstanceKey>
        extends JavaScriptAnalysisEngine<I> {

    @Override
    public void buildAnalysisScope() {
        loaderFactory = new SwiftLoaderFactory(translatorFactory);

        SourceModule[] files = moduleFiles.toArray(new SourceModule[0]);

        scope = new CAstAnalysisScope(files, loaderFactory, Collections.singleton(JavaScriptLoader.JS));
    }

    public static class SwiftPropagationJavaScriptAnalysisEngine
            extends SwiftAnalysisEngine<InstanceKey> {

        @Override
        protected CallGraphBuilder<InstanceKey> getCallGraphBuilder(
                IClassHierarchy cha, AnalysisOptions options, IAnalysisCacheView cache) {
            return new ZeroCFABuilderFactory().make((JSAnalysisOptions) options, cache, cha);
        }
    }
}
