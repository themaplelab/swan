package ca.maple.swan.swift.translator.values;

import ca.maple.swan.swift.translator.RawAstTranslator;
import ca.maple.swan.swift.translator.types.SILType;
import ca.maple.swan.swift.translator.types.SILTypes;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.impl.CAstNodeTypeMapRecorder;
import com.ibm.wala.util.collections.Pair;

import java.util.ArrayList;

import static com.ibm.wala.cast.tree.CAstNode.OBJECT_REF;

public class Struct extends SILValue {
    private ArrayList<Pair<String, SILType>> fields;

    public Struct(String name, String type, CAstNodeTypeMapRecorder typeRecorder, ArrayList<Pair<String, String>> fields) {
        super(name, type, typeRecorder);
        for (Pair<String, String> field : fields) {
            this.fields.add(Pair.make(field.fst, SILTypes.getType(field.snd)));
        }
    }

    public CAstNode createObjectRef(CAstNodeTypeMapRecorder typeMap, String fieldName) {
        try {
            for (Pair<String, SILType> p : fields) {
                if (p.fst.equals(fieldName)) {
                    CAstNode objectRef = RawAstTranslator.Ast.makeNode(OBJECT_REF,
                            RawAstTranslator.Ast.makeConstant(this.name),
                            RawAstTranslator.Ast.makeConstant(fieldName));
                    typeMap.add(objectRef, p.snd);
                    return objectRef;
                }
            }
            throw new Exception("fieldName not valid");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public CAstNode createObjectRef(CAstNodeTypeMapRecorder typeMap, int index) {
        try {
            CAstNode objectRef = RawAstTranslator.Ast.makeNode(OBJECT_REF,
                    RawAstTranslator.Ast.makeConstant(this.name),
                    RawAstTranslator.Ast.makeConstant(fields.get(index).fst));
            typeMap.add(objectRef, fields.get(index).snd);
            return objectRef;
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Field createField(String name, String fieldName, CAstNodeTypeMapRecorder typeRecorder) {
        try {
            for (Pair<String, SILType> p : fields) {
                if (p.fst.equals(fieldName)) {
                    return new Field(name, p.snd.getName(), typeRecorder, this, fieldName);
                }
            }
            throw new Exception("fieldName not valid");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Field createField(String name, int index, CAstNodeTypeMapRecorder typeRecorder) {
        try {
            return new Field(name, fields.get(index).snd.getName(), typeRecorder, this, index);
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        return null;
    }
}
