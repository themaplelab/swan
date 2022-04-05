package ca.ualberta.maple.swan.spds.probe;
import java.util.*;
/** A represenatation of a set of fields. */
public class ProbeFieldSet {
    /** The set of fields in this points-to set. */
    public Set fields() { return fields; }
    public int hashCode() {
        return fields.size();
    }
    public boolean equals( Object o ) {
        if( !(o instanceof ProbeFieldSet) ) return false;
        ProbeFieldSet other = (ProbeFieldSet) o;
        if( !fields.equals(other.fields) ) return false;
        return true;
    }
    public String toString() {
        return fields.toString();
    }

    /* End of public methods. */

    ProbeFieldSet( Collection fields ) {
        this.fields = new HashSet(fields);
    }

    /* End of package methods. */

    private Set fields;
}
