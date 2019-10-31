package ca.maple.swan.swift.translator.silir.instructions;

import ca.maple.swan.swift.translator.silir.BasicBlock;
import ca.maple.swan.swift.translator.silir.context.InstructionContext;
import ca.maple.swan.swift.translator.silir.values.Value;
import com.ibm.wala.util.collections.Pair;

import java.util.ArrayList;

public class SwitchTypeOfInstruction extends SILIRInstruction {

    public final Value switchValue;

    public final ArrayList<Pair<String, BasicBlock>> cases;

    // Can be null.
    public final BasicBlock defaultBlock;

    public SwitchTypeOfInstruction(String switchValueName, ArrayList<Pair<String, BasicBlock>> cases, BasicBlock defaultBlock, InstructionContext ic) {
        super(ic);
        this.switchValue = ic.valueTable().getValue(switchValueName);
        this.cases = cases;
        this.defaultBlock = defaultBlock;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("switch typeof ");
        s.append(switchValue.simpleName());
        for (Pair<String, BasicBlock> p : cases) {
            s.append("\n            case ");
            s.append(p.fst);
            s.append(": bb");
            s.append(p.snd.getNumber());
        }
        if (defaultBlock != null) {
            s.append("\n            default: bb");
            s.append(defaultBlock.getNumber());
        }
        s.append("\n");
        return s.toString();
    }

    @Override
    public void visit(ISILIRVisitor v) {
        v.visitSwitchTypeOfInstruction(this);
    }
}
