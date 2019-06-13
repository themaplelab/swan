package ca.maple.swan.swift.tree;

import com.ibm.wala.cast.tree.CAstType;

import java.util.Collection;

public class SwiftScriptType implements CAstType {
    @Override
    public String getName() {
        return "Script";
    }

    @Override
    public Collection<CAstType> getSupertypes() {
        return null;
    }
}
