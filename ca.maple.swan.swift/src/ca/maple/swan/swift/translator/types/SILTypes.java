package ca.maple.swan.swift.translator.types;

import java.util.HashMap;

public class SILTypes {

    static HashMap<String, SILType> types = new HashMap<>();

    public static SILType getType(String name) {
        if (!types.containsKey(name)) {
            types.put(name, new SILType(name));
        }
        return types.get(name);
    }
}
