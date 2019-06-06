/******************************************************************************
 * Copyright (c) 2017 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 		IBM Corporation - initial API and implementation
 *****************************************************************************/

package ca.maple.swan.swift.translator;

import ca.maple.swan.swift.tree.CAstEntityInfo;
import ca.maple.swan.swift.tree.ScriptEntityBuilder;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import com.ibm.wala.cast.ir.translator.NativeTranslatorToCAst;
import com.ibm.wala.cast.tree.CAst;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.cast.tree.rewrite.CAstRewriter.CopyKey;
import com.ibm.wala.cast.tree.rewrite.CAstRewriter.RewriteContext;
import com.ibm.wala.cast.tree.rewrite.CAstRewriterFactory;
import com.ibm.wala.classLoader.ModuleEntry;

import java.util.ArrayList;

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

	public native ArrayList<CAstEntityInfo> translateToCAstNodes();

	@Override
	public CAstEntity translateToCAst() {
		return ScriptEntityBuilder.buildScriptEntity(new File(getFile()), translateToCAstNodes());
	}
}
