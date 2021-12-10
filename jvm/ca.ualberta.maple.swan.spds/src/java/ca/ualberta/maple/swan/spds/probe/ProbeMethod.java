package ca.ualberta.maple.swan.spds.probe;
/** A represenatation of a method. */
public class ProbeMethod {

    /** The object representing the declaring class of the method. */
    public ProbeClass cls() { return cls; }
    /** The name of the method. */
    public String name() { return name; }
    /** The arguments to the method, in the same format as in Java bytecode. */
    public String signature() { return signature; }
    public int hashCode() {
        return cls.hashCode() + name.hashCode() + signature.hashCode();
    }
    public boolean equals( Object o ) {
        if( !(o instanceof ProbeMethod) ) return false;
        ProbeMethod other = (ProbeMethod) o;
        if( !cls.equals(other.cls) ) return false;
        if( !name.equals(other.name) ) return false;
        if( !signature.equals(other.signature) ) return false;
        return true;
    }
    public String toString() {
        return name;
    }

    /* End of public methods. */

    ProbeMethod( ProbeClass cls, String name, String signature ) {
        if( cls == null ) throw new NullPointerException();
        if( name == null ) throw new NullPointerException();
        if( signature == null ) throw new NullPointerException();
        this.cls = cls;
        this.name = name;
        this.signature = signature;
    }

    /* End of package methods. */

    private ProbeClass cls;
    private String name;
    private String signature;
}
