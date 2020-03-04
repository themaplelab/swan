//===--- Tester.java -----------------------------------------------------===//
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

package ca.maple.swan.swift.test;

import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

import java.util.List;

public abstract class Tester {

    protected Tester() {
        parser = configureParser();
    }

    protected ArgumentParser parser;

    protected abstract void doTest(Namespace ns);

    protected abstract void verifyAndReportResults(List<List<CAstSourcePositionMap.Position>> results);

    protected abstract ArgumentParser configureParser();
}
