//===--- EntityPrinter.java ----------------------------------------------===//
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
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.CAstType;
import com.ibm.wala.cast.tree.impl.CAstControlFlowRecorder;

import java.util.*;

public class EntityPrinter {
    public static void print(AbstractCodeEntity entity) {
        System.out.println("====================");
        if (entity instanceof ScriptEntity) {
            System.out.println("<SCRIPT_ENTITY>");
            System.out.println("\t<FILE>" + ((ScriptEntity)entity).getFileName() + "</FILE>");
        } else if (entity instanceof FunctionEntity) {
            System.out.println("<FUNCTION_ENTITY>");
        }
        System.out.println("\t<FUNCTION_NAME>" + entity.getName() + "</FUNCTION_NAME>");

        if (entity instanceof FunctionEntity) {
            System.out.println("<RETURN_TYPE>" + ((CAstType.Function) entity.getType()).getReturnType().getName() + "</RETURN_TYPE>");
            System.out.println("<ARGUMENT_COUNT>" + ((CAstType.Function) entity.getType()).getArgumentCount() + "</ARGUMENT_COUNT>");
            System.out.println("<ARGUMENTS>");

            List<CAstType> argumentTypes = ((CAstType.Function) entity.getType()).getArgumentTypes();
            String[] argumentNames = entity.getArgumentNames();
            for (int i = 0; i < ((CAstType.Function) entity.getType()).getArgumentCount(); ++i) {
                System.out.println("\t<ARGUMENT>");
                System.out.println("\t\t<TYPE>" + argumentTypes.get(i).getName() + "</TYPE>");
                if (i < argumentNames.length) {
                    System.out.println("\t\t<NAME>" + argumentNames[i] + "</NAME>");
                }
                System.out.println("\t</ARGUMENT>");
            }

            System.out.println("</ARGUMENTS>");
        }

        if (entity.getNodeTypeMap().keySet().size() > 0) {
            System.out.println("\t<VARIABLES>");
            for (CAstNode node: entity.getNodeTypeMap().keySet()) {
                System.out.println("\t\t<NAME>" + node.getChild(0) + "</NAME>");
                System.out.println("\t\t<TYPE>" + entity.getNodeTypeMap().get(node).getName() + "</TYPE>");
            }
            System.out.println("\t</VARIABLES>");
        }

        if (!entity.getAllScopedEntities().equals(Collections.emptyMap())) {
            System.out.println("\t<SCOPED_ENTITIES>");

            Iterator iterator = entity.getAllScopedEntities().entrySet().iterator();
            while (iterator.hasNext()) {
                System.out.println("\t\t<SCOPED_ENTITY>");
                Map.Entry pair = (Map.Entry) iterator.next();
                for (CAstEntity scopedEntity : (Collection<CAstEntity>) (pair.getValue())) {
                    System.out.println("\t\t\t<ENTITY>" + scopedEntity.getName().replace("\n", " | ") + "</ENTITY>");
                }
                System.out.println("\t\t</SCOPED_ENTITY>");
            }

            System.out.println("\t</SCOPED_ENTITIES>");
        }

        if (!entity.getControlFlow().getMappedNodes().isEmpty()) {
            System.out.println("\t<CONTROL_FLOW_MAP>");
            System.out.println("\t<NOTE>Reflexive edges are ommited!</NOTE>");

            CAstControlFlowRecorder map = entity.getControlFlow();
            for (CAstNode source : map.getMappedNodes()) {
                if (map.isMapped(source) && !(map.getTargetLabels(source).isEmpty())) {
                    System.out.println("\t\t<CONTROL_FLOW>");
                    System.out.println("\t\t\t<FROM>" + source.toString().replace("\n", " | ") + "</FROM>");
                    for (Object label : map.getTargetLabels(source)) {
                        CAstNode target = map.getTarget(source, label);
                        if (target != source) {
                            System.out.println("\t\t\t<TO>" + target.toString().replace("\n", " | ") + "</TO>");
                        }
                    }
                    System.out.println("\t\t</CONTROL_FLOW>");
                }
            }
            System.out.println("\t</CONTROL_FLOW_MAP>");
        }

        if (entity.getAST() != null) {
            System.out.println("\t<AST>");
            System.out.println(entity.getAST());
            System.out.println("\t</AST>");
        }

        if (entity instanceof ScriptEntity) {
            System.out.println("</SCRIPT_ENTITY>");
        } else if (entity instanceof FunctionEntity) {
            System.out.println("</FUNCTION_ENTITY>");
        }

        System.out.println("====================\n");
    }
}
