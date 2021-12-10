package ca.ualberta.maple.swan.spds.probe;
/** A represenatation of a bytecode instruction. */
public class ProbeParameter implements Pointer {
    /** The object representing the method of which this is a parameter. */
    public ProbeMethod method() { return method; }
    /** The position of this parameter. */
    public int number() { return number; }
    public int hashCode() {
        return number + method.hashCode();
    }
    public boolean equals( Object o ) {
        if( !(o instanceof ProbeParameter) ) return false;
        ProbeParameter other = (ProbeParameter) o;
        if( number != other.number ) return false;
        if( !method.equals(other.method) ) return false;
        return true;
    }
    public String toString() {
        return method.toString()+"("+number+")";
    }

    /* End of public methods. */

    ProbeParameter( ProbeMethod method, int number ) {
        if( method == null ) throw new NullPointerException();
        this.method = method;
        this.number = number;
    }

    /* End of package methods. */

    private ProbeMethod method;
    private int number;
}
