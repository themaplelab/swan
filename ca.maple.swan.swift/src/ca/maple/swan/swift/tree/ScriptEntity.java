//===--- ScriptEntity.java -----------------------------------------------===//
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

package ca.maple.swan.swift.tree;

import com.ibm.wala.cast.ir.translator.AbstractScriptEntity;
import com.ibm.wala.cast.tree.*;
import com.ibm.wala.cast.tree.impl.CAstSourcePositionRecorder;

import java.io.File;

/*
 * This class represents the translated "main" SIL function.
 */
public class ScriptEntity extends AbstractScriptEntity {

    CAstSourcePositionRecorder sourcePositionRecorder;
    String scriptName;

    public ScriptEntity(String scriptName, File file, CAstSourcePositionRecorder cAstSourcePositionRecorder) {
        super(file, new SwiftScriptType());
        this.sourcePositionRecorder = cAstSourcePositionRecorder;
        this.scriptName = scriptName;
    }

    @Override
    public CAstSourcePositionMap.Position getNamePosition() {
        return null;
    }

    @Override
    public CAstSourcePositionMap.Position getPosition(int i) {
        return null;
    }

    @Override
    public String getName() {
        return scriptName;
    }

    @Override
    public CAstSourcePositionRecorder getSourceMap() {
        return this.sourcePositionRecorder;
    }
}
