package ca.maple.swan.swift.ir;

import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.analysis.typeInference.PrimitiveType;
import com.ibm.wala.classLoader.LanguageImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.AbstractRootMethod;
import com.ibm.wala.ipa.callgraph.impl.FakeRootClass;
import com.ibm.wala.ipa.callgraph.impl.FakeRootMethod;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.modref.ExtendedHeapModel;
import com.ibm.wala.ipa.modref.ModRef;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.Atom;

import java.util.Collection;

public class SwiftLanguage extends LanguageImpl {

    public static final SwiftLanguage Swift = new SwiftLanguage();

    @Override
    public Atom getName() {
        return SwiftTypes.swiftName;
    }

    @Override
    public TypeReference getRootType() {
        return SwiftTypes.Root;
    }

    @Override
    public TypeReference getThrowableType() {
        return null;
    }

    @Override
    public TypeReference getConstantType(Object o) {
        return null;
    }

    @Override
    public boolean isNullType(TypeReference typeReference) {
        return false;
    }

    @Override
    public boolean isIntType(TypeReference typeReference) {
        return typeReference == SwiftTypes.Integer;
    }

    @Override
    public boolean isLongType(TypeReference typeReference) {
        return false;
    }

    @Override
    public boolean isVoidType(TypeReference typeReference) {
        return false;
    }

    @Override
    public boolean isFloatType(TypeReference typeReference) {
        return typeReference == SwiftTypes.Float;
    }

    @Override
    public boolean isDoubleType(TypeReference typeReference) {
        return false;
    }

    @Override
    public boolean isStringType(TypeReference typeReference) {
        return typeReference == SwiftTypes.String;
    }

    @Override
    public boolean isMetadataType(TypeReference typeReference) {
        return false;
    }

    @Override
    public boolean isCharType(TypeReference typeReference) {
        return false;
    }

    @Override
    public boolean isBooleanType(TypeReference typeReference) {
        return false;
    }

    @Override
    public Object getMetadataToken(Object o) {
        return null;
    }

    @Override
    public TypeReference[] getArrayInterfaces() {
        return new TypeReference[0];
    }

    @Override
    public TypeName lookupPrimitiveType(String s) {
        return null;
    }

    @Override
    public SSAInstructionFactory instructionFactory() {
        return new SwiftInstructionFactory();
    }

    @Override
    public Collection<TypeReference> inferInvokeExceptions(MethodReference methodReference, IClassHierarchy iClassHierarchy) throws InvalidClassFileException {
        return null;
    }

    @Override
    public TypeReference getStringType() {
        return SwiftTypes.String;
    }

    @Override
    public TypeReference getPointerType(TypeReference typeReference) {
        return null;
    }

    @Override
    public PrimitiveType getPrimitive(TypeReference typeReference) {
        return null;
    }

    @Override
    public boolean methodsHaveDeclaredParameterTypes() {
        return false;
    }

    @Override
    public AbstractRootMethod getFakeRootMethod(IClassHierarchy cha, AnalysisOptions options, IAnalysisCacheView cache) {
        return new FakeRootMethod(new FakeRootClass(SwiftTypes.swiftLoader, cha), options, cache);
    }

    @Override
    public <T extends InstanceKey> ModRef.RefVisitor<T, ? extends ExtendedHeapModel> makeRefVisitor(CGNode cgNode, Collection<PointerKey> collection, PointerAnalysis<T> pointerAnalysis, ExtendedHeapModel extendedHeapModel) {
        return null;
    }

    @Override
    public <T extends InstanceKey> ModRef.ModVisitor<T, ? extends ExtendedHeapModel> makeModVisitor(CGNode cgNode, Collection<PointerKey> collection, PointerAnalysis<T> pointerAnalysis, ExtendedHeapModel extendedHeapModel, boolean b) {
        return null;
    }
}
