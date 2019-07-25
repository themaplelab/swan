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

/*
 * This class represents a translated SIL function.
 */
public class FunctionEntity extends AbstractCodeEntity {

    String functionName;
    private final String[] arguments;
    CAstSourcePositionRecorder sourcePositionRecorder;

    // TODO
    // private final CAstSourcePositionMap.Position namePosition;
    // private final CAstSourcePositionMap.Position[] paramPositions;

    public FunctionEntity(String name, String returnType,
                          ArrayList<String> argumentTypes,
                          ArrayList<String> argumentNames, CAstSourcePositionRecorder sourcePositionRecorder) {
        super(new SwiftFunctionType(returnType, argumentTypes));
        this.functionName = name;
        this.arguments = argumentNames.toArray(new String[0]);
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
        return this.arguments;
    }

    @Override
    public CAstNode[] getArgumentDefaults() {
        return new CAstNode[0];
    }

    @Override
    public int getArgumentCount() {
        return this.arguments.length;
    }

    @Override
    public CAstSourcePositionMap.Position getNamePosition() {
        // TODO return namePosition;
        return null;
    }

    @Override
    public CAstSourcePositionMap.Position getPosition() {
        return this.sourcePositionRecorder.getPosition(this.getAST());
    }

    @Override
    public CAstSourcePositionMap.Position getPosition(int arg) {
        // TODO return paramPositions[arg];
        return null;
    }


    @Override
    public Collection<CAstQualifier> getQualifiers() {
        return null;
    }

    @Override
    public String toString() {
        return "<Swift function " + getName() + ">";
    }

    @Override
    public CAstSourcePositionRecorder getSourceMap() {
        return this.sourcePositionRecorder;
    }

}
