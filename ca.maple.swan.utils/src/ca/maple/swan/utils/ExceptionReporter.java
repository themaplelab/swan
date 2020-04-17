package ca.maple.swan.utils;

// This class MUST be used whenever handling exceptions as later on when we need to report errors to the frontend,
// we will know about them even if they are deep and caught.
public class ExceptionReporter {

    // Add as needed

    public static <T extends Exception> void report(T e) throws T {
        // Do something with the exception here.

        throw e;
    }
}
