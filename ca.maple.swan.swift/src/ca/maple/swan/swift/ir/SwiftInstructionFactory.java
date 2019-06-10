package ca.maple.swan.swift.ir;

import com.ibm.wala.cast.ir.ssa.*;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction;
import com.ibm.wala.shrikeBT.IComparisonInstruction;
import com.ibm.wala.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.shrikeBT.IUnaryOpInstruction;
import com.ibm.wala.shrikeCT.BootstrapMethodsReader;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeReference;

public class SwiftInstructionFactory implements AstInstructionFactory {

    @Override
    public AssignInstruction AssignInstruction(int i, int i1, int i2) {
        return null;
    }

    @Override
    public AstAssertInstruction AssertInstruction(int i, int i1, boolean b) {
        return null;
    }

    @Override
    public AstEchoInstruction EchoInstruction(int i, int[] ints) {
        return null;
    }

    @Override
    public AstGlobalRead GlobalRead(int i, int i1, FieldReference fieldReference) {
        return null;
    }

    @Override
    public AstGlobalWrite GlobalWrite(int i, FieldReference fieldReference, int i1) {
        return null;
    }

    @Override
    public AstIsDefinedInstruction IsDefinedInstruction(int i, int i1, int i2, int i3, FieldReference fieldReference) {
        return null;
    }

    @Override
    public AstIsDefinedInstruction IsDefinedInstruction(int i, int i1, int i2, FieldReference fieldReference) {
        return null;
    }

    @Override
    public AstIsDefinedInstruction IsDefinedInstruction(int i, int i1, int i2, int i3) {
        return null;
    }

    @Override
    public AstIsDefinedInstruction IsDefinedInstruction(int i, int i1, int i2) {
        return null;
    }

    @Override
    public AstLexicalRead LexicalRead(int i, AstLexicalAccess.Access[] accesses) {
        return null;
    }

    @Override
    public AstLexicalRead LexicalRead(int i, AstLexicalAccess.Access access) {
        return null;
    }

    @Override
    public AstLexicalRead LexicalRead(int i, int i1, String s, String s1, TypeReference typeReference) {
        return null;
    }

    @Override
    public AstLexicalWrite LexicalWrite(int i, AstLexicalAccess.Access[] accesses) {
        return null;
    }

    @Override
    public AstLexicalWrite LexicalWrite(int i, AstLexicalAccess.Access access) {
        return null;
    }

    @Override
    public AstLexicalWrite LexicalWrite(int i, String s, String s1, TypeReference typeReference, int i1) {
        return null;
    }

    @Override
    public EachElementGetInstruction EachElementGetInstruction(int i, int i1, int i2, int i3) {
        return null;
    }

    @Override
    public EachElementHasNextInstruction EachElementHasNextInstruction(int i, int i1, int i2, int i3) {
        return null;
    }

    @Override
    public AstPropertyRead PropertyRead(int i, int i1, int i2, int i3) {
        return null;
    }

    @Override
    public AstPropertyWrite PropertyWrite(int i, int i1, int i2, int i3) {
        return null;
    }

    @Override
    public AstYieldInstruction YieldInstruction(int i, int[] ints) {
        return null;
    }

    @Override
    public SSAAddressOfInstruction AddressOfInstruction(int i, int i1, int i2, TypeReference typeReference) {
        return null;
    }

    @Override
    public SSAAddressOfInstruction AddressOfInstruction(int i, int i1, int i2, int i3, TypeReference typeReference) {
        return null;
    }

    @Override
    public SSAAddressOfInstruction AddressOfInstruction(int i, int i1, int i2, FieldReference fieldReference, TypeReference typeReference) {
        return null;
    }

    @Override
    public SSAArrayLengthInstruction ArrayLengthInstruction(int i, int i1, int i2) {
        return null;
    }

    @Override
    public SSAArrayLoadInstruction ArrayLoadInstruction(int i, int i1, int i2, int i3, TypeReference typeReference) {
        return null;
    }

    @Override
    public SSAArrayStoreInstruction ArrayStoreInstruction(int i, int i1, int i2, int i3, TypeReference typeReference) {
        return null;
    }

    @Override
    public SSAAbstractBinaryInstruction BinaryOpInstruction(int i, IBinaryOpInstruction.IOperator iOperator, boolean b, boolean b1, int i1, int i2, int i3, boolean b2) {
        return null;
    }

