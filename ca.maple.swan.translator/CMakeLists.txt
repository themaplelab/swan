cmake_minimum_required(VERSION 3.5.1)
project(SwiftWalaTranslator)

# CHANGE THESE
if(NOT SWAN)
set(WALA_DIR "/home/bob/swift-source/WALA")
set(WALA_PATH_TO_SWIFT_BUILD "/home/bob/swift-source/build/Ninja-DebugAssert/swift-linux-x86_64")
set(SWIFT_PATH_TO_LLVM_SOURCE "/home/bob/swift-source/llvm")
set(SWIFT_PATH_TO_LLVM_BUILD "/home/bob/swift-source/build/Ninja-DebugAssert/llvm-linux-x86_64")
set(SWIFT_PATH_TO_CMARK_BUILD "/home/bob/swift-source/build/Ninja-DebugAssert/cmark-linux-x86_64")
endif()

list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake/modules")
set(Clang_DIR "${SWIFT_PATH_TO_LLVM_BUILD}/lib/cmake/clang")
set(LLVM_DIR "${SWIFT_PATH_TO_LLVM_BUILD}/lib/cmake/llvm")
set(CMAKE_C_COMPILER "clang" CACHE STRING "C Compiler")
set(CMAKE_CXX_COMPILER "clang++" CACHE STRING "C++ Compiler")
set(CMAKE_INSTALL_RPATH_USE_LINK_PATH TRUE)

include(SwiftWalaUtils)
include(SwiftWalaSharedCMakeConfig)

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)
set(CMAKE_CXX_STANDARD 14)
add_compile_options(-fno-rtti)

find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})
include_directories(${WALA_DIR}/com.ibm.wala.cast/source/c/include)
link_directories(${WALA_DIR}/com.ibm.wala.cast/cast/build/lib/main/debug/)

add_subdirectory(lib)
