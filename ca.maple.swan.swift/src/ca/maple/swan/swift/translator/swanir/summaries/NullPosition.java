//===--- NullPosition.java -----------------------------------------------===//
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

import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.classLoader.IMethod;

import java.io.IOException;
import java.io.Reader;
import java.net.URL;


/*
 * Needed for summary instructions since they have no position.
 */
public class NullPosition implements CAstSourcePositionMap.Position {
    @Override
    public URL getURL() {
        return null;
    }

    @Override
    public Reader getReader() throws IOException {
        return null;
    }

    @Override
    public int getFirstLine() {
        return 0;
    }

    @Override
    public int getLastLine() {
        return 0;
    }

    @Override
    public int getFirstCol() {
        return 0;
    }

    @Override
    public int getLastCol() {
        return 0;
    }

    @Override
    public int getFirstOffset() {
        return 0;
    }

    @Override
    public int getLastOffset() {
        return 0;
    }

    @Override
    public int compareTo(IMethod.SourcePosition o) {
        return 0;
    }

    @Override
    public String toString() {
        return "";
    }
}
