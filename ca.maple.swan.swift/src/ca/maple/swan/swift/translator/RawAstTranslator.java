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
import com.ibm.wala.cast.tree.*;
import com.ibm.wala.cast.tree.impl.CAstControlFlowRecorder;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.cast.tree.impl.CAstOperator;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;

/*****************************  AST FORMAT ************************************

    Note: "node" here means PRIMITIVE.

    Every function is represented by one parent node. The C++ translator
    returns a single CAstNode with every one of these "parent" nodes as
    children.

    The first child of the function contains the basic block nodes.

    The second node under a function node has the meta information.

    Basic blocks' node's children represent the instructions.

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

    ### Instruction format:

    PRIMITIVE <--- OPERANDS
        PRIMITIVE <--- OPERAND
            NAME
            TYPE
        ...
    PRIMITIVE <--- RESULTS
        PRIMITIVE <--- RESULT
            NAME
            TYPE
        ...
    (ANYTHING ELSE)

    -----------------

    This translator tries to limit the amount of CAst it generates
    by caching (if possible) into the SILValueTable instead of immediately
    creating CAst for each instruction.

    In order to do away with low level problems, such as pointers which WALA
    doesn't support, we keep track of the values ourselves with the
    following classes.

    SILConstant
        Useful for literals.

    SILField
        Instead of holding a copy of a field, we can generate an OBJECT_REF
        each time we use the value.

    SILFunctionRef
        Simply holds the name of a function, used for explicitness as
        opposed to SILConstant

    SILFunctionRef.SILSummarizedFunctionRef
        Used to explicitly mark functions as summarizable.

    SILPointer
        We can keep track of a pointer's underlying value using this class.
        This class does not have to have an underlying value, so we need
        to check if it has an underlying value ( hasValue() ). This is so
        that instructions that create pointers such as alloc_stack can be
        handled explicitly. Typically any type starting with $*.

        We want as much as possibly 1:1 representation type wise. So if
        an instruction expects a pointer as an operand, we have to make sure
        it exists as a SILPointer in our table.

    SILStruct
        Used to represent structs, but can really represent any object with
        named fields. Can generate SILFields.

    SILTuple
        Same as SILStruct but field names are (obviously) numbers.
        Holds 2 values.

    SILValue
        Base class that can also hold its own as a generic value.

    -----------------

    NOTES:

    We index assuming we have the correct number of results/operands.
    Exception handling will make it obvious if we are indexing wrong.

    We need to be careful to handle built in pointers such as
    $*Builtin.UnsafeValueBuffer which are most likely introduced via built in
    functions.

    Treating references like regular values may be an extremely naive and
    problematic approach. e.g. project_box might be inaccurate. Depends on
    how boxes are introduced, esp by built in functions.

    Not sure about relationship between function args and bb0 args.

    Theoretically all basic block args are handled by the calling block.

    Problem: If a block with args is called by multiple blocks, then the
    value table will contain the latest value for that block, meaning that
    we have to handle that for each type. e.g. for a pointer, we would have
    to assign

 *****************************************************************************/

/*
 * Translates a raw, custom formatted AST into a complete AST. The result
 * is the root entity of the file being analyzed.
 */
public class RawAstTranslator extends SILInstructionVisitor<CAstNode, SILInstructionContext> {

    public static CAstImpl Ast = new CAstImpl();

    public CAstEntity translate(File file, CAstNode n) {

        /* DEBUG
        System.out.println("\n\n<<<<<< DEBUG >>>>>\n");
        System.out.println(n);
        System.out.println("<<<<<< DEBUG >>>>>\n\n");
         DEBUG */

        // 1. Create CAstEntity for each function.
        ArrayList<AbstractCodeEntity> allEntities = new ArrayList<>();
        HashMap<CAstNode, AbstractCodeEntity>  mappedEntities = new HashMap<>();

        AbstractCodeEntity scriptEntity = null;

        for (CAstNode function : n.getChildren()) {
            AbstractCodeEntity newEntity;
            if (((String)function.getChild(0).getValue()).equals("main")) {
                newEntity = makeScriptEntity("main", file);
                scriptEntity = newEntity;
            } else {
                newEntity = makeFunctionEntity(function);
            }
            allEntities.add(newEntity);
            mappedEntities.put(function, newEntity);
        }

        // 2. Analyze each entity.
        for (CAstNode function: mappedEntities.keySet()) {
            SILInstructionContext C = new SILInstructionContext(mappedEntities.get(function), allEntities);
            int blockNo =  0;
            for (CAstNode block: function.getChild(4).getChildren()) {
                C.clearInstructions();
                for (CAstNode instruction: block.getChildren()) {
                    try {
                        CAstNode Node = this.visit(instruction, C);
                        if ((Node != null) && (Node.getKind() != CAstNode.EMPTY)) {
                            C.instructions.add(Node);
                        }
                    } catch (Throwable e) {
                        System.err.println("ERROR: " + instruction.getChild(0).getValue() + " failed to translate");
                        System.err.println("\t Function: " + C.parent.getName() + " | " + "Block: #" + blockNo);
                        System.err.println("\t" + instruction.toString().replaceAll("\n", "\n\t"));
                        e.printStackTrace();
                    }
                }
                C.instructions.addAll(0, C.valueTable.getDecls());
                C.instructions.add(0, Ast.makeNode(CAstNode.LABEL_STMT,
                        Ast.makeConstant(blockNo)));
                C.blocks.add(C.instructions);
                ++blockNo;
            }
            int i = 1; // Assuming BB0 is never branched to.
            for (ArrayList<CAstNode> block : C.blocks.subList(1, C.blocks.size())) {
                CAstNode BlockStmt = Ast.makeNode(CAstNode.BLOCK_STMT, block);
                if (C.danglingGOTOs.containsKey(i)) {
                    for (CAstNode dangling : C.danglingGOTOs.get(i)) {
                        C.parent.setGotoTarget(dangling, BlockStmt);
                    }
                }
                C.blocks.get(0).add(BlockStmt);
                ++i;
            }
            mappedEntities.get(function).setAst(Ast.makeNode(CAstNode.BLOCK_STMT, C.blocks.get(0)));
            EntityPrinter.print(mappedEntities.get(function));
        }

        ASTtoDot.print(allEntities);

        return scriptEntity;
    }

