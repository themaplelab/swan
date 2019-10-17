package ca.maple.swan.swift.translator.values;

import ca.maple.swan.swift.translator.SILInstructionContext;
import com.ibm.wala.cast.tree.CAstNode;

import java.util.ArrayList;

import static com.ibm.wala.cast.tree.CAstNode.OBJECT_LITERAL;

/*
 * Used for keeping track of enums. An enum in SIL can have attached
 * data, so we just store this under the arbitrary field name "value".
 */

public class SILEnum extends SILValue {

    private final CAstNode LiteralNode;
    private final String fieldName = "value";

    public SILEnum(String name, String type, SILInstructionContext C, SILValue field) {
        super(name, type, C);
        ArrayList<CAstNode> LiteralFields = new ArrayList<>();
        LiteralFields.add(Ast.makeNode(CAstNode.NEW, Ast.makeConstant(type)));
        if (field != null) {
            LiteralFields.add(Ast.makeConstant(fieldName));
            LiteralFields.add(field.getVarNode());
        }
        LiteralNode = Ast.makeNode(OBJECT_LITERAL, LiteralFields);
        C.parent.setGotoTarget(LiteralNode, LiteralNode);
    }

    public CAstNode getLiteral() {
        return LiteralNode;
    }

    @Override
    public CAstNode getVarNode() {
        return getLiteral();
    }

    public SILField createField(String name, String type) {
        return new SILField(name, type, C, this, fieldName);
    }

}
