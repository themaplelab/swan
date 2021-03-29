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
import ca.ualberta.maple.swan.ir.Error;
import ca.ualberta.maple.swan.ir.*;
import ca.ualberta.maple.swan.ir.canonical.SWIRLPass;
import ca.ualberta.maple.swan.spds.SWANCallGraph;
import ca.ualberta.maple.swan.spds.SWANInvokeExpr;
import ca.ualberta.maple.swan.spds.SWANStatement;
import ca.ualberta.maple.swan.utils.Logging;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import wpds.impl.Weight;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class SPDSTests {

    @Test
    // Just smoke test for now.
    // For now, this test is here but should really be in .test later
    void testSPDSTranslation() throws URISyntaxException, Error {
        File swirlFile = new File(Objects.requireNonNull(getClass().getClassLoader()
                .getResource("playground/spds.swirl")).toURI());
        Logging.printInfo("(Playground) testSWIRL: Testing " + swirlFile.getName());
        Module parsedModule = new SWIRLParser(swirlFile.toPath()).parseModule();
        CanModule module = new SWIRLPass().runPasses(parsedModule);
        SWIRLPrinterOptions opts = new SWIRLPrinterOptions();
        String result = new SWIRLPrinter().print(module, opts);
        System.out.print(result);

        SWANCallGraph cg = new SWANCallGraph(module);
        Document doc = Jsoup.parse(cg.methods().values().iterator().next().toString());   // pretty print HTML
        System.out.println(doc.body().toString());
        // cg.constructStaticCG();

        AnalysisScope scope =
                new AnalysisScope(cg) {
                    @Override
                    protected Collection<? extends Query> generate(ControlFlowGraph.Edge edge) {
                        Statement statement = edge.getStart();
                        if (statement.containsInvokeExpr()) {
                            // Val ref = ((SWANInvokeExpr) statement.getInvokeExpr()).getFunctionRef();
                            Val ref = statement.getInvokeExpr().getArg(0);
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
