//===--- FunctionEntity.java ---------------------------------------------===//
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

import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.tree.*;
import com.ibm.wala.cast.tree.impl.CAstSourcePositionRecorder;

import java.util.ArrayList;
import java.util.Collection;

public class FunctionEntity extends AbstractCodeEntity {

    // WORK IN PROGRESS

    String functionName;
    private String[] argumentNames;
    CAstSourcePositionRecorder sourcePositionRecorder;

    public FunctionEntity(String name, String returnType,
                          ArrayList<String> argumentTypes,
                          ArrayList<String> argumentNames, CAstSourcePositionRecorder sourcePositionRecorder) {
        super(new SwiftFunctionType(returnType, argumentTypes));
        this.functionName = name;
        this.argumentNames = argumentNames.toArray(new String[0]);
        this.sourcePositionRecorder = sourcePositionRecorder;
    }

    @Override
    public int getKind() {
        return CAstEntity.FUNCTION_ENTITY;
    }

    @Override
    public String getName() {
        return this.functionName;
    }

    @Override
    public String[] getArgumentNames() {
        return this.argumentNames;
    }

    @Override
    public CAstNode[] getArgumentDefaults() {
        return new CAstNode[0]; // TODO?
    }

    @Override
    public int getArgumentCount() {
        return 0; // TODO?
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
    public Collection<CAstQualifier> getQualifiers() {
        return null;
    }

    @Override
    public String toString() {
        return "function " + this.functionName;
    }

    @Override
    public CAstSourcePositionRecorder getSourceMap() {
        return this.sourcePositionRecorder;
    }
}
