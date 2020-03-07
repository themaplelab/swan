//===--- SummaryParser.java ----------------------------------------------===//
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

import ca.maple.swan.swift.translator.swanir.BasicBlock;
import ca.maple.swan.swift.translator.swanir.Function;
import ca.maple.swan.swift.translator.swanir.context.BlockContext;
import ca.maple.swan.swift.translator.swanir.context.FunctionContext;
import ca.maple.swan.swift.translator.swanir.context.InstructionContext;
import ca.maple.swan.swift.translator.swanir.context.ProgramContext;
import ca.maple.swan.swift.translator.swanir.instructions.SWANIRInstruction;
import ca.maple.swan.swift.translator.swanir.values.Argument;

import java.io.*;

import java.net.URL;

import java.security.CodeSource;
import java.util.ArrayList;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/*
 * This is a horrible, inefficient SWANIR parser.
 */

public class SummaryParser {

    public static ArrayList<Function> parseSummaries(ProgramContext pc) {
        return parseLines(readFiles(getSummaryFiles()), pc);
    }

    private static ArrayList<Function> parseLines(ArrayList<String> lines, ProgramContext pc) {
        ArrayList<Function> summaries = new ArrayList<>();

        ArrayList<String> currentFunction = new ArrayList<>();

        boolean foundFunction = false;
        String curReturnType = "";
        String curFunctionName = "";
        String curArgs = "";

        for (String line : lines) {
            if (line.equals("}")) {
                Function f = parseFunction(curReturnType, curFunctionName, curArgs, currentFunction, pc);
                summaries.add(f);
                foundFunction = false;
                currentFunction = new ArrayList<>();
                continue;
            } else {
                Pattern pattern = Pattern.compile("\\s*func\\s+(.*)\\s+`(.*)`\\((.*)\\)\\s*\\{");
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    foundFunction = true;
                    curReturnType = matcher.group(1);
                    curFunctionName = matcher.group(2);
                    curArgs = matcher.group(3);
                    continue;
                }
            }
            if (foundFunction) {
                currentFunction.add(line);
            }
        }

        return summaries;
    }

    private static Function parseFunction(String returnType, String name, String rawArgs, ArrayList<String> lines, ProgramContext pc) {

        /*
        Function f = new Function(name, returnType, null, new ArrayList<>(), Function.Type.STUB);
        FunctionContext fc = new FunctionContext(f, pc);
        BasicBlock bb = new BasicBlock(0);
        BlockContext bc = new BlockContext(bb, fc);
        InstructionContext C = new InstructionContext(bc);
        String randomName = UUID.randomUUID().toString();
        bb.addInstruction(new NewInstruction(randomName, returnType, C));
        bb.addInstruction(new ReturnInstruction(randomName, C));
        f.addBlock(bb);
        return f;

         */


        InstructionParser instructionParser = new InstructionParser();

        ArrayList<Argument> args = parseArgs(rawArgs, instructionParser);

        Function function = new Function(name, returnType, null, args, Function.Type.SUMMARY);

        FunctionContext fc = new FunctionContext(function, pc);

        ArrayList<BasicBlock> blocks = new ArrayList<>();

        ArrayList<String> currentBlock = new ArrayList<>();

        boolean foundBlock = false;
        int curBlock = 0;
        String curArgs = "";

        for (String line : lines) {
            Pattern pattern = Pattern.compile("\\s*bb([0-9]+)\\((.*)\\)\\s*:");
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                if (foundBlock) {
                    BasicBlock bb = parseBlock(curBlock, curArgs, currentBlock, fc, instructionParser);
                    blocks.add(bb);
                    currentBlock = new ArrayList<>();
                } else {
                    foundBlock = true;
                }
                curBlock = Integer.parseInt(matcher.group(1));
                curArgs = matcher.group(2);
                continue;
            }
            pattern = Pattern.compile("bb([0-9])\\s*:\\s*");
            matcher = pattern.matcher(line);
            if (matcher.matches()) {
                if (foundBlock) {
                    BasicBlock bb = parseBlock(curBlock, curArgs, currentBlock, fc, instructionParser);
                    blocks.add(bb);
                    currentBlock = new ArrayList<>();
                } else {
                    foundBlock = true;
                }
                curBlock = Integer.parseInt(matcher.group(1));
                continue;
            }
            if (foundBlock) {
                currentBlock.add(line);
            }
        }
        if (foundBlock && currentBlock.size() > 0) {
            BasicBlock bb = parseBlock(curBlock, curArgs, currentBlock, fc, instructionParser);
            blocks.add(bb);
        }

        for (BasicBlock bb : blocks) {
            function.addBlock(bb);
        }

        return function;
    }

    private static ArrayList<Argument> parseArgs(String rawArgs, InstructionParser ip) {
        ArrayList<Argument> args = new ArrayList<>();
        String[] pairs = rawArgs.split(",");
        for (String pair : pairs) {
            pair = pair.trim();
            String[] nameAndType = pair.split(":");
            if (nameAndType.length == 2) {
                Pattern pattern = Pattern.compile("v([0-9]+)");
                Matcher matcher = pattern.matcher(nameAndType[0].trim());
                if (matcher.matches()) {
                    int arg = Integer.parseInt(matcher.group(1));
                    args.add(new Argument(ip.var(arg), nameAndType[1].trim()));
                }
            }
        }
        return args;
    }

    private static BasicBlock parseBlock(int number, String rawArgs, ArrayList<String> lines,
                                         FunctionContext fc, InstructionParser instructionParser) {

        ArrayList<Argument> args = parseArgs(rawArgs, instructionParser);

        BasicBlock basicBlock = new BasicBlock(number, args);

        BlockContext bc = new BlockContext(basicBlock, fc);

        InstructionContext ic = new InstructionContext(bc, new NullPosition());

        for (String line : lines) {
            SWANIRInstruction inst = instructionParser.parse(line, ic);
            if (inst != null) {
                basicBlock.addInstruction(inst);
            }
        }

        return basicBlock;
    }

    private static ArrayList<String> getSummaryFiles() {
        ArrayList<String> files = new ArrayList<>();
        try {
            CodeSource src = SummaryParser.class.getProtectionDomain().getCodeSource();
            if (src != null) {
                URL jar = src.getLocation();
                ZipInputStream zip = new ZipInputStream(jar.openStream());
                while(true) {
                    ZipEntry e = zip.getNextEntry();
                    if (e == null)
                        break;
                    String name = e.getName();
                    if (name.startsWith("summaries") && name.endsWith(".swanir")) {
                        files.add(name);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return files;
    }

    private static ArrayList<String> readFiles(ArrayList<String> files) {
        ArrayList<String> lines = new ArrayList<>();

        for (String file : files) {
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(SummaryParser.class.getClassLoader().getResourceAsStream(file)));
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().length() > 0) {
                        lines.add(line.trim());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return lines;
    }
}
