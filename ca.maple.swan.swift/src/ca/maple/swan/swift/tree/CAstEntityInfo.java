package ca.maple.swan.swift.tree;

import com.ibm.wala.cast.tree.CAstNode;

import java.util.ArrayList;

public class CAstEntityInfo {

    public String functionName;
    public ArrayList<CAstNode> basicBlocks;
    public ArrayList<CAstNode> callNodes;
    public ArrayList<CAstNode> cfNodes;
    public String returnType;
    public ArrayList<String> argumentTypes;
    public ArrayList<String> argumentNames;

    CAstEntityInfo(String functionName, ArrayList<CAstNode> basicBlocks,
                   ArrayList<CAstNode> callNodes, ArrayList<CAstNode> cfNodes,
                   String returnType, ArrayList<String> argumentTypes, ArrayList<String> argumentNames) {
        this.functionName = functionName;
        this.basicBlocks = basicBlocks;
        this.callNodes = callNodes;
        this.cfNodes = cfNodes;
        this.returnType = returnType;
        this.argumentTypes = argumentTypes;
        this.argumentNames = argumentNames;
    }
}
