#ifndef SWIFTWALATRANSLATOR_WALAINSTANCE_H
#define SWIFTWALATRANSLATOR_WALAINSTANCE_H

#include <jni.h>
#include <string>
#include <sstream>
#include <cstring>
#include <memory>

namespace swift {
    class SILModule;
}

class CAstWrapper;

namespace swift_wala {

class WALAInstance {
private:
  JNIEnv *JavaEnv;
  jobject XLator;
  std::string File;

public:
  CAstWrapper *CAst;

  jobject makeBigDecimal(const char *, int);
  void print(jobject Object);
  void analyze();
  void analyzeSILModule(swift::SILModule &SM);
  explicit WALAInstance(JNIEnv* Env, jobject Obj);
};
}

#endif //SWIFTWALATRANSLATOR_WALAINSTANCE_H

