package ca.maple.swan.swift.ipa.callgraph;

import java.util.Map;

import ca.maple.swan.swift.ipa.summaries.SwiftSummarizedFunction;
import ca.maple.swan.swift.ipa.summaries.SwiftSummary;
import ca.maple.swan.swift.ir.SwiftLanguage;
import ca.maple.swan.swift.ssa.SwiftInvokeInstruction;
import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.loader.DynamicCallSiteReference;
import com.ibm.wala.cast.types.AstMethodReference;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.MethodTargetSelector;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.strings.Atom;

public class SwiftTrampolineTargetSelector implements MethodTargetSelector {
    private final MethodTargetSelector base;

    public SwiftTrampolineTargetSelector(MethodTargetSelector base) {
        this.base = base;
    }

    private final Map<Pair<IClass,Integer>, IMethod> codeBodies = HashMapFactory.make();

    @SuppressWarnings("unchecked")
    @Override
    public IMethod getCalleeTarget(CGNode caller, CallSiteReference site, IClass receiver) {
        if (receiver != null) {
            IClassHierarchy cha = receiver.getClassHierarchy();
            if (cha.isSubclassOf(receiver, cha.lookupClass(SwiftTypes.Trampoline))) {
                SwiftInvokeInstruction call = (SwiftInvokeInstruction) caller.getIR().getCalls(site)[0];
                Pair<IClass,Integer> key = Pair.make(receiver,  call.getNumberOfTotalParameters());
                if (!codeBodies.containsKey(key)) {
                    Map<Integer,Atom> names = HashMapFactory.make();
                    MethodReference tr = MethodReference.findOrCreate(receiver.getReference(),
                            Atom.findOrCreateUnicodeAtom("trampoline" + call.getNumberOfTotalParameters()),
                            AstMethodReference.fnDesc);
                    SwiftSummary x = new SwiftSummary(tr, call.getNumberOfTotalParameters());
                    int v = call.getNumberOfTotalParameters() + 1;
                    x.addStatement(SwiftLanguage.Swift.instructionFactory().GetInstruction(0, v, 1, FieldReference.findOrCreate(SwiftTypes.Root, Atom.findOrCreateUnicodeAtom("$function"), SwiftTypes.Root)));
                    int v1 = v + 1;
                    x.addStatement(SwiftLanguage.Swift.instructionFactory().GetInstruction(1, v1, 1, FieldReference.findOrCreate(SwiftTypes.Root, Atom.findOrCreateUnicodeAtom("$self"), SwiftTypes.Root)));

                    int i = 0;
                    int[] params = new int[ call.getNumberOfPositionalParameters()+1 ];
                    params[i++] = v;
                    params[i++] = v1;
                    for(int j = 1; j < call.getNumberOfPositionalParameters(); j++) {
                        params[i++] = j+1;
                    }

                    int ki = 0, ji = call.getNumberOfPositionalParameters()+1;
                    Pair<String,Integer>[] keys = new Pair[0];
                    if (call.getKeywords() != null) {
                        keys = new Pair[call.getKeywords().size()];
                        for(String k : call.getKeywords()) {
                            names.put(ji, Atom.findOrCreateUnicodeAtom(k));
                            keys[ki++] = Pair.make(k, ji++);
                        }
                    }

                    int result = v1 + 1;
                    int except = v1 + 2;
                    CallSiteReference ref = new DynamicCallSiteReference(call.getCallSite().getDeclaredTarget(), 2);
                    x.addStatement(new SwiftInvokeInstruction(2, result, except, ref, params, keys));

                    x.addStatement(new SSAReturnInstruction(3, result, false));

                    x.setValueNames(names);

                    codeBodies.put(key, new SwiftSummarizedFunction(tr, x, receiver));
                }

                return codeBodies.get(key);
            }
        }

        return base.getCalleeTarget(caller, site, receiver);
    }

}