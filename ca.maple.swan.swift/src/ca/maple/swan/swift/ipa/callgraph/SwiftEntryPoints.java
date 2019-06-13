package ca.maple.swan.swift.ipa.callgraph;

import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.ipa.callgraph.ScriptEntryPoints;
import com.ibm.wala.cast.loader.DynamicCallSiteReference;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.IClassHierarchy;

public class SwiftEntryPoints extends ScriptEntryPoints {

    @Override
    protected CallSiteReference makeScriptSite(IMethod m, int pc) {
        return new DynamicCallSiteReference(SwiftTypes.CodeBody, pc);
    }

    public SwiftEntryPoints(IClassHierarchy cha, IClassLoader loader) {
        super(cha, loader.lookupClass(SwiftTypes.Script.getName()));
    }
}