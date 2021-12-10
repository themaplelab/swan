package ca.ualberta.maple.swan.spds.probe;
import java.util.*;

/** A representation of the set of allocation statements that may execute
 * more than once in a run of the program.
 */
public class ExecutesMany {
    /** @return The (mutable) set of ProbeStmt's representing allocation
     * sites that may execute more than once.
     */
    public Set/*ProbeStmt*/ stmts() {
        return stmts;
    }

    /* End of public methods. */

    private Set stmts = new HashSet();
}

