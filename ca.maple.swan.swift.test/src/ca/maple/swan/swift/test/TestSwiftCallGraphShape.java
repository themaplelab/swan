package ca.maple.swan.swift.test;

import ca.maple.swan.swift.client.SwiftAnalysisEngine;
import ca.maple.swan.swift.types.SwiftTypes;
import com.ibm.wala.cast.types.AstMethodReference;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Scanner;
import java.util.Set;

import com.ibm.wala.cast.ipa.callgraph.CAstCallGraphUtil;
import com.ibm.wala.cast.util.test.TestCallGraphShape;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.SourceURLModule;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.NullProgressMonitor;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.strings.Atom;

public class TestSwiftCallGraphShape extends TestCallGraphShape {

    @Override
    public Collection<CGNode> getNodes(CallGraph CG, String functionIdentifier) {
        if (functionIdentifier.contains(":")) {
            String cls = functionIdentifier.substring(0, functionIdentifier.indexOf(":"));
            String name = functionIdentifier.substring(functionIdentifier.indexOf(":")+1);
            return CG.getNodes(MethodReference.findOrCreate(TypeReference.findOrCreate(SwiftTypes.swiftLoader, TypeName.string2TypeName("L" + cls)), Atom.findOrCreateUnicodeAtom(name), AstMethodReference.fnDesc));
        } else {
            return CG.getNodes(MethodReference.findOrCreate(TypeReference.findOrCreate(SwiftTypes.swiftLoader, TypeName.string2TypeName("L" + functionIdentifier)), AstMethodReference.fnSelector));
        }
    }

    protected SourceURLModule getScript(String name) throws IOException {
        try {
            File f = new File(name);
            if (f.exists()) {
                return new SourceURLModule(f.toURI().toURL());
            } else {
                throw new IOException("Script name is not a valid file!");
            }
        } catch (MalformedURLException e) {
            return new SourceURLModule(getClass().getClassLoader().getResource(name));
        }
    }

    protected SwiftAnalysisEngine<?> createEngine() throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
        return new SwiftAnalysisEngine<Void>();
    }

    protected SwiftAnalysisEngine<?> makeEngine(SwiftAnalysisEngine<?> engine, String... name) throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
        Set<Module> modules = HashSetFactory.make();
        for(String n : name) {
            modules.add(getScript(n));
        }

        engine.setModuleFiles(modules);
        return engine;
    }

    protected SwiftAnalysisEngine<?> makeEngine(String... name) throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
        return makeEngine(createEngine(), name);
    }

    protected CallGraph process(String... name) throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
        return makeEngine(name).buildDefaultCallGraph();
    }

    StringBuffer dump(CallGraph CG) {
        StringBuffer sb = new StringBuffer();
        for(CGNode n : CG) {
            sb.append(n.getIR()).append("\n");
        }
        return sb;
    }

    public static void main(String[] args) throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {

        TestSwiftCallGraphShape driver = new TestSwiftCallGraphShape();

        SwiftAnalysisEngine<?> Engine;
        try {
            Engine = driver.makeEngine(args[0]);
        } catch (Exception e) {
            Engine = new SwiftAnalysisEngine<Void>(); // Make IDE happy.
            System.out.println("Could not create SwiftAnalysisEngine!");
            e.printStackTrace();
            System.exit(1);
        }

        CallGraphBuilder<? super InstanceKey> builder = Engine.defaultCallGraphBuilder();
        CallGraph CG = builder.makeCallGraph(Engine.getOptions(), new NullProgressMonitor());

        CAstCallGraphUtil.AVOID_DUMP = false;
        CAstCallGraphUtil.dumpCG(((SSAPropagationCallGraphBuilder)builder).getCFAContextInterpreter(), Engine.getPointerAnalysis(), CG);
    }
}
