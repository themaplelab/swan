package ca.maple.swan.swift.translator.silir.values;

import ca.maple.swan.swift.translator.silir.printing.ValueNameSimplifier;

public class ArrayValue extends Value {

    public ArrayValue(String name, String type) {
        super(name, type);
    }

    public String getName() {
        return this.name;
    }

    public String getType() {
        return this.type;
    }

    public String simpleName() {
        return ValueNameSimplifier.get(this.name);
    }
}
