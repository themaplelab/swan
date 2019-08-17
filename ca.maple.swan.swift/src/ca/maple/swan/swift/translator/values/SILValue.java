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
import ca.maple.swan.swift.translator.types.SILType;
import ca.maple.swan.swift.translator.types.SILTypes;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.cast.tree.impl.CAstNodeTypeMapRecorder;

public class SILValue {

    protected final String name;
    protected final SILType type;
    private final CAstNode varNode;

    protected static final CAstImpl Ast = RawAstTranslator.Ast;

    public SILValue(String name, String type, CAstNodeTypeMapRecorder typeRecorder) {
        this(name, SILTypes.getType(type), typeRecorder);
    }

    public SILValue(String name, SILType type, CAstNodeTypeMapRecorder typeRecorder) {
        this.name = name;
        this.type = type;
        this.varNode = Ast.makeNode(CAstNode.VAR, Ast.makeConstant(this.name));
        typeRecorder.add(varNode, this.type);
    }

    public CAstNode assignTo(SILValue to) {
        return Ast.makeNode(CAstNode.ASSIGN, Ast.makeConstant(to.name), Ast.makeConstant(this.name));
    }

    public CAstNode getVarNode() {
        return varNode;
    }

    public SILPointer makePointer(String name, String type, CAstNodeTypeMapRecorder typeRecorder) {
        return new SILPointer(name, type, typeRecorder, this);
    }

}
