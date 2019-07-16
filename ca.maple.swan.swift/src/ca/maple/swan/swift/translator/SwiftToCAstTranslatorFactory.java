//===--- SwiftToCAstTranslatorFactory.java -------------------------------===//
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

package ca.maple.swan.swift.translator;

import com.ibm.wala.cast.ir.translator.TranslatorToCAst;
import com.ibm.wala.cast.js.translator.JavaScriptTranslatorFactory;
import com.ibm.wala.cast.tree.CAst;
import com.ibm.wala.classLoader.ModuleEntry;

import java.net.MalformedURLException;

public class SwiftToCAstTranslatorFactory implements JavaScriptTranslatorFactory {

    public TranslatorToCAst make(CAst ast, ModuleEntry M) {
        try {
            return new SwiftToCAstTranslator(M);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
