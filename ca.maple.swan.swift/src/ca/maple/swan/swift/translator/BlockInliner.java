//===--- BlockInliner.java -----------------------------------------------===//
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

import ca.maple.swan.swift.translator.values.SILValue;
import com.ibm.wala.cast.tree.CAstNode;

import java.util.ArrayList;
import java.util.Arrays;

import static ca.maple.swan.swift.translator.RawAstTranslator.Ast;
import static ca.maple.swan.swift.translator.RawAstTranslator.getStringValue;

public class BlockInliner {

    // TODO: Might want to cache this result.
    public static boolean shouldInline(int destBlockNo, SILInstructionContext C) {
        if (destBlockNo == 0) { return false; }
        CAstNode block = C.currentFunction.getChild(4).getChild(destBlockNo);
        for (CAstNode arg : block.getChild(0).getChildren()) {
            String argType = getStringValue(arg, 1);
            if (BlockInliner.shouldInlineType(argType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldInlineType(String type) {
        return !Arrays.stream(blackListInlineTypes).anyMatch(type::equals);
    }

    public static void doInline(CAstNode N, int destBlockNo, SILInstructionContext C, ArrayList<SILValue> args, RawAstTranslator translator) {
        CAstNode block = C.currentFunction.getChild(4).getChild(destBlockNo);
        int i = 0;
        for (CAstNode arg : block.getChild(0).getChildren()) {
            String argName = getStringValue(arg, 0);
            SILValue ArgValue = args.get(i);
            // Ignore type because it should be the same.
            C.valueTable.copyValue(argName, ArgValue.getName());
            ++i;
        }
        int beginning = C.instructions.size();
        for (CAstNode instruction: block.getChildren().subList(1, block.getChildren().size())) {
            try {
                CAstNode Node = translator.visit(instruction, C);
                if ((Node != null) && (Node.getKind() != CAstNode.EMPTY)) {
                    C.instructions.add(Node);
                }
            } catch (Throwable e) {
                System.err.println("ERROR: " + instruction.getChild(0).getValue() + " failed to translate");
                System.err.println("\t Function: " + C.parent.getName() + " | " + "Block: #" + destBlockNo);
                System.err.println("\t" + instruction.toString().replaceAll("\n", "\n\t"));
                e.printStackTrace();
            }
        }
        C.instructions.addAll(beginning, C.valueTable.getDecls());
        C.instructions.add(beginning, Ast.makeNode(CAstNode.LABEL_STMT,
                Ast.makeConstant("inlined_" + destBlockNo)));
    }

    private static String[] blackListInlineTypes = new String[] {
            "$Int",
            "$Int32",
            "$UInt",
            "$Builtin.Word",
            "$Builtin.Int1",
            "$String"
    };
}
