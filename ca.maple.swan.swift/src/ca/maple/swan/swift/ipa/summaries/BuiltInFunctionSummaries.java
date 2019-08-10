package ca.maple.swan.swift.ipa.summaries;

import com.ibm.wala.cast.tree.CAst;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.impl.CAstImpl;

public class BuiltInFunctionSummaries {

    public static CAstNode findSummary(CAstNode cAstNode) {
        assert(cAstNode.getKind() == CAstNode.CALL);
        assert(cAstNode.getChild(0).getKind() == CAstNode.FUNCTION_EXPR);
        assert(cAstNode.getChild(1).getValue().equals("do"));

        switch((String)cAstNode.getChild(0).getValue()) {

            /*************** LITERALS ****************/
            case "Swift.Int.init(_builtinIntegerLiteral: Builtin.IntLiteral) -> Swift.Int": {
                // (Builtin.IntLiteral, @thin Int.Type)
                assert(cAstNode.getChild(2).getKind() == CAstNode.VAR);
                return cAstNode.getChild(2);
            }

            /********** OPERATOR FUNCTIONS ***********/
            // These are here in case we want to change how operator functions are
            // handled later. For now, the translator itself takes care of them.

            case "static Swift.Int.+ infix(Swift.Int, Swift.Int) -> Swift.Int": {
                // (Int, Int, @thin Int.Type)
                return null;

            }
            default: {
                return null;
            }
        }
    }
}
