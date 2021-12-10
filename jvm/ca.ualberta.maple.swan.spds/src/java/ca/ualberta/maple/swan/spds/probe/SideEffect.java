package ca.ualberta.maple.swan.spds.probe;
import java.util.*;

/** A representation of side-effect information. */
public class SideEffect {
    /** @return The (mutable) map mapping each Stmt to a ProbeFieldSet of the
     * fields it may read. */
    public Map/*ProbeStmt=>ProbeFieldSet*/ reads() {
        return reads;
    }

    /** @return The (mutable) map mapping each Stmt to a ProbeFieldSet of the
     * fields it may write. */
    public Map/*ProbeStmt=>ProbeFieldSet*/ writes() {
        return writes;
    }

    /* End of public methods. */

    private Map reads = new HashMap();
    private Map writes = new HashMap();
}

