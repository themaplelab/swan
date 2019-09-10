//===--- SILTuple.java ---------------------------------------------------===//
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
import ca.maple.swan.swift.translator.types.SILTypes;
import com.ibm.wala.cast.tree.CAstNode;

import java.util.ArrayList;

import static com.ibm.wala.cast.tree.CAstNode.OBJECT_REF;

/*
 * Same as SILStruct but field names are (obviously) numbers.
 * Holds 2 values.
 */

public class SILTuple extends SILValue {

    private final ArrayList<SILType> fieldTypes;

    public SILTuple(String name, String type, SILInstructionContext C, ArrayList<String> types) {
        super(name, type, C);
        fieldTypes = new ArrayList<>();
        for (String s : types) {
            fieldTypes.add(SILTypes.getType(s));
        }
    }

    public CAstNode createObjectRef(int index) {
        CAstNode ref = Ast.makeNode(OBJECT_REF, getVarNode(), Ast.makeConstant(index));
        C.parent.setGotoTarget(ref, ref);
        return ref;
    }

    public SILField createField(String name, int index) {
        return new SILField(name, fieldTypes.get(index).getName(), C, this, index);
    }

    public int getNoFields() {
        return fieldTypes.size();
    }

    public static class SILUnitArrayTuple extends SILValue {
        public SILUnitArrayTuple(String name, String type, SILInstructionContext C) {
            super(name, type, C);
        }
    }
}
