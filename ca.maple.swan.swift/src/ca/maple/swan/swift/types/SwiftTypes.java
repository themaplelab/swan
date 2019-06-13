package ca.maple.swan.swift.types;

import com.ibm.wala.cast.tree.CAstType;
import com.ibm.wala.cast.types.AstTypeReference;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.strings.Atom;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SwiftTypes extends AstTypeReference {

    public static final String swiftNameStr = "Swift";

    public static final String swiftLoaderStr = "SwiftLoader";

    public static final Atom swiftName = Atom.findOrCreateUnicodeAtom(swiftNameStr);

    public static final Atom swiftLoaderName = Atom.findOrCreateUnicodeAtom(swiftLoaderStr);

    public static final ClassLoaderReference swiftLoader = new ClassLoaderReference(swiftLoaderName, swiftName, null);

    public static final TypeReference Root = TypeReference.findOrCreate(swiftLoader, rootTypeName);

    public static final TypeReference Script = TypeReference.findOrCreate(swiftLoader, "LScript");

    public static final TypeReference CodeBody = TypeReference.findOrCreate(swiftLoader, functionTypeName);

    public static final TypeReference FunctionRef = TypeReference.findOrCreate(swiftLoader, "LFunctionRef");

    public static final TypeReference GlobalAddr = TypeReference.findOrCreate(swiftLoader, "LGlobalAddr");

    public static final TypeReference String = TypeReference.findOrCreate(swiftLoader, "LString");

    public static final TypeReference Integer = TypeReference.findOrCreate(swiftLoader, "LInteger");

    public static final TypeReference Float = TypeReference.findOrCreate(swiftLoader, "LFloat");

    // Boolean does not exist since it is represented as an Integer in translation

    private static Map<String, CAstType> CAstTypes = new HashMap<String, CAstType>();

    public static CAstType findOrCreateCAstType(String typeName) {
        if (CAstTypes.containsKey(typeName)) {
            return CAstTypes.get(typeName);
        } else {
            CAstType newType = new CAstType() {

                @Override
                public java.lang.String getName() {
                    return typeName;
                }

                @Override
                public Collection<CAstType> getSupertypes() {
                    return null;
                }
            };
            CAstTypes.put(typeName, newType);
            return newType;
        }
    }
}
