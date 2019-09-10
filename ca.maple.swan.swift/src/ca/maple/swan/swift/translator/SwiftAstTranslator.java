//===--- SwiftAstTranslator.java -----------------------------------------===//
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

import com.ibm.wala.cast.js.loader.JavaScriptLoader;
import com.ibm.wala.cast.js.translator.JSAstTranslator;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;

/*
 * Currently, we use this class to overwrite the doMaterializeFunction so that
 * we don't use the "construct" instruction the JSAstTranslator uses.
 * Instead we use the "new" instruction.
 *
 * Most likely this class will be extended as problematic limitations or
 * implications of the JS translator for Swift translation are discovered.
 */

public class SwiftAstTranslator extends JSAstTranslator {
    public SwiftAstTranslator(JavaScriptLoader loader) {
        super(loader);
    }

    @Override
    protected void doMaterializeFunction(
            CAstNode n, WalkContext context, int result, int exception, CAstEntity fn) {
        String fnName = composeEntityName(context, fn);
        IClass cls = loader.lookupClass(TypeName.findOrCreate("L" + fnName));
        TypeReference type = cls.getReference();

        context
                .cfg()
                .addInstruction(
                        insts.NewInstruction(
                                context.cfg().getCurrentInstruction(),
                                result,
                                new NewSiteReference(
                                        context.cfg().getCurrentInstruction(), type)));
    }
}