package ca.maple.swan.analysis.drivers;

import ca.maple.swan.analysis.translator.SwiftToCAstTranslator;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.util.CAstPrinter;

public class MainAnalysisDriver {

    static {
        String sharedDir = "/ca.maple.swan.translator/build/libs/swiftWala/shared/";
        String libName = "libswiftWala";

        String SWANDir = "";
        try {
            SWANDir = System.getenv("PATH_TO_SWAN");
        } catch (Exception e) {
            System.err.println("Error: PATH_TO_SWAN path not set! Exiting...");
            System.exit(1);
        }

        // try to load both dylib and so (instead of checking OS)
        try {
            System.load(SWANDir + sharedDir + libName + ".dylib");
        } catch (Exception dylibException) {
            try {
                System.load(SWANDir + sharedDir + libName + ".so");
            } catch (Exception soException) {
                System.err.println("Could not find shared library!");
                soException.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: One input file expected!");
        }
        else {
            try {
                SwiftToCAstTranslator translator = new SwiftToCAstTranslator(args[0]);
                CAstEntity entity = translator.translateToCAst();
                String astString = CAstPrinter.print(entity);
                System.out.println(astString);
            }
            catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
    }
}
