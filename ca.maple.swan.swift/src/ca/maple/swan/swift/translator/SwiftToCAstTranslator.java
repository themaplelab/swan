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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import com.ibm.wala.cast.ir.translator.NativeTranslatorToCAst;
import com.ibm.wala.cast.tree.CAst;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.cast.tree.rewrite.CAstRewriter.CopyKey;
import com.ibm.wala.cast.tree.rewrite.CAstRewriter.RewriteContext;
import com.ibm.wala.cast.tree.rewrite.CAstRewriterFactory;
import com.ibm.wala.classLoader.ModuleEntry;
import com.ibm.wala.util.debug.Assertions;
import org.apache.commons.io.FilenameUtils;

/*
 * This class translates the Swift code to a single CAstEntity
 * (the "main" ScriptEntity) by calling a JNI method that calls into the
 * C++ translator code.
 */

public class SwiftToCAstTranslator extends NativeTranslatorToCAst {

	private static Map<String, CAstNode> translatedModules;

    static {
        SwiftTranslatorPathLoader.load();
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

	public static native ArrayList<CAstNode> translateToCAstNodes(ArrayList<String> args);

	@Override
	public CAstEntity translateToCAst() {
		assert(!translatedModules.isEmpty());
		return new RawAstTranslator().translate(new File(getFile()), translatedModules.get(this.sourceFileName).getChild(1));
	}

	public static String[] doTranslation(String[] rawArgs) {
		return doTranslation(new ArrayList<>(Arrays.asList(rawArgs)));
	}

	public static String[] doTranslation(ArrayList<String> rawArgs) {

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
}
