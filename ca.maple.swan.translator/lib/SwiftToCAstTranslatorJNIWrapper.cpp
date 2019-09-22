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
Java_ca_maple_swan_swift_translator_SwiftToCAstTranslator_translateToCAstNodes(JNIEnv *env, jobject obj, jobject args)
{
    WALAInstance Instance(env, obj);

    // Convert given ArrayList<String> to std::list<string>.
    // Credit: https://gist.github.com/qiao-tw/6e43fb2311ee3c31752e11a4415deeb1
    auto java_util_ArrayList      = static_cast<jclass>(env->NewGlobalRef(env->FindClass("java/util/ArrayList")));
    auto java_util_ArrayList_size = env->GetMethodID (java_util_ArrayList, "size", "()I");
    auto java_util_ArrayList_get  = env->GetMethodID(java_util_ArrayList, "get", "(I)Ljava/lang/Object;");
    jint len = env->CallIntMethod(args, java_util_ArrayList_size);
    std::list<std::string> result;
    for (jint i=0; i<len; i++) {
      jstring element = static_cast<jstring>(env->CallObjectMethod(args, java_util_ArrayList_get, i));
      const char* pchars = env->GetStringUTFChars(element, nullptr);
      result.push_back(pchars);
      env->ReleaseStringUTFChars(element, pchars);
      env->DeleteLocalRef(element);
    }

    Instance.analyze(result);
    return Instance.getRoots();
}
