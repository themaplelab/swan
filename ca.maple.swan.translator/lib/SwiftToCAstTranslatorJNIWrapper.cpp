//===--- SwiftToCAstTranslatorJNIWrapper.cpp - JNI translator bridge -----===//
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
///
/// This file implements the JNI method called by the Java side of the
/// framework. The method fires up WALAInstance, which starts the
/// compilation/translation process.
///
//===---------------------------------------------------------------------===//

#include "SwiftToCAstTranslatorJNIWrapper.h"
#include <jni.h>
#include <memory>
#include <WALAInstance.h>

using namespace swan;

JNIEXPORT jobject JNICALL
Java_ca_maple_swan_swift_translator_wala_SwiftToCAstTranslator_translateToCAstNodes(JNIEnv *env, jobject obj, jobject args)
{
    WALAInstance Instance(env, obj, args);
    return Instance.getRoots();
}
