package ca.maple.swan.swift.loader;

import ca.maple.swan.swift.ir.SwiftLanguage;
import ca.maple.swan.swift.translator.SwiftCAstToIRTranslator;
import ca.maple.swan.swift.translator.SwiftToCAstTranslator;
import ca.maple.swan.swift.translator.SwiftTranslatorFactory;
import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.ir.translator.TranslatorToCAst;
import com.ibm.wala.cast.ir.translator.TranslatorToIR;
import com.ibm.wala.cast.loader.CAstAbstractModuleLoader;
import com.ibm.wala.cast.tree.CAst;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.impl.CAstTypeDictionaryImpl;
import com.ibm.wala.cast.tree.rewrite.CAstRewriterFactory;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.ModuleEntry;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.types.ClassLoaderReference;

import java.io.IOException;

public class SwiftLoader extends CAstAbstractModuleLoader {

    private final SwiftTranslatorFactory translatorFactory;

    private final CAstRewriterFactory<?, ?> preprocessor;

    private final CAstTypeDictionaryImpl<String> typeDictionary = new CAstTypeDictionaryImpl<String>();

    public SwiftLoader(IClassHierarchy cha, SwiftTranslatorFactory translatorFactory) {
        this(cha, translatorFactory, null);
    }

    public SwiftLoader(
            IClassHierarchy cha,
            SwiftTranslatorFactory translatorFactory,
            CAstRewriterFactory<?, ?> preprocessor) {
        super(cha);
        this.translatorFactory = translatorFactory;
        this.preprocessor = preprocessor;
    }

    @Override
    protected TranslatorToCAst getTranslatorToCAst(CAst ast, ModuleEntry module) throws IOException {
        TranslatorToCAst translator = translatorFactory.make(ast, module);
        if (preprocessor != null) translator.addRewriter(preprocessor, true);
        return translator;
    }

    @Override
    protected boolean shouldTranslate(CAstEntity cAstEntity) {
        return true;
    }

    @Override
    protected TranslatorToIR initTranslator() {
        return new SwiftCAstToIRTranslator(this);
    }

    @Override
    public ClassLoaderReference getReference() {
        return SwiftTypes.swiftLoader;
    }

    @Override
    public Language getLanguage() {
        return SwiftLanguage.Swift;
    }

    @Override
    public SSAInstructionFactory getInstructionFactory() {
        return null;
    }
}
