package ca.maple.swan.swift.ipa.summaries;

import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.eclipse.lsp4j.jsonrpc.messages.Either;

import ca.maple.swan.swift.ir.SwiftLanguage;
import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.ir.ssa.AstInstructionFactory;
import com.ibm.wala.cast.loader.AstDynamicField;
import com.ibm.wala.cast.types.AstMethodReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.ClassTargetSelector;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.annotations.Annotation;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.strings.Atom;

public class BuiltinFunctions {

    private final IClassHierarchy cha;

    public BuiltinFunctions(IClassHierarchy cha) {
        this.cha = cha;
    }

    private static IMethod typeSummary(IClass cls, String name, TypeReference type) {
        SwiftSummary S = typeSummary(cls, builtinFunction(name), type);
        return new SwiftSummarizedFunction((MethodReference) S.getMethod(), S, cls);
    }

    private static SwiftSummary typeSummary(IClass cls, TypeReference type, TypeReference returnedType) {
        MethodReference ref = MethodReference.findOrCreate(type, AstMethodReference.fnSelector);
        SwiftSummary x = new SwiftSummary(ref, 10);

        AstInstructionFactory factory = SwiftLanguage.Swift.instructionFactory();
        x.addStatement(factory.NewInstruction(0, 11, NewSiteReference.make(0, returnedType)));
        x.addStatement(factory.ReturnInstruction(1, 11, false));

        return x;
    }

    private static IMethod argSummary(IClass cls, String name, int arg) {
        SwiftSummary S = argSummary(cls, builtinFunction(name), arg);
        return new SwiftSummarizedFunction((MethodReference) S.getMethod(), S, cls);
    }

    private static SwiftSummary argSummary(IClass cls, TypeReference type, int arg) {
        MethodReference ref = MethodReference.findOrCreate(type, AstMethodReference.fnSelector);
        SwiftSummary x = new SwiftSummary(ref, 10);

        AstInstructionFactory factory = SwiftLanguage.Swift.instructionFactory();
        x.addStatement(factory.ReturnInstruction(0, arg, false));

        return x;
    }

    private static IMethod noopSummary(IClass cls, String name) {
        SwiftSummary S = noopSummary(cls, builtinFunction(name));
        return new SwiftSummarizedFunction((MethodReference) S.getMethod(), S, cls);
    }

    private static SwiftSummary noopSummary(IClass cls, TypeReference type) {
        MethodReference ref = MethodReference.findOrCreate(type, AstMethodReference.fnSelector);
        SwiftSummary x = new SwiftSummary(ref, 10);

        AstInstructionFactory factory = SwiftLanguage.Swift.instructionFactory();
        x.addStatement(factory.ReturnInstruction(0));

        return x;
    }

    public static class BuiltinFunction implements IClass {
        private final TypeReference ref;
        private final IMethod builtinCode;
        private final IClassHierarchy cha;

        public BuiltinFunction(IClassHierarchy cha, String name, TypeReference returnedType) {
            this.cha = cha;
            this.ref = builtinFunction(name);
            this.builtinCode = returnedType==null? noopSummary(this, name): typeSummary(this, name, returnedType);
        }

        public BuiltinFunction(IClassHierarchy cha, String name, int arg) {
            this.cha = cha;
            this.ref = builtinFunction(name);
            this.builtinCode = argSummary(this, name, arg);
        }

        public BuiltinFunction(IClassHierarchy cha, String name) {
            this(cha, name, null);
        }

        @Override
        public IClassHierarchy getClassHierarchy() {
            return cha;
        }

        @Override
        public IClassLoader getClassLoader() {
            return cha.getLoader(SwiftTypes.swiftLoader);
        }

        @Override
        public boolean isInterface() {
            return false;
        }

        @Override
        public boolean isAbstract() {
            return false;
        }

        @Override
        public boolean isPublic() {
            return true;
        }

        @Override
        public boolean isPrivate() {
            return false;
        }

        @Override
        public boolean isSynthetic() {
            return false;
        }

        @Override
        public int getModifiers() throws UnsupportedOperationException {
            return 0;
        }

        @Override
        public IClass getSuperclass() {
            return cha.lookupClass(SwiftTypes.CodeBody);
        }

        @Override
        public Collection<? extends IClass> getDirectInterfaces() {
            return Collections.emptySet();
        }

