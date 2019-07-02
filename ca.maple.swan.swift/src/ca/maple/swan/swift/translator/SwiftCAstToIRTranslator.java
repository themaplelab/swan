//===--- SwiftCAstToIRTranslator.java ------------------------------------===//
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

import ca.maple.swan.swift.loader.SwiftLoader;
import ca.maple.swan.swift.ssa.SwiftInvokeInstruction;
import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.ir.ssa.AstInstructionFactory;
import com.ibm.wala.cast.ir.translator.AstTranslator;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.loader.DynamicCallSiteReference;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.tree.CAstType;
import com.ibm.wala.cast.tree.impl.CAstControlFlowRecorder;
import com.ibm.wala.cast.tree.impl.CAstSymbolImpl;
import com.ibm.wala.cfg.AbstractCFG;
import com.ibm.wala.cfg.IBasicBlock;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.strings.Atom;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ca.maple.swan.swift.ir.SwiftLanguage.Swift;

public class SwiftCAstToIRTranslator extends AstTranslator {

    private final Map<CAstType, TypeName> walaTypeNames = HashMapFactory.make();

    public SwiftCAstToIRTranslator(SwiftLoader loader) {
        super(loader);
    }

    @Override
    protected boolean useDefaultInitValues() {
        return false;
    }

    @Override
    protected boolean hasImplicitGlobals() {
        return false;
    }

    @Override
    protected boolean treatGlobalsAsLexicallyScoped() {
        return false;
    }

    @Override
    protected TypeReference defaultCatchType() {
        return null; // TODO
    }

    @Override
    protected TypeReference makeType(CAstType cAstType) {
        return TypeReference.findOrCreate(SwiftTypes.swiftLoader, TypeName.string2TypeName(cAstType.getName()));
    }

    @Override
    protected boolean defineType(CAstEntity type, WalkContext wc) {
        CAstType cls = type.getType();
        scriptScope(wc.currentScope()).declare(new CAstSymbolImpl(cls.getName(), cls));

        String typeNameStr = composeEntityName(wc, type);
        TypeName typeName = TypeName.findOrCreate("L" + typeNameStr);
        walaTypeNames.put(cls, typeName);

        ((SwiftLoader) loader)
                .defineType(
                        typeName,
                        cls.getSupertypes().isEmpty() ?
                                SwiftTypes.Object.getName() :
                                walaTypeNames.get(cls.getSupertypes().iterator().next()), type.getPosition());

        return true;
    }

    private Scope scriptScope(Scope s) {
        if (s.getEntity().getKind() == CAstEntity.SCRIPT_ENTITY) {
            return s;
        } else {
            return scriptScope(s.getParent());
        }
    }

    @Override
    protected void declareFunction(CAstEntity N, WalkContext context) {
        for (String s : N.getArgumentNames()) {
            context.currentScope().declare(new CAstSymbolImpl(s, N.getType()));
        }

        String fnName = composeEntityName(context, N);
        if (N.getType() instanceof CAstType.Method) {
            ((SwiftLoader) loader).defineMethodType("L" + fnName, N.getPosition(), N, walaTypeNames.get(((CAstType.Method)N.getType()).getDeclaringType()), context);
        } else {
            ((SwiftLoader) loader).defineFunctionType("L" + fnName, N.getPosition(), N, context);
        }
    }

    @Override
    protected void defineFunction(CAstEntity N, WalkContext definingContext,
                                  AbstractCFG<SSAInstruction, ? extends IBasicBlock<SSAInstruction>> cfg, SymbolTable symtab,
                                  boolean hasCatchBlock, Map<IBasicBlock<SSAInstruction>, TypeReference[]> catchTypes, boolean hasMonitorOp,
                                  AstLexicalInformation lexicalInfo, AstMethod.DebuggingInformation debugInfo) {
        String fnName = composeEntityName(definingContext, N);

        ((SwiftLoader) loader).defineCodeBodyCode("L" + fnName, cfg, symtab, hasCatchBlock, catchTypes, hasMonitorOp, lexicalInfo,
                debugInfo, N.getArgumentDefaults().length);
    }

