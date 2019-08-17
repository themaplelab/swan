package ca.maple.swan.swift.translator.types;

import com.ibm.wala.cast.tree.CAstType;

import java.util.Collection;

public class SILType implements CAstType {
    private String Name;

    SILType(String name) {
        this.Name = name;
    }

    @Override
    public String getName() {
        return Name;
    }

    @Override
    public Collection<CAstType> getSupertypes() {
        return null;
    }
}
