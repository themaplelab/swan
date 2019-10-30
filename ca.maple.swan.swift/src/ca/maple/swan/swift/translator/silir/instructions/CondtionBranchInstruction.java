package ca.maple.swan.swift.translator.silir.instructions;

import ca.maple.swan.swift.translator.silir.BasicBlock;
import ca.maple.swan.swift.translator.silir.context.InstructionContext;
import ca.maple.swan.swift.translator.silir.values.Value;

public class CondtionBranchInstruction extends SILIRInstruction {

    public final Value conditionValue;

    public final BasicBlock trueBlock;

    public final BasicBlock falseBlock;

    public CondtionBranchInstruction(String conditionValue, BasicBlock trueBlock, BasicBlock falseBlock, InstructionContext ic) {
        super(ic);
        this.conditionValue = ic.valueTable().getValue(conditionValue);
        this.trueBlock = trueBlock;
        this.falseBlock = falseBlock;
    }

    @Override
    public String toString() {
        return "cond_br " + conditionValue.simpleName() + " true: bb" + trueBlock.getNumber() + ", false: bb" + falseBlock.getNumber() + "\n";
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitConditionBranchInstruction(this);
    }
}
