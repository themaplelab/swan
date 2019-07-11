//===--- SwiftLoaderFactory.java -----------------------------------------===//
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

package ca.maple.swan.swift.loader;

import ca.maple.swan.swift.translator.SwiftToCAstTranslatorFactory;
import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.loader.SingleClassLoaderFactory;
import com.ibm.wala.cast.tree.rewrite.CAstRewriterFactory;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;


public class SwiftLoaderFactory extends SingleClassLoaderFactory {
    protected final SwiftToCAstTranslatorFactory translatorFactory;
    protected final CAstRewriterFactory<?, ?> preprocessor;

    public SwiftLoaderFactory(SwiftToCAstTranslatorFactory factory) {
        this(factory, null);
    }

    public SwiftLoaderFactory(
            SwiftToCAstTranslatorFactory factory, CAstRewriterFactory<?, ?> preprocessor) {
        this.translatorFactory = factory;
        this.preprocessor = preprocessor;
    }

    @Override
    protected IClassLoader makeTheLoader(IClassHierarchy cha) {
        return new SwiftLoader(cha, translatorFactory, preprocessor);
    }

    @Override
    public ClassLoaderReference getTheReference() {
        return SwiftTypes.swiftLoader;
    }
}