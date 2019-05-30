package ca.maple.swan.swift.client;

import ca.maple.swan.swift.ipa.callgraph.SwiftSSAPropagationCallGraphBuilder;
import ca.maple.swan.swift.ir.SwiftLanguage;
import ca.maple.swan.swift.loader.SwiftLoader;
import ca.maple.swan.swift.loader.SwiftLoaderFactory;
import ca.maple.swan.swift.translator.SwiftTranslatorFactory;
import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.ipa.callgraph.AstCFAPointerKeys;
import com.ibm.wala.cast.ipa.callgraph.AstContextInsensitiveSSAContextInterpreter;
import com.ibm.wala.cast.ipa.callgraph.CAstAnalysisScope;
import com.ibm.wala.cast.ir.ssa.AstIRFactory;
import com.ibm.wala.cast.types.AstMethodReference;
import com.ibm.wala.cast.util.Util;
import com.ibm.wala.classLoader.*;
import com.ibm.wala.client.AbstractAnalysisEngine;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.ClassHierarchyClassTargetSelector;
import com.ibm.wala.ipa.callgraph.impl.ClassHierarchyMethodTargetSelector;
import com.ibm.wala.ipa.callgraph.impl.ContextInsensitiveSelector;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.nCFAContextSelector;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.WalaRuntimeException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.cha.SeqClassHierarchyFactory;
import com.ibm.wala.ssa.IRFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.debug.Assertions;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

public abstract class SwiftAnalysisEngine<T>
        extends AbstractAnalysisEngine<InstanceKey, SwiftSSAPropagationCallGraphBuilder, T> {

    private SwiftLoaderFactory loaderFactory;
    protected SwiftTranslatorFactory translatorFactory;
    private final IRFactory<IMethod> irs = AstIRFactory.makeDefaultFactory();

    public SwiftAnalysisEngine() {
        super();
    }

    @Override
    public void buildAnalysisScope() {
        System.out.println("Building analysis scope...");
        loaderFactory = new SwiftLoaderFactory(translatorFactory);
        SourceModule[] files = moduleFiles.toArray(new SourceModule[0]);
        scope = new CAstAnalysisScope(files, loaderFactory, Collections.singleton(SwiftLanguage.Swift));
    }

    @Override
    public IClassHierarchy buildClassHierarchy() {
        System.out.println("Building class hierarchy...");
        try {
            IClassHierarchy cha = SeqClassHierarchyFactory.make(scope, loaderFactory);
            Util.checkForFrontEndErrors(cha);
            for(Module m : moduleFiles) {
                // cha is NULL here!
                IClass entry = cha.lookupClass(TypeReference.findOrCreate(SwiftTypes.swiftLoader, TypeName.findOrCreate(scriptName(m))));
                System.out.println(entry.toString());
            }
            return cha;
        } catch (ClassHierarchyException e) {
            e.printStackTrace();
            return null;
        } catch (WalaException e) {
            throw new WalaRuntimeException(e.getMessage());
        }
    }

    public void setTranslatorFactory(SwiftTranslatorFactory factory) {
        this.translatorFactory = factory;
    }

    private String scriptName(Module m) {
        String path = ((ModuleEntry)m).getName();
        return "Lscript " + (path.contains("/")? path.substring(path.lastIndexOf('/')+1): path);
    }

    @Override
    protected Iterable<Entrypoint> makeDefaultEntrypoints(AnalysisScope scope, IClassHierarchy cha) {
        Set<Entrypoint> result = HashSetFactory.make();
        for(Module m : moduleFiles) {
            // cha is NULL here!
            IClass entry = cha.lookupClass(TypeReference.findOrCreate(SwiftTypes.swiftLoader, TypeName.findOrCreate(scriptName(m))));
            assert entry != null: "bad root name " + scriptName(m) + ":\n" + cha;
            MethodReference er = MethodReference.findOrCreate(entry.getReference(), AstMethodReference.fnSelector);
            result.add(new DefaultEntrypoint(er, cha));
        }
        return result;
    }

    @Override
    protected SwiftSSAPropagationCallGraphBuilder getCallGraphBuilder(IClassHierarchy cha, AnalysisOptions options, IAnalysisCacheView cache2) {
        IAnalysisCacheView cache = new AnalysisCacheImpl(irs, options.getSSAOptions());

        options.setSelector(new ClassHierarchyClassTargetSelector(cha));
        options.setSelector(new ClassHierarchyMethodTargetSelector(cha));

        options.setUseConstantSpecificKeys(true);

        SSAOptions ssaOptions = options.getSSAOptions();
        ssaOptions.setDefaultValues(new SSAOptions.DefaultValues() {
            @Override
            public int getDefaultValue(SymbolTable symtab, int valueNumber) {
                return symtab.getNullConstant();
            }
        });
        options.setSSAOptions(ssaOptions);

        SwiftSSAPropagationCallGraphBuilder builder =
                new SwiftSSAPropagationCallGraphBuilder(cha, options, cache, new AstCFAPointerKeys());

        AstContextInsensitiveSSAContextInterpreter interpreter = new AstContextInsensitiveSSAContextInterpreter(options, cache);
        builder.setContextInterpreter(interpreter);

        builder.setContextSelector(new nCFAContextSelector(1, new ContextInsensitiveSelector()));

        return builder;
    }

    public abstract T performAnalysis(PropagationCallGraphBuilder builder) throws CancelException;

}
