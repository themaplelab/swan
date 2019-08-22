//===--- RawAstTranslator.java -------------------------------------------===//
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

import ca.maple.swan.swift.ipa.summaries.BuiltInFunctionSummaries;
import ca.maple.swan.swift.translator.values.*;
import ca.maple.swan.swift.tree.EntityPrinter;
import ca.maple.swan.swift.tree.FunctionEntity;
import ca.maple.swan.swift.tree.ScriptEntity;
import ca.maple.swan.swift.visualization.ASTtoDot;
import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

/*****************************  AST FORMAT ************************************

    Note: "node" here means PRIMITIVE.

    Every function is represented by one parent node. The C++ translator
    returns a single CAstNode with every one of these "parent" nodes as
    children.

    The first child of the function contains the basic block nodes.

    The second node under a function node has the meta information.

    Under every basic block node there is a node representing an instruction.

    ### GENERAL PROCEDURE:

    1. Create a CAstEntity for each function.
    2. Analyze each CAstEntity's corresponding root node on a new thread.

    ### Function meta information format:

    NAME (CONSTANT)
    RETURN_TYPE (CONSTANT)
    JOBJECT <-- FUNCTION_POSITION
    PRIMITIVE <--- ARGUMENTS
        PRIMITIVE <--- ARGUMENT
            NAME
            TYPE
            JOBJECT <-- ARGUMENT_POSITION
        ...

    ### Basic block format:

    PRIMITIVE <-- INSTRUCTION_INFORMATION
        NAME
        JOBJECT <-- INSTRUCTION_POSITION
        ... <-- ANYTHING_NEEDED_TO_TRANSLATE_INSTRUCTION
    ...


 *****************************************************************************/

/*
 * Translates a raw, custom formatted AST into a complete AST. The result
 * is the root entity of the file being analyzed.
 */
public class RawAstTranslator extends SILInstructionVisitor<CAstNode, SILInstructionContext> {

    public static CAstImpl Ast = new CAstImpl();

