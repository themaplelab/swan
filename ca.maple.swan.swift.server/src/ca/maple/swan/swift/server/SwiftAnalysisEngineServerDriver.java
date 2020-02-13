//===--- SwiftAnalysisEngineServerDriver.java ----------------------------===//
//
// This source file is part of the SWAN open source project
//
// Copyright (c) 2019 Maple @ University of Alberta
// All rights reserved. This program and the accompanying materials (unless
// otherwise specified by a license inside of the accompanying material)
// are made available under the terms of the Eclipse Public License v2.0
// which accompanies this distribution, and is available at
// http://www.eclipse.org/legal/epl-v20.html
//
//===---------------------------------------------------------------------===//

package ca.maple.swan.swift.server;

import ca.maple.swan.swift.client.SwiftAnalysisEngine;
import ca.maple.swan.swift.translator.RawData;
import ca.maple.swan.swift.translator.wala.SwiftToCAstTranslator;
import ca.maple.swan.swift.translator.wala.SwiftToCAstTranslatorFactory;
import com.ibm.wala.cast.ir.ssa.AstIRFactory;
import com.ibm.wala.cast.js.client.JavaScriptAnalysisEngine;
import com.ibm.wala.cast.js.ipa.modref.JavaScriptModRef;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.SourceURLModule;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer;
import com.ibm.wala.ssa.IRFactory;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.util.NullProgressMonitor;
import com.ibm.wala.util.collections.HashSetFactory;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public class SwiftAnalysisEngineServerDriver {

    static final boolean PRINT = true;

    private static final SSAOptions options = new SSAOptions();
    private static final IRFactory irFactory = AstIRFactory.makeDefaultFactory();

    protected static SourceURLModule getScript(String name) throws IOException {
        File f = new File(name);
        if (f.exists()) {
            return new SourceURLModule(f.toURI().toURL());
        } else if (new File((name.substring(name.indexOf("/")+1).trim())).exists()) {
            return new SourceURLModule((new File((name.substring(name.indexOf("/")+1).trim())).toURI().toURL()));
        } else {
            throw new IOException(String.format("Script name (%s) is not a valid file!", name));
        }
    }

    protected static JavaScriptAnalysisEngine createEngine() throws IllegalArgumentException {
        JavaScriptAnalysisEngine engine = new SwiftAnalysisEngine.SwiftPropagationJavaScriptAnalysisEngine();
        engine.setTranslatorFactory(new SwiftToCAstTranslatorFactory());
        return engine;

    }

    protected static JavaScriptAnalysisEngine makeEngine(JavaScriptAnalysisEngine engine, String... name) throws IllegalArgumentException, IOException {
        Set<Module> modules = HashSetFactory.make();
        for(String n : name) {
            modules.add(getScript(n));
        }
        engine.setModuleFiles(modules);
        return engine;
    }

    protected static JavaScriptAnalysisEngine makeEngine(String... name) throws IllegalArgumentException, IOException {
        return makeEngine(createEngine(), name);
    }

    static void dumpCHA(IClassHierarchy cha) {
        System.out.println("*** DUMPING CHA... ***");
        for (IClass c: cha) {
            System.out.println("<CLASS>"+c+"</CLASS");
            for (IMethod m: c.getDeclaredMethods()) {
                System.out.println("<METHOD>"+m+"</METHOD");
                System.out.println("<# ARGUMENTS>"+m.getNumberOfParameters()+"</# ARGUMENTS>");
                //noinspection unchecked
                System.out.println(irFactory.makeIR(m, Everywhere.EVERYWHERE, options));
            }
        }
        System.out.println("*** ...FINISHED DUMPING CHA ***\n");
    }

    public static SDG<InstanceKey> generateSDG(String[] args) throws Exception {
        JavaScriptAnalysisEngine Engine;

        RawData data = new RawData(args, new CAstImpl());

        SwiftToCAstTranslator.setRawData(data);

        String module = data.setup();

        Engine = makeEngine(module);
        CallGraphBuilder builder = Engine.defaultCallGraphBuilder();
        CallGraph CG = builder.makeCallGraph(Engine.getOptions(), new NullProgressMonitor());

        if (PRINT) {
            dumpCHA(CG.getClassHierarchy());
        }

        @SuppressWarnings("unchecked") SDG<InstanceKey> sdg = new SDG<InstanceKey>(CG, builder.getPointerAnalysis(), new JavaScriptModRef<>(), Slicer.DataDependenceOptions.NO_EXCEPTIONS, Slicer.ControlDependenceOptions.NONE);

        return sdg;

    }
}
