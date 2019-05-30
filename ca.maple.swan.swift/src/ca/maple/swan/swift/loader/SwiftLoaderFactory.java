package ca.maple.swan.swift.loader;

import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.loader.SingleClassLoaderFactory;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;

public class SwiftLoaderFactory extends SingleClassLoaderFactory {

    @Override
    public ClassLoaderReference getTheReference() {
        return SwiftTypes.swiftLoader;
    }

    @Override
    protected IClassLoader makeTheLoader(IClassHierarchy cha) {
        return new SwiftLoader(cha);
    }
}
