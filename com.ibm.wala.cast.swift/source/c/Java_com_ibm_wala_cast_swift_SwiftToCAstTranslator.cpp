#include <jni.h>
#include <memory>
#include <swift-wala/WALASupport/WALAInstance.h>

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
