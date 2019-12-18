//===--- Server.java -----------------------------------------------------===//
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

import ca.maple.swan.swift.taint.TaintAnalysisDriver;
import ca.maple.swan.swift.translator.SwiftToCAstTranslator;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.slicer.SDG;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.List;

import static java.lang.System.exit;

public class Server {

    // TODO: Better exception handling across SWAN to report back to the frontend (extension).

    static SDG<InstanceKey> sdg = null;

    // Set to false to only translate to SILIR (for debugging).
    private static boolean GENERATE_SDG = false;

    public static void main(String[] args) throws IllegalArgumentException {

        System.out.println("Server started");

        try {
            Socket socket = IO.socket("http://localhost:4040");
            socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    System.out.println("Connected");
                }

            }).on("generateSDG", new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    try {
                        JSONArray jsonArgs = (JSONArray)args[0];

                        if (GENERATE_SDG) {
                            System.out.println("Generating SDG (includes compilation)...");
                            sdg = SwiftAnalysisEngineServerDriver.generateSDG(JSONArrayToJavaStringArray(jsonArgs));
                            socket.emit("generatedSDG");
                            System.out.println("Done generating SDG");
                        } else {
                            System.out.println("GENERATE_SDG == false, only translating to SILIR");
                            SwiftToCAstTranslator swiftToCAstTranslator = new SwiftToCAstTranslator();
                            String[] modules = swiftToCAstTranslator.doTranslation(JSONArrayToJavaStringArray(jsonArgs));
                            swiftToCAstTranslator.translateToSILIR();
                            socket.emit("generatedSDG");
                        }
                    } catch (Exception e) {
                        // TODO: This error emit doesn't seem to work.
                        socket.emit("error", e);
                        System.err.println("Could not generate SDG");
                        e.printStackTrace();
                    }
                }

            }).on("runTaintAnalysis", new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    try {
                        if (GENERATE_SDG && sdg != null) {
                            System.out.println("Running taint analysis...");
                            JSONObject sss = (JSONObject)args[0];
                            JSONArray sources = (JSONArray)sss.get("Sources");
                            JSONArray sinks = (JSONArray)sss.get("Sinks");
                            JSONArray sanitizers = (JSONArray)sss.get("Sanitizers");
                            List<List<CAstSourcePositionMap.Position>> paths = TaintAnalysisDriver.doTaintAnalysis(
                                    sdg,
                                    JSONArrayToJavaStringArray(sources),
                                    JSONArrayToJavaStringArray(sinks),
                                    JSONArrayToJavaStringArray(sanitizers)
                            );
                            JSONObject result = pathsToJSON(paths);
                            JSONArray functions = new JSONArray(SwiftToCAstTranslator.functionNames);
                            result.put("functions", functions);
                            System.out.println("Returning taint analysis results...");
                            socket.emit("taintAnalysisResults", result);
                        } else {
                            System.out.println("GENERATE_SDG == false, no taint analysis");
                        }
                    } catch (Exception e) {
                        socket.emit("error", e);
                        e.printStackTrace();
                    }
                }

            }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    socket.disconnect();
                    System.out.println("Disconnected. Exiting...");
                    exit(0);
                }

            });
            socket.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String[] JSONArrayToJavaStringArray(JSONArray jsonArray) throws JSONException {
        String[] javaArray = new String[jsonArray.length()];
        for (int i = 0; i < javaArray.length; ++i) {
            javaArray[i] = (String)jsonArray.get(i);
        }
        return javaArray;
    }

    private static JSONObject pathsToJSON(List<List<CAstSourcePositionMap.Position>> paths) throws JSONException {
        JSONObject returnObject = new JSONObject();
        JSONArray jsonPaths = new JSONArray();
        int counter = 0;
        for (List<CAstSourcePositionMap.Position> path : paths) {
            JSONObject jsonPath = new JSONObject();
            jsonPath.put("pathName", "path " + counter);
            JSONArray positions = new JSONArray();
            for (CAstSourcePositionMap.Position pos : path) {
                try {
                    JSONObject element = new JSONObject();
                    element.put("file", pos.getURL().toURI().getRawPath());
                    element.put("fl", pos.getFirstLine());
                    element.put("fc", pos.getFirstCol());
                    element.put("ll", pos.getLastLine());
                    element.put("lc", pos.getLastCol());
                    positions.put(element);
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            }
            jsonPath.put("elements", positions);
            jsonPaths.put(jsonPath);
            ++counter;
        }
        returnObject.put("paths", jsonPaths);
        return returnObject;
    }

}

