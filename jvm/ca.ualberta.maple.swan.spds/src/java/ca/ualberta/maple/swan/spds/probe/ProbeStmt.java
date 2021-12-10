package ca.ualberta.maple.swan.spds.probe;
/** A represenatation of a bytecode instruction. */
public class ProbeStmt implements Pointer, HeapObject {
    /** The object representing the method in whose body the instruction
     * appears. */
    public ProbeMethod method() { return method; }
    /** The bytecode offset of the instruction. */
    public int offset() { return offset; }
    public int hashCode() {
        return offset + method.hashCode();
    }
    public boolean equals( Object o ) {
        if( !(o instanceof ProbeStmt) ) return false;
        ProbeStmt other = (ProbeStmt) o;
        if( offset != other.offset ) return false;
        if( !method.equals(other.method) ) return false;
        return true;
    }
    public String toString() {
        return method.toString()+": "+offset;
    }

    /* End of public methods. */

    ProbeStmt( ProbeMethod method, int offset ) {
        if( method == null ) throw new NullPointerException();
        this.method = method;
        this.offset = offset;
    }

    /* End of package methods. */

    private ProbeMethod method;
    private int offset;
}
