package ca.maple.swan.swift.translator.values;

import ca.maple.swan.swift.translator.RawAstTranslator;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.cast.tree.impl.CAstNodeTypeMapRecorder;

public class SILFunctionRef extends SILValue {

    private final String functionName;

    public SILFunctionRef(String name, String type, CAstNodeTypeMapRecorder typeRecorder, String functionName) {
        super(name, type, typeRecorder);
        this.functionName = functionName;
    }

    public String getFunctionName() {
        return functionName;
    }

    public CAstNode getFunctionRef() {
        return Ast.makeNode(CAstNode.FUNCTION_EXPR,
                Ast.makeConstant(functionName));
    }
}
