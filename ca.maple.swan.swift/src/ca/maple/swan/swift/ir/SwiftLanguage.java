package ca.maple.swan.swift.ir;

import ca.maple.swan.swift.ssa.SwiftInvokeInstruction;
import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.analysis.typeInference.PrimitiveType;
import com.ibm.wala.cast.ir.ssa.*;
import com.ibm.wala.cast.java.loader.JavaSourceLoaderImpl;
import com.ibm.wala.cast.types.AstMethodReference;
import com.ibm.wala.classLoader.CallSiteReference;
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
import com.ibm.wala.shrikeCT.BootstrapMethodsReader;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;
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
        return false;
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
        return false;
    }

    @Override
    public boolean isDoubleType(TypeReference typeReference) {
        return false;
    }

    @Override
    public boolean isStringType(TypeReference typeReference) {
        return false;
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
    public AstInstructionFactory instructionFactory() {
        return new JavaSourceLoaderImpl.InstructionFactory() {
            @Override
            public AstEchoInstruction EchoInstruction(int iindex, int[] rvals) {
                return new AstEchoInstruction(iindex, rvals);
            }

            @Override
            public AstPropertyRead PropertyRead(int iindex, int result, int objectRef, int memberRef) {
                return new SwiftPropertyRead(iindex, result, objectRef, memberRef);
            }

            @Override
            public AstPropertyWrite PropertyWrite(int iindex, int objectRef, int memberRef, int value) {
                return new SwiftPropertyWrite(iindex, objectRef, memberRef, value);
            }

            @Override
            public AstGlobalRead GlobalRead(int iindex, int lhs, FieldReference global) {
                return new AstGlobalRead(iindex, lhs, global);
            }

            @Override
            public AstGlobalWrite GlobalWrite(int iindex, FieldReference global, int rhs) {
                return new AstGlobalWrite(iindex, global, rhs);
            }

            @SuppressWarnings("unchecked")
            @Override
            public SSAAbstractInvokeInstruction InvokeInstruction(int iindex, int result, int[] params, int exception,
                                                                  CallSiteReference site, BootstrapMethodsReader.BootstrapMethod bootstrap) {
                if (site.getDeclaredTarget().getName().equals(AstMethodReference.fnAtom) &&
                        site.getDeclaredTarget().getDescriptor().equals(AstMethodReference.fnDesc)) {
                    return new SwiftInvokeInstruction(iindex, result, exception, site, params, new Pair[0]);
                } else {
                    return super.InvokeInstruction(iindex, result, params, exception, site, bootstrap);
                }
            }

            @Override
            public SSAInvokeInstruction InvokeInstruction(int iindex, int[] params, int exception,
                                                          CallSiteReference site, BootstrapMethodsReader.BootstrapMethod bootstrap) {
                // TODO Auto-generated method stub
                return super.InvokeInstruction(iindex, params, exception, site, bootstrap);
            }

            @Override
            public SSAArrayStoreInstruction ArrayStoreInstruction(int iindex, int arrayref, int index, int value,
                                                                  TypeReference declaredType) {
                return new SwiftStoreProperty(iindex, arrayref, index, value);
            }

            @Override
            public EachElementGetInstruction EachElementGetInstruction(int iindex, int value, int objectRef, int prevProp) {
                return new EachElementGetInstruction(iindex, value, objectRef, prevProp);
            }

            @Override
            public AstYieldInstruction YieldInstruction(int iindex, int[] rvals) {
                return new AstYieldInstruction(iindex, rvals);
            }
        };
    }

    @Override
    public Collection<TypeReference> inferInvokeExceptions(MethodReference methodReference, IClassHierarchy iClassHierarchy) throws InvalidClassFileException {
        return null;
    }

    @Override
    public TypeReference getStringType() {
        return null;
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
