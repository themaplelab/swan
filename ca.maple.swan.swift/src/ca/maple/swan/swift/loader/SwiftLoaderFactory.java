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

import com.ibm.wala.cast.js.loader.JavaScriptLoaderFactory;
import com.ibm.wala.cast.js.translator.JavaScriptTranslatorFactory;
import com.ibm.wala.cast.tree.rewrite.CAstRewriterFactory;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.ipa.cha.IClassHierarchy;

public class SwiftLoaderFactory extends JavaScriptLoaderFactory {

    public SwiftLoaderFactory(JavaScriptTranslatorFactory factory) {
        this(factory, null);
    }

    public SwiftLoaderFactory(
            JavaScriptTranslatorFactory factory, CAstRewriterFactory<?, ?> preprocessor) {
        super(factory, preprocessor);
    }

    @Override
    protected IClassLoader makeTheLoader(IClassHierarchy cha) {
        return new SwiftLoader(cha, translatorFactory, preprocessor);
    }

}
