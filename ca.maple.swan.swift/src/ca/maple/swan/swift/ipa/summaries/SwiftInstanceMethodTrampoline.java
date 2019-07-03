//===--- SwiftInstanceMethodTrampoline.java ------------------------------===//
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

package ca.maple.swan.swift.ipa.summaries;

import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.annotations.Annotation;
import com.ibm.wala.util.strings.Atom;

import java.util.Collection;
import java.util.Collections;

public class SwiftInstanceMethodTrampoline extends SwiftSyntheticClass {
    private static final Atom selfName = Atom.findOrCreateUnicodeAtom("self");

    public static final FieldReference self = FieldReference.findOrCreate(SwiftTypes.Root, selfName, SwiftTypes.Object);

    public static TypeReference findOrCreate(TypeReference cls, IClassHierarchy cha) {
        TypeReference t = trampoline(cls);
        if (cha.lookupClass(t) == null) {
            new SwiftInstanceMethodTrampoline(cls, cha);
        }
        return t;
    }

    public static TypeReference trampoline(TypeReference x) {
        return TypeReference.findOrCreate(x.getClassLoader(), "L$" + x.getName().toString().substring(1));
    }

    private final IClass realClass;

    public SwiftInstanceMethodTrampoline(TypeReference functionType, IClassHierarchy cha) {
        super(trampoline(functionType), cha);
        realClass = cha.lookupClass(functionType);
        fields.put(selfName, new IField() {
            @Override
            public IClass getDeclaringClass() {
                return SwiftInstanceMethodTrampoline.this;
            }

            @Override
            public Atom getName() {
                return selfName;
            }

            @Override
            public Collection<Annotation> getAnnotations() {
                return Collections.emptySet();
            }

            @Override
            public IClassHierarchy getClassHierarchy() {
                return SwiftInstanceMethodTrampoline.this.getClassHierarchy();
            }

            @Override
            public TypeReference getFieldTypeReference() {
                return SwiftTypes.Object;
            }

            @Override
            public FieldReference getReference() {
                return self;
            }

            @Override
            public boolean isFinal() {
                return true;
            }

            @Override
            public boolean isPrivate() {
                return true;
            }

            @Override
            public boolean isProtected() {
                return false;
            }

            @Override
            public boolean isPublic() {
                return false;
            }

            @Override
            public boolean isStatic() {
                return false;
            }

            @Override
            public boolean isVolatile() {
                return false;
            }
        });

        cha.addClass(this);
    }

    public String toString() {
        return "Trampoline[" + getReference().getName().toString().substring(1) + "]";
    }

    @Override
    public IClass getSuperclass() {
        return getClassHierarchy().lookupClass(SwiftTypes.Trampoline);
    }

    public IClass getRealClass() {
        return realClass;
    }
}