cmake_minimum_required(VERSION 3.5.1)
project(SwiftWalaTranslator)

# SET THIS FOR C++ IDE
# set(PACKAGES_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../packages/")

set(WALA_DIR "${PACKAGES_DIR}/WALA")
set(SWIFT_PATH_TO_LLVM_SOURCE "${PACKAGES_DIR}/llvm")

if (APPLE)
set(WALA_PATH_TO_SWIFT_BUILD "${PACKAGES_DIR}/build/Ninja-DebugAssert/swift-macosx-x86_64")
set(SWIFT_PATH_TO_LLVM_BUILD "${PACKAGES_DIR}/build/Ninja-DebugAssert/llvm-macosx-x86_64")
set(SWIFT_PATH_TO_CMARK_BUILD "${PACKAGES_DIR}/build/Ninja-DebugAssert/cmark-macosx-x86_64")
endif()

if (UNIX AND NOT APPLE)
set(WALA_PATH_TO_SWIFT_BUILD "${PACKAGES_DIR}/build/Ninja-DebugAssert/swift-linux-x86_64")
set(SWIFT_PATH_TO_LLVM_BUILD "${PACKAGES_DIR}/build/Ninja-DebugAssert/llvm-linux-x86_64")
set(SWIFT_PATH_TO_CMARK_BUILD "${PACKAGES_DIR}/build/Ninja-DebugAssert/cmark-linux-x86_64")
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