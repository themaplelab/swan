package ca.maple.swan.swift.server;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.json.JSONObject;

import static java.lang.System.exit;

public class Server {


    public static void main(String[] args) throws IllegalArgumentException {


        System.out.println("Hello World!");

        try {
            Socket socket = IO.socket("http://localhost:4040");
            socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                }

            }).on("runTaintAnalysis", new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    try {
                        JSONObject obj = new JSONObject("{\n" +
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
                        socket.emit("taintAnalysisResults", obj);
                    } catch (Exception e) {}
                }

            }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    socket.disconnect();
                    System.err.println("test");
                    exit(0);
                }

            });
            socket.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