    @Override
    protected void defineField(CAstEntity topEntity, WalkContext context, CAstEntity fieldEntity) {
        ((SwiftLoader)loader).defineField(walaTypeNames.get(topEntity.getType()), fieldEntity);
    }

    @Override
    protected String composeEntityName(WalkContext parent, CAstEntity f) {
        if (f.getKind() == CAstEntity.SCRIPT_ENTITY)
            return f.getName();
        else {
            String name;
            name = f.getName();
            return parent.getName() + "/" + name;
        }
    }

    @Override
    protected void doThrow(WalkContext context, int exception) {
        context
                .cfg()
                .addInstruction(insts.ThrowInstruction(context.cfg().getCurrentInstruction(), exception));
    }

    @Override
    public void doArrayRead(WalkContext context, int result, int arrayValue, CAstNode arrayRef, int[] dimValues) {
        if (dimValues.length == 1) {
            int currentInstruction = context.cfg().getCurrentInstruction();
            context.cfg().addInstruction(((AstInstructionFactory) insts).PropertyRead(currentInstruction, result, arrayValue, dimValues[0]));
            context.cfg().noteOperands(currentInstruction, context.getSourceMap().getPosition(arrayRef));
        }
    }

    @Override
    public void doArrayWrite(WalkContext context, int arrayValue, CAstNode arrayRef, int[] dimValues, int rval) {
        assert dimValues.length == 1;
        context.cfg().addInstruction(((AstInstructionFactory) insts).PropertyWrite(context.cfg().getCurrentInstruction(), arrayValue, dimValues[0], rval));
    }


    @Override
    protected void doFieldRead(WalkContext context, int result, int receiver, CAstNode elt, CAstNode parent) {
        int currentInstruction = context.cfg().getCurrentInstruction();
        if (elt.getKind() == CAstNode.CONSTANT && elt.getValue() instanceof String) {
            FieldReference f = FieldReference.findOrCreate(SwiftTypes.Root, Atom.findOrCreateUnicodeAtom((String)elt.getValue()), SwiftTypes.Root);
            context.cfg().addInstruction(Swift.instructionFactory().GetInstruction(currentInstruction, result, receiver, f));
        } else {
            visit(elt, context, this);
            assert context.getValue(elt) != -1;
            context.cfg().addInstruction(((AstInstructionFactory) insts).PropertyRead(currentInstruction, result, receiver, context.getValue(elt)));
        }
        context.cfg().noteOperands(currentInstruction, context.getSourceMap().getPosition(parent.getChild(0)), context.getSourceMap().getPosition(elt));
    }

    @Override
    protected void doFieldWrite(WalkContext context, int receiver, CAstNode elt, CAstNode parent, int rval) {
        if (elt.getKind() == CAstNode.CONSTANT && elt.getValue() instanceof String) {
            FieldReference f = FieldReference.findOrCreate(SwiftTypes.Root, Atom.findOrCreateUnicodeAtom((String)elt.getValue()), SwiftTypes.Root);
            context.cfg().addInstruction(Swift.instructionFactory().PutInstruction(context.cfg().getCurrentInstruction(), receiver, rval, f));
        } else {
            visit(elt, context, this);
            assert context.getValue(elt) != -1;
            context.cfg().addInstruction(((AstInstructionFactory) insts).PropertyWrite(context.cfg().getCurrentInstruction(), receiver, context.getValue(elt), rval));
        }
    }

