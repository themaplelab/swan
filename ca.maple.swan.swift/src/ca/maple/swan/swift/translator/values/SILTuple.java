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
        for (String s : types) {
            fieldTypes.add(SILTypes.getType(s));
        }
    }

    public CAstNode createObjectRef(int index) {
        return Ast.makeNode(OBJECT_REF, Ast.makeConstant(this.name), Ast.makeConstant(index));
    }

    public SILField createField(String name, int index) {
        return new SILField(name, fieldTypes.get(index).getName(), C, this, index);
    }

    // For tuples where the first element is any type, and the second element points to the first.
    public static class SILBuiltinPointerTuple extends SILValue {

        public SILBuiltinPointerTuple(String name, String type, SILInstructionContext C) {
            super(name, type, C);
        }

        public void destructure(String ResultName1, String ResultType1,
                                                    String ResultName2, String ResultType2, SILInstructionContext C) {
            SILValue element1 = new SILValue(ResultName1, ResultType1, C);
            SILPointer element2 = new SILPointer(ResultName2, ResultType2, C, element1);
            C.valueTable.addValue(element1);
            C.valueTable.addValue(element2);
        }
    }

}
