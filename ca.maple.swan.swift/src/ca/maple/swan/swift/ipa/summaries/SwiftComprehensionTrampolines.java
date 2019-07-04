package ca.maple.swan.swift.ipa.summaries;

import java.util.Map;

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
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.strings.Atom;

public class SwiftComprehensionTrampolines implements MethodTargetSelector {
    private final MethodTargetSelector base;
    private final Map<IClass, SwiftSummarizedFunction> trampolines = HashMapFactory.make();

    public SwiftComprehensionTrampolines(MethodTargetSelector base) {
        this.base = base;
    }

    @Override
    public IMethod getCalleeTarget(CGNode caller, CallSiteReference site, IClass receiver) {
        MethodReference method = site.getDeclaredTarget();
        if (method.getSelector().equals(AstMethodReference.fnSelector) &&
                caller.getClassHierarchy().isSubclassOf(receiver, caller.getClassHierarchy().lookupClass(SwiftTypes.Comprehension)) &&
                !trampolines.values().contains(caller.getMethod())) {

            if (trampolines.containsKey(receiver)) {
                return trampolines.get(receiver);

            } else {

                MethodReference synth =
                        MethodReference.findOrCreate(
                                method.getDeclaringClass(),
                                new Selector(
                                        Atom.findOrCreateUnicodeAtom("__" + receiver.getName()),
                                        method.getSelector().getDescriptor()));

                SSAAbstractInvokeInstruction inst = caller.getIR().getCalls(site)[0];
                int v = inst.getNumberOfUses() + 3;
                int[] args = new int[ inst.getNumberOfUses()-1 ];
                args[0] = 1;
                int nullVal = v++;

                SwiftSummary x = new SwiftSummary(synth, inst.getNumberOfUses());
                int idx = 0;

                x.addConstant(nullVal, null);

                int ofv = -1;
                for(int lst = 3; lst <= inst.getNumberOfUses(); lst++) {
                    int fv = v++;
                    ofv = fv;
                    int lv = v++;
                    x.addStatement(SwiftLanguage.Swift.instructionFactory().EachElementGetInstruction(idx++, fv, lst, nullVal));
                    x.addStatement(SwiftLanguage.Swift.instructionFactory().PropertyRead(idx++, lv, lst, fv));
                    args[lst-2] = lv;
                }

                int s = idx++;
                int r = v++;
                CallSiteReference ss = new DynamicCallSiteReference(SwiftTypes.CodeBody, s);
                x.addStatement(new SwiftInvokeInstruction(s, r, v++, ss, args, new Pair[0]));

                x.addStatement(SwiftLanguage.Swift.instructionFactory().PropertyWrite(idx++, 2, ofv, r));

                x.addStatement(SwiftLanguage.Swift.instructionFactory().ReturnInstruction(idx++, 2, false));

                SwiftSummarizedFunction code = new SwiftSummarizedFunction(synth, x, receiver);

                trampolines.put(receiver, code);

                return code;
            }
        }

        return base.getCalleeTarget(caller, site, receiver);
    }

}