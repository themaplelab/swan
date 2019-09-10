//===--- SILStruct.java --------------------------------------------------===//
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

import ca.maple.swan.swift.translator.SILInstructionContext;
import ca.maple.swan.swift.translator.types.SILType;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.util.collections.Pair;

import java.util.ArrayList;

import static com.ibm.wala.cast.tree.CAstNode.OBJECT_LITERAL;
import static com.ibm.wala.cast.tree.CAstNode.OBJECT_REF;

/*
 * Used to represent structs, but can really represent any object with
 * named fields. Can generate SILFields.
 */

public class SILStruct extends SILValue {

    private final ArrayList<Pair<String, SILType>> fields;
    private final CAstNode LiteralNode;

    public SILStruct(String name, String type, SILInstructionContext C, ArrayList<Pair<String, String>> givenFields) {
        super(name, type, C);
        fields = new ArrayList<>();
        ArrayList<CAstNode> LiteralFields = new ArrayList<>();
        LiteralFields.add(Ast.makeNode(CAstNode.NEW, Ast.makeConstant(type)));
        for (Pair<String, String> field : givenFields) {
            this.fields.add(Pair.make(field.fst, C.valueTable.getValue(field.snd).getType()));
            LiteralFields.add(Ast.makeConstant(field.fst));
            LiteralFields.add(C.valueTable.getValue(field.snd).getVarNode());
        }
        LiteralNode = Ast.makeNode(OBJECT_LITERAL, LiteralFields);
        C.parent.setGotoTarget(LiteralNode, LiteralNode);
    }

    public CAstNode getLiteral() {
        return LiteralNode;
    }

    public int getNumFields() {
        return fields.size();
    }

    @Override
    public CAstNode getVarNode() {
        return getLiteral();
    }

    @Override
    public CAstNode createObjectRef(String fieldName) {
        try {
            for (Pair<String, SILType> p : fields) {
                if (p.fst.equals(fieldName)) {
                    CAstNode objectRef = Ast.makeNode(OBJECT_REF,
                            getVarNode(),
                            Ast.makeConstant(fieldName));
                    C.parent.getNodeTypeMap().add(objectRef, p.snd);
                    C.parent.setGotoTarget(objectRef, objectRef);
                    return objectRef;
                }
            }
            throw new Exception("fieldName not valid");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public SILField createField(String name, String type, String fieldName) {
        return new SILField(name, type, C, this, fieldName);
    }

    public SILField createField(String name, String type, int fieldNo) {
        return new SILField(name, type, C, this, fields.get(fieldNo).fst);
    }
}
