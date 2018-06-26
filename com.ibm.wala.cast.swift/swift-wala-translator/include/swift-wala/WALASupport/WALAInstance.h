#ifndef SWIFTWALATRANSLATOR_WALAINSTANCE_H
#define SWIFTWALATRANSLATOR_WALAINSTANCE_H

#include <jni.h>
#include <CAstWrapper.h>
#include <launch.h>
#include <string>
#include <sstream>
#include <cstring>
#include <memory>

namespace swift {
    class SILModule;
}

namespace swift_wala {
class WALAInstance : public std::enable_shared_from_this<WALAInstance> {
private:
  JNIEnv *JavaEnv;
  const jobject XLator;
  std::string File;

private:
    void WALAInstance::analyzeSILModule(swift::SILModule &SM);

public:
  std::unique_ptr<CAstWrapper> CAst;

  void print(jobject Object);
  void analyze();
  explicit WALAInstance(JNIEnv* Env, jobject XLator) : JavaEnv(Env), XLator(XLator) {
    TRY(Exception, JavaEnv)
        CAst = std::make_unique<CAstWrapper>(JavaEnv, Exception, XLator);
        auto XLatorClass = JavaEnv->FindClass("com/ibm/wala/cast/swift/SwiftToCAstTranslator");
        THROW_ANY_EXCEPTION(Exception);
        auto GetLocalFile = JavaEnv->GetMethodID(XLatorClass, "getLocalFile", "()Ljava/lang/String;");
        THROW_ANY_EXCEPTION(Exception);
        auto LocalFile = dynamic_cast<jstring>(JavaEnv->CallObjectMethod(XLator, GetLocalFile, 0));
        THROW_ANY_EXCEPTION(Exception);
        auto LocalFileStr = JavaEnv->GetStringUTFChars(LocalFile, 0);
        THROW_ANY_EXCEPTION(Exception);
        File = std::string(LocalFileStr);
        JavaEnv->ReleaseStringUTFChars(LocalFile, LocalFileStr);
        THROW_ANY_EXCEPTION(Exception);
    CATCH()
  };
};
}

#endif //SWIFTWALATRANSLATOR_WALAINSTANCE_H
