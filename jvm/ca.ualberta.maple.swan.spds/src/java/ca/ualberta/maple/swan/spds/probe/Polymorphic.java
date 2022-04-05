package ca.ualberta.maple.swan.spds.probe;
import java.util.*;

/** A representation of the set of polymorphic invokes.
 */
public class Polymorphic {
    /** @return The (mutable) set of ProbeStmt's representing polymorphic
     * invoke instructions.
     */
    public Set/*ProbeStmt*/ stmts() {
        return stmts;
    }

    /* End of public methods. */

    private Set stmts = new HashSet();
}

