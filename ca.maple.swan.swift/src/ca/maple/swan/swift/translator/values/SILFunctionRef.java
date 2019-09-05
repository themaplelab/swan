//===--- SILFunctionRef.java ---------------------------------------------===//
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
import ca.maple.swan.swift.translator.SILInstructionContext;
import com.ibm.wala.cast.tree.CAstNode;

public class SILFunctionRef extends SILValue {

    private final String functionName;
    private final CAstNode node;

    public SILFunctionRef(String name, String type,  SILInstructionContext C, String functionName) {
        super(name, type, C);
        this.functionName = functionName;
        node = Ast.makeNode(CAstNode.FUNCTION_EXPR,
                Ast.makeConstant(RawAstTranslator.findEntity(functionName, C.allEntities)));
    }

    public SILFunctionRef copyFuncRef(String name, String type) {
        return new SILFunctionRef(name, type, C, functionName);
    }

    public String getFunctionName() {
        return functionName;
    }

    public CAstNode getFunctionRef() {
        return node;
    }

    @Override
    public CAstNode getVarNode() {
        return node;
    }

    public static class SILSummarizedFunctionRef extends SILValue {
        private final String functionName;
        private final CAstNode node;

        public SILSummarizedFunctionRef(String name, String type,  SILInstructionContext C, String functionName) {
            super(name, type, C);
            this.functionName = functionName;
            node = Ast.makeNode(CAstNode.FUNCTION_EXPR,
                    Ast.makeConstant(functionName));
        }

        public String getFunctionName() {
            return functionName;
        }

        public CAstNode getFunctionRef() {
            return node;
        }

        @Override
        public CAstNode getVarNode() {
            return node;
        }
    }
}
