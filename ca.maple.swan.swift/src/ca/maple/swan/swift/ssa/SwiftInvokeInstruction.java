package ca.maple.swan.swift.ssa;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;

public class SwiftInvokeInstruction extends SSAAbstractInvokeInstruction {
    private final int result;
    private final int[] positionalParams;
    private final Pair<String,Integer>[] keywordParams;

    public SwiftInvokeInstruction(int iindex, int result, int exception, CallSiteReference site, int[] positionalParams, Pair<String,Integer>[] keywordParams) {
        super(iindex, exception, site);
        this.positionalParams = positionalParams;
        this.keywordParams = keywordParams;
        this.result = result;
    }

    @Override
    public int getNumberOfPositionalParameters() {
        return positionalParams.length;
    }

    public int getNumberOfKeywordParameters() {
        return keywordParams.length;
    }

    public int getNumberOfTotalParameters() {
        return positionalParams.length + keywordParams.length;
    }

    @Override
    public int getNumberOfUses() {
        return positionalParams.length + keywordParams.length;
    }

    public List<String> getKeywords() {
        List<String> names = new LinkedList<String>();
        for(Pair<String,?> a : keywordParams) {
            names.add(a.fst);
        }
        return names;
    }

    public int getUse(String keyword) {
        for(int i = 0; i < keywordParams.length; i++) {
            if (keywordParams[i].fst.equals(keyword)) {
                return keywordParams[i].snd;
            }
        }

        return -1;
    }

    @Override
    public int getUse(int j) throws UnsupportedOperationException {
        if (j < positionalParams.length) {
            return positionalParams[j];
        } else {
            assert j < getNumberOfTotalParameters();
            return keywordParams[j - positionalParams.length].snd;
        }
    }

    @Override
    public int getNumberOfReturnValues() {
        return 1;
    }

    @Override
    public int getReturnValue(int i) {
        assert i == 0;
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public SSAInstruction copyForSSA(SSAInstructionFactory insts, int[] defs, int[] uses) {
        int nr = defs==null || defs.length == 0? result: defs[0];
        int ne = defs==null || defs.length == 0? exception: defs[1];

        int[] newpos = positionalParams;
        Pair<String,Integer>[] newkey = keywordParams;
        if (uses != null && uses.length > 0) {
            int j = 0;
            newpos = new int[ positionalParams.length ];
            for(int i = 0; i < positionalParams.length; i++, j++) {
                newpos[i] = uses[j];
            }
            newkey = new Pair[ keywordParams.length ];
            for(int i = 0; i < keywordParams.length; i++, j++) {
                newkey[i] = Pair.make(keywordParams[i].fst, uses[j]);
            }
        }

        return new SwiftInvokeInstruction(iindex, nr, ne, site, newpos, newkey);
    }

    @Override
    public void visit(IVisitor v) {
        ((SwiftInstructionVisitor)v).visitSwiftInvoke(this);
    }

    @Override
    public int hashCode() {
        return getCallSite().hashCode() * result;
    }

    @Override
    public Collection<TypeReference> getExceptionTypes() {
        return Collections.emptySet();
    }

    @Override
    public String toString(SymbolTable symbolTable) {
        String s = "";
        if (keywordParams != null) {
            for(Pair<String,Integer> kp : keywordParams) {
                s = s + " " + kp.fst + ":" + kp.snd;
            }
        }
        return super.toString(symbolTable) + s;
    }

}