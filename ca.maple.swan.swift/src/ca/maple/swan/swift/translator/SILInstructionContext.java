package ca.maple.swan.swift.translator;

import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstNode;

import java.util.ArrayList;
import java.util.HashMap;

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
