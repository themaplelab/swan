package ca.ualberta.maple.swan.spds.probe;
/** A represenatation of a class. */
public class ProbeClass {
    /** The package containing the class, with subpackages separated by dots. */
    public String pkg() { return pkg; }
    /** The name of the class (not including the package name), as it appears
     * in Java bytecode. */
    public String name() { return name; }
    public int hashCode() {
        return pkg.hashCode() + name.hashCode();
    }
    public boolean equals( Object o ) {
        if( !(o instanceof ProbeClass) ) return false;
        ProbeClass other = (ProbeClass) o;
        if( !pkg.equals(other.pkg) ) return false;
        if( !name.equals(other.name) ) return false;
        return true;
    }
    public String toString() {
        if( "".equals(pkg) ) return name;
        return pkg+"."+name;
    }

    /* End of public methods. */

    ProbeClass( String pkg, String name ) {
        if( pkg == null ) throw new NullPointerException();
        if( name == null ) throw new NullPointerException();
        this.pkg = pkg;
        this.name = name;
    }

    /* End of package methods. */

    private String pkg;
    private String name;
}

