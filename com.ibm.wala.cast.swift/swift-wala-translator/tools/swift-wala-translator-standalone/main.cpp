//
// Created by leo on 01/07/18.
//

#include <swift-wala/WALASupport/WALAInstance.h>
#include <CAstWrapper.h>
#include <iostream>

int main(int argc, const char *argv[]) {
  if (argc <= 1)
    return 0;
  std::stringstream PathBuilder;
  auto WalaHome = std::getenv("WALA_HOME");
  auto SwiftWalaHome = std::getenv("SWIFT_WALA_HOME");
  PathBuilder << WalaHome << "/com.ibm.wala.util/build/classes/java/main:" << WalaHome
              << "/com.ibm.wala.shrike/build/classes/java/main:" << WalaHome
              << "/com.ibm.wala.core/build/classes/java/main:"
              << WalaHome << "/com.ibm.wala.cast/build/classes/java/main:" << SwiftWalaHome
              << "/com.ibm.wala.cast.swift/build/classes/java/main:" << SwiftWalaHome
              << "/com.ibm.wala.cast.swift/build/libs/translator/shared";

  std::cout << PathBuilder.str() << std::endl;
  char *Path = strdup(PathBuilder.str().c_str());
  auto JavaEnv = launch_jvm(Path);
  TRY(Exception, JavaEnv)
      auto XLatorClass = JavaEnv->FindClass("com/ibm/wala/cast/swift/SwiftToCAstTranslator");
      THROW_ANY_EXCEPTION(Exception);
      auto XLatorInit = JavaEnv->GetMethodID(XLatorClass, "<init>", "(Ljava/lang/String;)V");
      THROW_ANY_EXCEPTION(Exception);
      auto SourceName = JavaEnv->NewStringUTF(argv[1]);
      THROW_ANY_EXCEPTION(Exception);
      auto XLator = JavaEnv->NewObject(XLatorClass, XLatorInit, SourceName);
      THROW_ANY_EXCEPTION(Exception);
      auto Instance = swift_wala::WALAInstance(JavaEnv, XLator);
      Instance.analyze();
  CATCH()
}
