package ca.maple.swan.swift.translator.values;

import ca.maple.swan.swift.translator.SILInstructionContext;
import com.ibm.wala.cast.tree.CAstNode;

import java.util.ArrayList;

import static com.ibm.wala.cast.tree.CAstNode.OBJECT_LITERAL;

public class SILEnum extends SILValue {

    private SILValue field;
    private CAstNode LiteralNode;
    private final String fieldName = "value";

    public SILEnum(String name, String type, SILInstructionContext C, SILValue field) {
        super(name, type, C);
        this.field = field;
        ArrayList<CAstNode> LiteralFields = new ArrayList<>();
        LiteralFields.add(Ast.makeNode(CAstNode.NEW, Ast.makeConstant(type)));
        LiteralFields.add(Ast.makeConstant(fieldName));
        LiteralFields.add(field.getVarNode());
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
