//===--- ASTtoDot.java ---------------------------------------------------===//
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
// This particular source file uses graphviz-java which is licensed under the
// Apache License 2.0
//
//===---------------------------------------------------------------------===//

package ca.maple.swan.swift.visualization;

import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.util.CAstPrinter;
import guru.nidi.graphviz.attribute.Color;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.model.MutableNode;

import java.io.File;
import java.util.ArrayList;

import static guru.nidi.graphviz.model.Factory.mutGraph;
import static guru.nidi.graphviz.model.Factory.mutNode;

public class ASTtoDot {

    private static boolean dirFlag = true;

    private static MutableNode currentRoot;

    public static void print(ArrayList<AbstractCodeEntity> entities) {
        try {
            String dotDirStr = "dot/";
            if (dirFlag) { // Make sure we only initialize the directory once in the lifetime of the program.
                File dir = new File(dotDirStr);
                if (!dir.exists()) {
                    dir.mkdir();
                } else {
                    for (String f : dir.list()) {
                        new File(dir.getPath(), f).delete();
                    }
                    dir.delete();
                    dir.mkdir();
                }
                dirFlag = false;
            }
            File dotFile = new File(dotDirStr + entities.get(0).getName().replaceAll(" ", "_") + ".png");
            dotFile.createNewFile();
            MutableGraph g = mutGraph(entities.get(0).getName()).setDirected(true);

            for (AbstractCodeEntity entity: entities) {
                MutableNode mNode = mutNode(entity.getName()).add(Color.RED);
                currentRoot = mNode;
                buildGraph(mNode, entity.getAST());
                g.add(mNode);
            }

            Graphviz.fromGraph(g).render(Format.PNG).toFile(dotFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void buildGraph(MutableNode mNode, CAstNode cNode) {
        if (cNode == null) { return; }
        MutableNode newNode;
        String hash = System.identityHashCode(cNode) + mNode.hashCode() + "\n";
        switch (cNode.getKind()) {
            case CAstNode.DECL_STMT: {
                return;
            }
            case CAstNode.CONSTANT: {
                newNode = mutNode(hash + cNode.toString());
                int color[] = hashColor(cNode.toString());
                newNode.add(Color.rgb(color[0], color[1], color[2]));
                break;
            }
            case CAstNode.OPERATOR: {
                newNode = mutNode(hash + cNode.getValue());
                break;
            }
            case CAstNode.VAR: {
                newNode = mutNode(hash + CAstPrinter.kindAsString(cNode.getKind()));
                break;
            }
            default: {
                newNode = mutNode(hash + CAstPrinter.kindAsString(cNode.getKind()));
                break;
            }
        }
        // TODO: Fix the ordering of nodes (fork graphviz-java and remove adjustLinks?)
        mNode.addLink(newNode);
        for (CAstNode child: cNode.getChildren()) {
            buildGraph(newNode, child);
        }
    }

    private static int[] hashColor(String name) {
        int hash = name.hashCode();
        int returnRGB[] = new int[3];
        returnRGB[0] = (hash & 0xFF0000) >> 16;
        returnRGB[1] = (hash & 0x00FF00) >> 8;
        returnRGB[2] = hash & 0x0000FF;
        return returnRGB;
    }
}
