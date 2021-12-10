package ca.ualberta.maple.swan.spds.probe;
import java.util.*;

/** A representation of a points-to sets. */
public class PointsTo {
    /** @return The (mutable) map mapping each Pointer to a PtSet of the
     * objects it points to. */
    public Map/*Pointer=>PtSet*/ pointsTo() {
        return pointsTo;
    }

    /* End of public methods. */

    private Map pointsTo = new HashMap();
}

