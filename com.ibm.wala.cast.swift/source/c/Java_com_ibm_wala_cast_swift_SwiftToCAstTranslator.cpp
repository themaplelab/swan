#include <jni.h>
#include <memory>
#include <swift-wala/WALASupport/WALAInstance.h>

using namespace swift_wala;

JNIEXPORT jobject JNICALL
Java_com_ibm_wala_cast_swift_SwiftToCAstTranslator_translateToCAst(JNIEnv *env, jobject obj)
{
  WALAInstance Instance(env, obj);
  Instance.analyze();
}
