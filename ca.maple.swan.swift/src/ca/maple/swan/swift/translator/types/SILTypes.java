package ca.maple.swan.swift.translator.types;

import java.util.HashMap;

public class SILTypes {

    static HashMap<String, SILType> types = new HashMap<>();

    public static SILType getType(String name) {
        // TEMPORARY
        String tempName = "Any";
        if (!types.containsKey(tempName/*name*/)) {
            types.put(tempName, new SILType(tempName));
        }
        return types.get(tempName);
    }
}
