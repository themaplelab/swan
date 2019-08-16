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
import com.ibm.wala.cast.tree.impl.CAstNodeTypeMapRecorder;
import com.ibm.wala.cast.tree.impl.CAstSourcePositionRecorder;

import java.io.File;
import java.util.Collection;

/*
 * This class represents the translated "main" SIL function.
 */
public class ScriptEntity extends AbstractScriptEntity {

    // TODO: Is it necessary to implement getNamePosition(), getPosition(int[] arg), getPosition()?

    CAstSourcePositionRecorder sourcePositionRecorder;
    String scriptName;

    public ScriptEntity(String scriptName, File file) {
        super(file, new SwiftScriptType());
        this.sourcePositionRecorder = new CAstSourcePositionRecorder();
        this.scriptName = scriptName;
    }

    @Override
    public int getKind() {
        return CAstEntity.SCRIPT_ENTITY;
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

    @Override
    public String toString() {
        return "<Swift script " + getName() + ">";
    }
}
