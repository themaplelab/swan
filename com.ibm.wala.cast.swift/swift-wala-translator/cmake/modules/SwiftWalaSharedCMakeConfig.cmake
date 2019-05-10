precondition(WALA_PATH_TO_SWIFT_BUILD)
set(WALA_SWIFT_CMAKE_PATHS
    "${WALA_PATH_TO_SWIFT_BUILD}/share/swift/cmake"
    "${WALA_PATH_TO_SWIFT_BUILD}/lib/cmake/swift")
foreach(path ${WALA_SWIFT_CMAKE_PATHS})
  list(APPEND CMAKE_MODULE_PATH ${path})
endforeach()
find_package(Swift REQUIRED CONFIG
    HINTS "${WALA_PATH_TO_SWIFT_BUILD}" NO_DEFAULT_PATH)
include_directories(${SWIFT_INCLUDE_DIR})
link_directories(${SWIFT_LIBRARY_DIR})

list(APPEND CMAKE_MODULE_PATH "${SWIFT_MAIN_SRC_DIR}/cmake/modules")

include(SwiftUtils)
include(SwiftSharedCMakeConfig)
set(SWIFT_INCLUDE_TOOLS ON)
swift_common_standalone_build_config(SWIFT FALSE)

