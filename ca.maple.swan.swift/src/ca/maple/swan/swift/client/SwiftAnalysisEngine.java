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

import ca.maple.swan.swift.ipa.callgraph.*;
import ca.maple.swan.swift.ir.SwiftLanguage;
import ca.maple.swan.swift.loader.SwiftLoaderFactory;
import ca.maple.swan.swift.translator.SwiftToCAstTranslatorFactory;
import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.ipa.callgraph.AstCFAPointerKeys;
import com.ibm.wala.cast.ir.ssa.AstIRFactory;
import com.ibm.wala.cast.types.AstMethodReference;
import com.ibm.wala.cast.util.Util;
import com.ibm.wala.classLoader.*;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.client.AbstractAnalysisEngine;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.*;
import com.ibm.wala.ssa.IRFactory;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.WalaRuntimeException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.debug.Assertions;

import java.util.Collections;
import java.util.Set;
import java.util.jar.JarFile;

public abstract class SwiftAnalysisEngine<I extends InstanceKey>
        extends AbstractAnalysisEngine<I, CallGraphBuilder<I>, Void> {

    private final SwiftToCAstTranslatorFactory translatorFactory;
    private final SwiftLoaderFactory loaderFactory;
    private final IRFactory<IMethod> irs = AstIRFactory.makeDefaultFactory();

    public SwiftAnalysisEngine() {
        super();
        translatorFactory = new SwiftToCAstTranslatorFactory();
        loaderFactory = new SwiftLoaderFactory(translatorFactory);
    }

    @Override
    public void buildAnalysisScope() {
        SourceModule[] files = moduleFiles.toArray(new SourceModule[0]);
        scope = new SwiftAnalysisScope(files, loaderFactory, Collections.singleton(SwiftLanguage.Swift));
    }

    @Override
    public IClassHierarchy buildClassHierarchy() {
        try {
            IClassHierarchy cha = SeqClassHierarchyFactory.make(scope, loaderFactory, SwiftLanguage.Swift);
            Util.checkForFrontEndErrors(cha);
            setClassHierarchy(cha);
            return cha;
        } catch (ClassHierarchyException e) {
            Assertions.UNREACHABLE(e.toString());
            return null;
        } catch (WalaException e) {
            throw new WalaRuntimeException(e.getMessage());
        }
    }

    @Override
    public void setJ2SELibraries(JarFile[] libs) {
        Assertions.UNREACHABLE("Illegal to call setJ2SELibraries");
    }

    @Override
    public void setJ2SELibraries(Module[] libs) {
        Assertions.UNREACHABLE("Illegal to call setJ2SELibraries");
    }

    private String scriptName(Module m) {
        String path = ((ModuleEntry)m).getName();
        return "Lscript " + (path.contains("/")? path.substring(path.lastIndexOf('/')+1): path);
    }

    @Override
    protected Iterable<Entrypoint> makeDefaultEntrypoints(AnalysisScope scope, IClassHierarchy cha) {
        Set<Entrypoint> result = HashSetFactory.make();
        for(Module m : moduleFiles) {
            IClass entry = cha.lookupClass(TypeReference.findOrCreate(SwiftTypes.swiftLoader, TypeName.findOrCreate(scriptName(m))));
            assert entry != null: "bad root name " + scriptName(m) + ":\n" + cha;
            MethodReference er = MethodReference.findOrCreate(entry.getReference(), AstMethodReference.fnSelector);
            result.add(new DefaultEntrypoint(er, cha));
        }
        return result;
    }
    @Override
    public IAnalysisCacheView makeDefaultCache() {
        return new AnalysisCacheImpl(AstIRFactory.makeDefaultFactory());
    }

    @Override
    public SwiftAnalysisOptions getDefaultOptions(Iterable<Entrypoint> roots) {
        final SwiftAnalysisOptions options = new SwiftAnalysisOptions(scope, roots);

        // TODO: Which options need to be set here for SIL?

        return options;
    }


    /*

    public static class FieldBasedSwiftAnalysisEngine
            extends SwiftAnalysisEngine<ObjectVertex> {
        public enum BuilderType {
            PESSIMISTIC,
            OPTIMISTIC,
            REFLECTIVE
        }

        private BuilderType builderType = BuilderType.OPTIMISTIC;

        public BuilderType getBuilderType() {
            return builderType;
        }

        public void setBuilderType(BuilderType builderType) {
            this.builderType = builderType;
        }

        @Override
        public SwiftAnalysisOptions getDefaultOptions(Iterable<Entrypoint> roots) {
            return SwiftCallGraphUtil.makeOptions(scope, getClassHierarchy(), roots);
        }

        @Override
        protected CallGraphBuilder<ObjectVertex> getCallGraphBuilder(
                final IClassHierarchy cha, AnalysisOptions options, final IAnalysisCacheView cache) {
            Set<Entrypoint> roots = HashSetFactory.make();
            for (Entrypoint e : options.getEntrypoints()) {
                roots.add(e);
            }

            if (builderType.equals(BuilderType.OPTIMISTIC)) {
                ((SwiftAnalysisOptions) options).setHandleCallApply(false);
            }

            final FieldBasedCallGraphBuilder builder =
                    builderType.equals(BuilderType.PESSIMISTIC)
                            ? new PessimisticCallGraphBuilder(
                            getClassHierarchy(), options, makeDefaultCache(), true)
                            : new OptimisticCallgraphBuilder(
                            getClassHierarchy(), options, makeDefaultCache(), true);

            return new CallGraphBuilder<ObjectVertex>() {
                private PointerAnalysis<ObjectVertex> ptr;

                @Override
                public CallGraph makeCallGraph(AnalysisOptions options, MonitorUtil.IProgressMonitor monitor)
                        throws IllegalArgumentException, CallGraphBuilderCancelException {
                    Pair<SwiftCallGraph, PointerAnalysis<ObjectVertex>> dat;
                    try {
                        dat = builder.buildCallGraph(options.getEntrypoints(), monitor);
                    } catch (CancelException e) {
                        throw CallGraphBuilderCancelException.createCallGraphBuilderCancelException(
                                e, null, null);
                    }
                    ptr = dat.snd;
                    return dat.fst;
                }

                @Override
                public PointerAnalysis<ObjectVertex> getPointerAnalysis() {
                    return ptr;
                }

                @Override
                public IAnalysisCacheView getAnalysisCache() {
                    return cache;
                }

                @Override
                public IClassHierarchy getClassHierarchy() {
                    return cha;
                }
            };
        }
    }

    */

    public static class PropagationSwiftAnalysisEngine
            extends SwiftAnalysisEngine<InstanceKey> {

        @Override
        protected CallGraphBuilder<InstanceKey> getCallGraphBuilder(
                IClassHierarchy cha, AnalysisOptions options, IAnalysisCacheView cache) {
            return new SwiftSSAPropagationCallGraphBuilder(cha, options, cache, new AstCFAPointerKeys());
        }
    }
}
