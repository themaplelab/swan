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

package ca.maple.swan.swift.translator.wala;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import ca.maple.swan.swift.translator.sil.RawData;
import ca.maple.swan.swift.translator.sil.SwiftTranslatorPathLoader;
import ca.maple.swan.swift.translator.silir.context.ProgramContext;
import com.ibm.wala.cast.ir.translator.NativeTranslatorToCAst;
import com.ibm.wala.cast.tree.CAst;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.tree.impl.AbstractSourcePosition;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.cast.tree.rewrite.CAstRewriter.CopyKey;
import com.ibm.wala.cast.tree.rewrite.CAstRewriter.RewriteContext;
import com.ibm.wala.cast.tree.rewrite.CAstRewriterFactory;
import com.ibm.wala.classLoader.ModuleEntry;
import com.ibm.wala.util.debug.Assertions;

/*
 * This class translates the Swift code to a single CAstEntity
 * (the "main" ScriptEntity) by calling a JNI method that calls into the
 * C++ translator code.
 */

public class SwiftToCAstTranslator extends NativeTranslatorToCAst {

	private static final boolean DEBUG = true;

	private URL dynamicSourceURL;
	private String dynamicSourceFileName;

	private static RawData rawData = null;

	public static Set<String> functionNames = null;

	static {
		SwiftTranslatorPathLoader.load();
	}

	// Purely used for calling translateToCAstNodes.
	public SwiftToCAstTranslator() {
		this(new CAstImpl(), null, null);
	}

	// NECESSARY CALL! SETS UP STATIC DATA LATER USED IN translateToCAst().
	public static void setRawData(RawData data) {
		rawData = data;
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

	public native ArrayList<CAstNode> translateToCAstNodes(ArrayList<String> args);

	@Override
	public CAstEntity translateToCAst() {
		Assertions.productionAssertion(rawData != null);
		ProgramContext pc = new WALARawToSILIRTranslator().translate(rawData.getRawData().getChild(1));
		functionNames = pc.getFunctionNames();
		if (DEBUG) {
			pc.pruneIR();
			pc.generateLineNumbers();
			pc.printFunctions();
		}
		return new SILIRToCAstTranslator().translate(new File((String)rawData.getRawData().getChild(0).getValue()), pc);
	}

	// Specifically meant to be used by the C++ translator.
	public void setSource(String filename) {
		try {
			File newFile =
					new File(filename).exists() ?
					new File(filename) :
							(RawData.getPaths().containsKey(filename)) ?
									new File(RawData.getPaths().get(filename)) :
									new File(filename);

			this.dynamicSourceURL = newFile.toURI().toURL();
			this.dynamicSourceFileName = newFile.getName();
		} catch (Exception e) {
			this.dynamicSourceFileName = filename;
			this.dynamicSourceURL = null;
		}
	}

	@Override
	protected CAstSourcePositionMap.Position makeLocation(final int fl, final int fc, final int ll, final int lc) {
		return new AbstractSourcePosition() {

			private URL url = dynamicSourceURL;
			private String filename = dynamicSourceFileName;

			@Override
			public int getFirstLine() {
				return fl;
			}

			@Override
			public int getLastLine() {
				return ll;
			}

			@Override
			public int getFirstCol() {
				return fc;
			}

			@Override
			public int getLastCol() {
				return lc;
			}

			@Override
			public int getFirstOffset() {
				return -1;
			}

			@Override
			public int getLastOffset() {
				return -1;
			}

			@Override
			public URL getURL() {
				return url;
			}

			@Override
			public boolean equals(Object p) {
				if (p instanceof CAstSourcePositionMap.Position) {
					return (this.getFirstLine() == ((CAstSourcePositionMap.Position) p).getFirstLine() &&
							this.getFirstCol() == ((CAstSourcePositionMap.Position) p).getFirstCol() &&
							this.getLastLine() == ((CAstSourcePositionMap.Position) p).getLastLine() &&
							this.getLastCol() == ((CAstSourcePositionMap.Position) p).getLastCol());
				}
				return false;
			}

			public InputStream getInputStream() throws IOException {
				return new FileInputStream(filename);
			}

			@Override
			public String toString() {
				String urlString = url.toString();
				if (urlString.lastIndexOf(File.separator) == -1)
					return "[" + fl + ':' + fc + "]->[" + ll + ':' + lc + ']';
				else
					return urlString.substring(urlString.lastIndexOf(File.separator) + 1)
							+ "@["
							+ fl
							+ ':'
							+ fc
							+ "]->["
							+ ll
							+ ':'
							+ lc
							+ ']';
			}

			@Override
			public Reader getReader() throws IOException {
				return new InputStreamReader(getInputStream());
			}
		};
	}
}
