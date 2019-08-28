package ca.maple.swan.swift.translator.values;

import ca.maple.swan.swift.translator.RawAstTranslator;
import ca.maple.swan.swift.translator.SILInstructionContext;
import ca.maple.swan.swift.translator.types.SILType;
import ca.maple.swan.swift.translator.types.SILTypes;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.cast.tree.impl.CAstNodeTypeMapRecorder;
import com.ibm.wala.util.collections.Pair;

import java.util.ArrayList;

import static com.ibm.wala.cast.tree.CAstNode.OBJECT_REF;

public class SILTuple extends SILValue {

    ArrayList<SILType> fieldTypes;

    public SILTuple(String name, String type, SILInstructionContext C, ArrayList<String> types) {
        super(name, type, C);
        fieldTypes = new ArrayList<>();
        for (String s : types) {
            fieldTypes.add(SILTypes.getType(s));
        }
    }

    public CAstNode createObjectRef(int index) {
        CAstNode ref = Ast.makeNode(OBJECT_REF, Ast.makeConstant(this.name), Ast.makeConstant(index));
        C.parent.setGotoTarget(ref, ref);
        return ref;
    }

    public SILField createField(String name, int index) {
        return new SILField(name, fieldTypes.get(index).getName(), C, this, index);
    }

    public int getNoFields() {
        return fieldTypes.size();
    }

}
