package ca.maple.swan.swift.ipa.callgraph;

import com.ibm.wala.cast.ipa.callgraph.CAstAnalysisScope;
import com.ibm.wala.cast.loader.SingleClassLoaderFactory;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.Module;

import java.util.Collection;

public class SwiftAnalysisScope extends CAstAnalysisScope {

    public SwiftAnalysisScope(Module[] sources, SingleClassLoaderFactory loaders, Collection<Language> languages) {
        super(sources, loaders, languages);
    }

    @Override
    protected void initForJava() {

    }

    @Override
    protected void initCoreForJava() {

    }
}
