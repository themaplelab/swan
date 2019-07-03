//===--- SwiftSyntheticClass.java ----------------------------------------===//
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
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.SyntheticClass;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeBT.Constants;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.strings.Atom;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class SwiftSyntheticClass extends SyntheticClass {
    protected final Map<Selector,SwiftSummarizedFunction> functions = HashMapFactory.make();
    protected final Map<Atom,IField> fields = HashMapFactory.make();

    public SwiftSyntheticClass(TypeReference T, IClassHierarchy cha) {
        super(T, cha);
    }

    @Override
    public IClassLoader getClassLoader() {
        return getClassHierarchy().getLoader(SwiftTypes.swiftLoader);
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Override
    public boolean isPrivate() {
        return false;
    }

    @Override
    public int getModifiers() throws UnsupportedOperationException {
        return Constants.ACC_PUBLIC;
    }

    @Override
    public IClass getSuperclass() {
        return getClassHierarchy().lookupClass(SwiftTypes.Object);
    }

    @Override
    public Collection<? extends IClass> getDirectInterfaces() {
        return Collections.emptySet();
    }

    @Override
    public Collection<IClass> getAllImplementedInterfaces() {
        return Collections.emptySet();
    }

    @Override
    public IMethod getMethod(Selector selector) {
        return functions.get(selector);
    }

    @Override
    public IField getField(Atom name) {
        return fields.get(name);
    }

    @Override
    public IMethod getClassInitializer() {
        return null;
    }

    @Override
    public Collection<SwiftSummarizedFunction> getDeclaredMethods() {
        return functions.values();
    }

    @Override
    public Collection<IField> getAllInstanceFields() {
        return fields.values();
    }

    @Override
    public Collection<IField> getAllStaticFields() {
        return Collections.emptySet();
    }

    @Override
    public Collection<IField> getAllFields() {
        return fields.values();
    }

    @Override
    public Collection<SwiftSummarizedFunction> getAllMethods() {
        return functions.values();
    }

    @Override
    public Collection<IField> getDeclaredInstanceFields() {
        return fields.values();
    }

    @Override
    public Collection<IField> getDeclaredStaticFields() {
        return Collections.emptySet();
    }

    @Override
    public boolean isReferenceType() {
        return true;
    }

}