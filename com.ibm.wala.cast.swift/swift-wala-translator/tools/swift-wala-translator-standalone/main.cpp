/******************************************************************************
 * Copyright (c) 2019 Maple @ University of Alberta
 * All rights reserved. This program and the accompanying materials (unless
 * otherwise specified by a license inside of the accompanying material)
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *    Ao Li (Github: Leeleo3x) - initial implementation
 *    Daniil Tiganov (Github: tiganov) - documentation, QC changes
 *****************************************************************************/

//----------------------------------------------------------------------------/
/// PROGRAM DESCRIPTION
/// This program takes in a Swift file as a CMD parameter, and analyzes it
/// using the swift-wala translator and WALA.
///
/// The program currently can only take and analyze a single Swift file.
///
/// TECHNICAL OVERVIEW
/// First, all paths needed for the JVM are assembled. These paths consist of
/// WALA and swift-wala paths which point to .class and dynamic libraries.
/// A JVM is launched using the specified paths. The CAstWrapper provided by
/// WALA is used to handle JNI calls to the JVM. The swift-wala translator is
/// available and used as dynamic library (C++). The translator is bridged to
/// WALA using a Java class. Lastly, a WALAInstance is called, which hooks
/// into the Swift compiler and passes SIL to the translator.
//----------------------------------------------------------------------------/

#include <swift-wala/WALASupport/WALAInstance.h>
#include <CAstWrapper.h>
#include <iostream>
#include <string>

int main(int argc, const char *argv[]) {
  if (argc <= 1) {
    std::cout << "No Swift file specified. Exiting..." << std::endl;
    return 0;
  }

  if (argc > 2) {
    std::cout << "Only a single Swift file is currently supported!" << std::endl;
  }

  std::string first_param = argv[1];
  if (first_param.substr(first_param.find_last_of(".") + 1) != "swift") {
    std::cout << "File is not a Swift file. Exiting..." << std::endl;
    return 0;
  }

  std::stringstream PathBuilder;
  auto WalaHome = std::getenv("WALA_DIR");
  auto SwiftWalaHome = std::getenv("SWIFT_WALA_DIR");
  PathBuilder << WalaHome << "/com.ibm.wala.util/build/classes/java/main:" << WalaHome
              << "/com.ibm.wala.shrike/build/classes/java/main:" << WalaHome
              << "/com.ibm.wala.core/build/classes/java/main:"
              << WalaHome << "/com.ibm.wala.cast/build/classes/java/main:" << SwiftWalaHome
              << "/com.ibm.wala.cast.swift/build/classes/java/main:" << SwiftWalaHome
              << "/com.ibm.wala.cast.swift/build/libs/translator/shared";

  std::cout << "-=- PATH BUILDER -=-" << std::endl << "=========================" << std::endl;
  std::cout << PathBuilder.str() << std::endl;
  std::cout << "=========================" << std::endl;
  
  char *Path = strdup(PathBuilder.str().c_str());

  auto JavaEnv = launch_jvm(Path);

  TRY(Exception, JavaEnv)
      // find the bridge
      auto TranslatorClass = JavaEnv->FindClass("com/ibm/wala/cast/swift/SwiftToCAstTranslator");
      THROW_ANY_EXCEPTION(Exception);

      // get the class constructor method
      auto TranslatorInit = JavaEnv->GetMethodID(TranslatorClass, "<init>", "(Ljava/lang/String;)V");
      THROW_ANY_EXCEPTION(Exception);

      // take the given Swift file name and convert it to Java string
      auto SourceName = JavaEnv->NewStringUTF(argv[1]);
      THROW_ANY_EXCEPTION(Exception);

      // create the SwiftToCAstTranslator object
      auto Translator = JavaEnv->NewObject(TranslatorClass, TranslatorInit, SourceName);
      THROW_ANY_EXCEPTION(Exception);

      // start the WALAInstance, which hooks into the Swift compiler and will
      // pass the SIL to the translator
      auto Instance = swift_wala::WALAInstance(JavaEnv, Translator);

      Instance.analyze();
  CATCH()
      // TODO: Report exceptions to user
}
