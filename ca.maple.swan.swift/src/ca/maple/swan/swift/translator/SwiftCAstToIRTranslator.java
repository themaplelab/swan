package ca.maple.swan.swift.translator;

import ca.maple.swan.swift.loader.SwiftLoader;
import com.ibm.wala.cast.ir.translator.AstTranslator;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.tree.CAstType;
import com.ibm.wala.cfg.AbstractCFG;
import com.ibm.wala.cfg.IBasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.TypeReference;

import java.util.Map;

public class SwiftCAstToIRTranslator extends AstTranslator {

    public SwiftCAstToIRTranslator(SwiftLoader loader) {
        super(loader);
    }

    @Override
    protected boolean useDefaultInitValues() {
        return false;
    }

    @Override
    protected boolean treatGlobalsAsLexicallyScoped() {
        return false;
    }

    @Override
    protected TypeReference defaultCatchType() {
        return null;
    }

    @Override
    protected TypeReference makeType(CAstType cAstType) {
        return null;
    }

    @Override
    protected boolean defineType(CAstEntity cAstEntity, WalkContext walkContext) {
        return false;
    }

    @Override
    protected void declareFunction(CAstEntity cAstEntity, WalkContext walkContext) {

    }

    @Override
    protected void defineFunction(CAstEntity cAstEntity, WalkContext walkContext, AbstractCFG<SSAInstruction, ? extends IBasicBlock<SSAInstruction>> abstractCFG, SymbolTable symbolTable, boolean b, Map<IBasicBlock<SSAInstruction>, TypeReference[]> map, boolean b1, AstLexicalInformation astLexicalInformation, AstMethod.DebuggingInformation debuggingInformation) {

    }

    @Override
    protected void defineField(CAstEntity cAstEntity, WalkContext walkContext, CAstEntity cAstEntity1) {

    }

    @Override
    protected String composeEntityName(WalkContext walkContext, CAstEntity cAstEntity) {
        return null;
    }

    @Override
    protected void doThrow(WalkContext walkContext, int i) {

    }

    @Override
    public void doArrayRead(WalkContext walkContext, int i, int i1, CAstNode cAstNode, int[] ints) {

    }

    @Override
    public void doArrayWrite(WalkContext walkContext, int i, CAstNode cAstNode, int[] ints, int i1) {

    }

    @Override
    protected void doFieldRead(WalkContext walkContext, int i, int i1, CAstNode cAstNode, CAstNode cAstNode1) {

    }

    @Override
    protected void doFieldWrite(WalkContext walkContext, int i, CAstNode cAstNode, CAstNode cAstNode1, int i1) {

    }

    @Override
    protected void doMaterializeFunction(CAstNode cAstNode, WalkContext walkContext, int i, int i1, CAstEntity cAstEntity) {

    }

    @Override
    protected void doNewObject(WalkContext walkContext, CAstNode cAstNode, int i, Object o, int[] ints) {

    }

    @Override
    protected void doCall(WalkContext walkContext, CAstNode cAstNode, int i, int i1, CAstNode cAstNode1, int i2, int[] ints) {

    }

    @Override
    protected CAstType topType() {
        return null;
    }

    @Override
    protected CAstType exceptionType() {
        return null;
    }

    @Override
    protected void doPrimitive(int i, WalkContext walkContext, CAstNode cAstNode) {

    }

    @Override
    protected CAstSourcePositionMap.Position[] getParameterPositions(CAstEntity cAstEntity) {
        return new CAstSourcePositionMap.Position[0];
    }
}
