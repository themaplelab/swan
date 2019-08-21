package ca.maple.swan.swift.translator.values;

import ca.maple.swan.swift.translator.SILInstructionContext;
import ca.maple.swan.swift.translator.types.SILType;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.util.collections.Pair;

import java.util.ArrayList;

import static com.ibm.wala.cast.tree.CAstNode.OBJECT_REF;

public class SILStruct extends SILValue {

    private ArrayList<Pair<String, SILType>> fields;

    public SILStruct(String name, String type, SILInstructionContext C, ArrayList<Pair<String, String>> givenFields) {
        super(name, type, C);
        fields = new ArrayList<>();
        for (Pair<String, String> field : givenFields) {
            this.fields.add(Pair.make(field.fst, C.valueTable.getValue(field.snd).getType()));
        }
    }

    public CAstNode createObjectRef(String fieldName) {
        try {
            for (Pair<String, SILType> p : fields) {
                if (p.fst.equals(fieldName)) {
                    CAstNode objectRef = Ast.makeNode(OBJECT_REF,
                            getVarNode(),
                            Ast.makeConstant(fieldName));
                    C.parent.getNodeTypeMap().add(objectRef, p.snd);
                    return objectRef;
                }
            }
            throw new Exception("fieldName not valid");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public CAstNode createObjectRef(int index) {
        try {
            CAstNode objectRef = Ast.makeNode(OBJECT_REF,
                    getVarNode(),
                    Ast.makeConstant(fields.get(index).fst));
            C.parent.getNodeTypeMap().add(objectRef, fields.get(index).snd);
            return objectRef;
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        return null;
    }

    public SILField createField(String name, String fieldName) {
        try {
            for (Pair<String, SILType> p : fields) {
                if (p.fst.equals(fieldName)) {
                    return new SILField(name, p.snd.getName(), C, this, fieldName);
                }
            }
            throw new Exception("fieldName not valid");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public SILField createField(String name, int index) {
        try {
            return new SILField(name, fields.get(index).snd.getName(), C, this, index);
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        return null;
    }

}
