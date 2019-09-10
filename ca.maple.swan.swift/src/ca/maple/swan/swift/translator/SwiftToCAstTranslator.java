//===--- SwiftToCAstTranslator.java --------------------------------------===//
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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import com.ibm.wala.cast.ir.translator.NativeTranslatorToCAst;
import com.ibm.wala.cast.tree.CAst;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.cast.tree.rewrite.CAstRewriter.CopyKey;
import com.ibm.wala.cast.tree.rewrite.CAstRewriter.RewriteContext;
import com.ibm.wala.cast.tree.rewrite.CAstRewriterFactory;
import com.ibm.wala.classLoader.ModuleEntry;

/*
 * This class translates the Swift code to a single CAstEntity
 * (the "main" ScriptEntity) by calling a JNI method that calls into the
 * C++ translator code.
 */

public class SwiftToCAstTranslator extends NativeTranslatorToCAst {

    static {
        SwiftTranslatorPathLoader.load();
    }

	public SwiftToCAstTranslator(String fileName) throws MalformedURLException {
        this(new CAstImpl(), new File(fileName).toURI().toURL(), fileName);
    }

    public SwiftToCAstTranslator(ModuleEntry m) throws MalformedURLException {
        this(new CAstImpl(), new File(m.getName()).toURI().toURL(), m.getName());
    }

	private SwiftToCAstTranslator(CAst Ast, URL sourceURL, String sourceFileName) {
		super(Ast, sourceURL, sourceFileName);
	}

	@Override
	public <C extends RewriteContext<K>, K extends CopyKey<K>> void addRewriter(CAstRewriterFactory<C, K> factory,
			boolean prepend) {
		assert false;
	}

	public native CAstNode translateToCAstNodes();

	@Override
	public CAstEntity translateToCAst() {
		return new RawAstTranslator().translate(new File(getFile()), translateToCAstNodes());
	}
}
