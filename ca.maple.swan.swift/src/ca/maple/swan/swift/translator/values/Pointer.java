package ca.maple.swan.swift.translator.values;

import com.ibm.wala.cast.tree.impl.CAstNodeTypeMapRecorder;

public class Pointer extends SILValue {

    private final SILValue pointsTo;

    public Pointer(String name, String type, CAstNodeTypeMapRecorder typeRecorder, SILValue pointsTo) {
        super(name, type, typeRecorder);
        this.pointsTo = pointsTo;
    }

    public SILValue dereference() {
        return pointsTo;
    }
}
