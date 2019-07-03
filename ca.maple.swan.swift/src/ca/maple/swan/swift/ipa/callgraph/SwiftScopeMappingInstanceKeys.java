//===--- SwiftScopeMappingInstanceKeys.java ------------------------------===//
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

import ca.maple.swan.swift.ipa.summaries.SwiftInstanceMethodTrampoline;
import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.ipa.callgraph.ScopeMappingInstanceKeys;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.loader.CAstAbstractModuleLoader;
import com.ibm.wala.cast.types.AstMethodReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKeyFactory;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.util.collections.Pair;

import java.util.Collection;
import java.util.Collections;

public class SwiftScopeMappingInstanceKeys extends ScopeMappingInstanceKeys {

    private final IClassHierarchy cha;
    private final IClass codeBody;

    public SwiftScopeMappingInstanceKeys(PropagationCallGraphBuilder builder, InstanceKeyFactory basic) {
        super(builder, basic);
        this.cha = builder.getClassHierarchy();
        this.codeBody = cha.lookupClass(SwiftTypes.CodeBody);
    }

    protected AstMethod.LexicalParent[] getParents(InstanceKey base) {
        IClass cls = base.getConcreteType();

        if (cls instanceof SwiftInstanceMethodTrampoline) {
            cls = ((SwiftInstanceMethodTrampoline)cls).getRealClass();
        }

        CAstAbstractModuleLoader.DynamicMethodObject function = (CAstAbstractModuleLoader.DynamicMethodObject)
                cls.getMethod(AstMethodReference.fnSelector);

        return function==null? new AstMethod.LexicalParent[0]: function.getParents();
    }

    @Override
    protected boolean needsScopeMappingKey(InstanceKey base) {
        return
                cha.isSubclassOf(base.getConcreteType(), codeBody)
                        &&
                        getParents(base).length > 0;
    }

    @Override
    protected Collection<CGNode> getConstructorCallers(ScopeMappingInstanceKey smik, Pair<String, String> name) {
        return Collections.singleton(smik.getCreator());
    }

}