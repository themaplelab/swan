//===--- SwiftTypes.java -------------------------------------------------===//
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

package ca.maple.swan.swift.types;

import com.ibm.wala.cast.tree.CAstType;
import com.ibm.wala.cast.types.AstTypeReference;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.Atom;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SwiftTypes extends AstTypeReference {

    public static final String swiftNameStr = "Swift";

    public static final String swiftLoaderStr = "SwiftLoader";

    public static final Atom swiftName = Atom.findOrCreateUnicodeAtom(swiftNameStr);

    public static final Atom swiftLoaderName = Atom.findOrCreateUnicodeAtom(swiftLoaderStr);

    public static final ClassLoaderReference swiftLoader = new ClassLoaderReference(swiftLoaderName, swiftName, null);

    public static final TypeReference Root = TypeReference.findOrCreate(swiftLoader, rootTypeName);

    public static final TypeReference Script = TypeReference.findOrCreate(swiftLoader, "LScript");

    public static final TypeReference CodeBody = TypeReference.findOrCreate(swiftLoader, functionTypeName);

    public static final TypeReference Object = TypeReference.findOrCreate(swiftLoader, "LObject");

    public static final TypeReference String = TypeReference.findOrCreate(swiftLoader, "LString");

    public static final TypeReference Int = TypeReference.findOrCreate(swiftLoader, "LInt");

    public static final TypeReference Trampoline = TypeReference.findOrCreate(swiftLoader, "LTrampoline");

    public static final TypeReference Comprehension = TypeReference.findOrCreate(swiftLoader, "LComprehension");

    private static Map<String, CAstType> CAstTypes = new HashMap<>();

    public static CAstType findOrCreateCAstType(String typeName) {
        if (CAstTypes.containsKey(typeName)) {
            return CAstTypes.get(typeName);
        } else {
            CAstType newType = new CAstType() {

                @Override
                public java.lang.String getName() {
                    return typeName;
                }

                @Override
                public Collection<CAstType> getSupertypes() {
                    return null;
                }
            };
            CAstTypes.put(typeName, newType);
            return newType;
        }
    }
}
