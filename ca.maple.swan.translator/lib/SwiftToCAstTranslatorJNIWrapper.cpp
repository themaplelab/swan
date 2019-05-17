/******************************************************************************
 * Copyright (c) 2019 Maple @ University of Alberta
 * All rights reserved. This program and the accompanying materials (unless
 * otherwise specified by a license inside of the accompanying material)
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *****************************************************************************/

#include <jni.h>
#include <memory>
#include <WALAInstance.h>

using namespace swift_wala;

extern "C" {

JNIEXPORT jobject JNICALL
Java_com_ibm_wala_cast_swift_SwiftToCAstTranslator_translateToCAstNodes(JNIEnv *env, jobject obj)
	{
	  WALAInstance Instance(env, obj);
	  Instance.analyze();
	  // Make your Java Array here
	  return Instance.getCAstNodes();
	}
}
