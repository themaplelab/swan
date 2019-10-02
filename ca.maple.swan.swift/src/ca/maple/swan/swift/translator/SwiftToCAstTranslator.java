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
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;
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
		String multiMode = "MULTI";
		String usageString = String.format("Usage: <MODE> <args>\n\tMODE:\t\t%s or %s\n\targs:\t\tfile for %s mode, or arguments to performFrontend() for %s mode.\n\t\t\t\t\tThese should come from the shim script.", singleMode, multiMode, singleMode, multiMode);
		if (rawArgs.isEmpty() || rawArgs.size() == 1 || !(rawArgs.get(0).equals(singleMode) || rawArgs.get(0).equals(multiMode))) {
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
			} else { // multi mode.
				args.addAll(rawArgs.subList(1,rawArgs.size()));
			}
		}

		ArrayList<CAstNode> roots = translateToCAstNodes(args);

		// TODO: The whole no source handling thing is super janky and gross.
		//		Needs a more clean solution.

		// Get functions with no source.

		// FuncName -> (RawAst, NestedCalls)
		Map<String, Pair<CAstNode, ArrayList<String>>> noSourceFunctions = new HashMap<>();

		for (CAstNode root : roots) {
			String file = (String)root.getChild(0).getValue();
			// Note: "NO SOURCE" is coupled with C++ code.
			if (file.equals("NO SOURCE")) {
				for (CAstNode func : root.getChild(1).getChildren()) {
					ArrayList<String> calledFunctions = new ArrayList<>();
					for (CAstNode calledFunc : func.getChild(5).getChildren()) {
						calledFunctions.add((String)calledFunc.getValue());
					}
					noSourceFunctions.put((String)func.getChild(0).getValue(), Pair.make(func, calledFunctions));
				}
			}
		}

		ArrayList<String> moduleNames = new ArrayList<>();

		CAstNode main = null;

		// Find "main" to arbitrarily add to any file (doesn't matter which one).
		for (CAstNode root : roots) {
			if (root.getChild(0).getValue().equals("NO SOURCE")) {
				for (CAstNode f : root.getChild(1).getChildren()) {
					if (f.getChild(0).getValue().equals("main")) {
						main = f;
					}
				}
			}
		}

		Assertions.productionAssertion(main	!= null);

		for (CAstNode root : roots) {

			// Note: "NO SOURCE" is coupled with C++ code.
			if (root.getChild(0).getValue().equals("NO SOURCE")) {
				continue;
			}

			// Get all called functions of the current file.
			ArrayList<String> calledFunctions = new ArrayList<>();
			for (CAstNode func : root.getChild(1).getChildren()) {
				for (CAstNode calledFunc : func.getChild(5).getChildren()) {
					calledFunctions.add((String)calledFunc.getValue());
				}
			}

			// We can't mutate the ast so we create a new one.

			// First, we add all the current functions belonging to the file.
			ArrayList<CAstNode> newFunctions = new ArrayList<>();
			for (CAstNode func : root.getChild(1).getChildren()) {
				newFunctions.add(func);
			}

			// Add main if it hasn't already been added.
			if (main != null) {
				newFunctions.add(main);
				main = null;
			}

			// Then, we look at all calledFunctions, and analyze their calls
			// incl. nested calls to see if they have no source. Pull in those
			// with no source.
			for (String s : calledFunctions) {
				ArrayList<String> workList = new ArrayList<>();
				workList.add(s);
				while (!workList.isEmpty()) {
					String currentFunc = workList.get(0);
					workList.remove(0);
					if (!noSourceFunctions.containsKey(currentFunc)) { continue; }
					Pair<CAstNode, ArrayList<String>> currentPair = noSourceFunctions.get(currentFunc);
					// Inefficient O(n) call.
					if (!newFunctions.contains(currentPair.fst)){
						newFunctions.add(currentPair.fst);
						noSourceFunctions.remove(currentFunc);
					}
					for (String nestedCall : currentPair.snd) {
						workList.add(nestedCall);
					}
				}
			}

			CAstNode newRoot = Ast.makeNode(CAstNode.PRIMITIVE, root.getChild(0), Ast.makeNode(CAstNode.PRIMITIVE, newFunctions));

			String filename = (String)newRoot.getChild(0).getValue();
			translatedModules.put(filename, newRoot);
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
