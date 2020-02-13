//===--- RawData.java ----------------------------------------------------===//
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

import ca.maple.swan.swift.translator.wala.SwiftToCAstTranslator;
import com.ibm.wala.cast.tree.CAst;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.util.debug.Assertions;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.*;

public class RawData {

    private CAstNode rawData = null;

    private static Map<String, String> paths = new HashMap<>();

    private final ArrayList<String> args;

    private final CAst Ast;

    private boolean setupCalled = false;

    public RawData(String[] rawArgs, CAst Ast) {
        args = new ArrayList<>(Arrays.asList(rawArgs));
        this.Ast = Ast;
    }

    public static Map<String, String> getPaths() {
        return paths;
    }

    public CAstNode getRawData() {
        return rawData;
    }

    public String setup() {

        if (setupCalled) {
            Assertions.UNREACHABLE("Setup should only be called once!");
            return "";
        }

        // Find all files being analyzed so when setSource() is called later,
        // we can set the full path for the file.
        for (String s : args) {
            File f = new File(s);
            if (f.exists()) {
                paths.put(f.getName(), f.getAbsolutePath());
            }
        }

        // MAIN C++ CALL.
        // Arguments will be directly fed to performFrontend() call.
        ArrayList<CAstNode> roots = new SwiftToCAstTranslator().translateToCAstNodes(args);

        // Where all of the functions from every source file go, since we are using
        // a single module.
        ArrayList<CAstNode> newFunctions = new ArrayList<>();

        // All paths so we can later find the common one.
        ArrayList<String> paths = new ArrayList<>();

        // Collections.reverse(roots);

        for (CAstNode root : roots) {
            if (!root.getChild(0).getValue().equals("NO_SOURCE")) {
                paths.add((String)root.getChild(0).getValue());
            }

            newFunctions.addAll(root.getChild(1).getChildren());
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
            Assertions.UNREACHABLE();
            return "";
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

        rawData = newRoot;

        return commonPath;
    }

    private static String longestCommonPath(ArrayList<String> paths) {
        if (paths.size() == 1) {
            return paths.get(0);
        }
        String initialPath = paths.get(0);
        for (String path : paths.subList(1, paths.size())) {
            for (int i = 0; i < path.length() && i < initialPath.length(); ++i) {
                if (initialPath.charAt(i) != path.charAt(i)) {
                    initialPath = initialPath.substring(0, i);
                }
            }
        }
        return initialPath;
    }
}
