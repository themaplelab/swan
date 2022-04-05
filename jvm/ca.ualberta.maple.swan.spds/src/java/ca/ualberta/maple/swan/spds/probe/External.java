package ca.ualberta.maple.swan.spds.probe;
import java.util.*;

/** Represents a heap object whose allocation site is unknown.
  */

public class External implements HeapObject {
    /** Returns the singleton instance. */
    public static External v() {
        return instance;
    }
    private static External instance = new External();
}

