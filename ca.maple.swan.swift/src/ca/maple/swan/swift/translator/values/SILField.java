//===--- SILField.java ---------------------------------------------------===//
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
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.util.debug.Assertions;

public class SILField extends SILValue {

    private final SILValue object;
    private final Object fieldName;

    public SILField(String name, String type, SILInstructionContext C, SILValue object, Object fieldName) {
        super(name, type, C);
        this.object = object;
        this.fieldName = fieldName;
    }

    public SILValue getObject() {
        return object;
    }

    public Object getFieldName() {
        return fieldName;
    }

    @Override
    public CAstNode getVarNode() {
        if (object instanceof SILTuple) {
            return ((SILTuple) object).createObjectRef(Integer.parseInt((String)fieldName));
        } else if (object instanceof SILStruct) {
            return ((SILStruct) object).createObjectRef((String)fieldName);
        } else {
            Assertions.UNREACHABLE("Field must be from Struct or Tuple");
        }
        return null;
    }
}
