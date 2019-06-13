package ca.maple.swan.swift.loader;

import ca.maple.swan.swift.translator.SwiftTranslatorFactory;
import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.loader.SingleClassLoaderFactory;
import com.ibm.wala.cast.tree.rewrite.CAstRewriterFactory;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;


public class SwiftLoaderFactory extends SingleClassLoaderFactory {
    protected final SwiftTranslatorFactory translatorFactory;
    protected final CAstRewriterFactory<?, ?> preprocessor;

    public SwiftLoaderFactory(SwiftTranslatorFactory factory) {
        this(factory, null);
    }

    public SwiftLoaderFactory(
            SwiftTranslatorFactory factory, CAstRewriterFactory<?, ?> preprocessor) {
        this.translatorFactory = factory;
        this.preprocessor = preprocessor;
    }

    @Override
    protected IClassLoader makeTheLoader(IClassHierarchy cha) {
        return new SwiftLoader(cha, translatorFactory, preprocessor);
    }

    @Override
    public ClassLoaderReference getTheReference() {
        return SwiftTypes.swiftLoader;
    }
}