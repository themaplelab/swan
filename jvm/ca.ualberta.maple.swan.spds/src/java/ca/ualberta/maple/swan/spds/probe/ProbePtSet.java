package ca.ualberta.maple.swan.spds.probe;
import java.util.*;
/** A represenatation of a points-to set. */
public class ProbePtSet {
    /** The set of HeapObjects in this points-to set. */
    public Set heapObjects() { return heapObjects; }
    public int hashCode() {
        return heapObjects.size();
    }
    public boolean equals( Object o ) {
        if( !(o instanceof ProbePtSet) ) return false;
        ProbePtSet other = (ProbePtSet) o;
        if( !heapObjects.equals(other.heapObjects) ) return false;
        return true;
    }
    public String toString() {
        return heapObjects.toString();
    }

    /* End of public methods. */

    ProbePtSet( Collection heapObjects ) {
        this.heapObjects = new HashSet(heapObjects);
    }

    /* End of package methods. */

    private Set heapObjects;
}
