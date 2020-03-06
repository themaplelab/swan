package ca.maple.swan.swift.translator.swanir.context;

import ca.maple.swan.swift.translator.swanir.BasicBlock;
import ca.maple.swan.swift.translator.swanir.instructions.GotoInstruction;
import ca.maple.swan.swift.translator.swanir.values.Value;

import java.util.ArrayList;

public class CoroutineContext {

    public final ArrayList<Value> yieldedValues;
    public final BasicBlock returnBlock;
    public BasicBlock returnResumeBlock = null;
    public BasicBlock returnUnwindBlock = null;
    public BasicBlock resumeBlock = null;
    public BasicBlock unwindBlock = null;
    public GotoInstruction gotoReturnResume = null;
    public GotoInstruction gotoReturnUnwind = null;

    public CoroutineContext(ArrayList<Value> yieldedValues, BasicBlock returnBlock) {
        this.yieldedValues = yieldedValues;
        this.returnBlock = returnBlock;
    }

    public void linkResume() {
        gotoReturnResume.bb = returnResumeBlock;
        // If the unwind is never used (abort_apply), we just use the resume return block so we don't get an invalid
        // goto instruction. This should be okay to do.
        if (gotoReturnUnwind.bb == null) {
            gotoReturnUnwind.bb = returnResumeBlock;
        }
    }

    public void linkUnwind() {
        gotoReturnUnwind.bb = returnUnwindBlock;
        // Same thing here.
        if (gotoReturnResume.bb == null) {
            gotoReturnResume.bb = returnUnwindBlock;
        }
    }
}