        @Override
        public Collection<IClass> getAllImplementedInterfaces() {
            return Collections.emptySet();
        }

        @Override
        public IMethod getMethod(Selector selector) {
            return AstMethodReference.fnSelector.equals(selector)? builtinCode: null;
        }

        protected final Map<Atom, IField> declaredFields = HashMapFactory.make();

        @Override
        public IField getField(final Atom name) {
            IField x;
            if (declaredFields.containsKey(name)) {
                return declaredFields.get(name);
            } else if (getSuperclass() != null && (x = getSuperclass().getField(name)) != null) {
                return x;
            } else {
                declaredFields.put(name, new AstDynamicField(false, this, name, SwiftTypes.Root));

                return declaredFields.get(name);
            }

        }

        @Override
        public IField getField(Atom name, TypeName type) {
            return null;
        }

        @Override
        public TypeReference getReference() {
            return ref;
        }

        @Override
        public String getSourceFileName() throws NoSuchElementException {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Reader getSource() throws NoSuchElementException {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public IMethod getClassInitializer() {
            return null;
        }

        @Override
        public boolean isArrayClass() {
            return false;
        }

        @Override
        public Collection<? extends IMethod> getDeclaredMethods() {
            return Collections.singleton(builtinCode);
        }

        @Override
        public Collection<IField> getAllInstanceFields() {
            return Collections.emptySet();
        }

        @Override
        public Collection<IField> getAllStaticFields() {
            return Collections.emptySet();
        }

        @Override
        public Collection<IField> getAllFields() {
            return Collections.emptySet();
        }

        @Override
        public Collection<? extends IMethod> getAllMethods() {
            return Collections.singleton(builtinCode);
        }

        @Override
        public Collection<IField> getDeclaredInstanceFields() {
            return Collections.emptySet();
        }

        @Override
        public Collection<IField> getDeclaredStaticFields() {
            return Collections.emptySet();
        }

        @Override
        public TypeName getName() {
            return ref.getName();
        }

        @Override
        public boolean isReferenceType() {
            return true;
        }

        @Override
        public Collection<Annotation> getAnnotations() {
            return Collections.emptySet();
        }

    }

    public static TypeReference builtinFunction(String name) {
        return TypeReference.findOrCreate(SwiftTypes.swiftLoader, "Lwala/builtin/" + name);
    }

    private static final Map<String,Either<TypeReference,Integer>> builtinFunctions = HashMapFactory.make();

    static {
        /*
        builtinFunctions.put("enumerate", Either.forLeft(SwiftTypes.enumerate));
        builtinFunctions.put("int", Either.forLeft(TypeReference.Int));
        builtinFunctions.put("len", Either.forLeft(TypeReference.Int));
        builtinFunctions.put("list", Either.forLeft(SwiftTypes.list));
        builtinFunctions.put("range", Either.forLeft(SwiftTypes.list));
        builtinFunctions.put("sorted", Either.forLeft(SwiftTypes.list));
        builtinFunctions.put("str", Either.forLeft(SwiftTypes.string));
        builtinFunctions.put("sum", Either.forLeft(TypeReference.Int));
        builtinFunctions.put("type", Either.forLeft(SwiftTypes.object));
        builtinFunctions.put("zip", Either.forLeft(SwiftTypes.list));
        builtinFunctions.put("slice", Either.forRight(2));
        */
    }

    public static Set<String> builtins() {
        return builtinFunctions.keySet();
    }


    public ClassTargetSelector builtinClassTargetSelector(ClassTargetSelector current) {
        Map<TypeReference,IClass> builtins = HashMapFactory.make();
        builtinFunctions.entrySet().forEach((bf) -> {
            Either<TypeReference, Integer> v = bf.getValue();
            builtins.put(builtinFunction(bf.getKey()),
                    v.isLeft()?
                            new BuiltinFunction(cha, bf.getKey(), v.getLeft()):
                            new BuiltinFunction(cha, bf.getKey(), v.getRight()));
        });

        return new ClassTargetSelector() {
            @Override
            public IClass getAllocatedTarget(CGNode caller, NewSiteReference site) {
                if (builtins.containsKey(site.getDeclaredType())) {
                    return builtins.get(site.getDeclaredType());
                } else {
                    return current.getAllocatedTarget(caller, site);
                }
            }
        };
    }
}