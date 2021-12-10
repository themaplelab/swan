package ca.ualberta.maple.swan.spds.probe;
import java.util.*;

/** A Flyweight Factory for managing the objects representing classes,
 * methods, etc. */

public class ObjectManager {
    /** Returns the singleton instance of an ObjectManager. */
    public static ObjectManager v() {
        return instance;
    }
    /** Returns the object representing a class, creating if if necessary.
     * @param pkg - The package containing the class, with subpackages
     *                  separated by dots. 
     * @param name - The name of the class (not including the package name),
     *               as it appears in Java bytecode.
     **/
    public ProbeClass getClass( String pkg, String name ) {
        ProbeClass newcl = new ProbeClass(pkg, name);
        ProbeClass ret = (ProbeClass) classMap.get(newcl);
        if( ret == null ) {
            classMap.put( newcl, newcl );
            ret = newcl;
        }
        return ret;
    }

    /** Returns the object representing a class, creating if if necessary.
     * @param fullName - The name of the class including the package name, with
     *                   subpackages separated by dots. 
     **/
    public ProbeClass getClass( String fullName ) {
        int i = fullName.lastIndexOf('.');
        if( i < 0 ) return getClass("", fullName);
        String pkg = fullName.substring(0, i);
        String name = fullName.substring(i+1, fullName.length());
        return getClass(pkg, name);
    }

    /** Returns the object representing a method, creating it if necessary.
     * @param cls - The object representing the declaring class of the method.
     * @param name - The name of the method.
     * @param signature - The arguments to the method, in the same format as in
     *                    Java bytecode.
     **/
    public ProbeMethod getMethod( ProbeClass cls, String name, String signature ) {
        ProbeMethod newm = new ProbeMethod(cls, name, signature);
        ProbeMethod ret = (ProbeMethod) methodMap.get(newm);
        if( ret == null ) {
            methodMap.put( newm, newm );
            ret = newm;
        }
        return ret;
    }
    
    public ProbeMethod getMethod(String pkg, String cls, String name, String signature) {
    	return getMethod(getClass(pkg, cls), name, signature);
    }

    /** Returns the object representing a field, creating it if necessary.
     * @param cls - The object representing the declaring class of the field.
     * @param name - The name of the field.
     **/
    public ProbeField getField( ProbeClass cls, String name ) {
        ProbeField newf = new ProbeField(cls, name);
        ProbeField ret = (ProbeField) fieldMap.get(newf);
        if( ret == null ) {
            fieldMap.put( newf, newf );
            ret = newf;
        }
        return ret;
    }

    /** Returns the object representing a bytecode instruction, creating
     * it if necessary.
     * @param method - The method in whose body the instruction appears.
     * @param offset - The bytecode offset of the instruction.
     **/
    public ProbeStmt getStmt( ProbeMethod method, int offset ) {
        ProbeStmt news = new ProbeStmt(method, offset);
        ProbeStmt ret = (ProbeStmt) stmtMap.get(news);
        if( ret == null ) {
            stmtMap.put( news, news );
            ret = news;
        }
        return ret;
    }

    /** Returns the object representing a method parameter, creating
     * it if necessary.
     * @param method - The method to which this is a parameter.
     * @param number - The number of the parameter, starting from 0, or
     * -1 for the implicit this parameter, or -2 for the return value.
     **/
    public ProbeParameter getParameter( ProbeMethod method, int number ) {
        ProbeParameter newp = new ProbeParameter(method, number);
        ProbeParameter ret = (ProbeParameter) parameterMap.get(newp);
        if( ret == null ) {
            parameterMap.put( newp, newp );
            ret = newp;
        }
        return ret;
    }

    /** Returns the object representing a points-to set containing the
     * HeapObjects in heapObjects.
     * @param heapObjects - The set of HeapObjects in this points-to set.
     **/
    public ProbePtSet getPtSet( Collection heapObjects ) {
        ProbePtSet newp = new ProbePtSet(heapObjects);
        ProbePtSet ret = (ProbePtSet) ptSetMap.get(newp);
        if( ret == null ) {
            ptSetMap.put( newp, newp );
            ret = newp;
        }
        return ret;
    }

    /** Returns the object representing a field set containing the
     * ProbeFields in fields.
     * @param fields - The set of ProbeFields in this field set.
     **/
    public ProbeFieldSet getFieldSet( Collection fields ) {
        ProbeFieldSet news = new ProbeFieldSet(fields);
        ProbeFieldSet ret = (ProbeFieldSet) ptSetMap.get(news);
        if( ret == null ) {
            fieldSetMap.put( news, news );
            ret = news;
        }
        return ret;
    }

    /* End of public methods. */

    private Map classMap = new HashMap();
    private Map methodMap = new HashMap();
    private Map fieldMap = new HashMap();
    private Map stmtMap = new HashMap();
    private Map parameterMap = new HashMap();
    private Map ptSetMap = new HashMap();
    private Map fieldSetMap = new HashMap();
    private static ObjectManager instance = new ObjectManager();
}

