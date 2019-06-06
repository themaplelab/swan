package ca.maple.swan.swift.tree;

import com.ibm.wala.cast.tree.CAstNode;

import java.util.ArrayList;

public class CAstEntityInfo {

    public String functionName;
    public ArrayList<CAstNode> basicBlocks;
    public ArrayList<CAstNode> callNodes;
    public ArrayList<CAstNode> cfNodes;

    CAstEntityInfo(String functionName, ArrayList<CAstNode> basicBlocks,
                   ArrayList<CAstNode> callNodes, ArrayList<CAstNode> cfNodes) {
        this.functionName = functionName;
        this.basicBlocks = basicBlocks;
        this.callNodes = callNodes;
        this.cfNodes = cfNodes;
    }
}
