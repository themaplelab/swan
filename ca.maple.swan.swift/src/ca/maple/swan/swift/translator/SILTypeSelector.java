//===--- SILTypeSelector.java -----------------------------------------------===//
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

package ca.maple.swan.swift.translator;

import ca.maple.swan.swift.types.AnyCAstType;
import com.ibm.wala.cast.tree.CAstType;

/*
 * This class selects a type based on the given string representing a SIL type.
 */
public class SILTypeSelector {

    static CAstType select(String silType) {
        switch(silType) {
            default: return new AnyCAstType();
        }
    }
}