    private static ScriptEntity makeScriptEntity(File file) {
        return new ScriptEntity(file.getName(), file);
    }

    private static ScriptEntity makeScriptEntity(String name, File file) {
        return new ScriptEntity(name, file);
    }

    private static FunctionEntity makeFunctionEntity(CAstNode n) {
        String name = (String)n.getChild(0).getValue();
        String returnType = (String)n.getChild(1).getValue();
        CAstSourcePositionMap.Position functionPosition = (CAstSourcePositionMap.Position)n.getChild(2).getValue();
        ArrayList<String> argumentNames = new ArrayList<>();
        ArrayList<String> argumentTypes = new ArrayList<>();
        ArrayList<CAstSourcePositionMap.Position> argumentPositions = new ArrayList<>();
        for (CAstNode arg : n.getChild(3).getChildren()) {
            String argName = (String)arg.getChild(0).getValue();
            String argType = (String)arg.getChild(1).getValue();
            argumentNames.add(argName);
            argumentTypes.add(argType);
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

    private static void tryGOTO(CAstNode n, String label, SILInstructionContext C) {
        int bb = Integer.parseInt(label);
        if (bb <= C.blocks.size()) {
            C.parent.setGotoTarget(n, C.blocks.get(bb).get(0));
        } else {
            if (!C.danglingGOTOs.containsKey(bb)) {
                C.danglingGOTOs.put(bb, new ArrayList<>());
            }
            C.danglingGOTOs.get(bb).add(n);
        }
    }

    public static class RawValue {
        public final String Name;
        public final String Type;
        public RawValue(String name, String type) {
            this.Name = name;
            this.Type = type;
        }
        public RawValue(String name) {
            this(name, null);
        }
    }

    private static Pair<ArrayList<RawValue>, ArrayList<RawValue>> getOperandsAndResults(CAstNode N) {
        // TODO: cache the results so that subsequent calls such as
        //       getResult()/getOperand() don't call this whole process each time?
        Assertions.productionAssertion(N.getChildren().size() >= 2);
        ArrayList<RawValue> operands = new ArrayList<>();
        ArrayList<RawValue> results = new ArrayList<>();
        for (CAstNode operand : N.getChild(0).getChildren()) {
            operands.add(new RawValue(
                    (String)operand.getChild(0).getValue(),
                    (String)operand.getChild(1).getValue()));
        }
        for (CAstNode result : N.getChild(1).getChildren()) {
            results.add(new RawValue(
                    (String)result.getChild(0).getValue(),
                    (String)result.getChild(1).getValue()));
        }
        return Pair.make(operands, results);
    }

    private static RawValue getSingleResult(CAstNode N) {
        return getResult(N, 0);
    }

    public static RawValue getResult(CAstNode N, int index) {
        return getOperandsAndResults(N).snd.get(index);
    }

    private static RawValue getSingleOperand(CAstNode N) {
        return getOperand(N, 0);
    }

    private static RawValue getOperand(CAstNode N, int index) {
        return getOperandsAndResults(N).fst.get(index);
    }

    private static String getStringValue(CAstNode N, int index) {
        Assertions.productionAssertion(N.getChildren().size() > index);
        return (String)N.getChild(index).getValue();
    }

    public static int getIntValue(CAstNode N, int index) {
        Assertions.productionAssertion(N.getChildren().size() > index);
        return (int)N.getChild(index).getValue();
    }

    @Override
    protected CAstSourcePositionMap.Position getInstructionPosition(CAstNode N) {
        return (CAstSourcePositionMap.Position)N.getChild(1).getValue();
    }

    @Override
    protected CAstNode visitAllocStack(CAstNode N, SILInstructionContext C) {
        // Since we are creating a pointer, we use a SILPointer to be explicit.
        RawValue result = getSingleResult(N);
        SILPointer ResultValue = new SILPointer(result.Name, result.Type, C);
        C.valueTable.addValue(ResultValue);
        return Ast.makeNode(CAstNode.ASSIGN,
                ResultValue.getVarNode(),
                Ast.makeNode(CAstNode.NEW, Ast.makeConstant(result.Type)));
    }

    @Override
    protected CAstNode visitAllocRef(CAstNode N, SILInstructionContext C) {
        // We are allocating a reference to an object, so no pointer here.
        // We ignore the operand which specifies irrelevant memory information.
        RawValue result = getSingleResult(N);
        SILValue ResultValue = new SILValue(result.Name, result.Type, C);
        C.valueTable.addValue(ResultValue);
        return Ast.makeNode(CAstNode.ASSIGN,
                ResultValue.getVarNode(),
                Ast.makeNode(CAstNode.NEW, Ast.makeConstant(result.Type)));
    }

    @Override
    protected CAstNode visitAllocRefDynamic(CAstNode N, SILInstructionContext C) {
        // We are allocating a reference to an object, so no pointer here.
        // First operand specifies metatype value (hence dynamic) but we can ignore
        // it since we know the type of the result anyway.
        // We ignore the second operand which specifies irrelevant memory information.
        RawValue result = getSingleResult(N);
        SILValue ResultValue = new SILValue(result.Name, result.Type, C);
        C.valueTable.addValue(ResultValue);
        return Ast.makeNode(CAstNode.ASSIGN,
                ResultValue.getVarNode(),
                Ast.makeNode(CAstNode.NEW, Ast.makeConstant(result.Type)));
    }

    @Override
    protected CAstNode visitAllocBox(CAstNode N, SILInstructionContext C) {
        // This instruction allocates a box and returns a reference to it.
        // For now, we treat references as values.
        RawValue result = getSingleResult(N);
        SILValue ResultValue = new SILValue(result.Name, result.Type, C);
        C.valueTable.addValue(ResultValue);
        return Ast.makeNode(CAstNode.ASSIGN,
                ResultValue.getVarNode(),
                Ast.makeNode(CAstNode.NEW, Ast.makeConstant(result.Type)));
    }

    @Override
    protected CAstNode visitAllocValueBuffer(CAstNode N, SILInstructionContext C) {
        // Given a pointer, allocate space inside of it. So here we can just copy
        // the pointer.
        // We also allocate the new pointer.
        RawValue result = getSingleResult(N);
        RawValue operand = getSingleOperand(N);
        SILValue OperandValue = C.valueTable.getValue(operand.Name);
        Assertions.productionAssertion(OperandValue instanceof SILPointer);
        SILValue ResultValue = ((SILPointer)OperandValue).copyPointer(result.Name, result.Type);
        return Ast.makeNode(CAstNode.ASSIGN,
                ResultValue.getVarNode(),
                Ast.makeNode(CAstNode.NEW, Ast.makeConstant(result.Type)));
    }

    @Override
    protected CAstNode visitAllocGlobal(CAstNode N, SILInstructionContext C) {
        // Allocate a global which is identified by a name.
        // CUSTOM NODE FORMAT:
        //      NAME
        //      TYPE
        String GlobalName = getStringValue(N, 0);
        String GlobalType = getStringValue(N, 1);
        SILValue ResultValue = new SILValue(GlobalName, GlobalType, C);
        C.valueTable.addValue(ResultValue);
        return Ast.makeNode(CAstNode.ASSIGN,
                ResultValue.getVarNode(),
                Ast.makeNode(CAstNode.NEW, Ast.makeConstant(GlobalType)));
    }

    @Override
    protected CAstNode visitDeallocStack(CAstNode N, SILInstructionContext C) {
        // Deallocates memory previously allocated by alloc_stack.
        // We expect to have already destroyed the underlying memory value so we
        // just remove the pointer itself from the table.
        RawValue operand = getSingleOperand(N);
        C.valueTable.removeValue(operand.Name);
        return null;
    }

    @Override
    protected CAstNode visitDeallocBox(CAstNode N, SILInstructionContext C) {
        // Deallocates a $@box. We just remove the value since it isn't a pointer.
        RawValue operand = getSingleOperand(N);
        C.valueTable.removeValue(operand.Name);
        return null;
    }

    @Override
    protected CAstNode visitProjectBox(CAstNode N, SILInstructionContext C) {
        // Since we treat references as values, we create a pointer to the
        // box value.
        RawValue result = getSingleResult(N);
        RawValue operand = getSingleOperand(N);
        SILValue OperandValue = C.valueTable.getValue(operand.Name);
        C.valueTable.addValue(OperandValue.makePointer(result.Name, result.Type));
        return null;
    }

    @Override
    protected CAstNode visitDeallocRef(CAstNode N, SILInstructionContext C) {
        // Remove the value from the table since it is just a reference.
        RawValue operand = getSingleOperand(N);
        C.valueTable.removeValue(operand.Name);
        return null;
    }

    @Override
    protected CAstNode visitDeallocPartialRef(CAstNode N, SILInstructionContext C) {
        // We ignore the second operand which is the metatype that seems to just
        // be there to properly dealloc the value.
        RawValue operand = getOperand(N, 0);
        C.valueTable.removeValue(operand.Name);
        return null;
    }

    @Override
    protected CAstNode visitDeallocValueBuffer(CAstNode N, SILInstructionContext C) {
        // Deallocates the storage in the given pointer. We do not delete the pointer
        // itself (unless of course the pointer has no underlying value).
        RawValue operand = getSingleOperand(N);
        SILValue OperandValue = C.valueTable.getValue(operand.Name);
        Assertions.productionAssertion(OperandValue instanceof SILPointer);
        C.valueTable.removeValue(((SILPointer)OperandValue).dereference().getName());
        return null;
    }

    @Override
    protected CAstNode visitProjectValueBuffer(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitDebugValue(CAstNode N, SILInstructionContext C) {
        // TODO: Without this instruction, function arguments would not be found, no?
        //       So is this instruction optional or is it always called on function args?
        // For now, we just create a value for the operand.
        // We can always just look at the function entity's argument names/types and
        // add them to the value table before looking at the function instructions.
        // TODO: However, are the function arg names the same as bb0's names?
        RawValue operand = getSingleOperand(N);
        SILValue InitValue = new SILValue(operand.Name, operand.Type, C);
        C.valueTable.addValue(InitValue);
        return null;
    }

    @Override
    protected CAstNode visitDebugValueAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitLoad(CAstNode N, SILInstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        SILValue ResultValue = new SILValue(result.Name, result.Type, C);
        C.valueTable.addValue(ResultValue);
        Assertions.productionAssertion(C.valueTable.getValue(operand.Name) instanceof SILPointer);
        CAstNode Assign = ((SILPointer)C.valueTable.getValue(operand.Name)).dereference().assignTo(ResultValue);
        return Assign;
    }

    @Override
    protected CAstNode visitStore(CAstNode N, SILInstructionContext C) {
        String SourceName = getStringValue(N, 0);
        String DestName = getStringValue(N, 1);
        SILValue DestValue = C.valueTable.getValue(DestName);
        if (DestValue instanceof SILPointer) {
            return C.valueTable.getValue(SourceName).assignTo(((SILPointer)C.valueTable.getValue(DestName)).dereference());
        } else {
            return C.valueTable.getValue(SourceName).assignTo(C.valueTable.getValue(DestName));
        }
    }

    @Override
    protected CAstNode visitStoreBorrow(CAstNode N, SILInstructionContext C) {
        String SourceName = getStringValue(N, 0);
        String DestName = getStringValue(N, 1);
        SILValue DestValue = C.valueTable.getValue(DestName);
        if (DestValue instanceof SILPointer) {
            return C.valueTable.getValue(SourceName).assignTo(((SILPointer)C.valueTable.getValue(DestName)).dereference());
        } else {
            return C.valueTable.getValue(SourceName).assignTo(C.valueTable.getValue(DestName));
        }
    }

    @Override
    protected CAstNode visitLoadBorrow(CAstNode N, SILInstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        SILValue ResultValue = new SILValue(result.Name, result.Type, C);
        C.valueTable.addValue(ResultValue);
        SILValue OperandValue = C.valueTable.getValue(operand.Name);
        if (OperandValue instanceof SILPointer) {
            return ((SILPointer)OperandValue).dereference().assignTo(ResultValue);
        } else {
            return OperandValue.assignTo(ResultValue);
        }
    }

    @Override
    protected CAstNode visitBeginBorrow(CAstNode N, SILInstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        SILValue ResultValue = C.valueTable.getValue(operand.Name)
                .makePointer(result.Name, result.Type);
        C.valueTable.addValue(ResultValue);
        return null;
    }

    @Override
    protected CAstNode visitEndBorrow(CAstNode N, SILInstructionContext C) {
        RawValue operand = getSingleOperand(N);
        C.valueTable.removeValue(operand.Name);
        return null;
    }

    @Override
    protected CAstNode visitAssign(CAstNode N, SILInstructionContext C) {
        String SourceName = getStringValue(N, 0);
        String DestName = getStringValue(N, 1);
        SILValue SourceValue = C.valueTable.getValue(SourceName);
        SILValue DestValue = C.valueTable.getValue(DestName);
        Assertions.productionAssertion(DestValue instanceof SILPointer);
        ((SILPointer)DestValue).replaceUnderlyingVar(SourceValue);
        return null;
    }

    @Override
    protected CAstNode visitAssignByWrapper(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitMarkUninitialized(CAstNode N, SILInstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        SILPointer ResultValue = new SILPointer(result.Name, result.Type, C, C.valueTable.getValue(operand.Name));
        C.valueTable.addValue(ResultValue);
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
        RawValue operand = getSingleOperand(N);
        SILValue AddrToDestroy = C.valueTable.getValue(operand.Name);
        if  (AddrToDestroy instanceof  SILPointer) {
            C.valueTable.removeValue(((SILPointer) AddrToDestroy).dereference().getName());
        }
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
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        SILValue OperandValue = C.valueTable.getValue(operand.Name);
        SILValue ResultValue;
        if (OperandValue instanceof SILPointer) {
             ResultValue = ((SILPointer)C.valueTable.getValue(operand.Name)).copyPointer(result.Name, result.Type);
        } else {
            ResultValue = OperandValue.makePointer(result.Name, result.Type);
        }
        C.valueTable.addValue(ResultValue);
        return null;
    }

    @Override
    protected CAstNode visitEndAccess(CAstNode N, SILInstructionContext C) {
        RawValue operand = getSingleOperand(N);
        C.valueTable.removeValue(operand.Name);
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
        RawValue operand = getSingleOperand(N);
        C.valueTable.removeValue(operand.Name);
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
        RawValue result = getSingleResult(N);
        String FuncName = getStringValue(N, 2);
        if (!BuiltInFunctionSummaries.isBuiltIn(FuncName) && !BuiltInFunctionSummaries.isSummarized(FuncName)) {
            SILFunctionRef FuncRef = new SILFunctionRef(result.Name, result.Type, C, FuncName);
            C.valueTable.addValue(FuncRef);
            C.parent.addScopedEntity(null, findEntity(FuncName, C.allEntities));
        } else if (BuiltInFunctionSummaries.isSummarized(FuncName)) {
            SILFunctionRef.SILSummarizedFunctionRef FuncRef =
                    new SILFunctionRef.SILSummarizedFunctionRef(result.Name, result.Type, C, FuncName);
            C.valueTable.addValue(FuncRef);
        } else {
            SILConstant Constant = new SILConstant(result.Name, result.Type, C, FuncName);
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
        RawValue result = getSingleResult(N);
        String GlobalName = getStringValue(N, 2);
        SILPointer GlobalRef = C.valueTable.getValue(GlobalName)
                .makePointer(result.Name, result.Type);
        C.valueTable.addValue(GlobalRef);
        return null;
    }

    @Override
    protected CAstNode visitGlobalValue(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitIntegerLiteral(CAstNode N, SILInstructionContext C) {
        RawValue result = getSingleResult(N);
        int Integer = getIntValue(N, 2);
        SILConstant IntegerValue = new SILConstant(result.Name, result.Type, C, Integer);
        C.valueTable.addValue(IntegerValue);
        return null;
    }

    @Override
    protected CAstNode visitFloatLiteral(CAstNode N, SILInstructionContext C) {
        RawValue result = getSingleResult(N);
        float Float = ((BigDecimal)N.getChild(2).getValue()).floatValue();
        SILConstant FloatValue = new SILConstant(result.Name, result.Type, C, Float);
        C.valueTable.addValue(FloatValue);
        return null;
    }

    @Override
    protected CAstNode visitStringLiteral(CAstNode N, SILInstructionContext C) {
        RawValue result = getSingleResult(N);
        String StringValue = getStringValue(N, 2);
        SILConstant Value = new SILConstant(result.Name, result.Type, C, StringValue);
        C.valueTable.addValue(Value);
        return null;
    }

    @Override
    protected CAstNode visitClassMethod(CAstNode N, SILInstructionContext C) {
        RawValue result = getSingleResult(N);
        String FuncName = getStringValue(N, 2);
        SILConstant Constant = new SILConstant(result.Name, result.Type, C, FuncName);
        C.valueTable.addValue(Constant);
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
        RawValue result = getSingleResult(N);
        String FuncName = getStringValue(N, 2);
        SILConstant Constant = new SILConstant(result.Name, result.Type, C, FuncName);
        C.valueTable.addValue(Constant);
        return null;
    }

    @Override
    protected CAstNode visitApply(CAstNode N, SILInstructionContext C) {
        RawValue result = getSingleResult(N);
        String FuncRefName = getStringValue(N, 2);
        CAstNode Source;
        CAstNode FuncNode = N.getChild(3);
        switch (FuncNode.getKind()) {
            case CAstNode.UNARY_EXPR: {
                CAstNode Oper = C.valueTable.getValue(getStringValue(FuncNode, 1)).getVarNode();
                Source = Ast.makeNode(CAstNode.UNARY_EXPR, FuncNode.getChild(0), Oper);
                C.valueTable.addValue(new SILValue(result.Name, result.Type, C));
                break;
            }
            case CAstNode.BINARY_EXPR: {
                CAstNode Oper1 = C.valueTable.getValue(getStringValue(FuncNode, 1)).getVarNode();
                CAstNode Oper2 = C.valueTable.getValue(getStringValue(FuncNode, 2)).getVarNode();
                Source = Ast.makeNode(CAstNode.BINARY_EXPR, FuncNode.getChild(0), Oper1, Oper2);
                C.valueTable.addValue(new SILValue(result.Name, result.Type, C));
                break;
            }
            default: {
                String CalleeName = (String) FuncNode.getChild(0).getValue();
                SILValue FuncRef = C.valueTable.getValue(FuncRefName);
                if (FuncRef instanceof SILConstant) {
                    Source = ((SILConstant) FuncRef).getCAst();
                    C.valueTable.addValue(new SILValue(result.Name, result.Type, C));
                } else if (FuncRef instanceof SILFunctionRef) {
                    ArrayList<CAstNode> Params = new ArrayList<>();
                    Params.add(((SILFunctionRef) FuncRef).getFunctionRef());
                    Params.add(Ast.makeConstant("do"));
                    for (CAstNode RawParam : FuncNode.getChildren().subList(1, FuncNode.getChildren().size())) {
                        Params.add(C.valueTable.getValue((String)RawParam.getValue()).getVarNode());
                    }
                    Source = Ast.makeNode(CAstNode.CALL, Params);
                    C.parent.setGotoTarget(Source, Source);
                    C.valueTable.addValue(new SILValue(result.Name, result.Type, C));
                } else if (FuncRef instanceof SILFunctionRef.SILSummarizedFunctionRef) {
                    ArrayList<CAstNode> Params = new ArrayList<>();
                    Params.addAll(FuncNode.getChildren().subList(1, FuncNode.getChildren().size()));
                    C.valueTable.addValue(new SILValue(result.Name, result.Type, C));
                    Source = BuiltInFunctionSummaries.findSummary(
                            ((SILFunctionRef.SILSummarizedFunctionRef)FuncRef).getFunctionName(),
                            result.Name, result.Type, C, Params);
                    if (Source == null || Source.getKind() == CAstNode.EMPTY) {
                        return Ast.makeNode(CAstNode.EMPTY);
                    } else if (Source.getKind() == CAstNode.VAR) {
                        C.valueTable.addValue(new SILValue(result.Name, result.Type, C));
                        return Ast.makeNode(CAstNode.EMPTY);
                    }
                } else {
                    Source = Ast.makeConstant("UNKNOWN");
                    C.valueTable.addValue(new SILValue(result.Name, result.Type, C));
                }
                break;
            }
        }
        return Ast.makeNode(CAstNode.ASSIGN, C.valueTable.getValue(result.Name).getVarNode(), Source);
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
        RawValue result = getSingleResult(N);
        SILConstant Value = new SILConstant(result.Name, result.Type, C, result.Type);
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
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        SILValue ResultValue = new SILValue(result.Name, result.Type, C);
        C.valueTable.addValue(ResultValue);
        return C.valueTable.getValue(operand.Name).assignTo(ResultValue);
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
        RawValue operand = getSingleOperand(N);
        C.valueTable.removeValue(operand.Name);
        return null;
    }

    @Override
    protected CAstNode visitAutoreleaseValue(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitTuple(CAstNode N, SILInstructionContext C) {
        RawValue result = getSingleResult(N);
        ArrayList<CAstNode> NodeFields = new ArrayList<>();
        NodeFields.add(Ast.makeNode(CAstNode.NEW, Ast.makeConstant(result.Type)));
        ArrayList<String> FieldTypes = new ArrayList<>();
        int index = 0;
        for (CAstNode field : N.getChild(2).getChildren()) {
            String FieldValue = getStringValue(field, 0);
            String FieldType = getStringValue(field, 1);
            NodeFields.add(Ast.makeConstant(index));
            NodeFields.add(C.valueTable.getValue(FieldValue).getVarNode());
            FieldTypes.add(FieldType);
            ++index;
        }
        SILTuple ResultTuple = new SILTuple(result.Name, result.Type, C, FieldTypes);
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
        RawValue result1 = getResult(N, 0);
        RawValue result2 = getResult(N, 1);
        RawValue operand = getSingleOperand(N);
        SILValue OperandValue = C.valueTable.getValue(operand.Name);
        if (OperandValue instanceof SILTuple) {
            SILField element1 = new SILField(result1.Name, result1.Type, C, OperandValue, "0");
            SILField element2 = new SILField(result2.Name, result2.Type, C, OperandValue, "1");
            C.valueTable.addValue(element1);
            C.valueTable.addValue(element2);
        } else if (OperandValue instanceof SILTuple.SILUnitArrayTuple) {
            SILValue ArrayValue = new SILValue(result1.Name, result1.Type, C);
            SILPointer PointerValue = new SILPointer(result2.Name, result2.Type, C, ArrayValue);
            C.valueTable.addValue(ArrayValue);
            C.valueTable.addValue(PointerValue);
        } else {
            Assertions.UNREACHABLE("Operation undefined for non-tuple types");
        }
        return null;
    }

    @Override
    protected CAstNode visitStruct(CAstNode N, SILInstructionContext C) {
        RawValue result = getSingleResult(N);
        ArrayList<CAstNode> NodeFields = new ArrayList<>();
        NodeFields.add(Ast.makeNode(CAstNode.NEW, Ast.makeConstant(result.Type)));
        ArrayList<Pair<String, String>> Fields = new ArrayList<>();
        for (CAstNode field : N.getChild(2).getChildren()) {
            String FieldName = getStringValue(field, 0);
            String FieldValue = getStringValue(field, 1);
            NodeFields.add(Ast.makeConstant(FieldName));
            NodeFields.add(C.valueTable.getValue(FieldValue).getVarNode());
            Fields.add(Pair.make(FieldName, FieldValue));
        }
        SILStruct ResultStruct = new SILStruct(result.Name, result.Type, C, Fields);
        C.valueTable.addValue(ResultStruct);
        CAstNode ObjLiteral = Ast.makeNode(CAstNode.OBJECT_LITERAL, NodeFields);
        C.parent.setGotoTarget(ObjLiteral, ObjLiteral);
        return null;
    }

    @Override
    protected CAstNode visitStructExtract(CAstNode N, SILInstructionContext C) {
        RawValue result = getSingleResult(N);
        String StructName = getStringValue(N, 0);
        String FieldName = getStringValue(N, 1);
        SILValue StructValue = C.valueTable.getValue(StructName);
        if (StructValue instanceof SILStruct) {
            SILValue FieldValue = ((SILStruct)StructValue).createField(result.Name, result.Type, FieldName);
            C.valueTable.addValue(FieldValue);
            return null;
        } else {
            SILValue FieldValue = new SILValue(result.Name, result.Type, C);
            C.valueTable.addValue(FieldValue);
            return Ast.makeNode(CAstNode.ASSIGN,
                    FieldValue.getVarNode(),
                    C.valueTable.getValue(StructName).createObjectRef(FieldName));
        }
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
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        String FieldName = getStringValue(N, 2);
        SILValue ResultValue = new SILValue(result.Name, result.Type, C);
        C.valueTable.addValue(ResultValue);
        CAstNode FieldRef =
                Ast.makeNode(CAstNode.OBJECT_REF,
                    C.valueTable.getValue(operand.Name).getVarNode(),
                    Ast.makeConstant(FieldName));
        C.parent.setGotoTarget(FieldRef, FieldRef);
        return Ast.makeNode(CAstNode.ASSIGN,
                ResultValue.getVarNode(), FieldRef);
    }

    @Override
    protected CAstNode visitRefTailAddr(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitEnum(CAstNode N, SILInstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        String EnumName = getStringValue(N, 2);
        String CaseName = getStringValue(N, 3);
        CAstNode FieldNode;
        if (C.valueTable.hasValue(operand.Name)) {
            SILValue OperandValue = C.valueTable.getValue(operand.Name);
            FieldNode = OperandValue.getVarNode();
        } else {
            FieldNode = Ast.makeConstant(CaseName);
        }
        ArrayList<CAstNode> Fields = new ArrayList<>();
        Fields.add(Ast.makeNode(CAstNode.NEW, Ast.makeConstant(result.Type)));
        Fields.add(Ast.makeConstant("value"));
        Fields.add(FieldNode);
        SILValue ResultValue = new SILValue(result.Name, result.Type, C);
        C.valueTable.addValue(ResultValue);
        CAstNode EnumLiteral = Ast.makeNode(CAstNode.OBJECT_LITERAL, Fields);
        C.parent.setGotoTarget(EnumLiteral, EnumLiteral);
        return Ast.makeNode(CAstNode.ASSIGN, ResultValue.getVarNode(), EnumLiteral);
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
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        Assertions.productionAssertion(C.valueTable.getValue(operand.Name) instanceof SILPointer);
        SILPointer OperandPointer = (SILPointer)C.valueTable.getValue(operand.Name);
        SILValue ValueReferenced = OperandPointer.dereference();
        SILPointer ResultPointer = new SILPointer(result.Name, result.Type, C, ValueReferenced);
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
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        SILPointer ResultPointer = new SILPointer(result.Name, result.Type, C, C.valueTable.getValue(operand.Name));
        C.valueTable.addValue(ResultPointer);
        return null;
    }

    @Override
    protected CAstNode visitUncheckedRefCast(CAstNode N, SILInstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        SILValue ToCastValue = C.valueTable.getValue(operand.Name);
        SILPointer ResultValue = ToCastValue.makePointer(result.Name, result.Type);
        C.valueTable.addValue(ResultValue);
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
    protected CAstNode visitUncheckedOwnershipConversion(CAstNode N, SILInstructionContext C) {
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        SILValue ToCastValue = C.valueTable.getValue(operand.Name);
        SILPointer ResultValue = ToCastValue.makePointer(result.Name, result.Type);
        C.valueTable.addValue(ResultValue);
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
        RawValue operand = getSingleOperand(N);
        RawValue result = getSingleResult(N);
        SILValue OperandValue = C.valueTable.getValue(operand.Name);
        Assertions.productionAssertion(OperandValue instanceof SILFunctionRef);
        SILValue ResultValue = ((SILFunctionRef)OperandValue).copyFuncRef(result.Name, result.Type);
        C.valueTable.addValue(ResultValue);
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
        RawValue operand = getSingleOperand(N);
        if (!C.valueTable.hasValue(operand.Name)) {
            SILValue VarValue = new SILValue(operand.Name, operand.Type, C);
            C.valueTable.addValue(VarValue);
        }
        return Ast.makeNode(CAstNode.RETURN, C.valueTable.getValue(operand.Name).getVarNode());
    }

    @Override
    protected CAstNode visitThrow(CAstNode N, SILInstructionContext C) {
        return null;
    }

    @Override
    protected CAstNode visitYield(CAstNode N, SILInstructionContext C) {
        String ResumeLabel = getStringValue(N, 0);
        String UnwindLabel = getStringValue(N, 1);
        ArrayList<CAstNode> YieldValues = new ArrayList<>();
        for (CAstNode value : N.getChild(2).getChildren()) {
            YieldValues.add(C.valueTable.getValue((String)value.getValue()).getVarNode());
        }
        YieldValues.add(Ast.makeConstant(ResumeLabel));
        YieldValues.add(Ast.makeConstant(UnwindLabel));
        return Ast.makeNode(CAstNode.YIELD_STMT, YieldValues);
    }

    @Override
    protected CAstNode visitUnwind(CAstNode N, SILInstructionContext C) {
        return Ast.makeNode(CAstNode.UNWIND);
    }

    @Override
    protected CAstNode visitBr(CAstNode N, SILInstructionContext C) {
        String DestBranch = getStringValue(N, 0);
        for (CAstNode arg : N.getChild(1).getChildren()) {
            String OperandName = getStringValue(arg, 0);
            String DestArgName = getStringValue(arg, 1);
            String DestArgType = getStringValue(arg, 3);
            SILValue OperandValue = C.valueTable.getValue(OperandName);
            CAstNode ResultAssign = OperandValue.copy(DestArgName, DestArgType);
            if (ResultAssign != null) {
                C.instructions.add(ResultAssign);
            }
        }
        CAstNode GotoNode = Ast.makeNode(CAstNode.GOTO, Ast.makeConstant(DestBranch));
        tryGOTO(GotoNode, DestBranch, C);
        return GotoNode;
    }

    @Override
    protected CAstNode visitCondBr(CAstNode N, SILInstructionContext C) {
        String CondOperandName = getStringValue(N, 0);
        String TrueDestName = getStringValue(N, 1);
        String FalseDestName = getStringValue(N, 2);
        for (CAstNode arg : N.getChild(1).getChildren()) {
            String OperandName = getStringValue(arg, 0);
            String DestArgName = getStringValue(arg, 1);
            String DestArgType = getStringValue(arg, 2);
            SILValue OperandValue = C.valueTable.getValue(OperandName);
            CAstNode ResultAssign = OperandValue.copy(DestArgName, DestArgType);
            if (ResultAssign != null) {
                C.instructions.add(ResultAssign);
            }
        }
        CAstNode TrueGotoNode = Ast.makeNode(CAstNode.GOTO, Ast.makeConstant(TrueDestName));
        tryGOTO(TrueGotoNode, TrueDestName, C);
        for (CAstNode arg : N.getChild(2).getChildren()) {
            String OperandName = getStringValue(arg, 0);
            String DestArgName = getStringValue(arg, 1);
            String DestArgType = getStringValue(arg, 2);
            SILValue OperandValue = C.valueTable.getValue(OperandName);
            CAstNode ResultAssign = OperandValue.copy(DestArgName, DestArgType);
            if (ResultAssign != null) {
                C.instructions.add(ResultAssign);
            }
        }
        CAstNode FalseGotoNode = Ast.makeNode(CAstNode.GOTO, Ast.makeConstant(FalseDestName));
        tryGOTO(FalseGotoNode, FalseDestName, C);
        CAstNode IfStmt = Ast.makeNode(CAstNode.IF_STMT,
                Ast.makeNode(CAstNode.BINARY_EXPR, CAstOperator.OP_EQ, C.valueTable.getValue(CondOperandName).getVarNode(), Ast.makeConstant("1")),
                TrueGotoNode, FalseGotoNode);
        return IfStmt;
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
        String EnumName = (String)N.getChild(0).getValue();
        SILValue EnumValue = C.valueTable.getValue(EnumName);
        ArrayList<CAstNode> Fields = new ArrayList<>();
        CAstNode DefaultNode = CAstControlFlowRecorder.EXCEPTION_TO_EXIT;
        ArrayList<Pair<CAstNode, CAstNode>> labels = new ArrayList<>();
        for (CAstNode Case : N.getChild(1).getChildren()) {
            ArrayList<CAstNode> StmtNodes = new ArrayList<>();
            String CaseName = (String)Case.getChild(0).getValue();
            String DestBB = (String)Case.getChild(1).getValue();
            if (N.getChildren().size() > 2) {
                String ArgName = (String)N.getChild(2).getValue();
                String ArgType = (String)N.getChild(3).getValue();
                CAstNode Assign = EnumValue.copy(ArgName, ArgType);
                if (Assign != null) {
                    C.instructions.add(Assign);
                }
            }
            CAstNode LabelStmt = Ast.makeNode(CAstNode.LABEL_STMT, Ast.makeConstant(CaseName));
            CAstNode GotoNode = Ast.makeNode(CAstNode.GOTO, Ast.makeConstant(DestBB));
            tryGOTO(GotoNode, DestBB, C);
            StmtNodes.add(LabelStmt);
            StmtNodes.add(GotoNode);
            CAstNode BlockStmt = Ast.makeNode(CAstNode.BLOCK_STMT, StmtNodes);
            labels.add(Pair.make(BlockStmt, LabelStmt));
            Fields.add(BlockStmt);
        }
        if (N.getChildren().size() > 2) {
            ArrayList<CAstNode> StmtNodes = new ArrayList<>();
            CAstNode DefaultInfo = N.getChild(2);
            String DestBB = (String)DefaultInfo.getChild(0).getValue();
            if (DefaultInfo.getChildren().size() > 1) {
                String ArgName = (String)DefaultInfo.getChild(1).getValue();
                String ArgType = (String)DefaultInfo.getChild(2).getValue();
                CAstNode Assign = EnumValue.copy(ArgName, ArgType);
                if (Assign != null) {
                    C.instructions.add(Assign);
                }
            }
            CAstNode LabelStmt = Ast.makeNode(CAstNode.LABEL_STMT, Ast.makeConstant("default"));
            CAstNode GotoNode = Ast.makeNode(CAstNode.GOTO, Ast.makeConstant(DestBB));
            tryGOTO(GotoNode, DestBB, C);
            StmtNodes.add(LabelStmt);
            StmtNodes.add(GotoNode);
            CAstNode BlockStmt = Ast.makeNode(CAstNode.BLOCK_STMT, StmtNodes);
            DefaultNode = BlockStmt;
            Fields.add(BlockStmt);
        }
        CAstNode Switch = Ast.makeNode(CAstNode.SWITCH,
                EnumValue.getVarNode(),
                Ast.makeNode(CAstNode.BLOCK_STMT, Fields));
        //C.parent.setGotoTarget(Switch, Switch);
        C.parent.setLabelledGotoTarget(Switch, DefaultNode, CAstControlFlowRecorder.SWITCH_DEFAULT);
        for (Pair<CAstNode, CAstNode> l : labels) {
            C.parent.setLabelledGotoTarget(Switch, l.fst, l.snd.getChild(0));
        }
        System.out.println("*********");
        System.out.println(Switch);
        System.out.println("*********");
        return Switch;
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
        String FuncRefName = (String)N.getChild(0).getValue();
        CAstNode Source;
        CAstNode FuncNode = N.getChild(1);
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
                } else if (FuncRef instanceof SILFunctionRef) {
                    ArrayList<CAstNode> Params = new ArrayList<>();
                    Params.add(((SILFunctionRef) FuncRef).getFunctionRef());
                    Params.add(Ast.makeConstant("do"));
                    for (CAstNode RawParam : FuncNode.getChildren().subList(1, FuncNode.getChildren().size())) {
                        Params.add(C.valueTable.getValue((String)RawParam.getValue()).getVarNode());
                    }
                    Source = Ast.makeNode(CAstNode.CALL, Params);
                    C.parent.setGotoTarget(Source, Source);
                } else if (FuncRef instanceof SILFunctionRef.SILSummarizedFunctionRef) {
                    ArrayList<CAstNode> Params = new ArrayList<>();
                    Params.addAll(FuncNode.getChildren().subList(1, FuncNode.getChildren().size()));
                    Source = BuiltInFunctionSummaries.findSummary(
                            ((SILFunctionRef.SILSummarizedFunctionRef)FuncRef).getFunctionName(),
                            null, null, C, Params);
                    if (Source == null || Source.getKind() == CAstNode.EMPTY) {
                        return Ast.makeNode(CAstNode.EMPTY);
                    } else if (Source.getKind() == CAstNode.VAR) {
                        return Ast.makeNode(CAstNode.EMPTY);
                    }
                } else {
                    Source = Ast.makeConstant("UNKNOWN");
                }
                break;
            }
        }
        return Source;
    }
}