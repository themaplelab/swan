package ca.maple.swan.swift.translator.values;

import java.util.HashMap;

public class SILValueTable {

    private HashMap<String, SILValue> values;

    public SILValueTable() { }

    SILValue getValue(String valueName) {
        assert(values.containsValue(valueName));
        return values.get(valueName);
    }

    void addValue(SILValue v) {
        values.put(v.name, v);
    }

}
