package ca.maple.swan.swift.ipa.callgraph;

import java.util.Collections;
import java.util.Map;

import ca.maple.swan.swift.ipa.summaries.SwiftInstanceMethodTrampoline;
import ca.maple.swan.swift.ipa.summaries.SwiftSummarizedFunction;
import ca.maple.swan.swift.ipa.summaries.SwiftSummary;
import ca.maple.swan.swift.ir.SwiftLanguage;
import ca.maple.swan.swift.loader.SwiftLoader;
import ca.maple.swan.swift.ssa.SwiftInvokeInstruction;
import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.loader.DynamicCallSiteReference;
import com.ibm.wala.cast.types.AstMethodReference;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.MethodTargetSelector;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.strings.Atom;

public class SwiftConstructorTargetSelector implements MethodTargetSelector {
    private final Map<IClass,IMethod> ctors = HashMapFactory.make();

    private final MethodTargetSelector base;

    public SwiftConstructorTargetSelector(MethodTargetSelector base) {
        this.base = base;
    }

    @Override
    public IMethod getCalleeTarget(CGNode caller, CallSiteReference site, IClass receiver) {
        if (receiver != null) {
            IClassHierarchy cha = receiver.getClassHierarchy();
            if (cha.isSubclassOf(receiver, cha.lookupClass(SwiftTypes.Object)) && receiver instanceof SwiftLoader.SwiftClass) {
                if (!ctors.containsKey(receiver)) {
                    TypeReference ctorRef = TypeReference.findOrCreate(receiver.getClassLoader().getReference(), receiver.getName() + "/__init__");
                    IClass ctorCls = cha.lookupClass(ctorRef);
                    IMethod init = ctorCls==null? null: ctorCls.getMethod(AstMethodReference.fnSelector);
                    int params = init==null? 1: init.getNumberOfParameters();
                    int v = params+2;
                    int pc = 0;
                    int inst = v++;
                    MethodReference ref = MethodReference.findOrCreate(receiver.getReference(), site.getDeclaredTarget().getSelector());
                    SwiftSummary ctor = new SwiftSummary(ref, params);
                    SSAInstructionFactory insts = SwiftLanguage.Swift.instructionFactory();
                    ctor.addStatement(insts.NewInstruction(pc, inst, NewSiteReference.make(pc, SwiftTypes.Object)));
                    pc++;

                    SwiftLoader.SwiftClass x = (SwiftLoader.SwiftClass)receiver;
                    for(TypeReference r : x.getInnerReferences()) {
                        int orig_t = v++;
                        String typeName = r.getName().toString();
                        typeName = typeName.substring(typeName.lastIndexOf('/')+1);
                        FieldReference inner = FieldReference.findOrCreate(SwiftTypes.Root, Atom.findOrCreateUnicodeAtom(typeName), SwiftTypes.Root);

                        ctor.addStatement(insts.GetInstruction(pc, orig_t, 1, inner));
                        pc++;

                        ctor.addStatement(insts.PutInstruction(pc, inst, orig_t, inner));
                        pc++;
                    }

                    for(MethodReference r : x.getMethodReferences()) {
                        int f = v++;
                        ctor.addStatement(insts.NewInstruction(pc, f, NewSiteReference.make(pc, SwiftInstanceMethodTrampoline.findOrCreate(r.getDeclaringClass(), receiver.getClassHierarchy()))));
                        pc++;

                        ctor.addStatement(insts.PutInstruction(pc, f, inst, FieldReference.findOrCreate(SwiftTypes.Root, Atom.findOrCreateUnicodeAtom("$self"), SwiftTypes.Root)));
                        pc++;

                        int orig_f = v++;
                        ctor.addStatement(insts.GetInstruction(pc, orig_f, 1, FieldReference.findOrCreate(SwiftTypes.Root, r.getName(), SwiftTypes.Root)));
                        pc++;

                        ctor.addStatement(insts.PutInstruction(pc, f, orig_f, FieldReference.findOrCreate(SwiftTypes.Root, Atom.findOrCreateUnicodeAtom("$function"), SwiftTypes.Root)));
                        pc++;

                        ctor.addStatement(insts.PutInstruction(pc, inst, f, FieldReference.findOrCreate(SwiftTypes.Root, r.getName(), SwiftTypes.Root)));
                        pc++;
                    }

                    if (init != null) {
                        int fv = v++;
                        ctor.addStatement(insts.GetInstruction(pc, fv, 1, FieldReference.findOrCreate(SwiftTypes.Root, Atom.findOrCreateUnicodeAtom("__init__"), SwiftTypes.Root)));
                        pc++;

                        int[] cps = new int[ init.getNumberOfParameters() ];
                        cps[0] = fv;
                        cps[1] = inst;
                        for(int j = 2; j < init.getNumberOfParameters(); j++) {
                            cps[j]= j;
                        }

                        int result = v++;
                        int except = v++;
                        pc++;
                        CallSiteReference cref = new DynamicCallSiteReference(site.getDeclaredTarget(), pc);
                        ctor.addStatement(new SwiftInvokeInstruction(2, result, except, cref, cps, new Pair[0]));
                    }

                    ctor.addStatement(insts.ReturnInstruction(pc++, inst, false));

                    ctor.setValueNames(Collections.singletonMap(1, Atom.findOrCreateUnicodeAtom("self")));

                    ctors.put(receiver, new SwiftSummarizedFunction(ref, ctor, receiver));
                }

                return ctors.get(receiver);
            }
        }
        return base.getCalleeTarget(caller, site, receiver);
    }

}