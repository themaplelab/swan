package ca.maple.swan.swift.translator.values;

import com.ibm.wala.cast.tree.CAstNode;

import java.util.ArrayList;
import java.util.HashMap;

public class SILValueTable {

    private final HashMap<String, SILValue> values;
    private final ArrayList<SILValue> undeclaredValues;

    public SILValueTable() {
        values = new HashMap<>();
        undeclaredValues = new ArrayList<>();
    }

    public SILValue getValue(String valueName) {
        assert(values.containsKey(valueName));
        return values.get(valueName);

    }

    public void removeValue(String valueName) {
        assert(values.containsKey(valueName));
        values.remove(valueName);
    }

    public SILValue getAndRemoveValue(String valueName) {
        assert(values.containsKey(valueName));
        SILValue toReturn = values.get(valueName);
        values.remove(valueName);
        return toReturn;
    }

    public void addValue(SILValue v) {
        values.put(v.name, v);
        undeclaredValues.add(v);
    }

    public void clearValues() {
        values.clear();
    }

    public ArrayList<CAstNode> getDecls() {
        ArrayList<CAstNode> decls = new ArrayList<>();
        for (SILValue v : undeclaredValues) {
            decls.add(v.getDecl());
        }
        undeclaredValues.clear();
        return decls;
    }

}