    @Override
    public SSACheckCastInstruction CheckCastInstruction(int i, int i1, int i2, int[] ints, boolean b) {
        return null;
    }

    @Override
    public SSACheckCastInstruction CheckCastInstruction(int i, int i1, int i2, int i3, boolean b) {
        return null;
    }

    @Override
    public SSACheckCastInstruction CheckCastInstruction(int i, int i1, int i2, TypeReference[] typeReferences, boolean b) {
        return null;
    }

    @Override
    public SSACheckCastInstruction CheckCastInstruction(int i, int i1, int i2, TypeReference typeReference, boolean b) {
        return null;
    }

    @Override
    public SSAComparisonInstruction ComparisonInstruction(int i, IComparisonInstruction.Operator operator, int i1, int i2, int i3) {
        return null;
    }

    @Override
    public SSAConditionalBranchInstruction ConditionalBranchInstruction(int i, IConditionalBranchInstruction.IOperator iOperator, TypeReference typeReference, int i1, int i2, int i3) {
        return null;
    }

    @Override
    public SSAConversionInstruction ConversionInstruction(int i, int i1, int i2, TypeReference typeReference, TypeReference typeReference1, boolean b) {
        return null;
    }

    @Override
    public SSAGetCaughtExceptionInstruction GetCaughtExceptionInstruction(int i, int i1, int i2) {
        return null;
    }

    @Override
    public SSAGetInstruction GetInstruction(int i, int i1, FieldReference fieldReference) {
        return null;
    }

    @Override
    public SSAGetInstruction GetInstruction(int i, int i1, int i2, FieldReference fieldReference) {
        return null;
    }

    @Override
    public SSAGotoInstruction GotoInstruction(int i, int i1) {
        return null;
    }

    @Override
    public SSAInstanceofInstruction InstanceofInstruction(int i, int i1, int i2, TypeReference typeReference) {
        return null;
    }

    @Override
    public SSAAbstractInvokeInstruction InvokeInstruction(int i, int i1, int[] ints, int i2, CallSiteReference callSiteReference, BootstrapMethodsReader.BootstrapMethod bootstrapMethod) {
        return null;
    }

    @Override
    public SSAInvokeInstruction InvokeInstruction(int i, int[] ints, int i1, CallSiteReference callSiteReference, BootstrapMethodsReader.BootstrapMethod bootstrapMethod) {
        return null;
    }

    @Override
    public SSALoadIndirectInstruction LoadIndirectInstruction(int i, int i1, TypeReference typeReference, int i2) {
        return null;
    }

    @Override
    public SSALoadMetadataInstruction LoadMetadataInstruction(int i, int i1, TypeReference typeReference, Object o) {
        return null;
    }

    @Override
    public SSAMonitorInstruction MonitorInstruction(int i, int i1, boolean b) {
        return null;
    }

    @Override
    public SSANewInstruction NewInstruction(int i, int i1, NewSiteReference newSiteReference) {
        return null;
    }

    @Override
    public SSANewInstruction NewInstruction(int i, int i1, NewSiteReference newSiteReference, int[] ints) {
        return null;
    }

    @Override
    public SSAPhiInstruction PhiInstruction(int i, int i1, int[] ints) {
        return null;
    }

    @Override
    public SSAPiInstruction PiInstruction(int i, int i1, int i2, int i3, int i4, SSAInstruction ssaInstruction) {
        return null;
    }

    @Override
    public SSAPutInstruction PutInstruction(int i, int i1, int i2, FieldReference fieldReference) {
        return null;
    }

    @Override
    public SSAPutInstruction PutInstruction(int i, int i1, FieldReference fieldReference) {
        return null;
    }

    @Override
    public SSAReturnInstruction ReturnInstruction(int i) {
        return null;
    }

    @Override
    public SSAReturnInstruction ReturnInstruction(int i, int i1, boolean b) {
        return null;
    }

    @Override
    public SSAStoreIndirectInstruction StoreIndirectInstruction(int i, int i1, int i2, TypeReference typeReference) {
        return null;
    }

    @Override
    public SSASwitchInstruction SwitchInstruction(int i, int i1, int i2, int[] ints) {
        return null;
    }

    @Override
    public SSAThrowInstruction ThrowInstruction(int i, int i1) {
        return null;
    }

    @Override
    public SSAUnaryOpInstruction UnaryOpInstruction(int i, IUnaryOpInstruction.IOperator iOperator, int i1, int i2) {
        return null;
    }
}
