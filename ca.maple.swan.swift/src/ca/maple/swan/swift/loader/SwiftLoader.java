//===--- SwiftLoader.java ------------------------------------------------===//
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

import ca.maple.swan.swift.translator.SwiftAstTranslator;
import com.ibm.wala.cast.ir.translator.TranslatorToIR;
import com.ibm.wala.cast.js.loader.JavaScriptLoader;
import com.ibm.wala.cast.js.translator.JavaScriptTranslatorFactory;
import com.ibm.wala.cast.tree.rewrite.CAstRewriterFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;

/*
 * For now, we use a custom loader to use our own AstTranslator in the JS code.
 */

public class SwiftLoader extends JavaScriptLoader {
    public SwiftLoader(IClassHierarchy cha, JavaScriptTranslatorFactory translatorFactory) {
        this(cha, translatorFactory, null);
    }

    public SwiftLoader(
            IClassHierarchy cha,
            JavaScriptTranslatorFactory translatorFactory,
            CAstRewriterFactory<?, ?> preprocessor) {
        super(cha,  translatorFactory, preprocessor);
    }

    @Override
    protected TranslatorToIR initTranslator() {
        return new SwiftAstTranslator(this);
    }
}
