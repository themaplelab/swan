package ca.maple.swan.swift.taint;

import java.util.Arrays;

public class SSS {

    private static final String[] sources = new String[] {
        // LOCATION MANAGER
        "@objc __C.CLLocationManager.init() -> __C.CLLocationManager",
        "@objc __C.CLLocation.coordinate.getter : __C.CLLocationCoordinate2D",
        "@objc __C.CLLocationManager.location.getter : Swift.Optional<__C.CLLocation>"
    };

    private static final String[] sinks = new String[] {
        // LOGGER (os_log)
        "os.os_log(_: Swift.StaticString, dso: Swift.Optional<Swift.UnsafeRawPointer>, log: __C.OS_os_log, type: __C.os_log_type_t, _: Swift.CVarArg...) -> ()"
    };

    private static final String[] sanitizers = new String[] {

    };

    public static boolean isSource(String name) {
        //noinspection SimplifyStreamApiCallChains
        return Arrays.stream(sources).anyMatch(name::equals);
    }

    public static boolean isSink(String name) {
        //noinspection SimplifyStreamApiCallChains
        return Arrays.stream(sinks).anyMatch(name::equals);
    }

    public static boolean isSanitizer(String name) {
        //noinspection SimplifyStreamApiCallChains
        return Arrays.stream(sanitizers).anyMatch(name::equals);
    }
}
