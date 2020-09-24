/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

import java.io.*;
import java.util.ArrayList;

public class TestUtils {

    // HELPERS
    //
    // For now, do a bunch of janky string manipulations to make the output
    // match the expected. Should probably (later) write a customer comparator
    // function that doesn't report inequality due to things like extra
    // newlines.

    // Account for any known transformations that the parser does,
    // such as superficial type conversions, here.
    static String doReplacements(String inst) {
        //inst = inst.replaceAll("\\[([a-zA-Z0-9]+)\\]", "Array<$1>");
        if (inst.startsWith("func ")) {
            return "";
        }
        if (inst.contains("{ get set }")) {
            return "";
        }
        if (inst.startsWith("sil_property ")) { // Remove this because it's not supported yet
            return "";
        }
        inst = inst.split("//")[0];
        inst = inst.replaceAll("\\s+$", ""); // right trim
        return inst;
    }

    static String readFile(File file) throws IOException {
        return readFile(file, true);
    }

    static String readFile(File file, Boolean emptyLines) throws IOException {
        InputStream in = new FileInputStream(file);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder result = new StringBuilder();
        ArrayList<String> scopes = new ArrayList<String>();
        String line;
        while((line = reader.readLine()) != null) {
            // Empty line, preserve empty lines
            if (line.trim().isEmpty() && !result.toString().endsWith("\n\n") && emptyLines) {
                result.append(System.lineSeparator());
                continue;
            }
            line = doReplacements(line);
            // For commented out lines
            if (line.trim().length() > 0) {
                if (line.trim().startsWith("sil_scope")) {
                    scopes.add(line);
                } else {
                    result.append(line);
                    result.append(System.lineSeparator());
                }
            }
        }
        if (!scopes.isEmpty()) {
            // Assume "sil_canonical\n" is the first line
            result.insert(result.indexOf(System.lineSeparator()),
                    System.lineSeparator() + System.lineSeparator()
                            + String.join(System.lineSeparator() + System.lineSeparator(), scopes));
        }
        return result.toString();
    }
}
