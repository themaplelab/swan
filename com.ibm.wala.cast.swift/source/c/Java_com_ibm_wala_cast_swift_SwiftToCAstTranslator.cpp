#include <jni.h>
#include <stdio.h>

JNIEXPORT jobject JNICALL
Java_com_ibm_wala_cast_swift_SwiftToCAstTranslator_translateToCAst(JNIEnv *env, jobject obj)
{
    printf("Hello World!\n");
}