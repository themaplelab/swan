//===--- SILValue.java ---------------------------------------------------===//
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

package ca.maple.swan.swift.translator.values;

import ca.maple.swan.swift.translator.RawAstTranslator;
import ca.maple.swan.swift.translator.SILInstructionContext;
import ca.maple.swan.swift.translator.types.SILType;
import ca.maple.swan.swift.translator.types.SILTypes;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.cast.tree.impl.CAstSymbolImpl;

public class SILValue {

    protected final String name;
    protected SILType type;
    private final CAstNode varNode;
    protected final SILInstructionContext C;

    protected static final CAstImpl Ast = RawAstTranslator.Ast;

    public SILValue(String name, String type, SILInstructionContext C) {
        this(name, SILTypes.getType(type), C);
    }

    public SILValue(String name, SILType type, SILInstructionContext C) {
        this.name = name;
        this.type = type;
        this.varNode = Ast.makeNode(CAstNode.VAR, Ast.makeConstant(this.name));
        this.C = C;
        C.parent.getNodeTypeMap().add(varNode, this.type);
    }

    public CAstNode assignTo(SILValue to) {
        return Ast.makeNode(CAstNode.ASSIGN, to.getVarNode(), getVarNode());
    }

    public SILType getType() {
        return this.type;
    }

    public CAstNode getDecl() {
        return Ast.makeNode(CAstNode.DECL_STMT, Ast.makeConstant(new CAstSymbolImpl(this.name, this.type)));
    }

    public CAstNode getVarNode() {
        return varNode;
    }

    public SILPointer makePointer(String name, String type) {
        return new SILPointer(name, type, C, this);
    }

    public void castTo(String type) {
        this.type = SILTypes.getType(type);
    }

    public void castTo(SILType type) {
        this.type = type;
    }

}
