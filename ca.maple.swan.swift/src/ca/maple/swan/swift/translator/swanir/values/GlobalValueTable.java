package ca.maple.swan.swift.translator.swanir.values;

import ca.maple.swan.swift.translator.swanir.instructions.AssignGlobalInstruction;

import java.util.ArrayList;
import java.util.HashMap;

public class GlobalValueTable extends ValueTable {

    private HashMap<String, ArrayList<AssignGlobalInstruction>> delayedAccesses;

    public GlobalValueTable() {
        super();
        this.delayedAccesses = new HashMap<>();
    }

    public Value getGlobalValue(String s, AssignGlobalInstruction inst) {
        if (values.containsKey(s)) {
            return values.get(s);
        } else {
            if (delayedAccesses.containsKey(s)) {
                delayedAccesses.get(s).add(inst);
            } else {
                ArrayList<AssignGlobalInstruction> insts = new ArrayList<>();
                insts.add(inst);
                delayedAccesses.put(s, insts);
            }
            return new Value("temp", "temp");
        }
    }

    @Override
    public void add(Value v) {
        values.put(v.name, v);
        if (delayedAccesses.containsKey(v.name)) {
            for (AssignGlobalInstruction inst : delayedAccesses.get(v.name)) {
                inst.from = v;
            }
        }
    }

    @Override
    public void add(String s, Value v) {
        this.add(v);
    }
}
