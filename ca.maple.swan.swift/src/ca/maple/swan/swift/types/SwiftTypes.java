package ca.maple.swan.swift.types;

import com.ibm.wala.cast.types.AstTypeReference;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.Atom;

public class SwiftTypes extends AstTypeReference {

    public static final String swiftNameStr = "Swift";

    public static final String swiftLoaderStr = "PythonLoader";

    public static final Atom swiftName = Atom.findOrCreateUnicodeAtom(swiftNameStr);

    public static final Atom swiftLoaderName = Atom.findOrCreateUnicodeAtom(swiftLoaderStr);

    public static final ClassLoaderReference swiftLoader = new ClassLoaderReference(swiftLoaderName, swiftName, null);

    public static final TypeReference Root = TypeReference.findOrCreate(swiftLoader, rootTypeName);

    public static final TypeReference CodeBody = TypeReference.findOrCreate(swiftLoader, functionTypeName);

    // ... TODO
}
