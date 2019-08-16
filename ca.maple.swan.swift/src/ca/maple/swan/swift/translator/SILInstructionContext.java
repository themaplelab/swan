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

import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstNode;

import java.util.ArrayList;
import java.util.HashMap;

/*
 * This class contains any context needed for any node to be translated.
 */
public class SILInstructionContext {
    public final CAstEntity parent;
    public final ArrayList<AbstractCodeEntity> allEntities;
    public final HashMap<String, ArrayList<CAstNode>> danglingGOTOs;
    public final ArrayList<CAstNode> currentBlockAST;
    public final HashMap<String, SILValue> values;

    public SILInstructionContext(CAstEntity parent, ArrayList<AbstractCodeEntity> allEntities,
                                 HashMap<String, ArrayList<CAstNode>> danglingGOTOs,
                                 ArrayList<CAstNode> currentBlockAST, HashMap<String, SILValue> values) {
        this.parent = parent;
        this.allEntities = allEntities;
        this.danglingGOTOs = danglingGOTOs;
        this.currentBlockAST = currentBlockAST;
        this.values = values;
    }
}
