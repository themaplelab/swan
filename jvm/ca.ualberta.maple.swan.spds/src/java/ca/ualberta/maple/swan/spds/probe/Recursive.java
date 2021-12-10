package ca.ualberta.maple.swan.spds.probe;
import java.util.*;

/** A representation of the set of methods which recursively call themselves
 * (possibly transitively through other methods). */
public class Recursive {
    /** @return The (mutable) set of ProbeMethod's that recursively call
     * themselves.
     */
    public Set/*ProbeMethod*/ methods() {
        return methods;
    }

    /* End of public methods. */

    private Set methods = new HashSet();
}

