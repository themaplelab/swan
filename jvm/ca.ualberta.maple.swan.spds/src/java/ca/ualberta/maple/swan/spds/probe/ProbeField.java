package ca.ualberta.maple.swan.spds.probe;
/** A represenatation of a field. */
public class ProbeField {

    /** The object representing the declaring class of the field. */
    public ProbeClass cls() { return cls; }
    /** The name of the field. */
    public String name() { return name; }
    public int hashCode() {
        return cls.hashCode() + name.hashCode();
    }
    public boolean equals( Object o ) {
        if( !(o instanceof ProbeField) ) return false;
        ProbeField other = (ProbeField) o;
        if( !cls.equals(other.cls) ) return false;
        if( !name.equals(other.name) ) return false;
        return true;
    }
    public String toString() {
        return cls.toString()+": "+name;
    }

    /* End of public methods. */

    ProbeField( ProbeClass cls, String name ) {
        if( cls == null ) throw new NullPointerException();
        if( name == null ) throw new NullPointerException();
        this.cls = cls;
        this.name = name;
    }

    /* End of package methods. */

    private ProbeClass cls;
    private String name;
}
