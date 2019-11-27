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
import com.ibm.wala.cast.ipa.callgraph.StandardFunctionTargetSelector;
import com.ibm.wala.cast.js.client.JavaScriptAnalysisEngine;
import com.ibm.wala.cast.js.ipa.callgraph.JSAnalysisOptions;
import com.ibm.wala.cast.js.ipa.callgraph.JSZeroOrOneXCFABuilder;
import com.ibm.wala.cast.js.loader.JavaScriptLoader;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.SourceModule;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.ReceiverInstanceContext;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetUtil;

import java.util.Collections;

/*
 * This class extends the JavaScriptAnalysisEngine. In the future we will most likely make our own engine entirely.
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
            Util.addDefaultSelectors(options, cha);
            options.setSelector(new StandardFunctionTargetSelector(cha, options.getMethodTargetSelector()));
            return new JSZeroOrOneXCFABuilder(cha, (JSAnalysisOptions)options, cache, new SwiftContextSelector(), (SSAContextInterpreter)null, ZeroXInstanceKeys.ALLOCATIONS, false);
        }
    }

    private static class SwiftContextSelector implements ContextSelector {

        @Override
        public Context getCalleeTarget(CGNode cgNode, CallSiteReference callSiteReference, IMethod iMethod, InstanceKey[] instanceKeys) {
            String signature =  iMethod.getReference().getSignature();
            // For now, just always have context.
            if (/*signature.contains("setter") || signature.contains("getter") || signature.contains("__allocating_init")*/ true) {
                return new ReceiverInstanceContext(instanceKeys[0]);
            }
            return null;
        }

        @Override
        public IntSet getRelevantParameters(CGNode cgNode, CallSiteReference callSiteReference) {
            return IntSetUtil.make(new int[]{0});
        }
    }
}
