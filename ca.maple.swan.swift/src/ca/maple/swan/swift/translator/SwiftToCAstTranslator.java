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
import java.util.*;

import ca.maple.swan.swift.translator.silir.context.ProgramContext;
import ca.maple.swan.swift.tree.ScriptEntity;
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

	private static Map<String, String> paths = new HashMap<>();

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
		ProgramContext pc = new RawToSILIRTranslator().translate(translatedModules.get(this.sourceFileName).getChild(1));
		pc.printFunctions();
		return new SILIRToCAstTranslator().translate(new File((String)translatedModules.get(this.sourceFileName).getChild(0).getValue()), pc);
	}

	public String[] doTranslation(String[] rawArgs) {
		return doTranslation(new ArrayList<>(Arrays.asList(rawArgs)));
	}

	public String[] doTranslation(ArrayList<String> args) {

		// Find all files being analyzed so when setSource() is called later,
		// we can set the full path for the file.
		for (String s : args) {
			File f = new File(s);
			if (f.exists()) {
				paths.put(f.getName(), f.getAbsolutePath());
			}
		}

		// MAIN TRANSLATION CALL.
		// Arguments will be directly fed to performFrontend() call.
		ArrayList<CAstNode> roots = translateToCAstNodes(args);

		// Module names - in reality, this will have a size of 1 since we are throwing
		// everything into one module with the name being the common path of all files.
		ArrayList<String> moduleNames = new ArrayList<>();

		// Where all of the functions from every source file go, since we are using
		// a single module.
		ArrayList<CAstNode> newFunctions = new ArrayList<>();

		// All paths so we can later find the common one.
		ArrayList<String> paths = new ArrayList<>();

		for (CAstNode root : roots) {
			if (!root.getChild(0).getValue().equals("NO_SOURCE")) {
				paths.add((String)root.getChild(0).getValue());
			}

			for (CAstNode func : root.getChild(1).getChildren()) {
				newFunctions.add(func);
			}
		}

		// In the case that the only function is "main", which has no source information, find the first instruction
		// with a source filename and use that one for the function.
		if (paths.isEmpty() && !roots.isEmpty()) {
			CAstNode root = roots.get(0);
			boolean cont = false;
			if (root.getChild(0).getValue().equals("NO_SOURCE")) {
				for (CAstNode block : root.getChild(1).getChild(0).getChild(4).getChildren()) {
					if (cont) { break; }
					for (CAstNode instruction: block.getChildren().subList(1, block.getChildren().size())) {
						if (!((CAstSourcePositionMap.Position)instruction.getChild(1).getValue()).getURL().toString().equals("NO_SOURCE")) {
							try {
								paths.add(((CAstSourcePositionMap.Position)instruction.getChild(1).getValue()).getURL().toURI().getRawPath());
							} catch (Exception e) {
								e.printStackTrace();
							}
							cont = true;
							break;
						}
					}
				}
			}
		}

		if (paths.isEmpty()) {
			return new String[0];
		}

		String commonPath = longestCommonPath(paths);

		// We generally don't want to have a specific file as the module name.
		// E.g. In the case that there is another file with just the main function.
		File tempFile = new File(commonPath);
		if (FilenameUtils.getExtension(tempFile.getName()).equals("swift") && paths.size() > 1) {
			commonPath = FilenameUtils.getPath(tempFile.getPath());
		}

		CAstNode newRoot = Ast.makeNode(CAstNode.PRIMITIVE,
				Ast.makeConstant(commonPath),
				Ast.makeNode(CAstNode.PRIMITIVE, newFunctions));

		translatedModules.put(commonPath, newRoot);
		moduleNames.add(commonPath);

		return moduleNames.toArray(new String[0]);
	}

	private static String longestCommonPath(ArrayList<String> paths) {
		if (paths.size() == 0) {
			return null;
		} else if (paths.size() == 1) {
			return paths.get(0);
		}
		String initialPath = paths.get(0);
		for (String path : paths.subList(1, paths.size())) {
			for (int i = 0; i < path.length() && i < initialPath.length(); ++i) {
				if (initialPath.charAt(i) != path.charAt(i)) {
					initialPath = initialPath.substring(0, i);
					continue;
				}
			}
		}
		return initialPath;
	}

	// Specifically meant to be used by the C++ translator.
	public void setSource(String filename) {
		try {
			File newFile =
					new File(filename).exists() ?
					new File(filename) :
							(paths.containsKey(filename)) ?
									new File(paths.get(filename)) :
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