    @Override
    protected void doMaterializeFunction(CAstNode node, WalkContext context, int result, int exception, CAstEntity fn) {
        String fnName = composeEntityName(context, fn);
        IClass cls = loader.lookupClass(TypeName.findOrCreate("L" + fnName));
        TypeReference type = cls.getReference();
        int idx = context.cfg().getCurrentInstruction();
        context.cfg().addInstruction(Swift.instructionFactory().NewInstruction(idx, result, NewSiteReference.make(idx, type)));
        doGlobalWrite(context, fnName, SwiftTypes.Root, result);

        FieldReference fnField = FieldReference.findOrCreate(SwiftTypes.Root, Atom.findOrCreateUnicodeAtom(fn.getName()), SwiftTypes.Root);
        context.cfg().addInstruction(Swift.instructionFactory().PutInstruction(context.cfg().getCurrentInstruction(), 1, result, fnField));
    }

    @Override
    protected void doNewObject(WalkContext context, CAstNode newNode, int result, Object type, int[] arguments) {
        context.cfg().addInstruction(
                insts.NewInstruction(context.cfg().getCurrentInstruction(),
                        result,
                        NewSiteReference.make(
                                context.cfg().getCurrentInstruction(),
                                TypeReference.findOrCreate(
                                        SwiftTypes.swiftLoader,
                                        "L" + type))));
    }

    @Override
    protected void doCall(WalkContext context, CAstNode call, int result, int exception, CAstNode name, int receiver,
                          int[] arguments) {
        int pos = context.cfg().getCurrentInstruction();
        CallSiteReference site = new DynamicCallSiteReference(SwiftTypes.CodeBody, pos);

        List<CAstSourcePositionMap.Position> pospos = new ArrayList<CAstSourcePositionMap.Position>();
        List<CAstSourcePositionMap.Position> keypos = new ArrayList<CAstSourcePositionMap.Position>();
        List<Integer> posp = new ArrayList<Integer>();
        List<Pair<String,Integer>> keyp = new ArrayList<Pair<String,Integer>>();
        posp.add(receiver);
        pospos.add(context.getSourceMap().getPosition(call.getChild(0)));
        for(int i = 2; i < call.getChildCount(); i++) {
            CAstNode cl = call.getChild(i);
            if (cl.getKind() == CAstNode.ARRAY_LITERAL) {
                keyp.add(Pair.make(String.valueOf(cl.getChild(0).getValue()), context.getValue(cl.getChild(1))));
                keypos.add(context.getSourceMap().getPosition(cl));
            } else {
                posp.add(context.getValue(cl));
                pospos.add(context.getSourceMap().getPosition(cl));
            }
        }

        int params[] = new int[ arguments.length+1 ];
        params[0] = receiver;
        System.arraycopy(arguments, 0, params, 1, arguments.length);

        int[] hack = new int[ posp.size() ];
        for(int i = 0; i < hack.length; i++) {
            hack[i] = posp.get(i);
        }

        context.cfg().addInstruction(new SwiftInvokeInstruction(pos, result, exception, site, hack, keyp.toArray(new Pair[ keyp.size() ])));

        pospos.addAll(keypos);
        context.cfg().noteOperands(pos, pospos.toArray(new CAstSourcePositionMap.Position[pospos.size()]));
        context.cfg().addPreNode(call, context.getUnwindState());

        // this new block is for the normal termination case
        context.cfg().newBlock(true);

        // exceptional case: flow to target given in CAst, or if null, the exit node
        ((CAstControlFlowRecorder)context.getControlFlow()).map(call, call);

        if (context.getControlFlow().getTarget(call, null) != null)
            context.cfg().addPreEdge(call, context.getControlFlow().getTarget(call, null), true);
        else context.cfg().addPreEdgeToExit(call, true);
    }

    @Override
    protected CAstType topType() {
        return null;
    }

    @Override
    protected CAstType exceptionType() {
        return null;
    }

    @Override
    protected void doPrimitive(int resultVal, WalkContext context, CAstNode primitiveCall) {

    }

    @Override
    protected CAstSourcePositionMap.Position[] getParameterPositions(CAstEntity e) {
        CAstSourcePositionMap.Position[] ps = new CAstSourcePositionMap.Position[ e.getArgumentCount() ];
        for(int i = 1; i < e.getArgumentCount(); i++) {
            ps[i] = e.getPosition(i-1);
        }
        return ps;
    }
}
