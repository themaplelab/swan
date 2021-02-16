/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

import boomerang.BackwardQuery;
import boomerang.Boomerang;
import boomerang.DefaultBoomerangOptions;
import boomerang.Query;
import boomerang.results.BackwardBoomerangResults;
import boomerang.scene.*;
import ca.ualberta.maple.swan.ir.*;
import ca.ualberta.maple.swan.ir.canonical.SWIRLPass;
import ca.ualberta.maple.swan.ir.raw.SWIRLGen;
import ca.ualberta.maple.swan.parser.Error;
import ca.ualberta.maple.swan.parser.SILModule;
import ca.ualberta.maple.swan.parser.SILParser;
import ca.ualberta.maple.swan.spds.SWANCallGraph;
import ca.ualberta.maple.swan.spds.SWANInvokeExpr;
import org.junit.jupiter.api.Test;
import wpds.impl.Weight;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;

public class Tests {

    @Test
    // Just smoke test for now.
    void testSPDSTranslation() throws Error, URISyntaxException, Exceptions.IncorrectSWIRLStructureException,
            Exceptions.UnexpectedSILFormatException, Exceptions.UnexpectedSILTypeBehaviourException {
        System.out.println("Testing modules");
        File fileDir = new File(getClass().getClassLoader()
                .getResource("sil/modules/").toURI());
        File[] silFiles = fileDir.listFiles((dir, name) -> name.endsWith(".sil"));
        for (File sil : silFiles) {
            if (!sil.getName().contains("simple")) {
                continue;
            }
            System.out.println("    -> " + sil.getName());
            SILParser parser = new SILParser(sil.toPath());
            SILModule silModule = parser.parseModule();
            // System.out.println(new SILPrinter().print(silModule));
            // System.out.println("============================================");
            Module swirlModule = SWIRLGen.translateSILModule(silModule);
            // System.out.print(new SWIRLPrinter().print(swirlModule));
            // System.out.println("============================================");
            CanModule canSwirlModule = SWIRLPass.runPasses(swirlModule);
            System.out.print(new SWIRLPrinter().print(canSwirlModule, new SWIRLPrinterOptions()));

            SWANCallGraph cg = new SWANCallGraph(canSwirlModule);
            AnalysisScope scope =
                new AnalysisScope(cg) {
                    @Override
                    protected Collection<? extends Query> generate(ControlFlowGraph.Edge edge) {
                        Statement statement = edge.getTarget();
                        if (statement.containsInvokeExpr()) {
                            Val ref = ((SWANInvokeExpr) statement.getInvokeExpr()).getFunctionRef();
                            return Collections.singleton(BackwardQuery.make(edge, ref));
                        }
                        return Collections.emptySet();
                    }
                };
            Boomerang solver = new Boomerang(cg, DataFlowScope.INCLUDE_ALL, new DefaultBoomerangOptions());

            Collection<Query> seeds = scope.computeSeeds();
            for (Query query : seeds) {
                System.out.println("Solving query: " + query);
                BackwardBoomerangResults<Weight.NoWeight> backwardQueryResults =
                        solver.solve((BackwardQuery) query);
                System.out.println("All allocation sites of the query variable are:");
                System.out.println(backwardQueryResults.getAllocationSites());
            }
        }

    }


}
