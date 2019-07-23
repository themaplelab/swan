package ca.maple.swan.swift.test;

import ca.maple.swan.swift.translator.SwiftToCAstTranslatorFactory;
import com.ibm.wala.cast.js.client.JavaScriptAnalysisEngine;
import com.ibm.wala.cast.js.types.JavaScriptTypes;
import com.ibm.wala.cast.types.AstMethodReference;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Set;

import com.ibm.wala.cast.ipa.callgraph.CAstCallGraphUtil;
import com.ibm.wala.cast.util.test.TestCallGraphShape;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.SourceURLModule;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.NullProgressMonitor;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.strings.Atom;

public class TestSwiftCallGraphShapeUsingJS extends TestCallGraphShape {
    
    @Override
    public Collection<CGNode> getNodes(CallGraph CG, String functionIdentifier) {
        if (functionIdentifier.contains(":")) {
            String cls = functionIdentifier.substring(0, functionIdentifier.indexOf(":"));
            String name = functionIdentifier.substring(functionIdentifier.indexOf(":")+1);
            return CG.getNodes(MethodReference.findOrCreate(TypeReference.findOrCreate(JavaScriptTypes.jsLoader, TypeName.string2TypeName("L" + cls)), Atom.findOrCreateUnicodeAtom(name), AstMethodReference.fnDesc));
        } else {
            return CG.getNodes(MethodReference.findOrCreate(TypeReference.findOrCreate(JavaScriptTypes.jsLoader, TypeName.string2TypeName("L" + functionIdentifier)), AstMethodReference.fnSelector));
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

    protected JavaScriptAnalysisEngine createEngine() throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
        JavaScriptAnalysisEngine engine = new JavaScriptAnalysisEngine.PropagationJavaScriptAnalysisEngine();
        engine.setTranslatorFactory(new SwiftToCAstTranslatorFactory());
        return engine;

    }

    protected JavaScriptAnalysisEngine makeEngine(JavaScriptAnalysisEngine engine, String... name) throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
        Set<Module> modules = HashSetFactory.make();
        for(String n : name) {
            modules.add(getScript(n));
        }

        engine.setModuleFiles(modules);
        return engine;
    }

    protected JavaScriptAnalysisEngine makeEngine(String... name) throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
        return makeEngine(createEngine(), name);
    }

    static void dump(CallGraph CG) {
        System.out.println("*** DUMPING CG ***");
        StringBuffer sb = new StringBuffer();
        for(CGNode n : CG) {
            sb.append(n.getIR()).append("\n");
        }
        System.out.println(sb);
        System.out.println("*** FINISHED DUMPING CG ***");
    }

    public static void main(String[] args) throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {

        TestSwiftCallGraphShapeUsingJS driver = new TestSwiftCallGraphShapeUsingJS();

        JavaScriptAnalysisEngine Engine;
        try {
            Engine = driver.makeEngine(args[0]);
            CallGraphBuilder builder = Engine.defaultCallGraphBuilder();
            CallGraph CG = builder.makeCallGraph(Engine.getOptions(), new NullProgressMonitor());

            CAstCallGraphUtil.AVOID_DUMP = false;
            dump(CG);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
