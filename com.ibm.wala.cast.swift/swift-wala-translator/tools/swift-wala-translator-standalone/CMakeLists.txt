add_executable(swift-wala-translator-standalone main.cpp)
target_link_libraries(swift-wala-translator-standalone swiftWala
        ${JNI_LIBRARIES}
        ${WALA_DIR}/com.ibm.wala.cast/build/libs/cast/shared/libcast.so)
