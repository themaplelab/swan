//===--- Inliner.java ----------------------------------------------------===//
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
import ca.maple.swan.swift.tree.FunctionEntity;
import ca.maple.swan.swift.tree.SwiftFunctionType;
import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import static ca.maple.swan.swift.translator.RawAstTranslator.Ast;
import static ca.maple.swan.swift.translator.RawAstTranslator.getStringValue;
import static com.ibm.wala.cast.tree.CAstNode.BLOCK_STMT;
import static com.ibm.wala.cast.tree.CAstNode.LABEL_STMT;

/*
 * SIL basic block and function inliner.
 */

public class Inliner {

    public static boolean shouldInlineFunction(String functionName, SILInstructionContext C) {
        AbstractCodeEntity function = RawAstTranslator.findEntity(functionName, C.allEntities);
        if (function instanceof FunctionEntity) {
            // Temporary using realTypes until JS "Any" type problem is resolved.
            for (String argType : ((SwiftFunctionType)function.getType()).realTypes) {
                if (shouldInlineType(argType)) {
                    return true;
                }
            }
        }
        return false;
     }

    // TODO: Might want to cache this result.
    public static boolean shouldInlineBlock(int destBlockNo, SILInstructionContext C) {
        if (destBlockNo == 0) { return false; }
        CAstNode block = C.currentFunction.getChild(4).getChild(destBlockNo);
        for (CAstNode arg : block.getChild(0).getChildren()) {
            String argType = getStringValue(arg, 1);
            if (Inliner.shouldInlineType(argType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldInlineType(String type) {
        return !Arrays.stream(blackListInlineTypes).anyMatch(type::equals);
    }

    public static SILValue doFunctionInline(String functionName, SILInstructionContext C, ArrayList<SILValue> args, RawAstTranslator translator, ArrayList<CAstNode> blocks) {
        FunctionEntity function = (FunctionEntity)RawAstTranslator.findEntity(functionName, C.allEntities);
        assert function != null;
        SILInstructionContext C2 = new SILInstructionContext(function, C.allEntities, function.rawInfo);
        C2.inliningParent = true;
        C2.valueTable = C.valueTable;
        int argIndex = 1;
        for (SILValue arg : args) {
            C.valueTable.copyValue(function.getArgumentNames()[argIndex], arg.getName());
            ++argIndex;
        }
        int blockNo =  0;
        for (CAstNode block: function.rawInfo.getChild(4).getChildren()) {
            if (Inliner.shouldInlineBlock(blockNo, C2)) { continue; }
            C2.clearInstructions();
            for (CAstNode instruction: block.getChildren().subList(1, block.getChildren().size())) {
                try {
                    CAstNode Node = translator.visit(instruction, C2);
                    C2.parent.getSourceMap().setPosition(Node, translator.getInstructionPosition(instruction));
                    if ((Node != null) && (Node.getKind() != CAstNode.EMPTY)) {
                        C2.instructions.add(Node);
                    }
                } catch (Throwable e) {
                    System.err.println("ERROR: " + instruction.getChild(0).getValue() + " failed to translate");
                    System.err.println("\t Function: " + C2.parent.getName() + " | " + "Block: #" + blockNo);
                    System.err.println("\t" + instruction.toString().replaceAll("\n", "\n\t"));
                    e.printStackTrace();
                }
            }
            C.valueTable.addAll(C2.valueTable);
            C2.instructions.add(0, Ast.makeNode(CAstNode.LABEL_STMT,
                    Ast.makeConstant(blockNo)));
            C2.blocks.add(C2.instructions);
            ++blockNo;
        }
        ArrayList<CAstNode> flatBlocks = new ArrayList<>();
        C2.blocks.get(0).add(0, Ast.makeNode(LABEL_STMT, Ast.makeConstant("inlined_function_" + functionName)));
        flatBlocks.add(Ast.makeNode(BLOCK_STMT, C2.blocks.get(0)));
        int i = 1; // Assuming BB0 is never branched to.
        for (ArrayList<CAstNode> block : C2.blocks.subList(1, C2.blocks.size())) {
            CAstNode BlockStmt = Ast.makeNode(BLOCK_STMT, block);
            if (C2.danglingGOTOs.containsKey(i)) {
                for (CAstNode dangling : C2.danglingGOTOs.get(i)) {
                    C2.parent.setGotoTarget(dangling, BlockStmt);
                }
            }
            flatBlocks.add(BlockStmt);
        }
        // For handling the case of try_apply inlining which needs to add the instructions itself inside the normal block.
        if (blocks != null) {
            blocks.addAll(flatBlocks);
        } else {
            C.instructions.addAll(flatBlocks);
        }
        for (Collection<CAstEntity> e : C2.parent.getAllScopedEntities().values()) {
            for (CAstEntity entity : e) {
                C.parent.addScopedEntity(null, entity);
            }
        }
        C.parent.getControlFlow().addAll(C2.parent.getControlFlow());
        return C2.returnValue;
    }

    public static void doBlockInline(CAstNode N, int destBlockNo, SILInstructionContext C, ArrayList<SILValue> args, RawAstTranslator translator) {
        CAstNode block = C.currentFunction.getChild(4).getChild(destBlockNo);
        int argIndex = 0;
        for (CAstNode arg : block.getChild(0).getChildren()) {
            String argName = getStringValue(arg, 0);
            SILValue ArgValue = args.get(argIndex);
            // Ignore type because it should be the same.
            C.valueTable.copyValue(argName, ArgValue.getName());
            ++argIndex;
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
        C.instructions.add(beginning, Ast.makeNode(CAstNode.LABEL_STMT,
                Ast.makeConstant("inlined_" + destBlockNo)));
    }

    // Better safe than sorry, so we blacklist instead of whitelist.
    private static final String[] blackListInlineTypes = new String[] {
            "$Int",
            "$Int32",
            "$UInt",
            "$Bool", // This can exist as an OBJECT_LITERAL, but that's perfectly okay.
            "$Builtin.Word",
            "$Builtin.Int1",
            "$String",
            "self",
            "$Error"
    };
}
