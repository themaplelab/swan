//===--- SwiftSSAPropagationCallGraphBuilder.java ------------------------===//
//
// This source file is part of the SWAN open source project
//
// Copyright (c) 2019 Maple @ University of Alberta
// All rights reserved. This program and the accompanying materials (unless
// otherwise specified by a license inside of the accompanying material)
// are made available under the terms of the Eclipse Public License v2.0
// which accompanies this distribution, and is available at
// http://www.eclipse.org/legal/epl-v20.html
//
//===---------------------------------------------------------------------===//

package ca.maple.swan.swift.ipa.callgraph;

import ca.maple.swan.swift.ipa.summaries.BuiltinFunctions;
import ca.maple.swan.swift.ir.SwiftLanguage;
import ca.maple.swan.swift.ir.SwiftStoreProperty;
import ca.maple.swan.swift.ssa.SwiftInstructionVisitor;
import ca.maple.swan.swift.ssa.SwiftInvokeInstruction;
import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.ipa.callgraph.AstSSAPropagationCallGraphBuilder;
import com.ibm.wala.cast.ipa.callgraph.GlobalObjectKey;
import com.ibm.wala.fixpoint.AbstractOperator;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.propagation.*;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.strings.Atom;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

public class SwiftSSAPropagationCallGraphBuilder extends AstSSAPropagationCallGraphBuilder {

    @Override
    protected boolean useObjectCatalog() {
        return true;
    }

    @Override
    public GlobalObjectKey getGlobalObject(Atom language) {
        assert language.equals(SwiftLanguage.Swift.getName());
        return new GlobalObjectKey(cha.lookupClass(SwiftTypes.Root));
    }

    @Override
    protected AbstractFieldPointerKey fieldKeyForUnknownWrites(AbstractFieldPointerKey abstractFieldPointerKey) {
        return null;
    }

    @Override
    protected boolean sameMethod(CGNode opNode, String definingMethod) {
        return definingMethod.equals(opNode.getMethod().getReference().getDeclaringClass().getName().toString());
    }


    public SwiftSSAPropagationCallGraphBuilder(IClassHierarchy cha, AnalysisOptions options, IAnalysisCacheView cache,
                                               PointerKeyFactory pointerKeyFactory) {
        super(SwiftLanguage.Swift.getFakeRootMethod(cha, options, cache), options, cache, pointerKeyFactory);
    }

    private static final Collection<TypeReference> types = Arrays.asList(SwiftTypes.String, TypeReference.Int);

    public static class SwiftConstraintVisitor extends AstConstraintVisitor implements SwiftInstructionVisitor {

        public SwiftConstraintVisitor(AstSSAPropagationCallGraphBuilder builder, CGNode node) {
            super(builder, node);
        }

        private final Map<Pair<String, TypeReference>, BuiltinFunctions.BuiltinFunction> primitives = HashMapFactory.make();

        private BuiltinFunctions.BuiltinFunction ensure(Pair<String,TypeReference> key) {
            if (! primitives.containsKey(key)) {
                primitives.put(key, new BuiltinFunctions.BuiltinFunction(this.getClassHierarchy(), key.fst, key.snd));
            }

            return primitives.get(key);
        }

        @Override
        public void visitGet(SSAGetInstruction instruction) {
            SymbolTable symtab = ir.getSymbolTable();
            String name = instruction.getDeclaredField().getName().toString();

            int objVn = instruction.getRef();
            final PointerKey objKey = getPointerKeyForLocal(objVn);

            int lvalVn = instruction.getDef();
            final PointerKey lvalKey = getPointerKeyForLocal(lvalVn);

            if (contentsAreInvariant(symtab, du, objVn)) {
                system.recordImplicitPointsToSet(objKey);
                for (InstanceKey ik : getInvariantContents(objVn)) {
                    if (types.contains(ik.getConcreteType().getReference())) {
                        Pair<String,TypeReference> key = Pair.make(name, ik.getConcreteType().getReference());
                        system.newConstraint(lvalKey, new ConcreteTypeKey(ensure(key)));
                    }
                }
            } else {
                system.newSideEffect(new AbstractOperator<PointsToSetVariable>() {
                    @Override
                    public byte evaluate(PointsToSetVariable lhs, PointsToSetVariable[] rhs) {
                        if (rhs[0].getValue() != null)
                            rhs[0].getValue().foreach((i) -> {
                                InstanceKey ik = system.getInstanceKey(i);
                                if (types.contains(ik.getConcreteType().getReference())) {
                                    Pair<String,TypeReference> key = Pair.make(name, ik.getConcreteType().getReference());
                                    system.newConstraint(lvalKey, new ConcreteTypeKey(ensure(key)));
                                }
                            });
                        return NOT_CHANGED;
                    }

                    @Override
                    public int hashCode() {
                        return node.hashCode()*instruction.hashCode();
                    }

                    @Override
                    public boolean equals(Object o) {
                        return getClass().equals(o.getClass()) && hashCode() == o.hashCode();
                    }

                    @Override
                    public String toString() {
                        return "get function " + name + " at " + instruction;
                    }
                }, new PointerKey[] { lvalKey });
            }

            // TODO Auto-generated method stub
            super.visitGet(instruction);
        }


        @Override
        public void visitSwiftInvoke(SwiftInvokeInstruction inst) {
            visitInvokeInternal(inst, new DefaultInvariantComputer());
        }

        @Override
        public void visitSwiftStoreProperty(SwiftStoreProperty inst) {
            newFieldWrite(node, inst.getArrayRef(), inst.getIndex(), inst.getValue());
        }

        @Override
        public void visitArrayLoad(SSAArrayLoadInstruction inst) {
            newFieldRead(node, inst.getArrayRef(), inst.getIndex(), inst.getDef());
        }

        @Override
        public void visitArrayStore(SSAArrayStoreInstruction inst) {
            newFieldWrite(node, inst.getArrayRef(), inst.getIndex(), inst.getValue());
        }
    }

    @Override
    public SwiftConstraintVisitor makeVisitor(CGNode node) {
        return new SwiftConstraintVisitor(this, node);
    }
}
