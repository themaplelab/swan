package ca.ualberta.maple.swan.spds.probe;
import java.util.*;
import java.io.*;
import java.util.zip.*;

/** Outputs statistics about the number of potentially polymorphic call sites.
 * */
public class PolymorphicInfo {
    public static boolean dashV = false;
    public static String dashLib = null;
    public static String filename = null;
    public static void usage() {
        System.out.println( "Usage: java probe.PolymorphicInfo [options] polymorphic.gxl" );
        System.out.println( "  -v : print list of potentially polymorphic call sites" );
        System.out.println( "  -lib file : ignore methods in packages listed in file" );
        System.exit(1);
    }
    public static final void main( String[] args ) {
        boolean doneOptions = false;
        for( int i = 0; i < args.length; i++ ) {
            if( !doneOptions && args[i].equals("-lib") ) dashLib = args[++i];
            else if( !doneOptions && args[i].equals("-v") ) dashV = true;
            else if( !doneOptions && args[i].equals("--") ) doneOptions = true;
            else if( filename == null ) filename = args[i];
            else usage();
        }
        if( filename == null ) usage();

        Collection libs = Util.readLib(dashLib);

        Polymorphic a;
        try {
            if (filename.endsWith("txt.gzip")) {
                a = new TextReader().readPolymorphic(new GZIPInputStream(new FileInputStream(filename)));
            } else if (filename.endsWith("txt")) {
                a = new TextReader().readPolymorphic(new FileInputStream(filename));
            } else {
                throw new IOException("undefined file extension.");
            }
        } catch( IOException e ) {
            throw new RuntimeException( "caught IOException "+e );
        }

        Collection stmts = Util.filterLibs(libs, a.stmts());
        System.out.println( "Potentially polymorphic sites : "+stmts.size() );

        if(dashV) {
            for( Iterator sIt = stmts.iterator(); sIt.hasNext(); ) {
                final ProbeStmt s = (ProbeStmt) sIt.next();
                System.out.println(s);
            }
        }
    }

}

