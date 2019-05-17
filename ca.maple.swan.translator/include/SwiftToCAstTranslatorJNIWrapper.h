/******************************************************************************
 * Copyright (c) 2019 Maple @ University of Alberta
 * All rights reserved. This program and the accompanying materials (unless
 * otherwise specified by a license inside of the accompanying material)
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *****************************************************************************/

#include <jni.h>
#ifndef _SwiftToCAstTranslatorJNIWrapper
#define _SwiftToCAstTranslatorJNIWrapper
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL
Java_ca_maple_swan_analysis_translator_SwiftToCAstTranslator_translateToCAstNodes(JNIEnv *env, jobject obj);

#ifdef __cplusplus
}
#endif
#endif
