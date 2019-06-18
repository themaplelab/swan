package ca.maple.swan.swift.loader;

import ca.maple.swan.swift.ir.SwiftLanguage;
import ca.maple.swan.swift.translator.SwiftCAstToIRTranslator;
import ca.maple.swan.swift.translator.SwiftTranslatorFactory;
import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.ir.translator.AstTranslator;
import com.ibm.wala.cast.ir.translator.TranslatorToCAst;
import com.ibm.wala.cast.ir.translator.TranslatorToIR;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.loader.CAstAbstractModuleLoader;
import com.ibm.wala.cast.tree.CAst;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.tree.CAstType;
import com.ibm.wala.cast.tree.rewrite.CAstRewriterFactory;
import com.ibm.wala.cast.types.AstMethodReference;
import com.ibm.wala.cfg.AbstractCFG;
import com.ibm.wala.cfg.IBasicBlock;
import com.ibm.wala.classLoader.*;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.*;
import com.ibm.wala.types.annotations.Annotation;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.strings.Atom;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

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

    final CoreClass ROOT = new CoreClass(SwiftTypes.rootTypeName, null, this, null);

    final CoreClass SCRIPT = new CoreClass(SwiftTypes.Script.getName(), SwiftTypes.rootTypeName, this, null);

    final CoreClass CODE_BODY = new CoreClass(SwiftTypes.CodeBody.getName(), SwiftTypes.Script.getName(), this, null);

    final CoreClass OBJECT = new CoreClass(SwiftTypes.Object.getName(), SwiftTypes.rootTypeName, this, null);

    public void defineType(TypeName cls, TypeName parent, CAstSourcePositionMap.Position sourcePosition) {
        new SwiftClass(cls, parent, this, sourcePosition);
    }

    public class SwiftClass extends CoreClass {
        private java.util.Set<IField> staticFields = HashSetFactory.make();
        private java.util.Set<MethodReference> methodTypes = HashSetFactory.make();
        private java.util.Set<TypeReference> innerTypes = HashSetFactory.make();

        public SwiftClass(TypeName name, TypeName superName, IClassLoader loader, CAstSourcePositionMap.Position sourcePosition) {
            super(name, superName, loader, sourcePosition);
            if (name.toString().lastIndexOf('/') > 0) {
                String maybeOuterName = name.toString().substring(0, name.toString().lastIndexOf('/'));
                TypeName maybeOuter = TypeName.findOrCreate(maybeOuterName);
                if (types.containsKey(maybeOuter)) {
                    IClass cls = types.get(maybeOuter);
                    if (cls instanceof SwiftClass) {
                        ((SwiftClass)cls).innerTypes.add(this.getReference());
                    }
                }
            }
        }

        @Override
        public Collection<IField> getDeclaredStaticFields() {
            return staticFields;
        }

        public Collection<MethodReference> getMethodReferences() {
            return methodTypes;
        }

        public Collection<TypeReference> getInnerReferences() {
            return innerTypes;
        }
    }

    public class DynamicMethodBody extends DynamicCodeBody {
        private final IClass container;

        public DynamicMethodBody(TypeReference codeName, TypeReference parent, IClassLoader loader,
                                 CAstSourcePositionMap.Position sourcePosition, CAstEntity entity, AstTranslator.WalkContext context, IClass container) {
            super(codeName, parent, loader, sourcePosition, entity, context);
            this.container = container;
        }

        public IClass getContainer() {
            return container;
        }

    }

    public IClass makeCodeBodyType(String name, TypeReference P, CAstSourcePositionMap.Position sourcePosition, CAstEntity entity, AstTranslator.WalkContext context) {
        return new DynamicCodeBody(TypeReference.findOrCreate(SwiftTypes.swiftLoader, TypeName.string2TypeName(name)), P, this,
                sourcePosition, entity, context);
    }

    public IClass makeMethodBodyType(String name, TypeReference P, CAstSourcePositionMap.Position sourcePosition, CAstEntity entity, AstTranslator.WalkContext context, IClass container) {
        return new DynamicMethodBody(TypeReference.findOrCreate(SwiftTypes.swiftLoader, TypeName.string2TypeName(name)), P, this,
                sourcePosition, entity, context, container);
    }

    public IMethod defineCodeBodyCode(String clsName, AbstractCFG<?, ?> cfg, SymbolTable symtab, boolean hasCatchBlock,
                                      Map<IBasicBlock<SSAInstruction>, TypeReference[]> caughtTypes, boolean hasMonitorOp, AstTranslator.AstLexicalInformation lexicalInfo, AstMethod.DebuggingInformation debugInfo, int defaultArgs) {
        DynamicCodeBody C = (DynamicCodeBody) lookupClass(clsName, cha);
        assert C != null : clsName;
        return C.setCodeBody(makeCodeBodyCode(cfg, symtab, hasCatchBlock, caughtTypes, hasMonitorOp, lexicalInfo, debugInfo, C, defaultArgs));
    }

    public DynamicMethodObject makeCodeBodyCode(AbstractCFG<?, ?> cfg, SymbolTable symtab, boolean hasCatchBlock,
                                                Map<IBasicBlock<SSAInstruction>, TypeReference[]> caughtTypes, boolean hasMonitorOp, AstTranslator.AstLexicalInformation lexicalInfo, AstMethod.DebuggingInformation debugInfo,
                                                IClass C, int defaultArgs) {
        return new DynamicMethodObject(C, Collections.emptySet(), cfg, symtab, hasCatchBlock, caughtTypes, hasMonitorOp, lexicalInfo,
                debugInfo) {
            @Override
            public int getNumberOfDefaultParameters() {
                return defaultArgs;
            }
        };
    }

    public IClass defineFunctionType(String name, CAstSourcePositionMap.Position pos, CAstEntity entity, AstTranslator.WalkContext context) {
        CAstType st = entity.getType();
        return makeCodeBodyType(name, lookupClass(TypeName.findOrCreate("L" + st.getName())).getReference(), pos, entity, context);
    }

    public IClass defineMethodType(String name, CAstSourcePositionMap.Position pos, CAstEntity entity, TypeName typeName, AstTranslator.WalkContext context) {
        SwiftClass self = (SwiftClass)types.get(typeName);

        IClass fun = makeMethodBodyType(name, SwiftTypes.CodeBody, pos, entity, context, self);

        assert types.containsKey(typeName);
        MethodReference me = MethodReference.findOrCreate(fun.getReference(), Atom.findOrCreateUnicodeAtom(entity.getType().getName()), AstMethodReference.fnDesc);
        self.methodTypes.add(me);

        return fun;
    }

    public void defineField(TypeName cls, CAstEntity field) {
        assert types.containsKey(cls);
        ((SwiftClass)types.get(cls)).staticFields.add(new IField() {
            @Override
            public String toString() {
                return "field:" + getName();
            }

            @Override
            public IClass getDeclaringClass() {
                return types.get(cls);
            }

            @Override
            public Atom getName() {
                return Atom.findOrCreateUnicodeAtom(field.getName());
            }

            @Override
            public Collection<Annotation> getAnnotations() {
                return Collections.emptySet();
            }

            @Override
            public IClassHierarchy getClassHierarchy() {
                return cha;
            }

            @Override
            public TypeReference getFieldTypeReference() {
                return SwiftTypes.Root;
            }

            @Override
            public FieldReference getReference() {
                return FieldReference.findOrCreate(getDeclaringClass().getReference(), getName(), getFieldTypeReference());
            }

            @Override
            public boolean isFinal() {
                return false;
            }

            @Override
            public boolean isPrivate() {
                return false;
            }

            @Override
            public boolean isProtected() {
                return false;
            }

            @Override
            public boolean isPublic() {
                return true;
            }

            @Override
            public boolean isStatic() {
                return true;
            }

            @Override
            public boolean isVolatile() {
                return false;
            }
        });
    }

}