    public CAstEntity translate(File file, CAstNode n) {

        /* DEBUG */
        System.out.println("\n\n<<<<<< DEBUG >>>>>\n");
        System.out.println(n);
        System.out.println("<<<<<< DEBUG >>>>>\n\n");
        /* DEBUG */

        // 1. Create CAstEntity for each function.
        ArrayList<AbstractCodeEntity> allEntities = new ArrayList<>();
        HashMap<CAstNode, AbstractCodeEntity>  mappedEntities = new HashMap<>();

        AbstractCodeEntity newEntity = makeScriptEntity(file);
        allEntities.add(newEntity);
        mappedEntities.put(n.getChild(0), newEntity);

        for (CAstNode function : n.getChildren().subList(1, n.getChildren().size())) {
            newEntity = makeFunctionEntity(function);
            allEntities.add(newEntity);
            mappedEntities.put(function, newEntity);
        }

        // 2. Analyze each entity. (TODO)
        for (CAstNode function: mappedEntities.keySet()) {
            ArrayList<ArrayList<CAstNode>> blocks = new ArrayList<>();
            SILInstructionContext C = new SILInstructionContext(mappedEntities.get(function), allEntities);
            int blockNo =  0;
            for (CAstNode block: function.getChild(4).getChildren()) {
                ArrayList<CAstNode> instructions = new ArrayList<>();
                C.instructions = instructions;
                for (CAstNode instruction: block.getChildren()) {
                    try {
                        CAstNode Node = this.visit(instruction, C);
                        if ((Node != null) && (Node.getKind() != CAstNode.EMPTY)) {
                            instructions.add(Node);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                instructions.addAll(0, C.valueTable.getDecls());
                instructions.add(0, Ast.makeNode(CAstNode.LABEL_STMT,
                        Ast.makeConstant(blockNo)));
                blocks.add(instructions);
                ++blockNo;
            }
            for (ArrayList<CAstNode> block : blocks.subList(1, blocks.size())) {
                blocks.get(0).add(Ast.makeNode(CAstNode.BLOCK_STMT, block));
            }
            mappedEntities.get(function).setAst(Ast.makeNode(CAstNode.BLOCK_STMT, blocks.get(0)));
            EntityPrinter.print(mappedEntities.get(function));
        }

        ASTtoDot.print(allEntities);

        return allEntities.get(0);
    }

    private static ScriptEntity makeScriptEntity(File file) {
        return new ScriptEntity(file.getName(), file);
    }

    private static FunctionEntity makeFunctionEntity(CAstNode n) {
        String name = (String)n.getChild(0).getValue();
        String returnType = (String)n.getChild(1).getValue();
        CAstSourcePositionMap.Position functionPosition = (CAstSourcePositionMap.Position)n.getChild(2).getValue();
        ArrayList<String> argumentNames = new ArrayList<>();
        ArrayList<String> argumentTypes = new ArrayList<>();
        ArrayList<CAstSourcePositionMap.Position> argumentPositions = new ArrayList<>();
        for (CAstNode arg : n.getChild(3).getChildren()) {
            argumentNames.add((String)arg.getChild(0).getValue());
            argumentTypes.add((String)arg.getChild(1).getValue());
            argumentPositions.add((CAstSourcePositionMap.Position)arg.getChild(2).getValue());
        }
        return new FunctionEntity(name, returnType, argumentTypes, argumentNames, functionPosition, argumentPositions);
    }

    public static AbstractCodeEntity findEntity(String name, ArrayList<AbstractCodeEntity> entities) {
        for (AbstractCodeEntity e : entities) {
            if (e.getName().equals(name)) {
                return e;
            }
        }
        System.err.println("ERROR: Entity with name " + name + " not found");
        return null;
    }

    @Override
    protected CAstSourcePositionMap.Position getInstructionPosition(CAstNode N) {
        return (CAstSourcePositionMap.Position)N.getChild(1).getValue();
    }

    @Override
    protected CAstNode visitAllocStack(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitAllocRef(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitAllocRefDynamic(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitAllocBox(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitAllocValueBuffer(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitAllocGlobal(CAstNode N, SILInstructionContext C) {
        String GlobalName = (String)N.getChild(0).getValue();
        String GlobalType = (String)N.getChild(1).getValue();
        C.valueTable.addValue(
                new SILValue(GlobalName, GlobalType, C));
        return null;
    }

    @Override
    protected CAstNode visitDeallocStack(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitDeallocBox(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitProjectBox(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitDeallocRef(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitDeallocPartialRef(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitDeallocValueBuffer(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitProjectValueBuffer(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitDebugValue(CAstNode N, SILInstructionContext C) {
        String OperandName = (String)N.getChild(0).getValue();
        String OperandType = (String)N.getChild(1).getValue();
        SILValue InitValue = new SILValue(OperandName, OperandType, C);
        C.valueTable.addValue(InitValue);
        return null;
    }

    @Override
    protected CAstNode visitDebugValueAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitLoad(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitStore(CAstNode N, SILInstructionContext C) {
        String SourceName = (String)N.getChild(0).getValue();
        String DestName = (String)N.getChild(1).getValue();
        assert(C.valueTable.getValue(DestName) instanceof SILPointer);
        return C.valueTable.getValue(SourceName).assignTo(((SILPointer)C.valueTable.getValue(DestName)).dereference());
    }

    @Override
    protected CAstNode visitLoadBorrow(CAstNode N, SILInstructionContext C) {
        String OperandName = (String)N.getChild(0).getValue();
        String ResultName = (String)N.getChild(1).getValue();
        String ResultType = (String)N.getChild(2).getValue();
        SILValue ResultValue = new SILValue(ResultName, ResultType, C);
        C.valueTable.addValue(ResultValue);
        assert(C.valueTable.getValue(OperandName) instanceof SILPointer);
        CAstNode Assign = ((SILPointer)C.valueTable.getValue(OperandName)).dereference().assignTo(ResultValue);
        return Assign;
    }

    @Override
    protected CAstNode visitBeginBorrow(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitEndBorrow(CAstNode N, SILInstructionContext C) {
        String OperandName = (String)N.getChild(0).getValue();
        C.valueTable.removeValue(OperandName);
        return null;
    }

    @Override
    protected CAstNode visitAssign(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitAssignByWrapper(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitMarkUninitialized(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitMarkFunctionEscape(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitMarkUninitializedBehavior(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitCopyAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitDestroyAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitIndexAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitTailAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitIndexRawPointer(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitBindMemory(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitBeginAccess(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitEndAccess(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitBeginUnpairedAccess(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitEndUnpairedAccess(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitStrongRetain(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitStrongRelease(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitSetDeallocating(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitStrongRetainUnowned(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitUnownedRetain(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitUnownedRelease(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitLoadWeak(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitStoreWeak(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitLoadUnowned(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitStoreUnowned(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitFixLifetime(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitEndLifetime(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitMarkDependence(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitIsUnique(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitIsEscapingClosure(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitCopyBlock(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitCopyBlockWithoutEscaping(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitFunctionRef(CAstNode N, SILInstructionContext C) {
        String FuncName = (String)N.getChild(0).getValue();
        String ResultName = (String)N.getChild(1).getValue();
        String ResultType = (String)N.getChild(2).getValue();
        if (!BuiltInFunctionSummaries.isBuiltIn(FuncName) && !BuiltInFunctionSummaries.isSummarized(FuncName)) {
            SILFunctionRef FuncRef = new SILFunctionRef(ResultName, ResultType, C, FuncName);
            C.valueTable.addValue(FuncRef);
            C.parent.addScopedEntity(null, findEntity(FuncName, C.allEntities));
        } else if (BuiltInFunctionSummaries.isSummarized(FuncName)) {
            SILFunctionRef.SILSummarizedFunctionRef FuncRef = new SILFunctionRef.SILSummarizedFunctionRef(ResultName, ResultType, C, FuncName);
            C.valueTable.addValue(FuncRef);
        } else {
            SILConstant Constant = new SILConstant(ResultName, ResultType, C, FuncName);
            C.valueTable.addValue(Constant);
        }
        return null;
}

    @Override
    protected CAstNode visitDynamicFunctionRef(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitPrevDynamicFunctionRef(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitGlobalAddr(CAstNode N, SILInstructionContext C) {
        String GlobalName = (String)N.getChild(0).getValue();
        String ResultName = (String)N.getChild(1).getValue();
        String ResultType = (String)N.getChild(2).getValue();
        SILPointer GlobalRef = C.valueTable.getValue(GlobalName)
                .makePointer(ResultName, ResultType);
        C.valueTable.addValue(GlobalRef);
        return null;
    }

    @Override
    protected CAstNode visitGlobalValue(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitIntegerLiteral(CAstNode N, SILInstructionContext C) {
        int Integer = (int)N.getChild(0).getValue();
        String ResultName = (String)N.getChild(1).getValue();
        String ResultType = (String)N.getChild(2).getValue();
        SILConstant IntegerValue = new SILConstant(ResultName, ResultType, C, Integer);
        C.valueTable.addValue(IntegerValue);
        return null;
    }

    @Override
    protected CAstNode visitFloatLiteral(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitStringLiteral(CAstNode N, SILInstructionContext C) {
        String StringValue = (String)N.getChild(0).getValue();
        String ResultName = (String)N.getChild(1).getValue();
        String ResultType = (String)N.getChild(2).getValue();
        SILConstant Value = new SILConstant(ResultName, ResultType, C, StringValue);
        C.valueTable.addValue(Value);
        return null;
    }

    @Override
    protected CAstNode visitClassMethod(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitObjCMethod(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitSuperMethod(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitObjCSuperMethod(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitWitnessMethod(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitApply(CAstNode N, SILInstructionContext C) {
        String ResultName = (String)N.getChild(0).getValue();
        String ResultType = (String)N.getChild(1).getValue();
        String FuncRefName = (String)N.getChild(2).getValue();
        CAstNode Source;
        CAstNode FuncNode = N.getChild(3);
        switch (FuncNode.getKind()) {
            case CAstNode.UNARY_EXPR: {
                CAstNode Oper = C.valueTable.getValue((String)FuncNode.getChild(1).getValue()).getVarNode();
                Source = Ast.makeNode(CAstNode.UNARY_EXPR, FuncNode.getChild(0), Oper);
                break;
            }
            case CAstNode.BINARY_EXPR: {
                CAstNode Oper1 = C.valueTable.getValue((String)FuncNode.getChild(1).getValue()).getVarNode();
                CAstNode Oper2 = C.valueTable.getValue((String)FuncNode.getChild(2).getValue()).getVarNode();
                Source = Ast.makeNode(CAstNode.BINARY_EXPR, FuncNode.getChild(0), Oper1, Oper2);
                break;
            }
            default: {
                String CalleeName = (String) FuncNode.getChild(0).getValue();
                SILValue FuncRef = C.valueTable.getValue(FuncRefName);
                if (FuncRef instanceof SILConstant) {
                    Source = ((SILConstant) FuncRef).getCAst();
                    C.valueTable.addValue(new SILValue(ResultName, ResultType, C));
                } else if (FuncRef instanceof SILFunctionRef) {
                    ArrayList<CAstNode> Params = new ArrayList<>();
                    Params.add(((SILFunctionRef) FuncRef).getFunctionRef());
                    Params.add(Ast.makeConstant("do"));
                    for (CAstNode RawParam : FuncNode.getChildren().subList(1, FuncNode.getChildren().size())) {
                        Params.add(C.valueTable.getValue((String)RawParam.getValue()).getVarNode());
                    }
                    Source = Ast.makeNode(CAstNode.CALL, Params);
                    C.parent.setGotoTarget(Source, Source);
                    C.valueTable.addValue(new SILValue(ResultName, ResultType, C));
                } else if (FuncRef instanceof SILFunctionRef.SILSummarizedFunctionRef) {
                    ArrayList<CAstNode> Params = new ArrayList<>();
                    Params.addAll(FuncNode.getChildren().subList(1, FuncNode.getChildren().size()));
                    C.valueTable.addValue(new SILValue(ResultName, ResultType, C));
                    Source = BuiltInFunctionSummaries.findSummary(
                            ((SILFunctionRef.SILSummarizedFunctionRef)FuncRef).getFunctionName(),
                            ResultName, ResultType, C, Params);
                    if (Source == null || Source.getKind() == CAstNode.EMPTY) {
                        return Ast.makeNode(CAstNode.EMPTY);
                    }
                } else {
                    Source = Ast.makeConstant("UNKNOWN");
                    C.valueTable.addValue(new SILValue(ResultName, ResultType, C));
                }
                break;
            }
        }
        return Ast.makeNode(CAstNode.ASSIGN, C.valueTable.getValue(ResultName).getVarNode(), Source);
    }

    @Override
    protected CAstNode visitBeginApply(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitAbortApply(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitEndApply(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitPartialApply(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitBuiltin(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitMetatype(CAstNode N, SILInstructionContext C) {
        String ResultName = (String)N.getChild(0).getValue();
        String ResultType = (String)N.getChild(1).getValue();
        SILConstant Value = new SILConstant(ResultName, ResultType, C, ResultType);
        C.valueTable.addValue(Value);
        return null;
    }

    @Override
    protected CAstNode visitValueMetatype(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitExistentialMetatype(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitObjCProtocol(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitRetainValue(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitRetainValueAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitUnmanagedRetainValue(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitCopyValue(CAstNode N, SILInstructionContext C) {
        String ResultName = (String)N.getChild(0).getValue();
        String ResultType = (String)N.getChild(1).getValue();
        String OperandName = (String)N.getChild(2).getValue();
        SILValue ResultValue = new SILValue(ResultName, ResultType, C);
        C.valueTable.addValue(ResultValue);
        return C.valueTable.getValue(OperandName).assignTo(ResultValue);
    }

    @Override
    protected CAstNode visitReleaseValue(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitReleaseValueAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitUnmanagedReleaseValue(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitDestroyValue(CAstNode N, SILInstructionContext C) {
        String OperandName = (String)N.getChild(0).getValue();
        C.valueTable.removeValue(OperandName);
        return null;
    }

    @Override
    protected CAstNode visitAutoreleaseValue(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitTuple(CAstNode N, SILInstructionContext C) {
        String ResultName = (String)N.getChild(0).getValue();
        String ResultType = (String)N.getChild(1).getValue();
        ArrayList<CAstNode> NodeFields = new ArrayList<>();
        NodeFields.add(Ast.makeConstant("TUPLE"));
        ArrayList<String> FieldTypes = new ArrayList<>();
        int index = 0;
        for (CAstNode field : N.getChild(2).getChildren()) {
            String FieldValue = (String)field.getChild(0).getValue();
            String FieldType = (String)field.getChild(1).getValue();
            NodeFields.add(Ast.makeConstant(index));
            NodeFields.add(C.valueTable.getValue(FieldValue).getVarNode());
            FieldTypes.add(FieldType);
            ++index;
        }
        SILTuple ResultTuple = new SILTuple(ResultName, ResultType, C, FieldTypes);
        C.valueTable.addValue(ResultTuple);
        CAstNode ObjLiteral = Ast.makeNode(CAstNode.OBJECT_LITERAL, NodeFields);
        C.parent.setGotoTarget(ObjLiteral, ObjLiteral);
        return Ast.makeNode(CAstNode.ASSIGN, ResultTuple.getVarNode(), ObjLiteral);
    }

    @Override
    protected CAstNode visitTupleExtract(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitTupleElementAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitDestructureTuple(CAstNode N, SILInstructionContext C) {
        String Result1Name = (String)N.getChild(0).getValue();
        String Result1Type = (String)N.getChild(1).getValue();
        String Result2Name = (String)N.getChild(2).getValue();
        String Result2Type = (String)N.getChild(3).getValue();
        String OperandName = (String)N.getChild(4).getValue();
        SILValue ResultValue = C.valueTable.getValue(OperandName);
        if (ResultValue instanceof SILTuple) {
            SILValue element1 = new SILValue(Result1Name, Result1Type, C);
            C.valueTable.addValue(element1);
            SILValue element2 = new SILValue(Result2Name, Result2Type, C);
            C.valueTable.addValue(element2);
            CAstNode firstAssign = Ast.makeNode(
                    CAstNode.ASSIGN,
                    element1.getVarNode(),
                    ((SILTuple)ResultValue).createObjectRef(0));
            C.instructions.add(firstAssign);
            CAstNode secondAssign = Ast.makeNode(
                    CAstNode.ASSIGN,
                    element2.getVarNode(),
                    ((SILTuple)ResultValue).createObjectRef(1));
            C.instructions.add(secondAssign);
        } else {
            Assertions.UNREACHABLE("Operation undefined for non-tuple types");
        }
        return null;
    }

    @Override
    protected CAstNode visitStruct(CAstNode N, SILInstructionContext C) {
        String ResultName = (String)N.getChild(0).getValue();
        String ResultType = (String)N.getChild(1).getValue();
        ArrayList<CAstNode> NodeFields = new ArrayList<>();
        NodeFields.add(Ast.makeConstant("STRUCT"));
        ArrayList<Pair<String, String>> Fields = new ArrayList<>();
        for (CAstNode field : N.getChild(2).getChildren()) {
            String FieldName = (String)field.getChild(0).getValue();
            String FieldValue = (String)field.getChild(1).getValue();
            NodeFields.add(Ast.makeConstant(FieldName));
            NodeFields.add(C.valueTable.getValue(FieldValue).getVarNode());
            Fields.add(Pair.make(FieldName, FieldValue));
        }
        SILStruct ResultStruct = new SILStruct(ResultName, ResultType, C, Fields);
        C.valueTable.addValue(ResultStruct);
        CAstNode ObjLiteral = Ast.makeNode(CAstNode.OBJECT_LITERAL, NodeFields);
        C.parent.setGotoTarget(ObjLiteral, ObjLiteral);
        return Ast.makeNode(CAstNode.ASSIGN, ResultStruct.getVarNode(), ObjLiteral);
    }

    @Override
    protected CAstNode visitStructExtract(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitStructElementAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitDestructureStruct(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitObject(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitRefElementAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitRefTailAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitEnum(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitUncheckedEnumData(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitInitEnumDataAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitInjectEnumAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitUncheckedTakeEnumDataAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitSelectEnum(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitSelectEnumAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitInitExistentialAddr(CAstNode N, SILInstructionContext C) {
        // TODO: Is it sufficient to say that the value pointed to by
        //       the result is the same value pointed to by the operand?
        String ResultName = (String)N.getChild(0).getValue();
        String ResultType = (String)N.getChild(1).getValue();
        String OperandName = (String)N.getChild(2).getValue();
        assert(C.valueTable.getValue(OperandName) instanceof SILPointer);
        SILPointer OperandPointer = (SILPointer)C.valueTable.getValue(OperandName);
        SILValue ValueReferenced = OperandPointer.dereference();
        SILPointer ResultPointer = new SILPointer(ResultName, ResultType, C, ValueReferenced);
        C.valueTable.addValue(ResultPointer);
        return null;
    }

    @Override
    protected CAstNode visitInitExistentialValue(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitDeinitExistentialAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitDeinitExistentialValue(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitOpenExistentialAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitOpenExistentialValue(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitInitExistentialRef(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitOpenExistentialRef(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitInitExistentialMetatype(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitOpenExistentialMetatype(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitAllocExistentialBox(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitProjectExistentialBox(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitOpenExistentialBox(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitOpenExistentialBoxValue(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitDeallocExistentialBox(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitProjectBlockStorage(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitInitBlockStorageHeader(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitUpcast(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitAddressToPointer(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitPointerToAddress(CAstNode N, SILInstructionContext C) {
        // TODO: Is it sufficient to make the result point to the operand?
        String ResultName = (String)N.getChild(0).getValue();
        String ResultType = (String)N.getChild(1).getValue();
        String OperandName = (String)N.getChild(2).getValue();
        SILPointer ResultPointer = new SILPointer(ResultName, ResultType, C, C.valueTable.getValue(OperandName));
        C.valueTable.addValue(ResultPointer);
        return null;
    }

    @Override
    protected CAstNode visitUncheckedRefCast(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitUncheckedRefCastAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitUncheckedAddrCast(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitUncheckedTrivialBitCast(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitUncheckedBitwiseCast(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitRefToRawPointer(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitRawPointerToRef(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitRefToUnowned(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitUnownedToRef(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitRefToUnmanaged(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitUnmanagedToRef(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitConvertFunction(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitConvertEscapeToNoEscape(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitThinFunctionToPointer(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitPointerToThinFunction(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitClassifyBridgeObject(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitValueToBridgeObject(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitRefToBridgeObject(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitBridgeObjectToRef(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitBridgeObjectToWord(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitThinToThickFunction(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitThickToObjCMetatype(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitObjCToThickMetatype(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitObjCMetatypeToObject(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitObjCExistentialMetatypeToObject(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitUnconditionalCheckedCast(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitUnconditionalCheckedCastAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitUnconditionalCheckedCastValue(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitCondFail(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitUnreachable(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitReturn(CAstNode N, SILInstructionContext C) {
        String OperandName = (String)N.getChild(0).getValue();
        return Ast.makeNode(CAstNode.RETURN, C.valueTable.getValue(OperandName).getVarNode());
    }

    @Override
    protected CAstNode visitThrow(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitYield(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitUnwind(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitBr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitCondBr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitSwitchValue(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitSelectValue(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitSwitchEnum(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitSwitchEnumAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitDynamicMethodBr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitCheckedCastBr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitCheckedCastValueBr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitCheckedCastAddrBr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitTryApply(CAstNode N, SILInstructionContext C) {
        return null;
    }
}