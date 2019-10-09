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

import ca.maple.swan.swift.taint.TaintAnalysis;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.slicer.SDG;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import jdk.nashorn.internal.parser.JSONParser;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import static java.lang.System.exit;

public class Server {

    // TODO: Better exception handling across SWAN. Perhaps have some kind of
    //  global optional exception listener.

    static SDG<InstanceKey> sdg;

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
                        System.out.println("Generating SDG (includes compilation)...");
                        JSONArray jsonArgs = (JSONArray)args[0];
                        sdg = SwiftAnalysisEngineServerDriver.generateSDG(JSONArrayToJavaStringArray(jsonArgs));
                        socket.emit("generatedSDG");
                        System.out.println("Done generating SDG");
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
                    System.out.println("Running taint analysis...");
                    try {
                        JSONObject sss = (JSONObject)args[0];
                        JSONArray sources = (JSONArray)sss.get("Sources");
                        JSONArray sinks = (JSONArray)sss.get("Sinks");
                        JSONArray sanitizers = (JSONArray)sss.get("Sanitizers");
                        ArrayList<ArrayList<CAstSourcePositionMap.Position>> paths = TaintAnalysis.doTaintAnalysis(
                                JSONArrayToJavaStringArray(sources),
                                JSONArrayToJavaStringArray(sinks),
                                JSONArrayToJavaStringArray(sanitizers)
                        );
                        JSONObject result = pathsToJSON(paths);
                        System.out.println("Returning taint analysis results...");
                        socket.emit("taintAnalysisResults", result);
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

    private static JSONObject pathsToJSON(ArrayList<ArrayList<CAstSourcePositionMap.Position>> paths) throws JSONException {
        // TEMPORARY DUMMY RESULT
        return new JSONObject("{\n" +
                "\"paths\": \n" +
                "\t[\n" +
                "\t\t{ \n" +
                "\t\t\"pathName\": \"path1\",\n" +
                "\t\t\"elements\": \n" +
                "\t\t\t[\n" +
                "\t\t\t\t{\n" +
                "\t\t\t\t\t\"file\" : \"/Users/tiganov/Documents/CS/proj/TestMultiFile/TestMultiFile/main.swift\",\n" +
                "\t\t\t\t\t\"position\" : \"testPosition\"\n" +
                "\t\t\t\t},\n" +
                "\t\t\t\t{\n" +
                "\t\t\t\t\t\"file\" : \"/Users/tiganov/Documents/CS/proj/TestMultiFile/TestMultiFile/main.swift\",\n" +
                "\t\t\t\t\t\"position\" : \"testPosition\"\n" +
                "\t\t\t\t},\n" +
                "\t\t\t\t{\n" +
                "\t\t\t\t\t\"file\" : \"/Users/tiganov/Documents/CS/proj/TestMultiFile/TestMultiFile/secondFile.swift\",\n" +
                "\t\t\t\t\t\"position\" : \"testPosition\"\n" +
                "\t\t\t\t}\n" +
                "\t\t\t]\n" +
                "\t\t}\n" +
                "\t]\n" +
                "}");
    }

}

