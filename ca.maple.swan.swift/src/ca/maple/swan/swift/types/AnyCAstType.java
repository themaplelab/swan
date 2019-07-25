package ca.maple.swan.swift.types;

import com.ibm.wala.cast.tree.CAstType;

import java.util.Collection;
import java.util.Collections;

public class AnyCAstType implements CAstType {

    @Override
    public String getName() {
        return "Any";
    }

    @Override
    public Collection<CAstType> getSupertypes() {
        return Collections.emptySet();
    }
};