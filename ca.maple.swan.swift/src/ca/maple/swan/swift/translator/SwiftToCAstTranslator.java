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

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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
import org.apache.commons.io.FilenameUtils;

/*
 * This class translates the Swift code to a single CAstEntity
 * (the "main" ScriptEntity) by calling a JNI method that calls into the
 * C++ translator code.
 */

public class SwiftToCAstTranslator extends NativeTranslatorToCAst {

	private URL dynamicSourceURL;
	private String dynamicSourceFileName;

	private static Map<String, CAstNode> translatedModules = new HashMap<>();

    static {
        SwiftTranslatorPathLoader.load();
    }

    public SwiftToCAstTranslator() {
    	this(new CAstImpl(), null, null);
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
		assert(!translatedModules.isEmpty());
		return new RawAstTranslator().translate(new File((String)translatedModules.get(this.sourceFileName).getChild(0).getValue()), translatedModules.get(this.sourceFileName).getChild(1));
	}

	public String[] doTranslation(String[] rawArgs) {
		return doTranslation(new ArrayList<>(Arrays.asList(rawArgs)));
	}

	public String[] doTranslation(ArrayList<String> rawArgs) {

		ArrayList<String> args = new ArrayList<>();

		String singleMode = "SINGLE";
		String iOSMode = "iOS";
		String usageString = String.format("Usage: <MODE> <args>\n\tMODE:\t\t%s or %s\n\targs:\t\tfile for %s mode, or arguments to performFrontend() for %s mode.\n\t\t\t\t\tThese should come from the shim script.", singleMode, iOSMode, singleMode, iOSMode);
		if (rawArgs.isEmpty() || rawArgs.size() == 1 || !(rawArgs.get(0).equals(singleMode) || rawArgs.get(0).equals(iOSMode))) {
			System.err.println(usageString);
			System.exit(1);
		} else {
			String mode = rawArgs.get(0);
			if (mode.equals(singleMode)) {
				String filePath = rawArgs.get(1);
				File file = new File(FilenameUtils.getFullPath(filePath) + FilenameUtils.getName(filePath));
				if (!file.exists()) {
					System.err.println(String.format("Error: File %s does not exist", filePath));
					System.exit(1);
				} else {
					String[] singleArgs = new String[]
							{"", "-emit-silgen", "-oout.sil", "-Onone", file.getAbsolutePath()};
					args.addAll(Arrays.asList(singleArgs));
				}
			} else { // iOS mode.
				args.addAll(rawArgs.subList(1,rawArgs.size()));
			}
		}

		ArrayList<CAstNode> roots = translateToCAstNodes(args);

		ArrayList<String> moduleNames = new ArrayList<>();
		for (CAstNode root : roots) {
			String filename = (String)root.getChild(0).getValue();
			translatedModules.put(filename, root);
			moduleNames.add(filename);
		}
		return moduleNames.toArray(new String[0]);
	}

	public void setSource(String url) {
		try {
			File newFile = new File(url);
			this.dynamicSourceURL = newFile.toURI().toURL();
			this.dynamicSourceFileName = newFile.getName();
		} catch (Exception e) {
			System.err.println("Error: Invalid url given");
			e.printStackTrace();
		}
	}

	protected CAstSourcePositionMap.Position makeLocation(final int fl, final int fc, final int ll, final int lc) {
		return new AbstractSourcePosition() {
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
				return dynamicSourceURL;
			}

			public InputStream getInputStream() throws IOException {
				return new FileInputStream(dynamicSourceFileName);
			}

			@Override
			public String toString() {
				String urlString = dynamicSourceURL.toString();
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
