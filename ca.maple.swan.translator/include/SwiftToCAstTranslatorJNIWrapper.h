//===--- SwiftToCAstTranslatorJNIWrapper.h - JNI translator bridge -------===//
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
/// This file defines the JNI method called by the Java side of the
/// framework. The method fires up WALAInstance, which starts the
/// compilation/translation process.
///
//===---------------------------------------------------------------------===//

#ifndef SWAN_SWIFTTOCASTJNIWRAPPER_H
#define SWAN_SWIFTTOCASTJNIWRAPPER_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif // __cplusplus

/// JNI method called from the Java (analysis) side to start
/// the translation process.
JNIEXPORT jobject JNICALL
Java_ca_maple_swan_swift_translator_SwiftToCAstTranslator_translateToCAstNodes(JNIEnv *env, jobject obj);

#ifdef __cplusplus
}
#endif // __cplusplus

#endif // SWAN_SWIFTTOCASTJNIWRAPPER_H
