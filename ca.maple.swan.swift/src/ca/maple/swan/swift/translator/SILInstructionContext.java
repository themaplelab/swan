//===--- SILInstructionContext.java --------------------------------------===//
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

import ca.maple.swan.swift.translator.values.SILValueTable;
import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.tree.CAstNode;

import java.util.ArrayList;

/*
 * This class contains any context needed for any node to be translated.
 */
public class SILInstructionContext {
    public final AbstractCodeEntity parent;
    public final ArrayList<AbstractCodeEntity> allEntities;
    public final SILValueTable valueTable;
    public ArrayList<CAstNode> instructions;

    public SILInstructionContext(AbstractCodeEntity parent, ArrayList<AbstractCodeEntity> allEntities) {
        this.parent = parent;
        this.allEntities = allEntities;
        valueTable =  new SILValueTable();
    }
}
