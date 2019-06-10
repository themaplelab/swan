package ca.maple.swan.swift.loader;

import ca.maple.swan.swift.ir.SwiftInstructionFactory;
import ca.maple.swan.swift.ir.SwiftLanguage;
import ca.maple.swan.swift.translator.SwiftCAstToIRTranslator;
import ca.maple.swan.swift.translator.SwiftTranslatorFactory;
import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.ir.translator.TranslatorToCAst;
import com.ibm.wala.cast.ir.translator.TranslatorToIR;
import com.ibm.wala.cast.loader.CAstAbstractModuleLoader;
import com.ibm.wala.cast.tree.CAst;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.rewrite.CAstRewriterFactory;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.ModuleEntry;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.types.ClassLoaderReference;

import java.io.IOException;

public class SwiftLoader extends CAstAbstractModuleLoader {

    private final SwiftTranslatorFactory translatorFactory;

    private final CAstRewriterFactory<?, ?> preprocessor;

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
        return SwiftLanguage.Swift.instructionFactory();
    }

    // TODO: What exactly are these for?

    final CoreClass ROOT = new CoreClass(SwiftTypes.rootTypeName, null, this, null);

    final CoreClass CODE_BODY = new CoreClass(SwiftTypes.CodeBody.getName(), SwiftTypes.rootTypeName, this, null);

    final CoreClass STRING = new CoreClass(SwiftTypes.String.getName(), null, this, null);

    final CoreClass BOOLEAN = new CoreClass(SwiftTypes.Boolean.getName(), null, this, null);

    final CoreClass OBJECT = new CoreClass(SwiftTypes.Object.getName(), null, this, null);
}
