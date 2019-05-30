package ca.maple.swan.swift.translator;

public class SwiftTranslatorPathLoader {

    public static void load() {
        String sharedDir = "/ca.maple.swan.translator/build/libs/swiftWala/shared/";
        String libName = "libswiftWala";

        String SWANDir = "";
        try {
            SWANDir = System.getenv("PATH_TO_SWAN");
        } catch (Exception e) {
            System.err.println("Error: PATH_TO_SWAN path not set! Exiting...");
            e.printStackTrace();
        }

        // Try to load both dylib and so (instead of checking OS).
        try {
            System.load(SWANDir + sharedDir + libName + ".dylib");
        } catch (UnsatisfiedLinkError dylibException) {
            try {
                System.load(SWANDir + sharedDir + libName + ".so");
            } catch (UnsatisfiedLinkError soException) {
                System.err.println("Could not find shared library!");
                soException.printStackTrace();
            }
        }
    }
}
