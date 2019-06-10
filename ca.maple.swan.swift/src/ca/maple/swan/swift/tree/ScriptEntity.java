package ca.maple.swan.swift.tree;

import com.ibm.wala.cast.ir.translator.AbstractScriptEntity;
import com.ibm.wala.cast.tree.*;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


public class ScriptEntity extends AbstractScriptEntity {

    // WORK IN PROGRESS

    // ScriptEntity -> AbstractScriptEntity -> AbstractCodeEntity -> AbstractEntity -> CAstEntity
    // AbstractScriptEntity:
    //      Handles basic filename, kind, name, etc
    // AbstractCodeEntity:
    //      Handles AST, CFG, types
    // AbstractEntity:
    //      Handles scoped entities.
    // CAstEntity:
    //      Defines types and methods, which are all implemented by the above so we don't deal with
    //      this class directly.

    // What this class expects from the C++ translator:
    //      Map<String, CAstNode>
    //      String - Function name, first entry should be "main"
    //      CAstNode - Basic Block #0 of the SILFunction

    public ScriptEntity(File file) {
        super(file, new SwiftFunction());
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
        return "main";
    }
}
