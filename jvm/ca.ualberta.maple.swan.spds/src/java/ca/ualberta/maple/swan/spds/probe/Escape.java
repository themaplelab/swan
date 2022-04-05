package ca.ualberta.maple.swan.spds.probe;
import java.util.*;

/** A representation of objects that escape their allocating method/thread.
 */
public class Escape {
    /** @return The (mutable) set of ProbeStmt's containing allocation
     * sites whose objects escape their allocating thread.
     */
    public Set/*ProbeStmt*/ escapesThread() {
        return escapesThread;
    }

    /** @return The (mutable) set of ProbeStmt's containing allocation
     * sites whose objects escape their allocating method.
     */
    public Set/*ProbeStmt*/ escapesMethod() {
        return escapesMethod;
    }

    /** @return The (mutable) set of all allocation sites.
     */
    public Set/*ProbeStmt*/ anyAlloc() {
        return anyAlloc;
    }

    /* End of public methods. */

    private Set escapesThread = new HashSet();
    private Set escapesMethod = new HashSet();
    private Set anyAlloc = new HashSet();
}

