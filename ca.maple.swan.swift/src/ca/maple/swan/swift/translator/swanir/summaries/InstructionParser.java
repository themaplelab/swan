//===--- InstructionParser.java ------------------------------------------===//
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

package ca.maple.swan.swift.translator.swanir.summaries;

import ca.maple.swan.swift.translator.swanir.Function;
import ca.maple.swan.swift.translator.swanir.context.InstructionContext;
import ca.maple.swan.swift.translator.swanir.instructions.*;
import com.ibm.wala.util.collections.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InstructionParser {

    private static final String U_TYPE = "unknown";

    HashMap<Integer, String> vars = new HashMap<>();

    public InstructionParser() {

    }

    public SWANIRInstruction parse(String line, InstructionContext ic) {
        SWANIRInstruction inst;

        inst = parseNewGlobalInstruction(line, ic);
        if (inst != null) { return inst; }

        inst = parseNewInstruction(line, ic);
        if (inst != null) { return inst; }

        inst = parseAssignGlobalInstruction(line, ic);
        if (inst != null) { return inst; }

        inst = parseLiteralInstruction(line, ic);
        if (inst != null) { return inst; }

        inst = parseFunctionRefInstruction(line, ic);
        if (inst != null) { return inst; }

        inst = parseApplyInstruction(line, ic);
        if (inst != null) { return inst; }

        inst = parseFieldReadInstruction(line, ic);
        if (inst != null) { return inst; }

        inst = parseFieldWriteInstruction(line, ic);
        if (inst != null) { return inst; }

        inst = parseBinaryOperatorInstruction(line, ic);
        if (inst != null) { return inst; }

        inst = parseReturnInstruction(line, ic);
        if (inst != null) { return inst; }

        inst = parseNewArrayTupleInstruction(line, ic);
        if (inst != null) { return inst; }

        inst = parsePrintInstruction(line, ic);
        if (inst != null) { return inst; }

        System.err.println("ERROR: Could not parse : " + line);

        return inst;
    }

    private static Matcher checkMatch(String line, String pat) {
        Pattern pattern = Pattern.compile(pat);
        Matcher matcher = pattern.matcher(line);
        if (matcher.matches()) {
            return matcher;
        }
        return null;
    }

    public String var(int id) {
        if (!vars.containsKey(id)) {
            vars.put(id, UUID.randomUUID().toString());
        }
        return vars.get(id);
    }

    private ArrayList<String> parseArgs(String args) {
        ArrayList<String> parsedArgs = new ArrayList<>();
        String[] seperated = args.split(",");
        for (String s : seperated) {
            s = s.trim();
            Pattern pattern = Pattern.compile("v([0-9]+)");
            Matcher matcher = pattern.matcher(s);
            if (matcher.matches()) {
                int v = Integer.parseInt(matcher.group(1));
                parsedArgs.add(var(v));
            }
        }
        return parsedArgs;
    }

    private Pair<Object, String> parseLiteral(String literal) {
        try {
            return Pair.make(Integer.valueOf(literal), "$Int32");
        } catch (Exception e) { }

        try {
            return Pair.make(Float.valueOf(literal), "$Float");
        } catch (Exception e) { }

        return Pair.make(literal, "$String");
    }

    private SWANIRInstruction parseNewGlobalInstruction(String line, InstructionContext ic) {
        Matcher matcher = checkMatch(line, "new\\s+global\\s+(.*[^\\s])\\s*:\\s*(.*[^\\s])");
        if (matcher != null) {
            String name = matcher.group(1);
            String type = matcher.group(2);
            return new NewGlobalInstruction(name + " : " + type, type, ic);
        }
        return null;
    }

    // Must come before NewInstruction
    private SWANIRInstruction parseNewArrayTupleInstruction(String line, InstructionContext ic) {
        Matcher matcher = checkMatch(line, "v([0-9]+)\\s*:=\\s*new array tuple\\s*");
        if (matcher != null) {
            int result = Integer.parseInt(matcher.group(1));
            return new NewArrayTupleInstruction(var(result), U_TYPE, ic);
        }
        return null;
    }

    private SWANIRInstruction parseNewInstruction(String line, InstructionContext ic) {
        Matcher matcher = checkMatch(line, "v([0-9]+)\\s*:=\\s*new\\s+(.*)");
        if (matcher != null) {
            int result = Integer.parseInt(matcher.group(1));
            String type = matcher.group(2);
            return new NewInstruction(var(result), type, ic);
        }
        return null;
    }

    private SWANIRInstruction parseAssignGlobalInstruction(String line, InstructionContext ic) {
        Matcher matcher = checkMatch(line, "v([0-9]+)\\s*:=\\s*(.*[^\\s])\\s*:\\s*(.*[^\\s])");
        if (matcher != null) {
            int result = Integer.parseInt(matcher.group(1));
            String name = matcher.group(2);
            String type = matcher.group(3);
            return new AssignGlobalInstruction(var(result), type, name + " : " + type, ic);
        }
        return null;
    }

    private SWANIRInstruction parseLiteralInstruction(String line, InstructionContext ic) {
        Matcher matcher = checkMatch(line, "v([0-9]+)\\s*:=\\s*#(.*[^\\s])");
        if (matcher != null) {
            int result = Integer.parseInt(matcher.group(1));
            Pair<Object, String> literal = parseLiteral(matcher.group(2));
            return new LiteralInstruction(literal.fst, var(result), literal.snd, ic);
        }
        return null;
    }

    private SWANIRInstruction parseFunctionRefInstruction(String line, InstructionContext ic) {
        Matcher matcher = checkMatch(line, "v([0-9]+)\\s*:=\\s*func_ref\\s+(.*[^\\s])");
        if (matcher != null) {
            int result = Integer.parseInt(matcher.group(1));
            String func = matcher.group(2);
            Function f = ic.bc.fc.pc.getFunction(func);
            return f == null
                    ? new BuiltinInstruction(func, var(result), U_TYPE, ic)
                    : new FunctionRefInstruction(var(result), U_TYPE, f, ic);
        }
        return null;
    }

    private SWANIRInstruction parseApplyInstruction(String line, InstructionContext ic) {
        Matcher matcher = checkMatch(line, "v([0-9]+)\\s*:=\\s*v([0-9]+)\\((.*)\\)");
        if (matcher != null) {
            int result = Integer.parseInt(matcher.group(1));
            int funcValue = Integer.parseInt(matcher.group(2));
            String args = matcher.group(3);
            return new ApplyInstruction(var(funcValue), var(result), U_TYPE, parseArgs(args), ic);
        }
        return null;
    }

    private SWANIRInstruction parseFieldReadInstruction(String line, InstructionContext ic) {
        Matcher matcher = checkMatch(line, "v([0-9]+)\\s*:=\\s*v([0-9]+)\\.([^\\s].*[^\\s])");
        if (matcher != null) {
            int result = Integer.parseInt(matcher.group(1));
            int operand = Integer.parseInt(matcher.group(2));
            String field = matcher.group(3);
            return new FieldReadInstruction(var(result), U_TYPE, var(operand), field, ic);
        }
        matcher = checkMatch(line, "v([0-9]+)\\s*:=\\s*v([0-9]+)\\.v([0-9]+)");
        if (matcher != null) {
            int result = Integer.parseInt(matcher.group(1));
            int operand = Integer.parseInt(matcher.group(2));
            int field = Integer.parseInt(matcher.group(3));
            return new FieldReadInstruction(var(result), U_TYPE, var(operand), var(field), true, ic);
        }
        return null;
    }

    private SWANIRInstruction parseFieldWriteInstruction(String line, InstructionContext ic) {
        Matcher matcher = checkMatch(line, "v([0-9]+)\\.([^\\s]*)\\s*:=\\s*v([0-9]+)");
        if (matcher != null) {
            int result = Integer.parseInt(matcher.group(1));
            String field = matcher.group(2);
            int operand = Integer.parseInt(matcher.group(3));
            return new FieldWriteInstruction(var(result), field, var(operand), ic);
        }
        matcher = checkMatch(line, "v([0-9]+)\\.v([0-9]+)\\s*:=\\s*v([0-9]+)");
        if (matcher != null) {
            int result = Integer.parseInt(matcher.group(1));
            int field =  Integer.parseInt(matcher.group(2));
            int operand = Integer.parseInt(matcher.group(3));
            return new FieldWriteInstruction(var(result), var(field), var(operand), true, ic);
        }
        return null;
    }

    private SWANIRInstruction parseBinaryOperatorInstruction(String line, InstructionContext ic) {
        Matcher matcher = checkMatch(line, "v([0-9]+)\\s*:=\\s*v([0-9]+)\\s*([^\\.\\(\\s]*)\\s*v([0-9]*)");
        if (matcher != null) {
            int result = Integer.parseInt(matcher.group(1));
            int lhs =  Integer.parseInt(matcher.group(2));
            String operator = matcher.group(3);
            int rhs =  Integer.parseInt(matcher.group(4));
            if (ic.valueTable().has(var(result))) {
                return new BinaryOperatorInstruction(var(result), operator, var(lhs), var(rhs), ic);
            }
            return new BinaryOperatorInstruction(var(result), U_TYPE, operator, var(lhs), var(rhs), ic);
        }
        return null;
    }

    private SWANIRInstruction parseReturnInstruction(String line, InstructionContext ic) {
        Matcher matcher = checkMatch(line, "return\\s*v([0-9]+)");
        if (matcher != null) {
            int val = Integer.parseInt(matcher.group(1));
            return new ReturnInstruction(var(val), ic);
        }
        matcher = checkMatch(line, "return");
        if (matcher != null) {
            return new ReturnInstruction(ic);
        }
        return null;
    }

    private SWANIRInstruction parsePrintInstruction(String line, InstructionContext ic) {
        Matcher matcher = checkMatch(line, "print\\s+v([0-9]+)");
        if (matcher != null) {
            int result = Integer.parseInt(matcher.group(1));
            return new PrintInstruction(var(result), ic);
        }
        return null;
    }

    // TODO: Finish
}
