package ca.maple.swan.swift.translator.values;

import ca.maple.swan.swift.translator.RawAstTranslator;
import ca.maple.swan.swift.translator.types.SILType;
import ca.maple.swan.swift.translator.types.SILTypes;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.cast.tree.impl.CAstNodeTypeMapRecorder;

import java.util.ArrayList;

import static com.ibm.wala.cast.tree.CAstNode.OBJECT_REF;

public class SILTuple extends SILValue {
    ArrayList<SILType> fieldTypes;

    public SILTuple(String name, String type, CAstNodeTypeMapRecorder typeRecorder, String... types) {
        super(name, type, typeRecorder);
        for (String s : types) {
            fieldTypes.add(SILTypes.getType(s));
        }
    }

    public CAstNode createObjectRef(CAstNodeTypeMapRecorder typeMap, int index) {
        CAstNode objectRef = Ast.makeNode(OBJECT_REF, Ast.makeConstant(this.name), Ast.makeConstant(index));
        typeMap.add(objectRef, fieldTypes.get(index));
        return objectRef;
    }

    public SILField createField(String name, int index, CAstNodeTypeMapRecorder typeRecorder) {
        return new SILField(name, fieldTypes.get(index).getName(), typeRecorder, this, index);
    }
}
